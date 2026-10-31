package io.github.aasifashraf.unscroll.engine.store

import io.github.aasifashraf.unscroll.engine.clock.DayState
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
                "insta" to AppRule("insta", dailyLimitMinutes = 45, limitDays = setOf(DayOfWeek.MONDAY), pauseSeconds = 5, openLimit = 10),
                "game" to AppRule("game", blocked = true),
            ),
            schedules = listOf(Schedule("b", "Bedtime", true, setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), 1380, 420, setOf("insta", "game"))),
            focus = FocusSession(1_000, 2_000, setOf("insta")),
            quickBlockUntil = 3_000,
            extraMinutesToday = mapOf("insta" to 5),
            blockedSites = setOf("reddit.com"),
            blockedKeywords = setOf("shorts"),
        ),
        settings = Settings(onboardingDone = true, dailyGoalMinutes = 120),
        day = DayState(LocalDate.of(2026, 11, 1), ZoneId.of("Asia/Kolkata"), 10, 20, 30, 4),
        focusHistory = listOf(FocusRecord(1_000, 2_000, completed = true)),
        extensionsUsedToday = mapOf("insta" to 1),
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
}
