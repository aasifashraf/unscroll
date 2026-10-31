package io.github.aasifashraf.unscroll.engine.clock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DayClockTest {
    private val kolkata = ZoneId.of("Asia/Kolkata")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val honolulu = ZoneId.of("Pacific/Honolulu")
    private val newYork = ZoneId.of("America/New_York")

    private fun wall(dateTime: String, zone: ZoneId) =
        LocalDateTime.parse(dateTime).atZone(zone).toInstant().toEpochMilli()

    private val hour = 3_600_000L
    private val minute = 60_000L

    private fun reading(
        wallMillis: Long,
        elapsed: Long,
        boot: Int = 1,
        zone: ZoneId = kolkata,
        autoTime: Boolean = false,
    ) = ClockReading(wallMillis, elapsed, boot, zone, autoTime)

    @Test
    fun firstReadingStartsTodayInTheCurrentZone() {
        val state = DayClock.advance(null, reading(wall("2026-11-01T10:00", kolkata), 1_000))
        assertEquals(LocalDate.of(2026, 11, 1), state.day)
        assertEquals(wall("2026-11-01T00:00", kolkata), state.dayStartMillis)
    }

    @Test
    fun dayAdvancesAtLocalMidnight() {
        val start = wall("2026-11-01T23:30", kolkata)
        val s1 = DayClock.advance(null, reading(start, 0))
        val s2 = DayClock.advance(s1, reading(start + 31 * minute, 31 * minute))
        assertEquals(LocalDate.of(2026, 11, 2), s2.day)
        assertEquals(wall("2026-11-02T00:00", kolkata), s2.dayStartMillis)
    }

    @Test
    fun manualClockForwardDoesNotStartANewDayWhenAutoTimeIsOff() {
        val start = wall("2026-11-01T10:00", kolkata)
        val s1 = DayClock.advance(null, reading(start, 0))
        // User moves the clock +1 day; only 1 minute really passed.
        val s2 = DayClock.advance(s1, reading(start + 24 * hour + minute, minute))
        assertEquals(LocalDate.of(2026, 11, 1), s2.day)
        assertEquals(start + minute, s2.trustedWallMillis)
    }

    @Test
    fun manualClockBackwardNeverMovesTheDayBack() {
        val start = wall("2026-11-02T00:10", kolkata)
        val s1 = DayClock.advance(null, reading(start, 0))
        val s2 = DayClock.advance(s1, reading(start - 24 * hour, minute))
        assertEquals(LocalDate.of(2026, 11, 2), s2.day)
    }

    @Test
    fun autoTimeOnTrustsTheWallClockButNeverGoesBackADay() {
        val start = wall("2026-11-01T10:00", kolkata)
        val s1 = DayClock.advance(null, reading(start, 0, autoTime = true))
        // Network time corrects the clock 30 s backwards: still the same day, no crash.
        val s2 = DayClock.advance(s1, reading(start - 30_000, minute, autoTime = true))
        assertEquals(LocalDate.of(2026, 11, 1), s2.day)
    }

    @Test
    fun timeZoneChangeMidDayKeepsTheDayBoundaryOfTheStartingZone() {
        // 20:00 in India = 23:30 in Tokyo. Switching to Tokyo must not end the day at Tokyo midnight.
        val start = wall("2026-11-01T20:00", kolkata)
        val s1 = DayClock.advance(null, reading(start, 0))
        val s2 = DayClock.advance(s1, reading(start + 45 * minute, 45 * minute, zone = tokyo))
        assertEquals(LocalDate.of(2026, 11, 1), s2.day)
        // At India midnight the new day starts and the new zone takes over.
        val s3 = DayClock.advance(s2, reading(start + 4 * hour + minute, 4 * hour + minute, zone = tokyo))
        assertEquals(LocalDate.of(2026, 11, 2), s3.day)
        assertEquals(tokyo, s3.zone)
    }

    @Test
    fun zoneHoppingCannotCreateDaysShorterThanTwentyHours() {
        // Day starts in Honolulu (UTC-10), user jumps to UTC+14 right after midnight.
        val start = wall("2026-11-01T00:05", honolulu)
        val s1 = DayClock.advance(null, reading(start, 0, zone = honolulu))
        val kiritimati = ZoneId.of("Pacific/Kiritimati")
        // Next Honolulu midnight → new day, zone becomes UTC+14 (it's already 00:05 next-next day there).
        val s2 = DayClock.advance(s1, reading(start + 24 * hour, 24 * hour, zone = kiritimati))
        // Two hours later it is "midnight" again somewhere — but < 20 h since day start: no new day.
        val s3 = DayClock.advance(s2, reading(start + 26 * hour, 26 * hour, zone = kiritimati))
        assertEquals(s2.day, s3.day)
    }

    @Test
    fun dstSpringForwardDayStillRollsOver() {
        // US DST starts 2026-03-08: a 23-hour day.
        val start = wall("2026-03-08T00:30", newYork)
        val s1 = DayClock.advance(null, reading(start, 0, zone = newYork))
        val nextMidnight = wall("2026-03-09T00:00", newYork)
        val s2 = DayClock.advance(s1, reading(nextMidnight + minute, nextMidnight + minute - start, zone = newYork))
        assertEquals(LocalDate.of(2026, 3, 9), s2.day)
    }

    @Test
    fun dstFallBackDayIsTwentyFiveHours() {
        val start = wall("2026-11-01T00:30", newYork) // US DST ends 2026-11-01
        val s1 = DayClock.advance(null, reading(start, 0, zone = newYork))
        val s2 = DayClock.advance(s1, reading(start + 24 * hour, 24 * hour, zone = newYork))
        assertEquals("still Nov 1 after 24 h on a 25 h day", LocalDate.of(2026, 11, 1), s2.day)
        val s3 = DayClock.advance(s2, reading(start + 24 * hour + 31 * minute, 24 * hour + 31 * minute, zone = newYork))
        assertEquals(LocalDate.of(2026, 11, 2), s3.day)
    }

    @Test
    fun rebootUsesWallClockButNeverGoesBackwards() {
        val start = wall("2026-11-01T10:00", kolkata)
        val s1 = DayClock.advance(null, reading(start, 5 * hour, boot = 1))
        // After reboot elapsedRealtime restarts; the wall clock was moved back a day.
        val s2 = DayClock.advance(s1, reading(start - 24 * hour, 10_000, boot = 2))
        assertEquals(LocalDate.of(2026, 11, 1), s2.day)
        assertTrue(s2.trustedWallMillis >= s1.trustedWallMillis)
    }

    @Test
    fun rebootAfterPhoneWasOffOvernightStartsTheNewDay() {
        val start = wall("2026-11-01T23:00", kolkata)
        val s1 = DayClock.advance(null, reading(start, 5 * hour, boot = 1))
        val s2 = DayClock.advance(s1, reading(start + 9 * hour, 20_000, boot = 2))
        assertEquals(LocalDate.of(2026, 11, 2), s2.day)
    }

    @Test
    fun skippingSeveralDaysJumpsStraightToToday() {
        val start = wall("2026-11-01T10:00", kolkata)
        val s1 = DayClock.advance(null, reading(start, 0, autoTime = true))
        val s2 = DayClock.advance(s1, reading(start + 72 * hour, 72 * hour, autoTime = true))
        assertEquals(LocalDate.of(2026, 11, 4), s2.day)
    }

    @Test
    fun usageWindowStartIsMappedToTheWallClockWhenTheClockWasSetBack() {
        // Real time 10:00 on Nov 1; user sets the clock back one day (auto-time off).
        val start = wall("2026-11-01T10:00", kolkata)
        val s1 = DayClock.advance(null, reading(start, 0))
        val nowWall = start - 24 * hour + minute
        val s2 = DayClock.advance(s1, reading(nowWall, minute))
        // Android re-bases its usage history to the new clock, so "today" starts one day earlier in wall time.
        assertEquals(wall("2026-10-31T00:00", kolkata), DayClock.wallDayStart(s2, nowWall))
        assertTrue(DayClock.wallDayStart(s2, nowWall) <= nowWall)
    }

    @Test
    fun usageWindowStartEqualsDayStartWhenTheClockIsHonest() {
        val start = wall("2026-11-01T10:00", kolkata)
        val s1 = DayClock.advance(null, reading(start, 0))
        assertEquals(s1.dayStartMillis, DayClock.wallDayStart(s1, start))
    }

    @Test
    fun forwardRebootRestoreDoesNotSwitchLimitsOffForAYear() {
        val start = wall("2026-11-01T10:00", kolkata)
        val s1 = DayClock.advance(null, reading(start, 5 * hour, boot = 1))
        // Clock +1 year, reboot, then set back to the real time (auto-time off).
        val yearAhead = start + 365L * 24 * hour
        val s2 = DayClock.advance(s1, reading(yearAhead, 10_000, boot = 2))
        val s3 = DayClock.advance(s2, reading(start + minute, 70_000, boot = 2))
        // The usage window still starts before "now" in wall time, so today's usage is counted.
        assertTrue(DayClock.wallDayStart(s3, start + minute) <= start + minute)
    }

    @Test
    fun withAutoTimeANetworkCorrectionBackwardsReanchorsTrustedTime() {
        val start = wall("2026-11-01T10:00", kolkata)
        val s1 = DayClock.advance(null, reading(start + 2 * hour, 0, autoTime = true)) // clock was 2 h fast
        val s2 = DayClock.advance(s1, reading(start, minute, autoTime = true)) // network fixes it
        assertEquals(start, s2.trustedWallMillis)
        assertEquals(LocalDate.of(2026, 11, 1), s2.day)
    }
}
