package io.github.aasifashraf.unscroll.engine.shortform

/**
 * Pure decisions behind short-form blocking. Times are boot-clock millis (elapsedRealtime), so
 * clock changes don't matter. Every unclear case answers "don't block" (fail open).
 */
object ShortFormRules {
    /** At most 4 screen checks a second. */
    const val CHECK_INTERVAL_MILLIS = 250L

    /** A matched view must be at least this share of the screen tall: the player, not a thumbnail. */
    const val PLAYER_MIN_HEIGHT = 0.6

    /** The fallback's pager must cover at least this share of the screen's width and height. */
    const val FALLBACK_MIN_COVER = 0.9
    const val FALLBACK_PAGE_TURNS = 3
    const val FALLBACK_WINDOW_MILLIS = 20_000L

    /** At most one Back per app this often. */
    const val BACK_INTERVAL_MILLIS = 1_500L

    /** More Backs than this within [LOOP_WINDOW_MILLIS] means detection is wrong: stop. */
    const val LOOP_BACKS = 3
    const val LOOP_WINDOW_MILLIS = 10_000L

    /** How long the loop guard leaves an app alone. */
    const val LOOP_PAUSE_MILLIS = 10 * 60_000L

    fun isPlayerSized(visible: Boolean, heightPx: Int, screenHeightPx: Int): Boolean =
        visible && screenHeightPx > 0 && heightPx >= screenHeightPx * PLAYER_MIN_HEIGHT

    /** A visible pager or list ([className] contains ViewPager or RecyclerView) filling the screen. */
    fun isFullScreenPager(
        className: String?,
        visible: Boolean,
        widthPx: Int,
        heightPx: Int,
        screenWidthPx: Int,
        screenHeightPx: Int,
    ): Boolean = visible && className != null && ("ViewPager" in className || "RecyclerView" in className) &&
        screenWidthPx > 0 && screenHeightPx > 0 &&
        widthPx >= screenWidthPx * FALLBACK_MIN_COVER && heightPx >= screenHeightPx * FALLBACK_MIN_COVER

    /**
     * A scroll event (TYPE_VIEW_SCROLLED) that came to rest on exactly one item, a different one
     * than last time: one page of a pager. Lists that show several items never qualify.
     */
    fun settledOnNewPage(fromIndex: Int, toIndex: Int, itemCount: Int, previousIndex: Int?): Boolean =
        itemCount > 1 && fromIndex >= 0 && toIndex == fromIndex && previousIndex != null && fromIndex != previousIndex

    /**
     * Whether the scrolled list is vertical: from its collection shape ([rows] × [columns], -1 when
     * unknown), else from the scroll deltas. Unknown means no.
     */
    fun isVertical(deltaX: Int, deltaY: Int, rows: Int, columns: Int): Boolean = when {
        rows > 1 && columns == 1 -> true
        columns > 1 || rows == 1 -> false
        else -> deltaY != 0 && deltaX == 0
    }

    /**
     * The fallback may look for a full-screen pager: only when the map never matched in this app
     * version (if it did, its names work and no match means this isn't the player), and after
     * [FALLBACK_PAGE_TURNS] page turns within [FALLBACK_WINDOW_MILLIS].
     */
    fun fallbackMayApply(mapMatchedThisVersion: Boolean, pageTurnTimes: List<Long>, now: Long): Boolean =
        !mapMatchedThisVersion && pageTurnTimes.count { now - it in 0..FALLBACK_WINDOW_MILLIS } >= FALLBACK_PAGE_TURNS

    /** The map worked in the installed version. An unknown version counts as yes (no fallback guessing). */
    fun matchedInVersion(matchedVersion: Long?, currentVersion: Long?): Boolean =
        currentVersion == null || matchedVersion == currentVersion

    /** "Detection may be outdated": the loop guard tripped or the fallback was needed in this version. */
    fun maybeOutdated(suspectVersion: Long?, installedVersion: Long?): Boolean =
        suspectVersion != null && installedVersion != null && suspectVersion == installedVersion
}

/** Allows one action per [intervalMillis]. Not thread-safe (main thread only). */
class Throttle(private val intervalMillis: Long) {
    private var last: Long? = null

    /** Millis until the next action is allowed; 0 = now. */
    fun waitMillis(now: Long): Long = last?.let { (it + intervalMillis - now).coerceAtLeast(0) } ?: 0

    fun tryAcquire(now: Long): Boolean {
        if (waitMillis(now) > 0) return false
        last = now
        return true
    }
}

/** Rate limit and loop guard for the Back action, per app. */
class BackGuard {
    enum class Action {
        /** Go back now. */
        BACK,

        /** Went back moments ago; the screen may still be closing. */
        WAIT,

        /** Too many Backs: the loop guard just tripped. Stop and report. */
        LOOP,

        /** The loop guard is still leaving this app alone. */
        PAUSED,
    }

    private val backs = HashMap<String, List<Long>>()
    private val pausedUntil = HashMap<String, Long>()

    fun isPaused(pkg: String, now: Long): Boolean = (pausedUntil[pkg] ?: Long.MIN_VALUE) > now

    fun onDetected(pkg: String, now: Long): Action {
        if (isPaused(pkg, now)) return Action.PAUSED
        pausedUntil.remove(pkg)
        val recent = backs[pkg].orEmpty().filter { now - it < ShortFormRules.LOOP_WINDOW_MILLIS }
        if (recent.isNotEmpty() && now - recent.last() < ShortFormRules.BACK_INTERVAL_MILLIS) return Action.WAIT
        if (recent.size >= ShortFormRules.LOOP_BACKS) {
            backs.remove(pkg)
            pausedUntil[pkg] = now + ShortFormRules.LOOP_PAUSE_MILLIS
            return Action.LOOP
        }
        backs[pkg] = recent + now
        return Action.BACK
    }
}

/** Page turns seen per app, for the fallback. */
class PageTurns {
    private val index = HashMap<String, Int>()
    private val turns = HashMap<String, List<Long>>()

    /** A scroll in [pkg] settled on page [pageIndex]; true if that's a different page than before. */
    fun settledOn(pkg: String, pageIndex: Int): Boolean {
        val previous = index.put(pkg, pageIndex)
        return previous != null && previous != pageIndex
    }

    /** The page index [pkg] last settled on, or null. */
    fun lastIndex(pkg: String): Int? = index[pkg]

    fun record(pkg: String, now: Long) {
        turns[pkg] = recent(pkg, now) + now
    }

    /** Turn times within the fallback window. */
    fun recent(pkg: String, now: Long): List<Long> =
        turns[pkg].orEmpty().filter { now - it in 0..ShortFormRules.FALLBACK_WINDOW_MILLIS }

    fun clear(pkg: String) {
        turns.remove(pkg)
    }
}
