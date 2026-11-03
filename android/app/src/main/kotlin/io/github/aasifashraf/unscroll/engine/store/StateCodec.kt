package io.github.aasifashraf.unscroll.engine.store

import io.github.aasifashraf.unscroll.engine.clock.DayState
import io.github.aasifashraf.unscroll.engine.rules.AppGroup
import io.github.aasifashraf.unscroll.engine.rules.AppRule
import io.github.aasifashraf.unscroll.engine.rules.EngineState
import io.github.aasifashraf.unscroll.engine.rules.FocusSession
import io.github.aasifashraf.unscroll.engine.rules.RuleEngine
import io.github.aasifashraf.unscroll.engine.rules.Schedule
import io.github.aasifashraf.unscroll.engine.shortform.ShortFormMap
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

data class Settings(
    val onboardingDone: Boolean = false,
    val dailyGoalMinutes: Int = 180,
)

data class FocusRecord(val startedAt: Long, val endedAt: Long, val completed: Boolean)

/** Everything Unscroll keeps on disk. Usage history is not stored: the system keeps it. */
data class PersistedState(
    val engine: EngineState = EngineState(),
    val settings: Settings = Settings(),
    val day: DayState? = null,
    val focusHistory: List<FocusRecord> = emptyList(),
    /** "5 more minutes" taps used today per app (reset with the day). */
    val extensionsUsedToday: Map<String, Int> = emptyMap(),
    /** Times the user chose not to open an app on the pause screen today (reset with the day). */
    val declinedToday: Int = 0,
    /** The same, since Unscroll was installed. */
    val declinedTotal: Long = 0,
    /** Per short-form app: trusted time the feed was last detected and closed. */
    val shortFormLastSeen: Map<String, Long> = emptyMap(),
    /**
     * Per short-form app: the app version in which detection looked wrong (loop guard tripped or
     * only the fallback worked). Shown as "detection may be outdated" while that version is installed.
     */
    val shortFormSuspect: Map<String, Long> = emptyMap(),
    /** Per short-form app: the app version the detection map last matched in (gates the fallback). */
    val shortFormMatchedVersion: Map<String, Long> = emptyMap(),
    /** ShortFormMap.REVISION the two maps above were recorded with. */
    val shortFormMapRevision: Int = 0,
)

/**
 * JSON encoding of [PersistedState]. Decoding never throws: a corrupt document gives
 * defaults, a single broken entry is skipped, and out-of-range numbers are clamped.
 */
object StateCodec {
    const val VERSION = 1
    private const val MAX_HISTORY = 500
    private const val MAX_DAILY_MINUTES = 24 * 60L

    fun encode(state: PersistedState): String = JSONObject().apply {
        put("version", VERSION)
        put("rules", JSONArray(state.engine.rules.values.map(::encodeRule)))
        put("schedules", JSONArray(state.engine.schedules.map(::encodeSchedule)))
        put("groups", JSONArray(state.engine.groups.map(::encodeGroup)))
        state.engine.focus?.let {
            put("focus", JSONObject().put("startedAt", it.startedAt).put("endsAt", it.endsAt).put("packages", JSONArray(it.packages.sorted())))
        }
        state.engine.quickBlockUntil?.let { put("quickBlockUntil", it) }
        put("extraMinutesToday", JSONObject(state.engine.extraMinutesToday))
        put("blockedSites", JSONArray(state.engine.blockedSites.sorted()))
        put("blockedKeywords", JSONArray(state.engine.blockedKeywords.sorted()))
        put("extensionsUsedToday", JSONObject(state.extensionsUsedToday))
        put("sessionCooldownUntil", JSONObject(state.engine.sessionCooldownUntil))
        put("declinedToday", state.declinedToday)
        put("declinedTotal", state.declinedTotal)
        put("shortFormBlocked", JSONArray(state.engine.shortFormBlocked.sorted()))
        put("shortFormLastSeen", JSONObject(state.shortFormLastSeen))
        put("shortFormSuspect", JSONObject(state.shortFormSuspect))
        put("shortFormMatchedVersion", JSONObject(state.shortFormMatchedVersion))
        put("shortFormMapRevision", state.shortFormMapRevision)
        put("settings", JSONObject().put("onboardingDone", state.settings.onboardingDone).put("dailyGoalMinutes", state.settings.dailyGoalMinutes))
        state.day?.let {
            put(
                "day",
                JSONObject().put("day", it.day.toString()).put("zone", it.zone.id).put("dayStart", it.dayStartMillis)
                    .put("trustedWall", it.trustedWallMillis).put("elapsed", it.elapsedMillis).put("boot", it.bootCount),
            )
        }
        put(
            "focusHistory",
            JSONArray(state.focusHistory.takeLast(MAX_HISTORY).map { JSONObject().put("startedAt", it.startedAt).put("endedAt", it.endedAt).put("completed", it.completed) }),
        )
    }.toString()

    fun decode(json: String): PersistedState = decodeOrNull(json) ?: PersistedState()

    /** Like [decode], but null when the document isn't readable at all (so the caller can keep the file). */
    fun decodeOrNull(json: String): PersistedState? {
        val root = try {
            JSONObject(json)
        } catch (_: JSONException) {
            return null
        }
        val rules = root.objects("rules").mapNotNull { tryOrNull { decodeRule(it) } }.associateBy { it.packageName }
        val schedules = root.objects("schedules").mapNotNull { tryOrNull { decodeSchedule(it) } }
        val groups = root.objects("groups").mapNotNull { tryOrNull { decodeGroup(it) } }.distinctBy { it.id }
        val focus = root.optJSONObject("focus")?.let { f ->
            tryOrNull { FocusSession(f.getLong("startedAt"), f.getLong("endsAt"), f.strings("packages").toSet()) }
        }
        val settingsJson = root.optJSONObject("settings")
        val defaults = Settings()
        val settings = Settings(
            onboardingDone = settingsJson?.optBoolean("onboardingDone", false) ?: false,
            dailyGoalMinutes = settingsJson?.optInt("dailyGoalMinutes", defaults.dailyGoalMinutes)
                ?.takeIf { it in 15..1440 } ?: defaults.dailyGoalMinutes,
        )
        val day = root.optJSONObject("day")?.let { d ->
            tryOrNull {
                DayState(
                    LocalDate.parse(d.getString("day")), ZoneId.of(d.getString("zone")), d.getLong("dayStart"),
                    d.getLong("trustedWall"), d.getLong("elapsed"), d.getInt("boot"),
                )
            }
        }
        val history = root.objects("focusHistory").mapNotNull {
            tryOrNull { FocusRecord(it.getLong("startedAt"), it.getLong("endedAt"), it.getBoolean("completed")) }
        }
        return PersistedState(
            engine = EngineState(
                rules = rules,
                schedules = schedules,
                focus = focus,
                quickBlockUntil = if (root.has("quickBlockUntil")) root.optLong("quickBlockUntil") else null,
                extraMinutesToday = root.optJSONObject("extraMinutesToday").intMap(),
                blockedSites = root.strings("blockedSites").toSet(),
                blockedKeywords = root.strings("blockedKeywords").toSet(),
                groups = groups,
                sessionCooldownUntil = root.optJSONObject("sessionCooldownUntil").positiveLongMap(),
                shortFormBlocked = root.strings("shortFormBlocked").filter { it in ShortFormMap.packages }.toSet(),
            ),
            settings = settings,
            day = day,
            focusHistory = history,
            extensionsUsedToday = root.optJSONObject("extensionsUsedToday").intMap(),
            declinedToday = (root.opt("declinedToday") as? Number)?.toLong()?.coerceIn(0, Int.MAX_VALUE.toLong())?.toInt() ?: 0,
            declinedTotal = (root.opt("declinedTotal") as? Number)?.toLong()?.coerceAtLeast(0) ?: 0,
            shortFormLastSeen = root.optJSONObject("shortFormLastSeen").positiveLongMap().shortFormOnly(),
            shortFormSuspect = root.optJSONObject("shortFormSuspect").longMap(min = 0).shortFormOnly(),
            shortFormMatchedVersion = root.optJSONObject("shortFormMatchedVersion").longMap(min = 0).shortFormOnly(),
            shortFormMapRevision = (root.opt("shortFormMapRevision") as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
        )
    }

    private fun encodeRule(r: AppRule) = JSONObject().apply {
        put("package", r.packageName)
        put("blocked", r.blocked)
        r.dailyLimitMinutes?.let { put("dailyLimitMinutes", it) }
        put("limitDays", JSONArray(r.limitDays.map { it.value }.sorted()))
        r.pauseSeconds?.let { put("pauseSeconds", it) }
        r.openLimit?.let { put("openLimit", it) }
        if (r.askIntention) put("askIntention", true)
        r.sessionLimitMinutes?.let { put("sessionLimitMinutes", it) }
    }

    private fun decodeRule(o: JSONObject): AppRule {
        val days = o.ints("limitDays").filter { it in 1..7 }.map(DayOfWeek::of).toSet().ifEmpty { DayOfWeek.entries.toSet() }
        return AppRule(
            packageName = o.getString("package"),
            blocked = o.optBoolean("blocked", false),
            dailyLimitMinutes = o.intOrNull("dailyLimitMinutes")?.takeIf { it > 0 }?.coerceAtMost(24 * 60),
            limitDays = days,
            pauseSeconds = o.intOrNull("pauseSeconds")?.takeIf { it > 0 }?.coerceAtMost(30),
            openLimit = o.intOrNull("openLimit")?.takeIf { it > 0 },
            askIntention = o.opt("askIntention") == true,
            sessionLimitMinutes = o.intOrNull("sessionLimitMinutes")?.takeIf { it > 0 }
                ?.coerceIn(RuleEngine.MIN_SESSION_MINUTES, RuleEngine.MAX_SESSION_MINUTES),
        )
    }

    private fun encodeSchedule(s: Schedule) = JSONObject()
        .put("id", s.id).put("name", s.name).put("enabled", s.enabled)
        .put("days", JSONArray(s.days.map { it.value }.sorted()))
        .put("start", s.startMinute).put("end", s.endMinute)
        .put("packages", JSONArray(s.packages.sorted()))

    private fun decodeSchedule(o: JSONObject): Schedule {
        val start = o.getInt("start")
        val end = o.getInt("end")
        require(start in 0 until 1440 && end in 0 until 1440)
        return Schedule(
            id = o.getString("id"),
            name = o.optString("name", ""),
            enabled = o.optBoolean("enabled", true),
            days = o.ints("days").filter { it in 1..7 }.map(DayOfWeek::of).toSet(),
            startMinute = start,
            endMinute = end,
            packages = o.strings("packages").toSet(),
        )
    }

    private fun encodeGroup(g: AppGroup) = JSONObject()
        .put("id", g.id).put("name", g.name)
        .put("packages", JSONArray(g.packages.sorted()))
        .put("dailyLimitMinutes", g.dailyLimitMinutes)
        .put("limitDays", JSONArray(g.limitDays.map { it.value }.sorted()))

    /** Drops a group without an id, apps or a positive limit (it could never apply). */
    private fun decodeGroup(o: JSONObject): AppGroup {
        val limit = o.getInt("dailyLimitMinutes")
        val packages = o.strings("packages").filter { it.isNotBlank() }.toSet()
        val id = o.getString("id")
        require(limit > 0 && packages.isNotEmpty() && id.isNotBlank())
        return AppGroup(
            id = id,
            name = o.optString("name", ""),
            packages = packages,
            dailyLimitMinutes = limit.coerceAtMost(24 * 60),
            limitDays = o.ints("limitDays").filter { it in 1..7 }.map(DayOfWeek::of).toSet().ifEmpty { DayOfWeek.entries.toSet() },
        )
    }

    private inline fun <T> tryOrNull(block: () -> T): T? = try {
        block()
    } catch (_: JSONException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: java.time.DateTimeException) {
        null
    }

    private fun JSONObject.intOrNull(key: String): Int? = if (!has(key) || isNull(key)) null else getInt(key)

    private fun JSONObject.objects(key: String): List<JSONObject> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    private fun JSONObject.strings(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.opt(it) as? String }
    }

    private fun JSONObject.ints(key: String): List<Int> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { (array.opt(it) as? Number)?.toInt() }
    }

    /** Times (cool-down ends): non-numbers and values <= 0 dropped. */
    private fun JSONObject?.positiveLongMap(): Map<String, Long> = longMap(min = 1)

    /** Numbers of at least [min]; anything else dropped. */
    private fun JSONObject?.longMap(min: Long): Map<String, Long> {
        if (this == null) return emptyMap()
        return keys().asSequence().mapNotNull { k -> (opt(k) as? Number)?.toLong()?.takeIf { it >= min }?.let { k to it } }.toMap()
    }

    private fun Map<String, Long>.shortFormOnly() = filterKeys { it in ShortFormMap.packages }

    /** Per-day counters (extra minutes, extensions): negatives dropped, values capped at one day. */
    private fun JSONObject?.intMap(): Map<String, Int> {
        if (this == null) return emptyMap()
        return keys().asSequence().mapNotNull { k ->
            (opt(k) as? Number)?.toLong()?.takeIf { it >= 0 }?.let { k to it.coerceAtMost(MAX_DAILY_MINUTES).toInt() }
        }.toMap()
    }
}
