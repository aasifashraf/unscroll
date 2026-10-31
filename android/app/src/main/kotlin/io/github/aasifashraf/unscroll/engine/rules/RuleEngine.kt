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

data class FocusSession(val startedAt: Long, val endsAt: Long, val packages: Set<String>)

data class EngineState(
    val rules: Map<String, AppRule> = emptyMap(),
    val schedules: List<Schedule> = emptyList(),
    val focus: FocusSession? = null,
    val quickBlockUntil: Long? = null,
    val extraMinutesToday: Map<String, Int> = emptyMap(),
    val blockedSites: Set<String> = emptySet(),
    val blockedKeywords: Set<String> = emptySet(),
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
)

/** [WEBSITE] is produced by the address-bar check, not by [RuleEngine.decide]. */
enum class BlockReason { FOCUS, QUICK_BLOCK, SCHEDULE, BLOCKED, DAILY_LIMIT, OPEN_LIMIT, WEBSITE }

sealed interface Decision {
    /**
     * [remainingMillis]: time left before today's limit is reached, if the app has one.
     * [nextChangeAt]: when a schedule for this app starts, so an app that's open then gets blocked.
     */
    data class Allow(val remainingMillis: Long? = null, val nextChangeAt: Long? = null) : Decision

    data class Pause(val seconds: Int) : Decision

    /** [until]: when the block lifts by itself (null = end of day or rule change). */
    data class Block(val reason: BlockReason, val until: Long? = null) : Decision
}

/** Pure decision logic: what to do when [RuleInput.packageName] comes to the foreground. */
object RuleEngine {
    const val MAX_PAUSE_SECONDS = 30
    private const val PAUSE_STEP_SECONDS = 2

    /**
     * Today's time allowance for [rule]: the daily limit plus any extra minutes taken today, or
     * null when no limit applies on [day]. Also used for the limits list the UI shows.
     */
    fun allowedTodayMillis(rule: AppRule, state: EngineState, day: DayOfWeek): Long? {
        val limit = rule.dailyLimitMinutes ?: return null
        if (rule.blocked || day !in rule.limitDays) return null
        return (limit + (state.extraMinutesToday[rule.packageName] ?: 0)) * 60_000L
    }

    fun decide(input: RuleInput): Decision {
        val pkg = input.packageName
        val now = input.nowMillis
        val state = input.state
        if (pkg in input.neverBlock) return Decision.Allow()

        state.focus?.let { if (now < it.endsAt && pkg in it.packages) return Decision.Block(BlockReason.FOCUS, it.endsAt) }

        val rule = state.rules[pkg]
        val quickUntil = state.quickBlockUntil
        if (rule != null && quickUntil != null && now < quickUntil) return Decision.Block(BlockReason.QUICK_BLOCK, quickUntil)

        activeScheduleEnd(state.schedules, pkg, input.localTime)?.let { end ->
            return Decision.Block(BlockReason.SCHEDULE, now + millisUntil(input, end))
        }
        val nextChange = nextScheduleStart(state.schedules, pkg, input.localTime)?.let { now + millisUntil(input, it) }

        if (rule == null) return Decision.Allow(nextChangeAt = nextChange)
        if (rule.blocked) return Decision.Block(BlockReason.BLOCKED)

        val remaining = allowedTodayMillis(rule, state, input.localTime.dayOfWeek)?.let { it - input.usageToday.millis }
        if (remaining != null && remaining <= 0) return Decision.Block(BlockReason.DAILY_LIMIT)

        rule.openLimit?.let { if (input.usageToday.opens > it) return Decision.Block(BlockReason.OPEN_LIMIT) }

        val pause = rule.pauseSeconds
        if (pause != null && (input.passUntil == null || input.passUntil <= now)) {
            val rising = pause + PAUSE_STEP_SECONDS * (input.usageToday.opens - 1).coerceAtLeast(0)
            return Decision.Pause(rising.coerceAtMost(MAX_PAUSE_SECONDS))
        }
        return Decision.Allow(remaining, nextChange)
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
