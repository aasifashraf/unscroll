package io.github.aasifashraf.unscroll.engine.shortform

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import io.github.aasifashraf.unscroll.R
import io.github.aasifashraf.unscroll.engine.core.Engine
import io.github.aasifashraf.unscroll.engine.shortform.BackGuard.Action

/**
 * Closes short-video feeds (Reels, Shorts, Spotlight) in apps where the user turned that on,
 * and leaves the rest of the app alone. Looks only at view IDs, class names, sizes and the
 * window's activity name. Never reads text. Main thread only.
 */
class ShortFormWatcher(private val service: AccessibilityService) {
    private val handler = Handler(Looper.getMainLooper())
    private val throttle = Throttle(ShortFormRules.CHECK_INTERVAL_MILLIS)
    private val guard = BackGuard()
    private val pageTurns = PageTurns()
    private val windowClass = HashMap<String, String>()
    private var pending: ShortFormApp? = null

    fun onEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val app = ShortFormMap.forPackage(pkg) ?: return
        if (pkg !in Engine.shortFormBlocked()) return
        val now = SystemClock.elapsedRealtime()
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                event.className?.let { windowClass[pkg] = it.toString() }
                // Let the engine decide on a block or pause screen for the app first.
                schedule(app, now, SETTLE_MILLIS)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                notePageTurn(pkg, event, now)
                schedule(app, now, 0)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> schedule(app, now, 0)
            else -> Unit
        }
    }

    /**
     * Checks now if the throttle allows (and no [minDelay] is asked), else once when it does, so
     * the screen's final state is always checked.
     */
    private fun schedule(app: ShortFormApp, now: Long, minDelay: Long) {
        if (minDelay == 0L && pending == null && throttle.tryAcquire(now)) {
            check(app)
            return
        }
        val alreadyPending = pending != null
        pending = app // the latest app wins
        if (alreadyPending) return
        handler.postDelayed({
            val latest = pending ?: return@postDelayed
            pending = null
            // Outside onAccessibilityEvent's guard: a crash here would switch the service off.
            try {
                if (throttle.tryAcquire(SystemClock.elapsedRealtime())) check(latest)
            } catch (e: RuntimeException) {
                Log.e(TAG, "Ignored error in the short-video check", e)
            }
        }, maxOf(minDelay, throttle.waitMillis(now)))
    }

    private fun notePageTurn(pkg: String, event: AccessibilityEvent, now: Long) {
        val from = event.fromIndex
        if (from < 0 || event.toIndex != from || event.itemCount <= 1) return
        if (!pageTurns.settledOn(pkg, from)) return
        val (rows, columns) = collectionShape(event)
        val (dx, dy) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) event.scrollDeltaX to event.scrollDeltaY else 0 to 0
        if (ShortFormRules.isVertical(dx, dy, rows, columns)) pageTurns.record(pkg, now)
    }

    private fun collectionShape(event: AccessibilityEvent): Pair<Int, Int> {
        val source = try {
            event.source
        } catch (_: RuntimeException) {
            null
        } ?: return -1 to -1
        val info = source.collectionInfo
        val shape = if (info == null) -1 to -1 else info.rowCount to info.columnCount
        source.release()
        return shape
    }

    private fun check(app: ShortFormApp) {
        val pkg = app.packageName
        if (pkg !in Engine.shortFormBlocked() || !Engine.mayCloseShortForm(pkg)) return
        val now = SystemClock.elapsedRealtime()
        if (guard.isPaused(pkg, now)) return
        val root = try {
            service.rootInActiveWindow
        } catch (_: RuntimeException) {
            null
        } ?: return // window content unavailable: fail open
        try {
            if (root.packageName?.toString() != pkg) return // another window is in front now
            val metrics = service.resources.displayMetrics
            if (mapMatches(app, root, metrics.heightPixels)) {
                act(pkg, now, byFallback = false)
                return
            }
            val turns = pageTurns.recent(pkg, now)
            if (turns.size < ShortFormRules.FALLBACK_PAGE_TURNS) return // cheap exit before asking for the version
            val matched = ShortFormRules.matchedInVersion(Engine.shortFormMatchedVersion(pkg), Engine.system.versionCode(pkg))
            if (ShortFormRules.fallbackMayApply(matched, turns, now) &&
                hasFullScreenPager(root, metrics.widthPixels, metrics.heightPixels)
            ) {
                act(pkg, now, byFallback = true)
            }
        } finally {
            root.release()
        }
    }

    private fun mapMatches(app: ShortFormApp, root: AccessibilityNodeInfo, screenHeight: Int): Boolean {
        if (ShortFormMap.matchesActivity(app, windowClass[app.packageName])) return true
        val bounds = Rect()
        for (id in ShortFormMap.fullViewIds(app)) {
            val nodes = try {
                root.findAccessibilityNodeInfosByViewId(id)
            } catch (_: RuntimeException) {
                emptyList()
            }
            var found = false
            for (node in nodes) {
                if (!found) {
                    node.getBoundsInScreen(bounds)
                    found = ShortFormRules.isPlayerSized(node.isVisibleToUser, bounds.height(), screenHeight)
                }
                node.release()
            }
            if (found) return true
        }
        return false
    }

    /** Breadth-first search for a full-screen pager, bounded so a huge tree can't stall the main thread. */
    private fun hasFullScreenPager(root: AccessibilityNodeInfo, screenWidth: Int, screenHeight: Int): Boolean {
        val bounds = Rect()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) root.getChild(i)?.let(queue::addLast)
        var visited = 0
        var found = false
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (!found && visited < MAX_NODES) {
                visited++
                node.getBoundsInScreen(bounds)
                found = ShortFormRules.isFullScreenPager(
                    node.className?.toString(), node.isVisibleToUser, bounds.width(), bounds.height(), screenWidth, screenHeight,
                )
                if (!found) for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
            }
            node.release()
        }
        return found
    }

    private fun act(pkg: String, now: Long, byFallback: Boolean) {
        when (guard.onDetected(pkg, now)) {
            Action.BACK -> {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                pageTurns.clear(pkg)
                val label = Engine.system.label(pkg)
                Toast.makeText(service, service.getString(R.string.shortform_closed, label), Toast.LENGTH_SHORT).show()
                Engine.onShortFormClosed(pkg, Engine.system.versionCode(pkg), byFallback)
            }
            Action.LOOP -> {
                pageTurns.clear(pkg)
                Engine.onShortFormLoop(pkg, Engine.system.versionCode(pkg))
            }
            Action.WAIT, Action.PAUSED -> Unit
        }
    }

    /** Forget pending work (service stopping). */
    fun stop() {
        handler.removeCallbacksAndMessages(null)
        pending = null
    }

    private companion object {
        const val TAG = "UnscrollShortForm"
        const val MAX_NODES = 400
        const val SETTLE_MILLIS = 300L
    }
}

/** Returns a node to the system pool on Android versions that still pool them. */
internal fun AccessibilityNodeInfo.release() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        @Suppress("DEPRECATION")
        recycle()
    }
}
