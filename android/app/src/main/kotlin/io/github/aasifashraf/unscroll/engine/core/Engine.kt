package io.github.aasifashraf.unscroll.engine.core

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import io.github.aasifashraf.unscroll.MainActivity
import io.github.aasifashraf.unscroll.engine.access.UnscrollAccessibilityService
import io.github.aasifashraf.unscroll.engine.clock.ClockReading
import io.github.aasifashraf.unscroll.engine.clock.DayClock
import io.github.aasifashraf.unscroll.engine.clock.DayState
import io.github.aasifashraf.unscroll.engine.overlay.OverlayController
import io.github.aasifashraf.unscroll.engine.rules.BlockReason
import io.github.aasifashraf.unscroll.engine.rules.Decision
import io.github.aasifashraf.unscroll.engine.rules.RuleEngine
import io.github.aasifashraf.unscroll.engine.rules.RuleInput
import io.github.aasifashraf.unscroll.engine.rules.UrlRules
import io.github.aasifashraf.unscroll.engine.service.GuardService
import io.github.aasifashraf.unscroll.engine.service.Notifications
import io.github.aasifashraf.unscroll.engine.store.FocusRecord
import io.github.aasifashraf.unscroll.engine.store.PersistedState
import io.github.aasifashraf.unscroll.engine.store.RulesStore
import io.github.aasifashraf.unscroll.engine.system.SystemApps
import io.github.aasifashraf.unscroll.engine.usage.AppUsage
import io.github.aasifashraf.unscroll.engine.usage.UsageAggregator
import io.github.aasifashraf.unscroll.engine.usage.UsageReader
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * Glue between Android and the pure core. All decisions run on one background thread;
 * overlays are shown on the main thread. Survives the Flutter UI being closed.
 *
 * Two time frames: rules (focus, quick block, passes, schedules) use DayClock's tamper-resistant
 * "trusted" time; Android's usage history uses the device wall clock (see DayClock.wallDayStart).
 */
// Holds only the application context, and the accessibility service only while it is bound
// (cleared in attachAccessibility(null) from onUnbind/onDestroy), so nothing leaks.
@SuppressLint("StaticFieldLeak")
object Engine {
    const val PASS_MILLIS = 3 * 60_000L
    const val MAX_EXTENSIONS_PER_DAY = 2
    private const val ADDRESS_RECHECK_DELAY_MILLIS = 800L
    private val WARN_BEFORE = listOf(5 * 60_000L, 60_000L)

    lateinit var store: RulesStore
        private set
    lateinit var usage: UsageReader
        private set
    lateinit var system: SystemApps
        private set
    private lateinit var app: Context
    private lateinit var overlay: OverlayController
    private lateinit var worker: Handler
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    var accessibility: AccessibilityService? = null
        private set

    // Worker-thread state.
    private var foreground: String? = null

    /** The foreground app has an app-level block screen; the website check must not replace it. */
    private var appBlocked: String? = null
    private var neverBlock: Set<String> = emptySet()
    private var trackable: Set<String> = emptySet()
    private var urgent: Set<String> = emptySet()
    private var lastFocusEnd: Long? = null
    private val passes = ConcurrentHashMap<String, Long>()
    private val timerToken = Any()

    @Volatile
    private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        app = context.applicationContext
        store = RulesStore(app)
        usage = UsageReader(app)
        system = SystemApps(app)
        overlay = OverlayController(app, accessibilityService = { accessibility }, onGoHome = ::goHome, onGoBack = ::goBack)
        worker = Handler(HandlerThread("unscroll-engine").apply { start() }.looper)
        initialized = true
        // First in the queue, so events that arrive during start-up see the app lists.
        worker.post {
            loadSystemApps()
            today()
            scheduleTimedEnds(store.state)
        }
    }

    /** Safe to call before [init]: a fresh process can be unbound before it was ever connected. */
    fun attachAccessibility(service: AccessibilityService?) {
        if (service != null) init(service)
        accessibility = service
        if (!initialized) return
        if (service == null) {
            main.post { overlay.dismiss() }
            // Forget the current app, so the next event (from either source) decides afresh.
            worker.post {
                foreground = null
                appBlocked = null
            }
        } else {
            onUserPresent() // an app may already be on top (e.g. the service restarted)
        }
        GuardService.start(app)
    }

    /** Re-read launchable apps, launchers, keyboards and default phone/SMS apps. */
    fun refreshSystemApps() {
        if (initialized) worker.post { loadSystemApps() }
    }

    private fun loadSystemApps() {
        neverBlock = system.neverBlock()
        trackable = system.launchablePackages() + system.launcherPackages()
        urgent = system.urgentPackages()
    }

    /**
     * A window came to the foreground (from accessibility or usage polling). [className] tells
     * Unscroll's own screen apart from its overlay windows (same package).
     */
    fun onForeground(packageName: String, className: String?) {
        if (!initialized) return
        worker.post {
            if (packageName == app.packageName) {
                // The user opened Unscroll (e.g. to change the rule that's blocking them): get out of the way.
                if (className == MainActivity::class.java.name) unscrollIsForeground()
                return@post
            }
            process(packageName, force = false)
        }
    }

    fun onScreenOff() {
        if (!initialized) return
        worker.post {
            foreground = null
            appBlocked = null
            worker.removeCallbacksAndMessages(timerToken)
            scheduleTimedEnds(store.state)
            // Never leave a block screen over the lock screen, an incoming call or an emergency alert.
            main.post { overlay.dismiss() }
        }
    }

    /** The phone was unlocked: re-check whatever app is on top (it may send no new window event). */
    fun onUserPresent() {
        if (!initialized) return
        worker.post {
            refreshNeverBlockIfStale()
            val top = topPackage() ?: return@post
            if (top == app.packageName) unscrollIsForeground() else process(top, force = true)
        }
    }

    /** Text from a browser address bar changed. */
    fun onAddressBar(browserPackage: String, text: String) {
        if (!initialized) return
        worker.post {
            // appBlocked is checked here, on the worker, because the app's block screen may not be
            // attached yet (it is posted to the main thread).
            if (foreground == app.packageName || appBlocked == browserPackage || overlay.isShowingFor(browserPackage)) return@post
            val engine = store.state.engine
            if (UrlRules.isBlocked(text, engine.blockedSites, engine.blockedKeywords)) {
                // Show the explanation first; the user's "Go back" leaves the page. Pressing Back
                // ourselves would switch apps and dismiss the screen before it could be read.
                main.post { overlay.showBlock(browserPackage, label(browserPackage), BlockReason.WEBSITE, null, canExtend = false) {} }
            }
        }
    }

    private fun process(pkg: String, force: Boolean) {
        if (!EngineLogic.isAppSwitch(pkg, app.packageName, trackable, urgent)) return // shade, keyboard, dialogs
        // Same app: in-app windows (dialogs, new screens) don't re-decide. Limits and schedules
        // that start mid-session are covered by timers.
        if (!force && pkg == foreground) return
        foreground = pkg
        appBlocked = null
        worker.removeCallbacksAndMessages(timerToken)
        main.post { overlay.dismissIfNot(pkg) }

        val wallNow = System.currentTimeMillis()
        val day = today()
        val now = day.trustedWallMillis
        val state = store.state
        if (pkg in neverBlock) {
            main.post { overlay.dismiss() }
            scheduleTimedEnds(state)
            return
        }
        val stats = usageToday(pkg, day, wallNow)
        val decision = RuleEngine.decide(
            RuleInput(
                packageName = pkg,
                nowMillis = now,
                localTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), day.zone),
                state = state.engine,
                usageToday = stats,
                passUntil = passes[pkg],
                neverBlock = neverBlock,
                zone = day.zone,
            ),
        )
        act(pkg, decision, stats.opens, now)
        scheduleTimedEnds(state)
    }

    /** Today's usage of [pkg], counted in the wall-clock frame of Android's usage history. */
    private fun usageToday(pkg: String, day: DayState, wallNow: Long): AppUsage {
        val wallStart = DayClock.wallDayStart(day, wallNow)
        val result = usage.read(wallStart - UsageReader.LOOKBACK_MILLIS, wallNow)
        val stats = UsageAggregator.aggregate(result.events, wallStart, wallNow)[pkg] ?: AppUsage(0, 0)
        // The accessibility event can arrive before the system logs the open.
        val opens = stats.opens + if (UsageAggregator.foregroundAt(result.events, wallNow) == pkg) 0 else 1
        return AppUsage(stats.millis, opens)
    }

    private fun act(pkg: String, decision: Decision, opens: Int, now: Long) {
        when (decision) {
            is Decision.Block -> {
                appBlocked = pkg
                val canExtend = decision.reason == BlockReason.DAILY_LIMIT &&
                    (store.state.extensionsUsedToday[pkg] ?: 0) < MAX_EXTENSIONS_PER_DAY
                main.post {
                    overlay.showBlock(pkg, label(pkg), decision.reason, decision.until, canExtend) {
                        worker.post { extend(pkg) }
                    }
                }
                decision.until?.let { recheckAt(it, now, pkg) }
            }
            is Decision.Pause -> main.post {
                overlay.showPause(pkg, label(pkg), decision.seconds, opens) {
                    worker.post {
                        passes[pkg] = today().trustedWallMillis + PASS_MILLIS
                        process(pkg, force = true) // limits still apply after the pause
                    }
                }
            }
            is Decision.Allow -> {
                main.post { overlay.dismissFor(pkg) }
                decision.remainingMillis?.let { remaining ->
                    WARN_BEFORE.filter { remaining > it }.forEach { before ->
                        worker.postAtTime(
                            { if (foreground == pkg) Notifications.limitWarning(app, label(pkg), before / 60_000) },
                            timerToken,
                            SystemClock.uptimeMillis() + remaining - before,
                        )
                    }
                    recheckAt(now + remaining, now, pkg)
                }
                decision.nextChangeAt?.let { recheckAt(it, now, pkg) }
            }
        }
    }

    /** Re-decide [pkg] at trusted time [target] if it's still in front. */
    private fun recheckAt(target: Long, now: Long, pkg: String) {
        worker.postAtTime({ if (foreground == pkg) process(pkg, force = true) }, timerToken, uptimeIn(target - now + 500))
    }

    private fun uptimeIn(delayMillis: Long) = SystemClock.uptimeMillis() + delayMillis.coerceAtLeast(0)

    /** Clears expired focus sessions and quick blocks, and schedules the next expiry. */
    private fun scheduleTimedEnds(state: PersistedState) {
        val now = today().trustedWallMillis
        val focus = state.engine.focus
        if (focus != null && now >= focus.endsAt) {
            store.update {
                it.copy(
                    engine = it.engine.copy(focus = null),
                    focusHistory = it.focusHistory + FocusRecord(focus.startedAt, focus.endsAt, completed = true),
                )
            }
            Notifications.focusDone(app)
        } else if (focus != null) {
            worker.postAtTime({ scheduleTimedEnds(store.state) }, timerToken, uptimeIn(focus.endsAt - now))
        }
        val quick = state.engine.quickBlockUntil
        if (quick != null && now >= quick) {
            store.update { it.copy(engine = it.engine.copy(quickBlockUntil = null)) }
        } else if (quick != null) {
            worker.postAtTime({ scheduleTimedEnds(store.state) }, timerToken, uptimeIn(quick - now))
        }
        val focusEnd = store.state.engine.focus?.endsAt
        if (focusEnd != lastFocusEnd) {
            lastFocusEnd = focusEnd
            GuardService.refresh(app) // focus countdown in the notification
        }
    }

    private fun extend(pkg: String) {
        val rule = store.state.engine.rules[pkg] ?: return
        val limit = rule.dailyLimitMinutes ?: return
        val wallNow = System.currentTimeMillis()
        val used = usageToday(pkg, today(), wallNow).millis
        var granted = false
        store.update {
            val count = it.extensionsUsedToday[pkg] ?: 0
            if (count >= MAX_EXTENSIONS_PER_DAY) return@update it // second tap of a double tap
            granted = true
            val extra = EngineLogic.extraMinutesAfterExtension(limit, it.engine.extraMinutesToday[pkg] ?: 0, used)
            it.copy(
                engine = it.engine.copy(extraMinutesToday = it.engine.extraMinutesToday + (pkg to extra)),
                extensionsUsedToday = it.extensionsUsedToday + (pkg to count + 1),
            )
        }
        if (granted) process(pkg, force = true)
    }

    /**
     * Advances the tamper-resistant day (atomically with other state changes); resets per-day
     * extras when a new day begins. Callable from any thread.
     */
    fun today(): DayState {
        val reading = ClockReading(
            wallMillis = System.currentTimeMillis(),
            elapsedMillis = SystemClock.elapsedRealtime(),
            bootCount = Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, 0),
            zone = ZoneId.systemDefault(),
            autoTime = Settings.Global.getInt(app.contentResolver, Settings.Global.AUTO_TIME, 1) == 1,
        )
        var result: DayState? = null
        store.update { st ->
            val previous = st.day
            val next = DayClock.advance(previous, reading)
            result = next
            when {
                previous == null || next.day != previous.day ->
                    st.copy(day = next, engine = st.engine.copy(extraMinutesToday = emptyMap()), extensionsUsedToday = emptyMap())
                next.trustedWallMillis - previous.trustedWallMillis > 60_000 || next.bootCount != previous.bootCount ->
                    st.copy(day = next) // persist occasionally, not on every event
                else -> st
            }
        }
        return result!!
    }

    /** Current tamper-resistant time (the frame focus sessions and quick blocks are stored in). */
    fun trustedNow(): Long = today().trustedWallMillis

    fun goHome() {
        val service = accessibility
        if (service != null) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        } else {
            app.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        main.post { overlay.dismiss() }
    }

    fun goBack() {
        val service = accessibility ?: return goHome()
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        main.post { overlay.dismiss() }
        // If Back left the address unchanged (single-page site), the block must come back.
        main.postDelayed({ (accessibility as? UnscrollAccessibilityService)?.recheckAddressBar() }, ADDRESS_RECHECK_DELAY_MILLIS)
    }

    /**
     * Unscroll's own screen is showing (called on the main thread from MainActivity). Removes any
     * block screen right away so the user can always reach their settings.
     */
    fun onUnscrollOpened() {
        if (!initialized) return
        overlay.dismiss()
        worker.post {
            unscrollIsForeground()
            refreshNeverBlockIfStale()
        }
    }

    private fun unscrollIsForeground() {
        foreground = app.packageName
        appBlocked = null
        worker.removeCallbacksAndMessages(timerToken)
        main.post { overlay.dismiss() }
        scheduleTimedEnds(store.state) // keep the focus-end timer
    }

    private var neverBlockLoadedAt = 0L

    /** Default SMS app or keyboard may have changed; reload at most once a minute. */
    private fun refreshNeverBlockIfStale() {
        val now = SystemClock.elapsedRealtime()
        if (now - neverBlockLoadedAt < 60_000) return
        neverBlockLoadedAt = now
        loadSystemApps()
    }

    private fun topPackage(): String? {
        val fromAccessibility = try {
            accessibility?.rootInActiveWindow?.packageName?.toString()
        } catch (_: RuntimeException) {
            null
        }
        if (fromAccessibility != null) return fromAccessibility
        val wallNow = System.currentTimeMillis()
        return UsageAggregator.foregroundAt(usage.read(wallNow - 60 * 60_000L, wallNow).events, wallNow)
    }

    /** Called after the UI changes rules: re-evaluate the current app right away. */
    fun onStateChanged() {
        if (!initialized) return
        worker.post {
            scheduleTimedEnds(store.state)
            foreground?.takeIf { it != app.packageName }?.let { process(it, force = true) }
        }
    }

    private fun label(pkg: String) = system.label(pkg)

    val isAccessibilityConnected get() = accessibility != null
}
