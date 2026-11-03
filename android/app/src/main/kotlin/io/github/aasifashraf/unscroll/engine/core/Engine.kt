package io.github.aasifashraf.unscroll.engine.core

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioManager
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
import io.github.aasifashraf.unscroll.engine.rules.AppGroup
import io.github.aasifashraf.unscroll.engine.rules.BlockReason
import io.github.aasifashraf.unscroll.engine.rules.Decision
import io.github.aasifashraf.unscroll.engine.rules.RuleEngine
import io.github.aasifashraf.unscroll.engine.rules.RuleInput
import io.github.aasifashraf.unscroll.engine.rules.SessionTimes
import io.github.aasifashraf.unscroll.engine.rules.UrlRules
import io.github.aasifashraf.unscroll.engine.service.GuardService
import io.github.aasifashraf.unscroll.engine.service.Notifications
import io.github.aasifashraf.unscroll.engine.shortform.ShortFormMap
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

    /** Session-limit sessions per app (elapsedRealtime), with a short grace after leaving. */
    private var sessions: Map<String, EngineLogic.AppSession> = emptyMap()

    /** Cool-down ends per app (elapsedRealtime); loaded from the saved trusted-time copy on first use. */
    private var bootCooldowns: Map<String, Long>? = null

    /** App whose pause screen is up and not yet answered (for the "chose not to open" count). */
    // Volatile: also read by the short-form check on the main thread.
    @Volatile
    private var pausing: String? = null

    /** The foreground app has an app-level block screen; the website check must not replace it. */
    @Volatile
    private var appBlocked: String? = null

    @Volatile
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
        overlay = OverlayController(
            app,
            accessibilityService = { accessibility },
            onGoHome = ::goHome,
            onGoBack = ::goBack,
            onDeclinePause = ::declinePause,
        )
        worker = Handler(HandlerThread("unscroll-engine").apply { start() }.looper)
        initialized = true
        // First in the queue, so events that arrive during start-up see the app lists.
        worker.post {
            loadSystemApps()
            today()
            store.update { EngineLogic.withShortFormRevision(it, ShortFormMap.REVISION) }
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
                leaveForeground(null)
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
            leaveForeground(null) // ends the session; an unanswered pause isn't counted as declined
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
        val previous = foreground
        if (EngineLogic.countsAsDecline(pausing, previous, pkg, urgent)) recordDecline()
        if (pkg != pausing) pausing = null
        foreground = pkg
        appBlocked = null
        worker.removeCallbacksAndMessages(timerToken)
        main.post { overlay.dismissIfNot(pkg) }

        val wallNow = System.currentTimeMillis()
        val day = today()
        val now = day.trustedWallMillis
        val elapsedNow = SystemClock.elapsedRealtime()
        sessions = EngineLogic.sessionsAfterSwitch(sessions, pkg, elapsedNow)
        val state = store.state
        if (pkg in neverBlock) {
            main.post { overlay.dismiss() }
            scheduleTimedEnds(state)
            return
        }
        val stats = usageToday(pkg, state.engine.groups, day, wallNow)
        val decision = RuleEngine.decide(
            RuleInput(
                packageName = pkg,
                nowMillis = now,
                localTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), day.zone),
                state = state.engine,
                usageToday = stats.app,
                passUntil = passes[pkg],
                neverBlock = neverBlock,
                zone = day.zone,
                groupUsageMillis = stats.groups,
                session = SessionTimes(elapsedNow, sessions[pkg]?.startedAt, cooldowns(state, now, elapsedNow)[pkg]),
                inCall = inCall(),
            ),
        )
        act(pkg, decision, stats, now, wallNow, elapsedNow)
        scheduleTimedEnds(state)
    }

    /** Today's usage of one app and the summed usage of each group (group id → millis). */
    private class TodayUsage(val app: AppUsage, val groups: Map<String, Long>)

    /**
     * Today's usage of [pkg] and of [groups], counted in the wall-clock frame of Android's usage
     * history. One read and one aggregate cover the app and every group.
     */
    private fun usageToday(pkg: String, groups: List<AppGroup>, day: DayState, wallNow: Long): TodayUsage {
        val wallStart = DayClock.wallDayStart(day, wallNow)
        val result = usage.read(wallStart - UsageReader.LOOKBACK_MILLIS, wallNow)
        val all = UsageAggregator.aggregate(result.events, wallStart, wallNow)
        val stats = all[pkg] ?: AppUsage(0, 0)
        // The accessibility event can arrive before the system logs the open.
        val opens = stats.opens + if (UsageAggregator.foregroundAt(result.events, wallNow) == pkg) 0 else 1
        return TodayUsage(AppUsage(stats.millis, opens), RuleEngine.groupUsageMillis(groups, all, neverBlock))
    }

    /** Boot-clock cool-down ends per app, loaded once per process from the saved copy. */
    private fun cooldowns(state: PersistedState, trustedNow: Long, elapsedNow: Long): Map<String, Long> =
        bootCooldowns ?: EngineLogic.inBootCooldowns(state.engine.sessionCooldownUntil, trustedNow, elapsedNow)
            .also { bootCooldowns = it }

    /** Ringing or in a phone/VoIP call (no permission needed). */
    private fun inCall(): Boolean = EngineLogic.isCallMode(app.getSystemService(AudioManager::class.java).mode)

    /** A new cool-down for [pkg]: timed by the boot clock, with a trusted-time copy saved for after a reboot. */
    private fun startCooldown(pkg: String, trustedUntil: Long, now: Long, elapsedNow: Long) {
        val inBoot = cooldowns(store.state, now, elapsedNow)
        if ((inBoot[pkg] ?: Long.MIN_VALUE) > elapsedNow) return // this block is the running cool-down
        bootCooldowns = EngineLogic.withCooldown(inBoot, pkg, elapsedNow + (trustedUntil - now), elapsedNow)
        store.update {
            val saved = EngineLogic.withCooldown(it.engine.sessionCooldownUntil, pkg, trustedUntil, now)
            it.copy(engine = it.engine.copy(sessionCooldownUntil = saved))
        }
    }

    private fun act(pkg: String, decision: Decision, stats: TodayUsage, now: Long, wallNow: Long, elapsedNow: Long) {
        when (decision) {
            is Decision.Block -> {
                appBlocked = pkg
                pausing = null
                if (decision.reason == BlockReason.SESSION_LIMIT && decision.until != null) {
                    startCooldown(pkg, decision.until, now, elapsedNow)
                }
                // A used-up group gets its own "5 more minutes", counted per group.
                val extension = EngineLogic.extensionFor(decision.reason, decision.groupId, pkg, store.state.engine, stats.app.millis, stats.groups)
                val canExtend = extension != null && (store.state.extensionsUsedToday[extension.key] ?: 0) < MAX_EXTENSIONS_PER_DAY
                // "Back at HH:MM" on the phone's clock, which may differ from trusted time.
                val untilOnPhone = decision.until?.let { EngineLogic.toWallClock(it, now, wallNow) }
                val groupName = decision.groupId?.let { id -> store.state.engine.groups.firstOrNull { it.id == id } }
                    ?.name?.takeIf { it.isNotBlank() }
                main.post {
                    overlay.showBlock(pkg, label(pkg), decision.reason, untilOnPhone, canExtend, groupName) {
                        worker.post { extend(pkg, decision.reason, decision.groupId) }
                    }
                }
                decision.until?.let { recheckAt(it, now, pkg) }
            }
            is Decision.Pause -> {
                pausing = pkg.takeIf { overlay.canDraw() } // nothing to decline if the screen can't show
                val askIntention = store.state.engine.rules[pkg]?.askIntention == true
                main.post {
                    overlay.showPause(pkg, label(pkg), decision.seconds, stats.app.opens, askIntention) {
                        worker.post {
                            if (pausing == pkg) pausing = null
                            passes[pkg] = today().trustedWallMillis + PASS_MILLIS
                            process(pkg, force = true) // limits still apply after the pause
                        }
                    }
                }
            }
            is Decision.Allow -> {
                pausing = null
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

    /** "5 more minutes" for [pkg]'s own limit, or for the shared limit of group [groupId]. */
    private fun extend(pkg: String, reason: BlockReason, groupId: String?) {
        val engine = store.state.engine
        val stats = usageToday(pkg, engine.groups, today(), System.currentTimeMillis())
        // The tap already removed the block screen: re-decide even when nothing is granted
        // (rule or group removed meanwhile, second tap of a double tap), so it comes back if needed.
        val extension = EngineLogic.extensionFor(reason, groupId, pkg, engine, stats.app.millis, stats.groups)
        if (extension == null) {
            process(pkg, force = true)
            return
        }
        val (key, limit, used) = extension
        store.update {
            val count = it.extensionsUsedToday[key] ?: 0
            if (count >= MAX_EXTENSIONS_PER_DAY) return@update it
            val extra = EngineLogic.extraMinutesAfterExtension(limit, it.engine.extraMinutesToday[key] ?: 0, used)
            it.copy(
                engine = it.engine.copy(extraMinutesToday = it.engine.extraMinutesToday + (key to extra)),
                extensionsUsedToday = it.extensionsUsedToday + (key to count + 1),
            )
        }
        process(pkg, force = true)
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
            val next = DayClock.advance(st.day, reading)
            result = next
            EngineLogic.withDay(st, next)
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
        leaveForeground(app.packageName)
        worker.removeCallbacksAndMessages(timerToken)
        main.post { overlay.dismiss() }
        scheduleTimedEnds(store.state) // keep the focus-end timer
    }

    /**
     * The tracked app is gone from the front (screen off, Unscroll opened, service stopped): its
     * session pauses (it ends unless the app is back within a minute), and an unanswered pause is
     * dropped without counting it. Worker thread.
     */
    private fun leaveForeground(front: String?) {
        foreground = front
        appBlocked = null
        sessions = EngineLogic.sessionsAfterSwitch(sessions, null, SystemClock.elapsedRealtime())
        pausing = null
    }

    /** "I don't need it" or Back on the pause screen for [pkg]. Main thread. */
    private fun declinePause(pkg: String) {
        worker.post {
            // Once per pause, even on a double tap; the home screen that follows then counts nothing.
            if (pausing == pkg) recordDecline()
            pausing = null
        }
        goHome()
    }

    /** Counts one "chose not to open" for today and in total. Worker thread. */
    private fun recordDecline() {
        pausing = null
        today() // a new day first resets today's count
        store.update(EngineLogic::withDecline)
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

    /** Apps whose short-video feed the user blocked (in-memory snapshot, any thread). */
    fun shortFormBlocked(): Set<String> = if (initialized) store.state.engine.shortFormBlocked else emptySet()

    /** The app version the short-form map last matched in for [pkg] (any thread). */
    fun shortFormMatchedVersion(pkg: String): Long? = if (initialized) store.state.shortFormMatchedVersion[pkg] else null

    /**
     * Whether the short-form check may act in [pkg] now: never in Unscroll or never-block apps,
     * and not while a block or pause screen is up or decided. Main thread.
     */
    fun mayCloseShortForm(pkg: String): Boolean = initialized && pkg != app.packageName && pkg !in neverBlock &&
        appBlocked != pkg && pausing != pkg && !overlay.isShowing()

    /** A short-video feed in [pkg] was closed (by the map, or by the fallback). */
    fun onShortFormClosed(pkg: String, appVersion: Long?, byFallback: Boolean) {
        if (!initialized) return
        worker.post {
            val now = today().trustedWallMillis
            store.update { EngineLogic.withShortFormDetection(it, pkg, now, appVersion, byFallback) }
        }
    }

    /** The short-form loop guard tripped in [pkg]: detection may be outdated for this version. */
    fun onShortFormLoop(pkg: String, appVersion: Long?) {
        if (!initialized) return
        worker.post { store.update { EngineLogic.withShortFormSuspect(it, pkg, appVersion) } }
    }
}
