package io.github.aasifashraf.unscroll.engine.rules

import io.github.aasifashraf.unscroll.engine.usage.AppUsage
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/** The user's settings for one app. */
data class AppRule(
    val packageName: String,
    val blocked: Boolean = false,
    val dailyLimitMinutes: Int? = null,
    val limitDays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    /** Mindful pause before opening; null = off. */
    val pauseSeconds: Int? = null,
    val openLimit: Int? = null,
    /** Ask "What are you here for?" on the pause screen. The answer is never stored. */
    val askIntention: Boolean = false,
    /** Close the app after this many minutes in one open, for [RuleEngine.SESSION_COOLDOWN_MILLIS]; null = off. */
    val sessionLimitMinutes: Int? = null,
)

/** A recurring block window. If [endMinute] <= [startMinute] it runs overnight into the next day. */
data class Schedule(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val days: Set<DayOfWeek>,
    val startMinute: Int,
    val endMinute: Int,
    val packages: Set<String>,
)

/** Several apps sharing one daily limit: their usage today is added up. */
data class AppGroup(
    val id: String,
    val name: String,
    val packages: Set<String>,
    val dailyLimitMinutes: Int,
    val limitDays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
)

data class FocusSession(val startedAt: Long, val endsAt: Long, val packages: Set<String>)

data class EngineState(
    val rules: Map<String, AppRule> = emptyMap(),
    val schedules: List<Schedule> = emptyList(),
    val focus: FocusSession? = null,
    val quickBlockUntil: Long? = null,
    val extraMinutesToday: Map<String, Int> = emptyMap(),
    val blockedSites: Set<String> = emptySet(),
    val blockedKeywords: Set<String> = emptySet(),
    val groups: List<AppGroup> = emptyList(),
    /**
     * Per app: until when (trusted time) a finished session keeps it closed. Only the saved copy
     * for after a reboot; within a boot the engine times cool-downs with elapsedRealtime.
     */
    val sessionCooldownUntil: Map<String, Long> = emptyMap(),
    /** Apps whose short-video feed is blocked (only packages in ShortFormMap). */
    val shortFormBlocked: Set<String> = emptySet(),
)

data class RuleInput(
    val packageName: String,
    val nowMillis: Long,
    /** Local time in the zone of the current Unscroll day (see DayClock). */
    val localTime: LocalDateTime,
    val state: EngineState,
    /** Today's usage for this app; opens include the current open. */
    val usageToday: AppUsage,
    /** Until when a completed mindful pause lets this app through; null = no pass. */
    val passUntil: Long?,
    /** Apps that must never be blocked (phone, SMS, launcher, keyboard, Unscroll...). */
    val neverBlock: Set<String>,
    /** Zone of [localTime], for DST-correct durations. */
    val zone: java.time.ZoneId,
    /** Today's summed usage per group id (see [RuleEngine.groupUsageMillis]); missing = 0. */
    val groupUsageMillis: Map<String, Long> = emptyMap(),
    /** Session-limit clocks for this app; null = unknown (no session limit check). */
    val session: SessionTimes? = null,
    /** The phone is ringing or in a call: the session limit and the pause wait. */
    val inCall: Boolean = false,
)

/**
 * Session-limit clocks for one app, all SystemClock.elapsedRealtime(): no clock or time-zone
 * change can move them. A session never outlives a boot (screen off ends it), and a cool-down
 * from before a reboot is converted from its saved trusted-time copy.
 */
data class SessionTimes(
    val elapsedNow: Long,
    /** When the current session began; null = unknown. */
    val startedAt: Long?,
    /** When the app's cool-down ends (may be past); null = none. */
    val cooldownEndsAt: Long? = null,
)

/** [WEBSITE] is produced by the address-bar check, not by [RuleEngine.decide]. */
enum class BlockReason { FOCUS, QUICK_BLOCK, SCHEDULE, BLOCKED, DAILY_LIMIT, GROUP_LIMIT, OPEN_LIMIT, SESSION_LIMIT, WEBSITE }

sealed interface Decision {
    /**
     * [remainingMillis]: time left before today's limit is reached, if the app has one.
     * [nextChangeAt]: when the decision may change while the app stays open (a schedule starts or
     * the session limit runs out), so the engine re-checks then.
     */
    data class Allow(val remainingMillis: Long? = null, val nextChangeAt: Long? = null) : Decision

    data class Pause(val seconds: Int) : Decision

    /**
     * [until]: when the block lifts by itself (null = end of day or rule change).
     * [groupId]: the group whose shared time is used up, for [BlockReason.GROUP_LIMIT].
     */
    data class Block(val reason: BlockReason, val until: Long? = null, val groupId: String? = null) : Decision
}

/** Pure decision logic: what to do when [RuleInput.packageName] comes to the foreground. */
object RuleEngine {
    const val MAX_PAUSE_SECONDS = 30
    const val MIN_SESSION_MINUTES = 5
    const val MAX_SESSION_MINUTES = 120
    const val SESSION_COOLDOWN_MILLIS = 10 * 60_000L

    /** During a call, how often a waiting session limit is re-checked (so it applies once the call ends). */
    const val CALL_RECHECK_MILLIS = 30_000L
    private const val PAUSE_STEP_SECONDS = 2

    /**
     * Today's time allowance for [rule]: the daily limit plus any extra minutes taken today, or
     * null when no limit applies on [day]. Also used for the limits list the UI shows.
     */
    fun allowedTodayMillis(rule: AppRule, state: EngineState, day: DayOfWeek): Long? {
        val limit = rule.dailyLimitMinutes ?: return null
        if (rule.blocked || day !in rule.limitDays) return null
        return (limit.toLong() + (state.extraMinutesToday[rule.packageName] ?: 0)) * 60_000L
    }

    /** Key for a group's entries in extraMinutesToday / extensionsUsedToday (can't clash with a package name). */
    fun groupKey(groupId: String) = "group:$groupId"

    /**
     * Today's shared allowance for [group]: its limit plus extra minutes taken for the group
     * today, or null when the limit doesn't apply on [day]. Also used for the UI's limits list.
     */
    fun groupAllowedTodayMillis(group: AppGroup, state: EngineState, day: DayOfWeek): Long? {
        if (day !in group.limitDays) return null
        return (group.dailyLimitMinutes.toLong() + (state.extraMinutesToday[groupKey(group.id)] ?: 0)) * 60_000L
    }

    /**
     * Today's usage of each group: the sum of its apps' usage in [usage] (one aggregate for all
     * apps). Apps in [neverBlock] don't count: a call must never use up a group's time.
     */
    fun groupUsageMillis(groups: List<AppGroup>, usage: Map<String, AppUsage>, neverBlock: Set<String>): Map<String, Long> =
        groups.associate { g -> g.id to g.packages.sumOf { if (it in neverBlock) 0L else usage[it]?.millis ?: 0L } }

    fun decide(input: RuleInput): Decision {
        val pkg = input.packageName
        val now = input.nowMillis
        val state = input.state
        if (pkg in input.neverBlock) return Decision.Allow()

        state.focus?.let { if (now < it.endsAt && pkg in it.packages) return Decision.Block(BlockReason.FOCUS, it.endsAt) }

        // Quick block covers every app the user chose to limit: with a rule or in a group.
        val rule = state.rules[pkg]
        val quickUntil = state.quickBlockUntil
        if (quickUntil != null && now < quickUntil && (rule != null || state.groups.any { pkg in it.packages })) {
            return Decision.Block(BlockReason.QUICK_BLOCK, quickUntil)
        }

        activeScheduleEnd(state.schedules, pkg, input.localTime)?.let { end ->
            return Decision.Block(BlockReason.SCHEDULE, now + millisUntil(input, end))
        }
        val nextChange = nextScheduleStart(state.schedules, pkg, input.localTime)?.let { now + millisUntil(input, it) }

        if (rule?.blocked == true) return Decision.Block(BlockReason.BLOCKED)

        // Every limit that applies today must have time left: the strictest wins.
        val day = input.localTime.dayOfWeek
        val own = rule?.let { r -> allowedTodayMillis(r, state, day)?.let { it - input.usageToday.millis } }
        if (own != null && own <= 0) return Decision.Block(BlockReason.DAILY_LIMIT)
        val groupLeft = state.groups.filter { pkg in it.packages }.mapNotNull { g ->
            groupAllowedTodayMillis(g, state, day)?.let { g.id to it - (input.groupUsageMillis[g.id] ?: 0L) }
        }
        groupLeft.firstOrNull { it.second <= 0 }?.let { return Decision.Block(BlockReason.GROUP_LIMIT, groupId = it.first) }
        val remaining = (listOfNotNull(own) + groupLeft.map { it.second }).minOrNull()

        if (rule == null) return Decision.Allow(remaining, nextChange)
        rule.openLimit?.let { if (input.usageToday.opens > it) return Decision.Block(BlockReason.OPEN_LIMIT) }

        val minutes = rule.sessionLimitMinutes
        val times = input.session
        var sessionEnd: Long? = null
        if (minutes != null && times != null) {
            if (input.inCall) {
                sessionEnd = now + CALL_RECHECK_MILLIS // waits for the call to end, then applies
            } else {
                // Boot-clock durations, reported in trusted time. A cool-down can't last longer than one cool-down.
                val cooldownEnds = times.cooldownEndsAt
                val cooldownLeft = cooldownEnds?.let { minOf(it - times.elapsedNow, SESSION_COOLDOWN_MILLIS) }
                if (cooldownLeft != null && cooldownLeft > 0) return Decision.Block(BlockReason.SESSION_LIMIT, now + cooldownLeft)
                times.startedAt?.let { started ->
                    // Time spent under a finished cool-down's block screen doesn't count toward the next session.
                    val used = times.elapsedNow - maxOf(started, cooldownEnds ?: started)
                    val limit = minutes * 60_000L
                    if (used >= limit) return Decision.Block(BlockReason.SESSION_LIMIT, now + SESSION_COOLDOWN_MILLIS)
                    sessionEnd = now + (limit - used)
                }
            }
        }
        val nextCheck = listOfNotNull(nextChange, sessionEnd).minOrNull()

        val pause = rule.pauseSeconds
        if (pause != null && !input.inCall && (input.passUntil == null || input.passUntil <= now)) {
            val rising = pause + PAUSE_STEP_SECONDS * (input.usageToday.opens - 1).coerceAtLeast(0)
            return Decision.Pause(rising.coerceAtMost(MAX_PAUSE_SECONDS))
        }
        return Decision.Allow(remaining, nextCheck)
    }

    private fun millisUntil(input: RuleInput, target: LocalDateTime): Long =
        Duration.between(input.localTime.atZone(input.zone), target.atZone(input.zone)).toMillis()

    /** Start of the next enabled schedule covering [pkg] within the coming week, or null. */
    fun nextScheduleStart(schedules: List<Schedule>, pkg: String, local: LocalDateTime): LocalDateTime? =
        schedules.filter { it.enabled && pkg in it.packages && it.days.isNotEmpty() }.flatMap { s ->
            (0..7).map { offset -> local.toLocalDate().plusDays(offset.toLong()) }
                .filter { it.dayOfWeek in s.days }
                .map { it.atTime(LocalTime.of(s.startMinute / 60, s.startMinute % 60)) }
                .filter { it.isAfter(local) }
        }.minOrNull()

    /** End of the schedule covering [pkg] at [local], or null if none is active. */
    fun activeScheduleEnd(schedules: List<Schedule>, pkg: String, local: LocalDateTime): LocalDateTime? {
        val minute = local.hour * 60 + local.minute
        return schedules.filter { it.enabled && pkg in it.packages }.firstNotNullOfOrNull { s ->
            val end = LocalTime.of(s.endMinute / 60, s.endMinute % 60)
            if (s.endMinute > s.startMinute) {
                if (local.dayOfWeek in s.days && minute in s.startMinute until s.endMinute) local.toLocalDate().atTime(end) else null
            } else when {
                minute >= s.startMinute && local.dayOfWeek in s.days -> local.toLocalDate().plusDays(1).atTime(end)
                minute < s.endMinute && local.dayOfWeek.minus(1) in s.days -> local.toLocalDate().atTime(end)
                else -> null
            }
        }
    }
}
