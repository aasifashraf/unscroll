package io.github.aasifashraf.unscroll.engine.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
