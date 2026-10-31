package io.github.aasifashraf.unscroll.engine.rules

import io.github.aasifashraf.unscroll.engine.usage.AppUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

class RuleEngineTest {
    private val m = 60_000L
    private val now = 1_000_000_000L
    private val saturday10am = LocalDateTime.of(2026, 10, 31, 10, 0) // a Saturday

    private fun input(
        pkg: String = "insta",
        rules: List<AppRule> = emptyList(),
        schedules: List<Schedule> = emptyList(),
        focus: FocusSession? = null,
        quickBlockUntil: Long? = null,
        usedMillis: Long = 0,
        opens: Int = 1,
        passUntil: Long? = null,
        extraMinutes: Int = 0,
        local: LocalDateTime = saturday10am,
        neverBlock: Set<String> = setOf("dialer", "unscroll"),
    ) = RuleInput(
        packageName = pkg,
        nowMillis = now,
        localTime = local,
        state = EngineState(
            rules = rules.associateBy { it.packageName },
            schedules = schedules,
            focus = focus,
            quickBlockUntil = quickBlockUntil,
            extraMinutesToday = if (extraMinutes > 0) mapOf(pkg to extraMinutes) else emptyMap(),
        ),
        usageToday = AppUsage(usedMillis, opens),
        passUntil = passUntil,
        neverBlock = neverBlock,
        zone = ZoneId.of("Asia/Kolkata"),
    )

    @Test
    fun appsWithoutRulesAreAllowed() {
        assertEquals(Decision.Allow(), RuleEngine.decide(input()))
    }

    @Test
    fun neverBlockAppsWinOverEverything() {
        val d = RuleEngine.decide(
            input(pkg = "dialer", rules = listOf(AppRule("dialer", blocked = true)), quickBlockUntil = now + m),
        )
        assertEquals(Decision.Allow(), d)
    }

    @Test
    fun blockedAppIsBlocked() {
        assertEquals(Decision.Block(BlockReason.BLOCKED), RuleEngine.decide(input(rules = listOf(AppRule("insta", blocked = true)))))
    }

    @Test
    fun focusSessionBlocksItsAppsUntilItEnds() {
        val focus = FocusSession(startedAt = now - m, endsAt = now + 20 * m, packages = setOf("insta"))
        assertEquals(Decision.Block(BlockReason.FOCUS, until = now + 20 * m), RuleEngine.decide(input(focus = focus)))
        assertEquals(Decision.Allow(), RuleEngine.decide(input(pkg = "maps", focus = focus)))
    }

    @Test
    fun finishedFocusSessionNoLongerBlocks() {
        val focus = FocusSession(startedAt = now - 30 * m, endsAt = now - 1, packages = setOf("insta"))
        assertEquals(Decision.Allow(), RuleEngine.decide(input(focus = focus)))
    }

    @Test
    fun quickBlockCoversEveryAppThatHasARule() {
        val rules = listOf(AppRule("insta", pauseSeconds = 5))
        assertEquals(
            Decision.Block(BlockReason.QUICK_BLOCK, until = now + 30 * m),
            RuleEngine.decide(input(rules = rules, quickBlockUntil = now + 30 * m)),
        )
        assertEquals(Decision.Allow(), RuleEngine.decide(input(pkg = "maps", rules = rules, quickBlockUntil = now + 30 * m)))
    }

    @Test
    fun dailyLimitBlocksOnceUsedUp() {
        val rules = listOf(AppRule("insta", dailyLimitMinutes = 45))
        assertEquals(Decision.Block(BlockReason.DAILY_LIMIT), RuleEngine.decide(input(rules = rules, usedMillis = 45 * m)))
    }

    @Test
    fun underTheLimitReportsWhatIsLeft() {
        val rules = listOf(AppRule("insta", dailyLimitMinutes = 45))
        assertEquals(Decision.Allow(remainingMillis = 15 * m), RuleEngine.decide(input(rules = rules, usedMillis = 30 * m)))
    }

    @Test
    fun extraMinutesExtendTodaysLimit() {
        val rules = listOf(AppRule("insta", dailyLimitMinutes = 45))
        assertEquals(
            Decision.Allow(remainingMillis = 5 * m),
            RuleEngine.decide(input(rules = rules, usedMillis = 45 * m, extraMinutes = 5)),
        )
    }

    @Test
    fun limitOnlyAppliesOnItsDays() {
        val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        val rules = listOf(AppRule("insta", dailyLimitMinutes = 45, limitDays = weekdays))
        assertEquals(Decision.Allow(), RuleEngine.decide(input(rules = rules, usedMillis = 90 * m)))
    }

    @Test
    fun openLimitBlocksTheOpenAfterTheLast() {
        val rules = listOf(AppRule("insta", openLimit = 10))
        assertEquals(Decision.Allow(), RuleEngine.decide(input(rules = rules, opens = 10)))
        assertEquals(Decision.Block(BlockReason.OPEN_LIMIT), RuleEngine.decide(input(rules = rules, opens = 11)))
    }

    @Test
    fun pauseAsksFirstAndWaitsLongerEachOpen() {
        val rules = listOf(AppRule("insta", pauseSeconds = 5))
        assertEquals(Decision.Pause(seconds = 5), RuleEngine.decide(input(rules = rules, opens = 1)))
        assertEquals(Decision.Pause(seconds = 9), RuleEngine.decide(input(rules = rules, opens = 3)))
        val many = RuleEngine.decide(input(rules = rules, opens = 100)) as Decision.Pause
        assertEquals(RuleEngine.MAX_PAUSE_SECONDS, many.seconds)
    }

    @Test
    fun aPassedPauseLetsTheAppThroughUntilThePassExpires() {
        val rules = listOf(AppRule("insta", pauseSeconds = 5))
        assertEquals(Decision.Allow(), RuleEngine.decide(input(rules = rules, passUntil = now + m)))
        assertTrue(RuleEngine.decide(input(rules = rules, passUntil = now - 1)) is Decision.Pause)
    }

    @Test
    fun limitIsCheckedBeforeThePause() {
        val rules = listOf(AppRule("insta", dailyLimitMinutes = 10, pauseSeconds = 5))
        assertEquals(Decision.Block(BlockReason.DAILY_LIMIT), RuleEngine.decide(input(rules = rules, usedMillis = 10 * m)))
    }

    @Test
    fun scheduleBlocksDuringItsWindowOnItsDays() {
        val work = Schedule("w", "Work", true, setOf(DayOfWeek.SATURDAY), 9 * 60, 17 * 60, setOf("insta"))
        val d = RuleEngine.decide(input(schedules = listOf(work))) as Decision.Block
        assertEquals(BlockReason.SCHEDULE, d.reason)
        assertEquals(now + 7 * 60 * m, d.until) // ends at 17:00, it's 10:00
        assertTrue(RuleEngine.decide(input(schedules = listOf(work), local = saturday10am.withHour(18))) is Decision.Allow)
        assertEquals(Decision.Allow(), RuleEngine.decide(input(schedules = listOf(work.copy(enabled = false)))))
    }

    @Test
    fun overnightScheduleSpansMidnightAndBelongsToTheDayItStarted() {
        // Bedtime Friday 23:00 → Saturday 07:00.
        val bed = Schedule("b", "Bedtime", true, setOf(DayOfWeek.FRIDAY), 23 * 60, 7 * 60, setOf("insta"))
        val sat6am = saturday10am.withHour(6)
        val d = RuleEngine.decide(input(schedules = listOf(bed), local = sat6am)) as Decision.Block
        assertEquals(now + 60 * m, d.until)
        // Saturday 23:30 is not covered: the schedule only starts on Fridays.
        assertTrue(RuleEngine.decide(input(schedules = listOf(bed), local = saturday10am.withHour(23).withMinute(30))) is Decision.Allow)
    }

    @Test
    fun focusWinsOverPauseAndLimit() {
        val rules = listOf(AppRule("insta", dailyLimitMinutes = 100, pauseSeconds = 5))
        val focus = FocusSession(now - m, now + m, setOf("insta"))
        assertEquals(BlockReason.FOCUS, (RuleEngine.decide(input(rules = rules, focus = focus)) as Decision.Block).reason)
    }

    @Test
    fun allowedAppReportsWhenItsNextScheduleStarts() {
        val work = Schedule("w", "Work", true, setOf(DayOfWeek.SATURDAY), 11 * 60, 17 * 60, setOf("insta"))
        val d = RuleEngine.decide(input(schedules = listOf(work))) as Decision.Allow
        assertEquals(now + 60 * m, d.nextChangeAt) // it's 10:00, the schedule starts at 11:00
    }

    @Test
    fun nextScheduleStartWrapsToNextWeek() {
        // Only on Fridays; it's Saturday → next Friday 23:00.
        val bed = Schedule("b", "Bedtime", true, setOf(DayOfWeek.FRIDAY), 23 * 60, 7 * 60, setOf("insta"))
        val d = RuleEngine.decide(input(schedules = listOf(bed))) as Decision.Allow
        assertEquals(now + (6 * 24 + 13) * 60 * m, d.nextChangeAt)
    }

    @Test
    fun appsOutsideASchedulesListHaveNoScheduledChange() {
        val work = Schedule("w", "Work", true, setOf(DayOfWeek.SATURDAY), 11 * 60, 17 * 60, setOf("other"))
        assertEquals(Decision.Allow(), RuleEngine.decide(input(schedules = listOf(work))))
    }

    @Test
    fun scheduleEndAccountsForDstChanges() {
        // US DST ends 2026-11-01 02:00: an overnight 22:00→07:00 window is 10 real hours that night.
        val bed = Schedule("b", "Bedtime", true, setOf(DayOfWeek.SATURDAY), 22 * 60, 7 * 60, setOf("insta"))
        val sat23 = LocalDateTime.of(2026, 10, 31, 23, 0)
        val d = RuleEngine.decide(input(schedules = listOf(bed), local = sat23).copy(zone = ZoneId.of("America/New_York"))) as Decision.Block
        assertEquals(now + 9 * 60 * m, d.until) // 23:00 EDT → 07:00 EST = 9 h, not 8 h
    }

    // --- Today's allowance (shared by decide() and the UI's limit list) ---

    private val limited = AppRule("insta", dailyLimitMinutes = 45)

    @Test
    fun `allowance is the limit plus today's extra minutes`() {
        val state = EngineState(rules = mapOf("insta" to limited), extraMinutesToday = mapOf("insta" to 5))
        assertEquals(50 * m, RuleEngine.allowedTodayMillis(limited, state, DayOfWeek.SATURDAY))
    }

    @Test
    fun `no allowance on a day the limit doesn't apply`() {
        val weekdays = limited.copy(limitDays = DayOfWeek.entries.take(5).toSet())
        assertEquals(null, RuleEngine.allowedTodayMillis(weekdays, EngineState(), DayOfWeek.SATURDAY))
        assertEquals(45 * m, RuleEngine.allowedTodayMillis(weekdays, EngineState(), DayOfWeek.FRIDAY))
    }

    @Test
    fun `a blocked app or one without a limit has no allowance`() {
        assertEquals(null, RuleEngine.allowedTodayMillis(limited.copy(blocked = true), EngineState(), DayOfWeek.SATURDAY))
        assertEquals(null, RuleEngine.allowedTodayMillis(AppRule("insta", pauseSeconds = 5), EngineState(), DayOfWeek.SATURDAY))
    }

    @Test
    fun `decide blocks exactly when usage reaches the allowance`() {
        val rules = listOf(limited)
        assertTrue(RuleEngine.decide(input(rules = rules, usedMillis = 50 * m - 1, extraMinutes = 5)) is Decision.Allow)
        assertEquals(
            Decision.Block(BlockReason.DAILY_LIMIT),
            RuleEngine.decide(input(rules = rules, usedMillis = 50 * m, extraMinutes = 5)),
        )
    }
}
