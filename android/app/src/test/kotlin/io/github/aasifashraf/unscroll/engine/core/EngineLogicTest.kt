package io.github.aasifashraf.unscroll.engine.core

import android.media.AudioManager
import io.github.aasifashraf.unscroll.engine.clock.ClockReading
import io.github.aasifashraf.unscroll.engine.clock.DayClock
import io.github.aasifashraf.unscroll.engine.core.EngineLogic.AppSession
import io.github.aasifashraf.unscroll.engine.rules.AppGroup
import io.github.aasifashraf.unscroll.engine.rules.AppRule
import io.github.aasifashraf.unscroll.engine.rules.BlockReason
import io.github.aasifashraf.unscroll.engine.rules.EngineState
import io.github.aasifashraf.unscroll.engine.store.PersistedState
import io.github.aasifashraf.unscroll.engine.store.StateCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class EngineLogicTest {
    private val trackable = setOf("insta", "com.launcher", "com.android.settings")
    private val callApps = setOf("com.android.phone")

    @Test
    fun appsAndLaunchersAreRealSwitches() {
        assertTrue(EngineLogic.isAppSwitch("insta", "me", trackable, callApps))
        assertTrue(EngineLogic.isAppSwitch("com.launcher", "me", trackable, callApps))
    }

    @Test
    fun theInCallScreenIsASwitchSoEmergencyCallsAreNeverCovered() {
        assertTrue(EngineLogic.isAppSwitch("com.android.phone", "me", trackable, callApps))
    }

    @Test
    fun shadeKeyboardVolumePanelAndDialogsAreNotSwitches() {
        for (pkg in listOf("com.android.systemui", "com.google.android.inputmethod.latin", "android", "com.android.permissioncontroller")) {
            assertFalse(pkg, EngineLogic.isAppSwitch(pkg, "me", trackable, callApps))
        }
    }

    @Test
    fun unscrollItselfIsHandledSeparately() {
        assertFalse(EngineLogic.isAppSwitch("me", "me", trackable + "me", callApps))
    }

    @Test
    fun anExtensionAlwaysGivesFiveMinutesFromNow() {
        // Limit 45, no extra yet, already used 49 minutes (block screen stayed up for 4 minutes).
        assertEquals(9, EngineLogic.extraMinutesAfterExtension(limitMinutes = 45, extraNow = 0, usedMillis = 49 * 60_000L))
        // Used exactly the limit: plain +5.
        assertEquals(5, EngineLogic.extraMinutesAfterExtension(45, 0, 45 * 60_000L))
        // Second extension on top of the first.
        assertEquals(10, EngineLogic.extraMinutesAfterExtension(45, 5, 50 * 60_000L))
    }

    @Test
    fun partialMinutesRoundUpInTheUsersFavour() {
        assertEquals(6, EngineLogic.extraMinutesAfterExtension(45, 0, 45 * 60_000L + 1))
    }

    // --- Which limit "5 more minutes" extends ---

    private val m = 60_000L
    private val state = EngineState(
        rules = mapOf("insta" to AppRule("insta", dailyLimitMinutes = 45), "game" to AppRule("game", blocked = true)),
        groups = listOf(AppGroup("g1", "Social", setOf("insta", "tube"), 60)),
    )

    @Test
    fun aUsedUpOwnLimitExtendsTheAppWithItsOwnUsage() {
        assertEquals(
            EngineLogic.Extension(key = "insta", limitMinutes = 45, usedMillis = 46 * m),
            EngineLogic.extensionFor(BlockReason.DAILY_LIMIT, null, "insta", state, appUsedMillis = 46 * m, groupUsageMillis = mapOf("g1" to 70 * m)),
        )
    }

    @Test
    fun aUsedUpGroupExtendsTheGroupWithItsSummedUsage() {
        assertEquals(
            EngineLogic.Extension(key = "group:g1", limitMinutes = 60, usedMillis = 70 * m),
            EngineLogic.extensionFor(BlockReason.GROUP_LIMIT, "g1", "insta", state, appUsedMillis = 10 * m, groupUsageMillis = mapOf("g1" to 70 * m)),
        )
    }

    @Test
    fun otherBlocksCantBeExtended() {
        for (reason in BlockReason.entries - BlockReason.DAILY_LIMIT - BlockReason.GROUP_LIMIT) {
            assertNull(reason.name, EngineLogic.extensionFor(reason, null, "insta", state, 0, emptyMap()))
        }
    }

    @Test
    fun aGroupDeletedBeforeTheTapGrantsNothing() {
        assertNull(EngineLogic.extensionFor(BlockReason.GROUP_LIMIT, "gone", "insta", state, 0, mapOf("gone" to 90 * m)))
        assertNull(EngineLogic.extensionFor(BlockReason.GROUP_LIMIT, null, "insta", state, 0, emptyMap()))
    }

    @Test
    fun anOwnLimitRemovedBeforeTheTapGrantsNothing() {
        assertNull(EngineLogic.extensionFor(BlockReason.DAILY_LIMIT, null, "maps", state, 50 * m, emptyMap()))
        assertNull(EngineLogic.extensionFor(BlockReason.DAILY_LIMIT, null, "game", state, 50 * m, emptyMap()))
    }

    @Test
    fun aGroupMissingFromTheUsageMapCountsAsUnused() {
        assertEquals(0L, EngineLogic.extensionFor(BlockReason.GROUP_LIMIT, "g1", "insta", state, 10 * m, emptyMap())?.usedMillis)
    }

    // --- Names ---

    @Test
    fun namesAreCutToWholeCharacters() {
        assertEquals("Social", EngineLogic.truncateCodePoints("Social", 40))
        assertEquals("a".repeat(40), EngineLogic.truncateCodePoints("a".repeat(50), 40))
        val emoji = "\uD83D\uDE00" // one code point, two UTF-16 units
        assertEquals(emoji.repeat(40), EngineLogic.truncateCodePoints(emoji.repeat(41), 40))
    }

    // --- Session tracking (LI-6) ---

    private val grace = EngineLogic.SESSION_GRACE_MILLIS

    @Test
    fun aSessionKeepsItsStartWhileTheSameAppStaysInFront() {
        val s1 = EngineLogic.sessionsAfterSwitch(emptyMap(), "insta", now = 100L)
        val s2 = EngineLogic.sessionsAfterSwitch(s1, "insta", now = 900L) // forced re-check
        assertEquals(AppSession(100L), s2["insta"])
    }

    @Test
    fun aQuickTripHomeContinuesTheSession() {
        val opened = EngineLogic.sessionsAfterSwitch(emptyMap(), "insta", now = 0L)
        val home = EngineLogic.sessionsAfterSwitch(opened, "com.launcher", now = 10 * m)
        val back = EngineLogic.sessionsAfterSwitch(home, "insta", now = 10 * m + grace)
        assertEquals(0L, back.getValue("insta").startedAt)
    }

    @Test
    fun screenOffAndOnWithinAMinuteContinuesTheSession() {
        val opened = EngineLogic.sessionsAfterSwitch(emptyMap(), "insta", now = 0L)
        val off = EngineLogic.sessionsAfterSwitch(opened, null, now = 5 * m)
        assertEquals(0L, EngineLogic.sessionsAfterSwitch(off, "insta", now = 5 * m + 30_000).getValue("insta").startedAt)
    }

    @Test
    fun comingBackAfterMoreThanAMinuteStartsANewSession() {
        val opened = EngineLogic.sessionsAfterSwitch(emptyMap(), "insta", now = 0L)
        val tube = EngineLogic.sessionsAfterSwitch(opened, "tube", now = 5 * m)
        val back = EngineLogic.sessionsAfterSwitch(tube, "insta", now = 5 * m + grace + 1)
        assertEquals(5 * m + grace + 1, back.getValue("insta").startedAt)
        val off = EngineLogic.sessionsAfterSwitch(opened, null, now = 5 * m)
        assertEquals(8 * m, EngineLogic.sessionsAfterSwitch(off, "insta", now = 8 * m).getValue("insta").startedAt)
    }

    @Test
    fun sessionsLeftLongAgoAreForgotten() {
        val opened = EngineLogic.sessionsAfterSwitch(emptyMap(), "insta", now = 0L)
        val tube = EngineLogic.sessionsAfterSwitch(opened, "tube", now = m)
        val later = EngineLogic.sessionsAfterSwitch(tube, "tube", now = m + grace + 1)
        assertEquals(setOf("tube"), later.keys)
    }

    @Test
    fun aPersistedCoolDownBecomesABootClockCoolDownCutToTenMinutes() {
        val trusted = 1_000_000L
        val elapsed = 5_000L
        val persisted = mapOf("insta" to trusted + 4 * m, "old" to trusted - m, "far" to trusted + 50 * m)
        assertEquals(
            mapOf("insta" to elapsed + 4 * m, "far" to elapsed + 10 * m),
            EngineLogic.inBootCooldowns(persisted, trustedNow = trusted, elapsedNow = elapsed),
        )
    }

    @Test
    fun aNewCoolDownIsStoredAndExpiredOnesAreDropped() {
        val before = mapOf("old" to 50L, "tube" to 2_000L)
        assertEquals(mapOf("tube" to 2_000L, "insta" to 1_600L), EngineLogic.withCooldown(before, "insta", until = 1_600L, now = 1_000L))
    }

    // --- "Chose not to open" counter (MF-2) ---

    private val urgent = setOf("com.android.phone")

    @Test
    fun leavingAnUnansweredPauseForHomeOrAnotherAppCountsAsDeclined() {
        assertTrue(EngineLogic.countsAsDecline(pausing = "insta", previous = "insta", next = "com.launcher", urgent = urgent))
        assertTrue(EngineLogic.countsAsDecline(pausing = "insta", previous = "insta", next = "tube", urgent = urgent))
    }

    @Test
    fun aCallDuringThePauseDoesNotCount() {
        assertFalse(EngineLogic.countsAsDecline(pausing = "insta", previous = "insta", next = "com.android.phone", urgent = urgent))
    }

    @Test
    fun reCheckingTheSameAppDoesNotCount() {
        assertFalse(EngineLogic.countsAsDecline(pausing = "insta", previous = "insta", next = "insta", urgent = urgent))
    }

    @Test
    fun withoutAPauseNothingCounts() {
        assertFalse(EngineLogic.countsAsDecline(pausing = null, previous = "insta", next = "com.launcher", urgent = urgent))
        // A pause for an app that is no longer in front (already handled) doesn't count again.
        assertFalse(EngineLogic.countsAsDecline(pausing = "insta", previous = "tube", next = "com.launcher", urgent = urgent))
    }

    // --- Calls and clocks ---

    @Test
    fun ringingAndCallsAreCallModes() {
        assertTrue(EngineLogic.isCallMode(AudioManager.MODE_RINGTONE))
        assertTrue(EngineLogic.isCallMode(AudioManager.MODE_IN_CALL))
        assertTrue(EngineLogic.isCallMode(AudioManager.MODE_IN_COMMUNICATION))
        assertFalse(EngineLogic.isCallMode(AudioManager.MODE_NORMAL))
    }

    @Test
    fun aTrustedTimeIsShownOnThePhonesClock() {
        // The phone's clock is 2 hours ahead of trusted time: "back at" must use the phone's clock.
        assertEquals(10 * m + 120 * m, EngineLogic.toWallClock(10 * m, trustedNow = 0L, wallNow = 120 * m))
    }

    private val kolkata = ZoneId.of("Asia/Kolkata")
    private fun wall(dateTime: String) = LocalDateTime.parse(dateTime).atZone(kolkata).toInstant().toEpochMilli()
    private fun reading(dateTime: String, elapsed: Long) = ClockReading(wall(dateTime), elapsed, bootCount = 1, zone = kolkata, autoTime = false)

    @Test
    fun decliningCountsTodayAndInTotal() {
        val once = EngineLogic.withDecline(PersistedState(declinedToday = 2, declinedTotal = 40))
        assertEquals(3, once.declinedToday)
        assertEquals(41L, once.declinedTotal)
    }

    @Test
    fun aNewDayResetsTodaysDeclinesButKeepsTheTotal() {
        val day1 = DayClock.advance(null, reading("2026-11-01T23:50", 0))
        val start = PersistedState(day = day1, declinedToday = 4, declinedTotal = 10, extensionsUsedToday = mapOf("insta" to 1))
        // Saved and loaded again (e.g. after a process restart), then midnight passes.
        val loaded = StateCodec.decode(StateCodec.encode(start))
        val day2 = DayClock.advance(loaded.day, reading("2026-11-02T00:10", 20 * m))
        val next = EngineLogic.withDay(loaded, day2)
        assertEquals(LocalDate.of(2026, 11, 2), next.day?.day)
        assertEquals(0, next.declinedToday)
        assertEquals(10L, next.declinedTotal)
        assertEquals(emptyMap<String, Int>(), next.extensionsUsedToday)
    }

    @Test
    fun theSameDayKeepsTodaysDeclines() {
        val day1 = DayClock.advance(null, reading("2026-11-01T10:00", 0))
        val start = PersistedState(day = day1, declinedToday = 4, declinedTotal = 10)
        val later = DayClock.advance(day1, reading("2026-11-01T18:00", 8 * 60 * m))
        val next = EngineLogic.withDay(start, later)
        assertEquals(4, next.declinedToday)
        assertEquals(later, next.day)
    }

    @Test
    fun aClockMovedADayAheadDoesNotResetTodaysDeclines() {
        val day1 = DayClock.advance(null, reading("2026-11-01T10:00", 0))
        val start = PersistedState(day = day1, declinedToday = 4, declinedTotal = 10)
        // Wall clock set to tomorrow by hand, only a minute of real time passed.
        val tampered = DayClock.advance(day1, reading("2026-11-02T10:01", m))
        assertEquals(4, EngineLogic.withDay(start, tampered).declinedToday)
    }

    @Test
    fun smallClockStepsAreNotWrittenToDiskEveryTime() {
        val day1 = DayClock.advance(null, reading("2026-11-01T10:00", 0))
        val start = PersistedState(day = day1)
        val soon = DayClock.advance(day1, reading("2026-11-01T10:00:30", 30_000))
        assertTrue(EngineLogic.withDay(start, soon) === start)
    }

    private val yt = "com.google.android.youtube"

    @Test
    fun aNewDetectionMapClearsOldOutdatedMarksButKeepsTheUsersChoices() {
        val old = PersistedState(
            engine = EngineState(shortFormBlocked = setOf(yt)),
            shortFormSuspect = mapOf(yt to 5L),
            shortFormMatchedVersion = mapOf(yt to 5L),
            shortFormLastSeen = mapOf(yt to 100L),
            shortFormMapRevision = 1,
        )
        val next = EngineLogic.withShortFormRevision(old, 2)
        assertEquals(emptyMap<String, Long>(), next.shortFormSuspect)
        assertEquals(emptyMap<String, Long>(), next.shortFormMatchedVersion)
        assertEquals(setOf(yt), next.engine.shortFormBlocked)
        assertEquals(mapOf(yt to 100L), next.shortFormLastSeen)
        assertEquals(2, next.shortFormMapRevision)
        assertTrue("same map: unchanged", EngineLogic.withShortFormRevision(next, 2) === next)
    }

    @Test
    fun aMapDetectionRecordsTheTimeAndTheVersionTheMapWorkedIn() {
        val next = EngineLogic.withShortFormDetection(PersistedState(), yt, trustedNow = 1_000_000, appVersion = 42, byFallback = false)
        assertEquals(mapOf(yt to 1_000_000L), next.shortFormLastSeen)
        assertEquals(mapOf(yt to 42L), next.shortFormMatchedVersion)
        assertEquals(emptyMap<String, Long>(), next.shortFormSuspect)
    }

    @Test
    fun aFallbackDetectionMarksTheAppAsMaybeOutdated() {
        val next = EngineLogic.withShortFormDetection(PersistedState(), yt, trustedNow = 1_000_000, appVersion = 42, byFallback = true)
        assertEquals(mapOf(yt to 42L), next.shortFormSuspect)
        assertEquals(emptyMap<String, Long>(), next.shortFormMatchedVersion)
        assertEquals(mapOf(yt to 1_000_000L), next.shortFormLastSeen)
    }

    @Test
    fun repeatedDetectionsWithinAMinuteDoNotRewriteTheFile() {
        val first = EngineLogic.withShortFormDetection(PersistedState(), yt, 1_000_000, 42, byFallback = false)
        assertTrue(EngineLogic.withShortFormDetection(first, yt, 1_030_000, 42, byFallback = false) === first)
        val later = EngineLogic.withShortFormDetection(first, yt, 1_060_001, 42, byFallback = false)
        assertEquals(1_060_001L, later.shortFormLastSeen[yt])
        // A new app version is saved right away.
        assertEquals(43L, EngineLogic.withShortFormDetection(first, yt, 1_010_000, 43, byFallback = false).shortFormMatchedVersion[yt])
    }

    @Test
    fun unknownAppVersionStillRecordsTheTimeOnly() {
        val next = EngineLogic.withShortFormDetection(PersistedState(), yt, 1_000, appVersion = null, byFallback = true)
        assertEquals(mapOf(yt to 1_000L), next.shortFormLastSeen)
        assertEquals(emptyMap<String, Long>(), next.shortFormSuspect)
    }

    @Test
    fun theLoopGuardMarksTheAppVersionAsMaybeOutdated() {
        assertEquals(mapOf(yt to 42L), EngineLogic.withShortFormSuspect(PersistedState(), yt, 42).shortFormSuspect)
        val none = PersistedState()
        assertTrue(EngineLogic.withShortFormSuspect(none, yt, null) === none)
    }

    @Test
    fun turningAFeedOnOrOffSavesItAndClearsItsOutdatedMark() {
        val start = PersistedState(shortFormSuspect = mapOf(yt to 42L, "com.instagram.android" to 1L))
        val on = EngineLogic.withShortFormBlocked(start, yt, true)
        assertEquals(setOf(yt), on.engine.shortFormBlocked)
        assertEquals(mapOf("com.instagram.android" to 1L), on.shortFormSuspect)
        assertEquals(emptySet<String>(), EngineLogic.withShortFormBlocked(on, yt, false).engine.shortFormBlocked)
    }
}
