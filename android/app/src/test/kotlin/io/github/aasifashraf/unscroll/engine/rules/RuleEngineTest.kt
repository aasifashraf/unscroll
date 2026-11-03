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
        groups: List<AppGroup> = emptyList(),
        groupUsage: Map<String, Long> = emptyMap(),
        extra: Map<String, Int> = emptyMap(),
        nowMillis: Long = now,
        session: SessionTimes? = null,
        inCall: Boolean = false,
    ) = RuleInput(
        packageName = pkg,
        nowMillis = nowMillis,
        localTime = local,
        state = EngineState(
            rules = rules.associateBy { it.packageName },
            schedules = schedules,
            focus = focus,
            quickBlockUntil = quickBlockUntil,
            extraMinutesToday = (if (extraMinutes > 0) mapOf(pkg to extraMinutes) else emptyMap()) + extra,
            groups = groups,
        ),
        usageToday = AppUsage(usedMillis, opens),
        passUntil = passUntil,
        neverBlock = neverBlock,
        zone = ZoneId.of("Asia/Kolkata"),
        groupUsageMillis = groupUsage,
        session = session,
        inCall = inCall,
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

    // --- App groups with a shared daily limit ---

    private val social = AppGroup("g1", "Social", setOf("insta", "tube"), dailyLimitMinutes = 60)

    @Test
    fun `a group blocks its apps once their summed usage reaches the shared limit`() {
        // insta itself used 10 min; the group (insta + tube) used 60 min together.
        val d = RuleEngine.decide(input(usedMillis = 10 * m, groups = listOf(social), groupUsage = mapOf("g1" to 60 * m)))
        assertEquals(Decision.Block(BlockReason.GROUP_LIMIT, groupId = "g1"), d)
        assertEquals(
            Decision.Allow(remainingMillis = m),
            RuleEngine.decide(input(groups = listOf(social), groupUsage = mapOf("g1" to 59 * m))),
        )
    }

    @Test
    fun `apps outside a group are not affected by it`() {
        assertEquals(Decision.Allow(), RuleEngine.decide(input(pkg = "maps", groups = listOf(social), groupUsage = mapOf("g1" to 90 * m))))
    }

    @Test
    fun `the strictest limit wins between an app's own limit and its group`() {
        val own = listOf(AppRule("insta", dailyLimitMinutes = 30))
        // Own limit used up, group still has time.
        assertEquals(
            Decision.Block(BlockReason.DAILY_LIMIT),
            RuleEngine.decide(input(rules = own, usedMillis = 30 * m, groups = listOf(social), groupUsage = mapOf("g1" to 40 * m))),
        )
        // Own limit has time, group used up.
        assertEquals(
            Decision.Block(BlockReason.GROUP_LIMIT, groupId = "g1"),
            RuleEngine.decide(input(rules = own, usedMillis = 10 * m, groups = listOf(social), groupUsage = mapOf("g1" to 60 * m))),
        )
    }

    @Test
    fun `any used-up group blocks an app that is in several groups`() {
        val evening = AppGroup("g2", "Evening", setOf("insta"), dailyLimitMinutes = 20)
        val d = RuleEngine.decide(input(groups = listOf(social, evening), groupUsage = mapOf("g1" to 10 * m, "g2" to 20 * m)))
        assertEquals(Decision.Block(BlockReason.GROUP_LIMIT, groupId = "g2"), d)
    }

    @Test
    fun `a group limit only applies on its days`() {
        val weekdays = social.copy(limitDays = DayOfWeek.entries.take(5).toSet())
        assertEquals(Decision.Allow(), RuleEngine.decide(input(groups = listOf(weekdays), groupUsage = mapOf("g1" to 90 * m))))
    }

    @Test
    fun `remaining time is the smallest across the app's own limit and all its groups`() {
        val own = listOf(AppRule("insta", dailyLimitMinutes = 30))
        val evening = AppGroup("g2", "Evening", setOf("insta"), dailyLimitMinutes = 120)
        val d = RuleEngine.decide(
            input(rules = own, usedMillis = 5 * m, groups = listOf(social, evening), groupUsage = mapOf("g1" to 50 * m, "g2" to 5 * m)),
        )
        assertEquals(Decision.Allow(remainingMillis = 10 * m), d) // own 25, Social 10, Evening 115
    }

    @Test
    fun `group extensions are keyed by group id and extend the shared limit`() {
        val extra = mapOf(RuleEngine.groupKey("g1") to 5)
        assertEquals("group:g1", RuleEngine.groupKey("g1"))
        assertEquals(
            Decision.Allow(remainingMillis = 5 * m),
            RuleEngine.decide(input(groups = listOf(social), groupUsage = mapOf("g1" to 60 * m), extra = extra)),
        )
        // The app's own extension doesn't extend the group.
        assertTrue(
            RuleEngine.decide(input(groups = listOf(social), groupUsage = mapOf("g1" to 60 * m), extraMinutes = 5)) is Decision.Block,
        )
    }

    @Test
    fun `group allowance is the limit plus the group's extra minutes, or null on other days`() {
        val state = EngineState(groups = listOf(social), extraMinutesToday = mapOf("group:g1" to 10, "insta" to 99))
        assertEquals(70 * m, RuleEngine.groupAllowedTodayMillis(social, state, DayOfWeek.SATURDAY))
        val weekdays = social.copy(limitDays = DayOfWeek.entries.take(5).toSet())
        assertEquals(null, RuleEngine.groupAllowedTodayMillis(weekdays, state, DayOfWeek.SATURDAY))
    }

    @Test
    fun `group usage is the sum of today's usage of its apps`() {
        val usage = mapOf("insta" to AppUsage(20 * m, 3), "tube" to AppUsage(15 * m, 1), "maps" to AppUsage(50 * m, 2))
        val evening = AppGroup("g2", "Evening", setOf("maps", "unused"), dailyLimitMinutes = 20)
        assertEquals(mapOf("g1" to 35 * m, "g2" to 50 * m), RuleEngine.groupUsageMillis(listOf(social, evening), usage, neverBlock = emptySet()))
    }

    @Test
    fun `a never-block app in a group is still never blocked`() {
        val withDialer = social.copy(packages = social.packages + "dialer")
        assertEquals(
            Decision.Allow(),
            RuleEngine.decide(input(pkg = "dialer", groups = listOf(withDialer), groupUsage = mapOf("g1" to 90 * m))),
        )
    }

    @Test
    fun `a group with no usage reported counts as unused, not as used up`() {
        assertEquals(Decision.Allow(remainingMillis = 60 * m), RuleEngine.decide(input(groups = listOf(social))))
    }

    @Test
    fun `an app's own open limit and pause still apply inside a group with time left`() {
        val own = listOf(AppRule("insta", pauseSeconds = 5, openLimit = 3))
        assertEquals(Decision.Pause(5), RuleEngine.decide(input(rules = own, groups = listOf(social), groupUsage = mapOf("g1" to 10 * m))))
        assertEquals(
            Decision.Block(BlockReason.OPEN_LIMIT),
            RuleEngine.decide(input(rules = own, opens = 4, groups = listOf(social), groupUsage = mapOf("g1" to 10 * m))),
        )
        assertEquals(
            Decision.Allow(remainingMillis = 50 * m),
            RuleEngine.decide(input(rules = own, passUntil = now + m, groups = listOf(social), groupUsage = mapOf("g1" to 10 * m))),
        )
    }

    @Test
    fun `the app's own limit is the remaining time when it is the smallest`() {
        val own = listOf(AppRule("insta", dailyLimitMinutes = 30))
        assertEquals(
            Decision.Allow(remainingMillis = 5 * m),
            RuleEngine.decide(input(rules = own, usedMillis = 25 * m, groups = listOf(social), groupUsage = mapOf("g1" to 10 * m))),
        )
    }

    @Test
    fun `when the own limit and a group are both used up the own limit is reported`() {
        val own = listOf(AppRule("insta", dailyLimitMinutes = 30))
        assertEquals(
            Decision.Block(BlockReason.DAILY_LIMIT),
            RuleEngine.decide(input(rules = own, usedMillis = 30 * m, groups = listOf(social), groupUsage = mapOf("g1" to 60 * m))),
        )
    }

    @Test
    fun `a group that skips Mondays still counts at 23_59 on Sunday but not at midnight`() {
        val noMondays = social.copy(limitDays = DayOfWeek.entries.toSet() - DayOfWeek.MONDAY)
        val sunday2359 = LocalDateTime.of(2026, 11, 1, 23, 59)
        val monday0000 = LocalDateTime.of(2026, 11, 2, 0, 0)
        assertEquals(
            Decision.Block(BlockReason.GROUP_LIMIT, groupId = "g1"),
            RuleEngine.decide(input(local = sunday2359, groups = listOf(noMondays), groupUsage = mapOf("g1" to 60 * m))),
        )
        assertEquals(Decision.Allow(), RuleEngine.decide(input(local = monday0000, groups = listOf(noMondays), groupUsage = mapOf("g1" to 60 * m))))
    }

    @Test
    fun `a group limit is unaffected by a DST change during the day`() {
        // Europe/Berlin falls back on Sunday 2026-10-25 at 03:00 (a 25-hour day).
        val sundays = social.copy(limitDays = setOf(DayOfWeek.SUNDAY))
        val dstNight = LocalDateTime.of(2026, 10, 25, 2, 30)
        val berlin = ZoneId.of("Europe/Berlin")
        assertEquals(
            Decision.Allow(remainingMillis = 10 * m),
            RuleEngine.decide(input(local = dstNight, groups = listOf(sundays), groupUsage = mapOf("g1" to 50 * m)).copy(zone = berlin)),
        )
        assertEquals(
            Decision.Block(BlockReason.GROUP_LIMIT, groupId = "g1"),
            RuleEngine.decide(input(local = dstNight, groups = listOf(sundays), groupUsage = mapOf("g1" to 60 * m)).copy(zone = berlin)),
        )
    }

    @Test
    fun `never-block apps don't count toward a group's usage`() {
        val usage = mapOf("insta" to AppUsage(20 * m, 3), "dialer" to AppUsage(40 * m, 2))
        val withDialer = social.copy(packages = social.packages + "dialer")
        assertEquals(mapOf("g1" to 20 * m), RuleEngine.groupUsageMillis(listOf(withDialer), usage, neverBlock = setOf("dialer")))
    }

    @Test
    fun `huge extra minutes don't overflow the allowance`() {
        val big = Int.MAX_VALUE
        val rule = AppRule("insta", dailyLimitMinutes = 1440)
        val state = EngineState(extraMinutesToday = mapOf("insta" to big, "group:g1" to big))
        assertEquals((1440L + big) * m, RuleEngine.allowedTodayMillis(rule, state, DayOfWeek.SATURDAY))
        assertEquals((60L + big) * m, RuleEngine.groupAllowedTodayMillis(social, state, DayOfWeek.SATURDAY))
    }

    @Test
    fun `quick block also covers apps that are only in a group`() {
        assertEquals(
            Decision.Block(BlockReason.QUICK_BLOCK, until = now + 30 * m),
            RuleEngine.decide(input(groups = listOf(social), quickBlockUntil = now + 30 * m)),
        )
        assertEquals(Decision.Allow(), RuleEngine.decide(input(pkg = "maps", groups = listOf(social), quickBlockUntil = now + 30 * m)))
    }

    // --- Session limit (LI-6) ---
    // Session clocks are elapsedRealtime (boot-relative), so they ignore any wall/trusted clock change.

    private val session20 = listOf(AppRule("insta", sessionLimitMinutes = 20))
    private val cooldown = RuleEngine.SESSION_COOLDOWN_MILLIS
    private val elapsed = 50_000_000L

    /** A session that started [ago] ms ago; a cool-down that ends in [cooldownLeft] ms (negative = ended). */
    private fun times(ago: Long?, cooldownLeft: Long? = null, at: Long = elapsed) =
        SessionTimes(elapsedNow = at, startedAt = ago?.let { at - it }, cooldownEndsAt = cooldownLeft?.let { at + it })

    @Test
    fun `a session limit allows the app and re-checks when the session runs out`() {
        val d = RuleEngine.decide(input(rules = session20, session = times(ago = 5 * m)))
        assertEquals(Decision.Allow(nextChangeAt = now + 15 * m), d)
    }

    @Test
    fun `a session that reached its limit is closed for the cool-down`() {
        val exactly = RuleEngine.decide(input(rules = session20, session = times(ago = 20 * m)))
        assertEquals(Decision.Block(BlockReason.SESSION_LIMIT, until = now + cooldown), exactly)
        val late = RuleEngine.decide(input(rules = session20, session = times(ago = 90 * m)))
        assertEquals(Decision.Block(BlockReason.SESSION_LIMIT, until = now + cooldown), late)
    }

    @Test
    fun `re-opening during the cool-down stays blocked until it ends`() {
        // A fresh open (new session) after switching apps: the cool-down still holds.
        val d = RuleEngine.decide(input(rules = session20, session = times(ago = 0, cooldownLeft = 4 * m)))
        assertEquals(Decision.Block(BlockReason.SESSION_LIMIT, until = now + 4 * m), d)
    }

    @Test
    fun `after the cool-down a new session starts when it ended`() {
        // The app stayed in front under the block screen: the old session must not count.
        val d = RuleEngine.decide(input(rules = session20, session = times(ago = 40 * m, cooldownLeft = -2 * m)))
        assertEquals(Decision.Allow(nextChangeAt = now + 18 * m), d)
    }

    @Test
    fun `trusted time moving backwards mid-session does not lengthen the session`() {
        // 20 minutes really passed (elapsed), but trusted time was corrected back an hour.
        val d = RuleEngine.decide(input(rules = session20, nowMillis = now - 60 * m, session = times(ago = 20 * m)))
        assertEquals(Decision.Block(BlockReason.SESSION_LIMIT, until = now - 60 * m + cooldown), d)
    }

    @Test
    fun `trusted time jumping forward during a cool-down does not shorten it`() {
        // An accepted auto-time correction moved trusted time 9 minutes ahead; only 1 minute really passed.
        val d = RuleEngine.decide(
            input(rules = session20, nowMillis = now + 9 * m, session = times(ago = 1 * m, cooldownLeft = 9 * m)),
        )
        assertEquals(Decision.Block(BlockReason.SESSION_LIMIT, until = now + 9 * m + 9 * m), d)
    }

    @Test
    fun `the cool-down ignores the calendar date and time zone`() {
        // Clock moved a day ahead: localTime jumps, the boot-relative cool-down does not.
        val d = RuleEngine.decide(
            input(rules = session20, local = saturday10am.plusDays(1), session = times(ago = 0, cooldownLeft = 4 * m)),
        )
        assertEquals(Decision.Block(BlockReason.SESSION_LIMIT, until = now + 4 * m), d)
    }

    @Test
    fun `a cool-down further ahead than possible is cut to the full cool-down`() {
        val d = RuleEngine.decide(input(rules = session20, session = times(ago = 0, cooldownLeft = 5 * cooldown)))
        assertEquals(Decision.Block(BlockReason.SESSION_LIMIT, until = now + cooldown), d)
    }

    @Test
    fun `turning the session limit off lifts the cool-down`() {
        val d = RuleEngine.decide(input(rules = listOf(AppRule("insta")), session = times(ago = 90 * m, cooldownLeft = 4 * m)))
        assertEquals(Decision.Allow(), d)
    }

    @Test
    fun `without a known session start the session limit reports nothing`() {
        assertEquals(Decision.Allow(), RuleEngine.decide(input(rules = session20)))
        assertEquals(Decision.Allow(), RuleEngine.decide(input(rules = session20, session = times(ago = null))))
    }

    @Test
    fun `the next change is whichever comes first, the session end or a schedule start`() {
        val at1030 = Schedule("s", "Study", true, setOf(DayOfWeek.SATURDAY), 10 * 60 + 30, 11 * 60, setOf("insta"))
        val soonEnd = RuleEngine.decide(input(rules = session20, schedules = listOf(at1030), session = times(ago = 15 * m)))
        assertEquals(Decision.Allow(nextChangeAt = now + 5 * m), soonEnd)
        val soonSchedule = RuleEngine.decide(
            input(rules = listOf(AppRule("insta", sessionLimitMinutes = 60)), schedules = listOf(at1030), session = times(ago = 0)),
        )
        assertEquals(Decision.Allow(nextChangeAt = now + 30 * m), soonSchedule)
    }

    @Test
    fun `a daily limit is checked before the session limit and keeps its remaining time`() {
        val rules = listOf(AppRule("insta", dailyLimitMinutes = 30, sessionLimitMinutes = 20))
        assertEquals(
            Decision.Block(BlockReason.DAILY_LIMIT),
            RuleEngine.decide(input(rules = rules, usedMillis = 30 * m, session = times(ago = 25 * m))),
        )
        assertEquals(
            Decision.Allow(remainingMillis = 20 * m, nextChangeAt = now + 15 * m),
            RuleEngine.decide(input(rules = rules, usedMillis = 10 * m, session = times(ago = 5 * m))),
        )
    }

    @Test
    fun `the cool-down comes before the mindful pause`() {
        val rules = listOf(AppRule("insta", pauseSeconds = 5, sessionLimitMinutes = 20))
        assertEquals(
            Decision.Block(BlockReason.SESSION_LIMIT, until = now + 4 * m),
            RuleEngine.decide(input(rules = rules, session = times(ago = 0, cooldownLeft = 4 * m))),
        )
        assertEquals(Decision.Pause(5), RuleEngine.decide(input(rules = rules, session = times(ago = 0))))
    }

    // --- Calls ---

    @Test
    fun `during a call the session limit waits and is re-checked shortly`() {
        val d = RuleEngine.decide(input(rules = session20, session = times(ago = 90 * m, cooldownLeft = 4 * m), inCall = true))
        assertEquals(Decision.Allow(nextChangeAt = now + RuleEngine.CALL_RECHECK_MILLIS), d)
    }

    @Test
    fun `during a call there is no mindful pause`() {
        val d = RuleEngine.decide(input(rules = listOf(AppRule("insta", pauseSeconds = 5)), inCall = true))
        assertEquals(Decision.Allow(), d)
    }

    @Test
    fun `a call does not lift other limits`() {
        val rules = listOf(AppRule("insta", dailyLimitMinutes = 30, sessionLimitMinutes = 20))
        assertEquals(Decision.Block(BlockReason.DAILY_LIMIT), RuleEngine.decide(input(rules = rules, usedMillis = 30 * m, inCall = true)))
    }
}
