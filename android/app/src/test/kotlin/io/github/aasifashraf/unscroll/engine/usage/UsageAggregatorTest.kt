package io.github.aasifashraf.unscroll.engine.usage

import io.github.aasifashraf.unscroll.engine.usage.UsageEvent.Kind.PAUSED
import io.github.aasifashraf.unscroll.engine.usage.UsageEvent.Kind.RESUMED
import io.github.aasifashraf.unscroll.engine.usage.UsageEvent.Kind.SCREEN_OFF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageAggregatorTest {
    private val m = 60_000L
    private fun r(t: Long, pkg: String, cls: String = "Main") = UsageEvent(t, RESUMED, pkg, cls)
    private fun p(t: Long, pkg: String, cls: String = "Main") = UsageEvent(t, PAUSED, pkg, cls)
    private fun off(t: Long) = UsageEvent(t, SCREEN_OFF, null, null)

    @Test
    fun pairsResumeAndPause() {
        val usage = UsageAggregator.aggregate(listOf(r(0, "a"), p(10 * m, "a")), 0, 60 * m)
        assertEquals(10 * m, usage.getValue("a").millis)
        assertEquals(1, usage.getValue("a").opens)
    }

    @Test
    fun activitySwitchInsideOneAppIsNotANewOpen() {
        val events = listOf(r(0, "a", "Feed"), p(5 * m, "a", "Feed"), r(5 * m, "a", "Profile"), p(9 * m, "a", "Profile"))
        val usage = UsageAggregator.aggregate(events, 0, 60 * m)
        assertEquals(9 * m, usage.getValue("a").millis)
        assertEquals(1, usage.getValue("a").opens)
    }

    @Test
    fun returningAfterAnotherAppCountsAnotherOpen() {
        val events = listOf(r(0, "a"), p(1 * m, "a"), r(1 * m, "launcher"), p(2 * m, "launcher"), r(2 * m, "a"), p(3 * m, "a"))
        val usage = UsageAggregator.aggregate(events, 0, 60 * m)
        assertEquals(2, usage.getValue("a").opens)
        assertEquals(2 * m, usage.getValue("a").millis)
    }

    @Test
    fun missingPauseIsClosedByTheNextResumeNotLeftOpenForHours() {
        // "a" crashed: no PAUSED. "b" opens 5 minutes later.
        val events = listOf(r(0, "a"), r(5 * m, "b"), p(6 * m, "b"))
        val usage = UsageAggregator.aggregate(events, 0, 24 * 60 * m)
        assertEquals(5 * m, usage.getValue("a").millis)
        assertEquals(1 * m, usage.getValue("b").millis)
    }

    @Test
    fun screenOffClosesTheSessionAndTurningOnAgainIsANewOpen() {
        val events = listOf(r(0, "a"), off(4 * m), r(30 * m, "a"), p(31 * m, "a"))
        val usage = UsageAggregator.aggregate(events, 0, 60 * m)
        assertEquals(5 * m, usage.getValue("a").millis)
        assertEquals(2, usage.getValue("a").opens)
    }

    @Test
    fun sessionStartedBeforeTheRangeIsClippedAndNotCountedAsAnOpen() {
        // Opened at 23:50 yesterday, closed 00:10 today. Range starts at 00:00 (t = 10 m).
        val usage = UsageAggregator.aggregate(listOf(r(0, "a"), p(30 * m, "a")), 10 * m, 60 * m)
        assertEquals(20 * m, usage.getValue("a").millis)
        assertEquals(0, usage.getValue("a").opens)
    }

    @Test
    fun stillOpenAppCountsUntilTheEndOfTheRange() {
        val usage = UsageAggregator.aggregate(listOf(r(10 * m, "a")), 0, 25 * m)
        assertEquals(15 * m, usage.getValue("a").millis)
    }

    @Test
    fun outOfOrderEventsAreSorted() {
        val usage = UsageAggregator.aggregate(listOf(p(10 * m, "a"), r(0, "a")), 0, 60 * m)
        assertEquals(10 * m, usage.getValue("a").millis)
    }

    @Test
    fun appsUsedOnlyOutsideTheRangeAreAbsent() {
        val usage = UsageAggregator.aggregate(listOf(r(0, "a"), p(5 * m, "a")), 10 * m, 60 * m)
        assertNull(usage["a"])
    }

    @Test
    fun foregroundAtReturnsTheLastResumedAppOrNullAfterScreenOff() {
        assertEquals("b", UsageAggregator.foregroundAt(listOf(r(0, "a"), r(5 * m, "b")), 6 * m))
        assertNull(UsageAggregator.foregroundAt(listOf(r(0, "a"), off(5 * m)), 6 * m))
        assertNull(UsageAggregator.foregroundAt(listOf(r(0, "a"), p(5 * m, "a")), 6 * m))
    }
}
