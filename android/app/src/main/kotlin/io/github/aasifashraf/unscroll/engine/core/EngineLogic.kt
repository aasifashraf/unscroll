package io.github.aasifashraf.unscroll.engine.core

import kotlin.math.ceil

/** Pure helpers behind [Engine], kept separate so they can be unit-tested. */
object EngineLogic {
    /**
     * Whether a window from [pkg] means the user switched apps. Only apps with a launcher icon,
     * launchers ([trackable]) and call/emergency screens ([urgent]) count. The notification shade,
     * keyboards, volume panel and system dialogs don't: they must not dismiss a block screen or
     * cancel a limit timer. Unscroll itself is handled separately.
     */
    fun isAppSwitch(pkg: String, ownPackage: String, trackable: Set<String>, urgent: Set<String>): Boolean =
        pkg != ownPackage && (pkg in trackable || pkg in urgent)

    /**
     * Extra minutes for today after one "5 more minutes" tap: always [EXTENSION]-minutes from now,
     * even if the block screen stayed up a while (the app keeps counting underneath it).
     */
    fun extraMinutesAfterExtension(limitMinutes: Int, extraNow: Int, usedMillis: Long): Int {
        val overByMinutes = ceil((usedMillis - limitMinutes * 60_000L) / 60_000.0).toInt()
        return maxOf(extraNow + EXTENSION, overByMinutes + EXTENSION)
    }

    const val EXTENSION = 5
}
