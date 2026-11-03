package io.github.aasifashraf.unscroll.engine.shortform

import io.github.aasifashraf.unscroll.engine.shortform.BackGuard.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortFormMapTest {
    @Test
    fun mapsTheFourSupportedAppsByPackage() {
        assertEquals(
            setOf("com.instagram.android", "com.google.android.youtube", "com.facebook.katana", "com.snapchat.android"),
            ShortFormMap.packages,
        )
        assertEquals("YouTube Shorts", ShortFormMap.forPackage("com.google.android.youtube")?.feedName)
        assertNull(ShortFormMap.forPackage("com.zhiliaoapp.musically"))
        assertNull(ShortFormMap.forPackage(""))
    }

    @Test
    fun viewIdsAreFullyQualifiedWithTheirOwnPackage() {
        val yt = ShortFormMap.forPackage("com.google.android.youtube")!!
        val ids = ShortFormMap.fullViewIds(yt)
        assertTrue(ids.isNotEmpty())
        assertTrue(ids.all { it.startsWith("com.google.android.youtube:id/") })
        assertTrue("com.google.android.youtube:id/reel_watch_player" in ids)
    }

    @Test
    fun noEntryUsesATabButtonOrShelfThatAlsoShowsOnOtherScreens() {
        for (app in ShortFormMap.apps) {
            for (id in app.viewIds) {
                assertFalse(id, id.endsWith("_tab") || "shelf" in id || id.isBlank() || ':' in id || '/' in id)
            }
        }
    }

    @Test
    fun activityHintsMatchOnlyTheEndOfAClassName() {
        val fb = ShortFormMap.forPackage("com.facebook.katana")!!
        assertTrue(ShortFormMap.matchesActivity(fb, "com.facebook.some.path.FbShortsViewerActivity"))
        assertFalse(ShortFormMap.matchesActivity(fb, "com.facebook.katana.LoginActivity"))
        assertFalse(ShortFormMap.matchesActivity(fb, null))
        val ig = ShortFormMap.forPackage("com.instagram.android")!!
        assertFalse(ShortFormMap.matchesActivity(ig, "anything.FbShortsViewerActivity"))
    }
}

class ShortFormRulesTest {
    @Test
    fun aMatchedViewCountsOnlyWhenVisibleAndAtLeastSixtyPercentTall() {
        assertTrue(ShortFormRules.isPlayerSized(visible = true, heightPx = 1200, screenHeightPx = 2000))
        assertTrue(ShortFormRules.isPlayerSized(visible = true, heightPx = 2000, screenHeightPx = 2000))
        assertFalse(ShortFormRules.isPlayerSized(visible = true, heightPx = 1199, screenHeightPx = 2000))
        assertFalse(ShortFormRules.isPlayerSized(visible = false, heightPx = 2000, screenHeightPx = 2000))
        assertFalse(ShortFormRules.isPlayerSized(visible = true, heightPx = 2000, screenHeightPx = 0))
    }

    @Test
    fun fullScreenPagerNeedsAPagerOrListClassCoveringNinetyPercent() {
        fun pager(cls: String?, w: Int = 1080, h: Int = 1900, visible: Boolean = true) =
            ShortFormRules.isFullScreenPager(cls, visible, w, h, screenWidthPx = 1080, screenHeightPx = 2000)
        assertTrue(pager("androidx.viewpager.widget.ViewPager"))
        assertTrue(pager("androidx.recyclerview.widget.RecyclerView"))
        assertTrue(pager("androidx.viewpager2.widget.ViewPager2", h = 1800))
        assertFalse(pager("androidx.recyclerview.widget.RecyclerView", h = 1799))
        assertFalse(pager("androidx.recyclerview.widget.RecyclerView", w = 900))
        assertFalse(pager("android.widget.FrameLayout"))
        assertFalse(pager(null))
        assertFalse(pager("androidx.recyclerview.widget.RecyclerView", visible = false))
        assertFalse(ShortFormRules.isFullScreenPager("RecyclerView", true, 0, 0, 0, 0))
    }

    @Test
    fun aPageTurnIsASettleOnANewSinglePage() {
        assertTrue(ShortFormRules.settledOnNewPage(fromIndex = 4, toIndex = 4, itemCount = 10, previousIndex = 3))
        assertFalse("first sighting", ShortFormRules.settledOnNewPage(4, 4, 10, previousIndex = null))
        assertFalse("same page", ShortFormRules.settledOnNewPage(4, 4, 10, previousIndex = 4))
        assertFalse("two items visible: mid-scroll or a feed", ShortFormRules.settledOnNewPage(4, 5, 10, previousIndex = 3))
        assertFalse("single item list", ShortFormRules.settledOnNewPage(0, 0, 1, previousIndex = 1))
        assertFalse("unknown index", ShortFormRules.settledOnNewPage(-1, -1, 10, previousIndex = 3))
    }

    @Test
    fun verticalComesFromTheCollectionShapeOrElseFromTheScrollDelta() {
        assertTrue(ShortFormRules.isVertical(deltaX = 0, deltaY = 0, rows = 20, columns = 1))
        assertFalse("horizontal pager", ShortFormRules.isVertical(deltaX = 0, deltaY = 0, rows = 1, columns = 5))
        assertFalse("grid", ShortFormRules.isVertical(deltaX = 0, deltaY = 0, rows = 5, columns = 3))
        assertTrue(ShortFormRules.isVertical(deltaX = 0, deltaY = 1800, rows = -1, columns = -1))
        assertFalse(ShortFormRules.isVertical(deltaX = 900, deltaY = 0, rows = -1, columns = -1))
        assertFalse("diagonal", ShortFormRules.isVertical(deltaX = 10, deltaY = 1800, rows = -1, columns = -1))
        assertFalse("nothing known: fail open", ShortFormRules.isVertical(deltaX = 0, deltaY = 0, rows = -1, columns = -1))
    }

    @Test
    fun fallbackNeedsThreePageTurnsWithinTwentySeconds() {
        val now = 100_000L
        assertTrue(ShortFormRules.fallbackMayApply(false, listOf(now - 20_000, now - 10_000, now), now))
        assertFalse("only two", ShortFormRules.fallbackMayApply(false, listOf(now - 10_000, now), now))
        assertFalse("one too old", ShortFormRules.fallbackMayApply(false, listOf(now - 20_001, now - 10_000, now), now))
        assertFalse("none", ShortFormRules.fallbackMayApply(false, emptyList(), now))
    }

    @Test
    fun fallbackNeverRunsOnceTheMapWorkedInThisAppVersion() {
        val now = 100_000L
        val turns = listOf(now - 3_000, now - 2_000, now - 1_000)
        assertFalse(ShortFormRules.fallbackMayApply(mapMatchedThisVersion = true, turns, now))
    }

    @Test
    fun theMapCountsAsWorkingWhenItMatchedThisVersionOrTheVersionIsUnknown() {
        assertTrue(ShortFormRules.matchedInVersion(matchedVersion = 42, currentVersion = 42))
        assertFalse("app updated since", ShortFormRules.matchedInVersion(matchedVersion = 41, currentVersion = 42))
        assertFalse("never matched", ShortFormRules.matchedInVersion(matchedVersion = null, currentVersion = 42))
        assertTrue("unknown version: don't guess", ShortFormRules.matchedInVersion(matchedVersion = null, currentVersion = null))
    }

    @Test
    fun outdatedOnlyForTheAppVersionThatLooped() {
        assertTrue(ShortFormRules.maybeOutdated(suspectVersion = 7, installedVersion = 7))
        assertFalse("app updated: try again", ShortFormRules.maybeOutdated(suspectVersion = 7, installedVersion = 8))
        assertFalse(ShortFormRules.maybeOutdated(suspectVersion = null, installedVersion = 8))
        assertFalse("not installed", ShortFormRules.maybeOutdated(suspectVersion = 7, installedVersion = null))
    }
}

class ThrottleTest {
    @Test
    fun allowsAtMostFourChecksASecond() {
        val t = Throttle(ShortFormRules.CHECK_INTERVAL_MILLIS)
        assertTrue(t.tryAcquire(1_000))
        assertFalse(t.tryAcquire(1_100))
        assertEquals(150, t.waitMillis(1_100))
        assertFalse(t.tryAcquire(1_249))
        assertTrue(t.tryAcquire(1_250))
        val allowed = (0 until 1_000 step 10).count { t.tryAcquire(2_000L + it) }
        assertEquals(4, allowed)
    }

    @Test
    fun firstCheckIsImmediate() {
        assertEquals(0, Throttle(250).waitMillis(0))
    }
}

class BackGuardTest {
    private val pkg = "com.google.android.youtube"

    @Test
    fun goesBackAtMostOnceEveryOneAndAHalfSecondsPerApp() {
        val g = BackGuard()
        assertEquals(Action.BACK, g.onDetected(pkg, 0))
        assertEquals(Action.WAIT, g.onDetected(pkg, 1_000))
        assertEquals(Action.WAIT, g.onDetected(pkg, 1_499))
        assertEquals(Action.BACK, g.onDetected(pkg, 1_500))
        assertEquals("other apps are separate", Action.BACK, g.onDetected("com.instagram.android", 1_600))
    }

    @Test
    fun aFourthBackWithinTenSecondsTripsTheLoopGuardAndFailsOpen() {
        val g = BackGuard()
        assertEquals(Action.BACK, g.onDetected(pkg, 0))
        assertEquals(Action.BACK, g.onDetected(pkg, 2_000))
        assertEquals(Action.BACK, g.onDetected(pkg, 4_000))
        assertEquals(Action.LOOP, g.onDetected(pkg, 6_000))
        assertTrue(g.isPaused(pkg, 6_001))
        // No more Backs while paused, however often the screen is seen.
        for (t in 6_500L..60_000L step 1_500) assertEquals(Action.PAUSED, g.onDetected(pkg, t))
        assertFalse("other apps keep working", g.isPaused("com.instagram.android", 7_000))
    }

    @Test
    fun theLoopGuardPauseEndsAfterTenMinutes() {
        val g = BackGuard()
        listOf(0L, 2_000L, 4_000L).forEach { g.onDetected(pkg, it) }
        assertEquals(Action.LOOP, g.onDetected(pkg, 6_000))
        val end = 6_000 + ShortFormRules.LOOP_PAUSE_MILLIS
        assertEquals(Action.PAUSED, g.onDetected(pkg, end - 1))
        assertEquals(Action.BACK, g.onDetected(pkg, end))
    }

    @Test
    fun backsSpreadOverMoreThanTenSecondsAreNotALoop() {
        val g = BackGuard()
        for (t in 0L..60_000L step 5_001) assertEquals("at $t", Action.BACK, g.onDetected(pkg, t))
    }
}

class PageTurnsTest {
    @Test
    fun countsTurnsPerAppWithinTheWindowAndForgetsThemWhenCleared() {
        val p = PageTurns()
        assertFalse(p.settledOn("a", 0))
        assertTrue(p.settledOn("a", 1))
        assertFalse(p.settledOn("a", 1))
        assertTrue(p.settledOn("a", 0)) // swiping back up is a turn too
        p.record("a", 1_000)
        p.record("a", 2_000)
        p.record("b", 2_000)
        assertEquals(listOf(1_000L, 2_000L), p.recent("a", 3_000))
        assertEquals(listOf(2_000L), p.recent("a", 21_500))
        p.clear("a")
        assertEquals(emptyList<Long>(), p.recent("a", 3_000))
        assertEquals(listOf(2_000L), p.recent("b", 3_000))
    }
}
