package io.github.aasifashraf.unscroll.engine.store

import io.github.aasifashraf.unscroll.engine.clock.DayState
import io.github.aasifashraf.unscroll.engine.rules.AppGroup
import io.github.aasifashraf.unscroll.engine.rules.AppRule
import io.github.aasifashraf.unscroll.engine.rules.EngineState
import io.github.aasifashraf.unscroll.engine.rules.FocusSession
import io.github.aasifashraf.unscroll.engine.rules.Schedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class StateCodecTest {
    private val full = PersistedState(
        engine = EngineState(
            rules = mapOf(
                "insta" to AppRule(
                    "insta", dailyLimitMinutes = 45, limitDays = setOf(DayOfWeek.MONDAY), pauseSeconds = 5, openLimit = 10,
                    askIntention = true, sessionLimitMinutes = 25,
                ),
                "game" to AppRule("game", blocked = true),
            ),
            schedules = listOf(Schedule("b", "Bedtime", true, setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), 1380, 420, setOf("insta", "game"))),
            focus = FocusSession(1_000, 2_000, setOf("insta")),
            quickBlockUntil = 3_000,
            extraMinutesToday = mapOf("insta" to 5, "group:g1" to 10),
            blockedSites = setOf("reddit.com"),
            blockedKeywords = setOf("shorts"),
            groups = listOf(
                AppGroup("g1", "Social", setOf("insta", "tube"), 60, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),
                AppGroup("g2", "Games", setOf("game"), 30),
            ),
            sessionCooldownUntil = mapOf("insta" to 5_000L),
            shortFormBlocked = setOf("com.instagram.android", "com.google.android.youtube"),
        ),
        settings = Settings(onboardingDone = true, dailyGoalMinutes = 120),
        day = DayState(LocalDate.of(2026, 11, 1), ZoneId.of("Asia/Kolkata"), 10, 20, 30, 4),
        focusHistory = listOf(FocusRecord(1_000, 2_000, completed = true)),
        extensionsUsedToday = mapOf("insta" to 1, "group:g1" to 2),
        declinedToday = 3,
        declinedTotal = 120,
        shortFormLastSeen = mapOf("com.google.android.youtube" to 9_000L),
        shortFormSuspect = mapOf("com.snapchat.android" to 1234L),
        shortFormMatchedVersion = mapOf("com.google.android.youtube" to 1_540_000_000L),
        shortFormMapRevision = 1,
    )

    @Test
    fun roundTripsEveryField() {
        assertEquals(full, StateCodec.decode(StateCodec.encode(full)))
    }

    @Test
    fun emptyStateRoundTrips() {
        assertEquals(PersistedState(), StateCodec.decode(StateCodec.encode(PersistedState())))
    }

    @Test
    fun garbageDecodesToDefaultsInsteadOfCrashing() {
        assertEquals(PersistedState(), StateCodec.decode("{not json"))
        assertEquals(PersistedState(), StateCodec.decode(""))
    }

    @Test
    fun unknownFieldsAndMissingOptionalFieldsAreTolerated() {
        val json = """{"version":1,"rules":[{"package":"insta","futureField":true}],"somethingNew":[1,2]}"""
        val state = StateCodec.decode(json)
        assertEquals(AppRule("insta"), state.engine.rules["insta"])
    }

    @Test
    fun oneBrokenRuleDoesNotLoseTheOthers() {
        val json = """{"version":1,"rules":[{"package":"ok","blocked":true},{"noPackage":1},{"package":"also","pauseSeconds":"x"}]}"""
        val state = StateCodec.decode(json)
        assertEquals(setOf("ok"), state.engine.rules.keys)
    }

    @Test
    fun outOfRangeValuesAreClamped() {
        val json = """{"version":1,"rules":[{"package":"a","dailyLimitMinutes":-5,"pauseSeconds":9999}],"settings":{"dailyGoalMinutes":0}}"""
        val state = StateCodec.decode(json)
        assertEquals(null, state.engine.rules.getValue("a").dailyLimitMinutes)
        assertEquals(30, state.engine.rules.getValue("a").pauseSeconds)
        assertEquals(Settings().dailyGoalMinutes, state.settings.dailyGoalMinutes)
    }

    @Test
    fun emptyLimitDaysMeansEveryDayAndNegativeExtrasAreDropped() {
        val json = """{"version":1,"rules":[{"package":"a","dailyLimitMinutes":10,"limitDays":[]}],"extraMinutesToday":{"a":-5,"b":5}}"""
        val state = StateCodec.decode(json)
        assertEquals(java.time.DayOfWeek.entries.toSet(), state.engine.rules.getValue("a").limitDays)
        assertEquals(mapOf("b" to 5), state.engine.extraMinutesToday)
    }

    @Test
    fun pauseIsCappedAtThirtySeconds() {
        val state = StateCodec.decode("""{"version":1,"rules":[{"package":"a","pauseSeconds":45}]}""")
        assertEquals(30, state.engine.rules.getValue("a").pauseSeconds)
    }

    @Test
    fun unreadableDocumentsAreReportedSoTheFileCanBeKept() {
        assertEquals(null, StateCodec.decodeOrNull("{not json"))
        assertEquals(PersistedState(), StateCodec.decodeOrNull("{}"))
    }

    @Test
    fun oldStateWithoutGroupsStillLoads() {
        val json = """{"version":1,"rules":[{"package":"insta","blocked":true}]}"""
        val state = StateCodec.decode(json)
        assertEquals(emptyList<AppGroup>(), state.engine.groups)
        assertEquals(setOf("insta"), state.engine.rules.keys)
    }

    @Test
    fun invalidGroupsAreDroppedWithoutLosingTheOthers() {
        val json = """{"version":1,"groups":[
            {"id":"ok","name":"Social","packages":["insta"],"dailyLimitMinutes":60,"limitDays":[6,7]},
            {"name":"no id","packages":["insta"],"dailyLimitMinutes":60},
            {"id":"empty","name":"Empty","packages":[],"dailyLimitMinutes":60},
            {"id":"nolimit","name":"No limit","packages":["insta"]},
            {"id":"zero","name":"Zero","packages":["insta"],"dailyLimitMinutes":0},
            {"id":"bad","name":"Bad","packages":["insta"],"dailyLimitMinutes":"x"},
            "not an object"
        ]}"""
        val groups = StateCodec.decode(json).engine.groups
        assertEquals(listOf(AppGroup("ok", "Social", setOf("insta"), 60, setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))), groups)
    }

    @Test
    fun groupLimitIsClampedAndEmptyDaysMeanEveryDay() {
        val json = """{"version":1,"groups":[{"id":"g","name":"Long","packages":["a",3,"b"],"dailyLimitMinutes":5000,"limitDays":[0,9]}]}"""
        val group = StateCodec.decode(json).engine.groups.single()
        assertEquals(24 * 60, group.dailyLimitMinutes)
        assertEquals(DayOfWeek.entries.toSet(), group.limitDays)
        assertEquals(setOf("a", "b"), group.packages)
    }

    @Test
    fun duplicateGroupIdsKeepTheFirst() {
        val json = """{"version":1,"groups":[
            {"id":"g","name":"First","packages":["a"],"dailyLimitMinutes":10},
            {"id":"g","name":"Second","packages":["b"],"dailyLimitMinutes":20}
        ]}"""
        assertEquals(listOf("First"), StateCodec.decode(json).engine.groups.map { it.name })
    }

    @Test
    fun hugeExtraMinutesAreCappedAtOneDay() {
        val json = """{"version":1,"extraMinutesToday":{"a":99999999999,"b":1e30,"c":30},"extensionsUsedToday":{"a":99999999999}}"""
        val state = StateCodec.decode(json)
        assertEquals(mapOf("a" to 1440, "b" to 1440, "c" to 30), state.engine.extraMinutesToday)
        assertEquals(mapOf("a" to 1440), state.extensionsUsedToday)
    }

    @Test
    fun oldRulesGetNoIntentionPromptAndNoSessionLimit() {
        val state = StateCodec.decode("""{"version":1,"rules":[{"package":"a","pauseSeconds":5}]}""")
        val rule = state.engine.rules.getValue("a")
        assertEquals(false, rule.askIntention)
        assertEquals(null, rule.sessionLimitMinutes)
        assertEquals(emptyMap<String, Long>(), state.engine.sessionCooldownUntil)
        assertEquals(0, state.declinedToday)
        assertEquals(0L, state.declinedTotal)
    }

    @Test
    fun sessionLimitsAreClampedToFiveToOneHundredTwentyOrDropped() {
        val json = """{"version":1,"rules":[
            {"package":"low","sessionLimitMinutes":2},
            {"package":"high","sessionLimitMinutes":500},
            {"package":"zero","sessionLimitMinutes":0},
            {"package":"neg","sessionLimitMinutes":-3},
            {"package":"ok","sessionLimitMinutes":30,"askIntention":"yes"}
        ]}"""
        val rules = StateCodec.decode(json).engine.rules
        assertEquals(5, rules.getValue("low").sessionLimitMinutes)
        assertEquals(120, rules.getValue("high").sessionLimitMinutes)
        assertEquals(null, rules.getValue("zero").sessionLimitMinutes)
        assertEquals(null, rules.getValue("neg").sessionLimitMinutes)
        assertEquals(30, rules.getValue("ok").sessionLimitMinutes)
        assertEquals(false, rules.getValue("ok").askIntention)
    }

    @Test
    fun shortFormFieldsKeepOnlySupportedAppsAndSaneValues() {
        val json = """{"version":1,
            "shortFormBlocked":["com.instagram.android","com.zhiliaoapp.musically",7,"com.snapchat.android"],
            "shortFormLastSeen":{"com.instagram.android":500,"com.other":600,"com.snapchat.android":-1},
            "shortFormSuspect":{"com.facebook.katana":0,"com.other":3,"com.instagram.android":"x"},
            "shortFormMatchedVersion":{"com.google.android.youtube":12,"com.instagram.android":-2},
            "shortFormMapRevision":"bad"}"""
        val state = StateCodec.decode(json)
        assertEquals(setOf("com.instagram.android", "com.snapchat.android"), state.engine.shortFormBlocked)
        assertEquals(mapOf("com.instagram.android" to 500L), state.shortFormLastSeen)
        assertEquals(mapOf("com.facebook.katana" to 0L), state.shortFormSuspect)
        assertEquals(mapOf("com.google.android.youtube" to 12L), state.shortFormMatchedVersion)
        assertEquals(0, state.shortFormMapRevision)
    }

    @Test
    fun oldStateHasNoShortFormBlocking() {
        val state = StateCodec.decode("""{"version":1,"rules":[]}""")
        assertEquals(emptySet<String>(), state.engine.shortFormBlocked)
        assertEquals(emptyMap<String, Long>(), state.shortFormSuspect)
    }

    @Test
    fun brokenCoolDownsAndCountersAreDropped() {
        val json = """{"version":1,"sessionCooldownUntil":{"a":1000,"b":-5,"c":"x","d":0},"declinedToday":-4,"declinedTotal":"many"}"""
        val state = StateCodec.decode(json)
        assertEquals(mapOf("a" to 1000L), state.engine.sessionCooldownUntil)
        assertEquals(0, state.declinedToday)
        assertEquals(0L, state.declinedTotal)
    }
}
