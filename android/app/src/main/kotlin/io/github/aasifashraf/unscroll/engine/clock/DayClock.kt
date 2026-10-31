package io.github.aasifashraf.unscroll.engine.clock

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One sample of the device clocks. [elapsedMillis] is SystemClock.elapsedRealtime(). */
data class ClockReading(
    val wallMillis: Long,
    val elapsedMillis: Long,
    val bootCount: Int,
    val zone: ZoneId,
    /** Settings.Global.AUTO_TIME: the system keeps the wall clock synced. */
    val autoTime: Boolean,
)

/** What Unscroll currently considers "today". Persisted between readings. */
data class DayState(
    val day: LocalDate,
    /** Zone the day boundary is measured in; a zone change only applies from the next day. */
    val zone: ZoneId,
    /** When [day] began (epoch millis). Usage for limits is counted from here. */
    val dayStartMillis: Long,
    /** Best estimate of real time at the last reading. */
    val trustedWallMillis: Long,
    val elapsedMillis: Long,
    val bootCount: Int,
)

/**
 * Tamper-resistant day boundaries. The only place that decides when a new day starts.
 *
 * - Within one boot, real time advances by elapsedRealtime, so moving the clock by hand changes
 *   nothing. With auto-time on, small network corrections and corrections backwards are accepted
 *   (a later clock only lengthens the day); a big jump forward is not (e.g. auto-time toggled
 *   back on while offline after setting the clock ahead).
 * - Real time never moves backwards, and a day never ends before [MIN_DAY_MILLIS] has passed
 *   (stops time-zone hopping from minting extra days; DST's 23-hour day still rolls over).
 * - Known limit: after a reboot with auto-time off only the wall clock is available.
 */
object DayClock {
    const val MIN_DAY_MILLIS = 20L * 60 * 60 * 1000
    private const val SYNC_TOLERANCE_MILLIS = 10L * 60 * 1000

    fun advance(previous: DayState?, now: ClockReading): DayState {
        if (previous == null) {
            val day = dateOf(now.wallMillis, now.zone)
            return DayState(day, now.zone, startOf(day, now.zone), now.wallMillis, now.elapsedMillis, now.bootCount)
        }

        val sameBoot = now.bootCount == previous.bootCount && now.elapsedMillis >= previous.elapsedMillis
        val trusted = if (sameBoot) {
            val byElapsed = previous.trustedWallMillis + (now.elapsedMillis - previous.elapsedMillis)
            val drift = now.wallMillis - byElapsed
            if (now.autoTime && (drift < 0 || drift <= SYNC_TOLERANCE_MILLIS)) now.wallMillis else byElapsed
        } else {
            maxOf(now.wallMillis, previous.trustedWallMillis)
        }

        val dateInLockedZone = dateOf(trusted, previous.zone)
        val dayIsLongEnough = trusted - previous.dayStartMillis >= MIN_DAY_MILLIS
        if (dateInLockedZone.isAfter(previous.day) && dayIsLongEnough) {
            val midnight = startOf(dateInLockedZone, previous.zone)
            return DayState(
                day = dateInLockedZone,
                zone = now.zone,
                dayStartMillis = maxOf(midnight, previous.dayStartMillis + MIN_DAY_MILLIS),
                trustedWallMillis = trusted,
                elapsedMillis = now.elapsedMillis,
                bootCount = now.bootCount,
            )
        }
        return previous.copy(trustedWallMillis = trusted, elapsedMillis = now.elapsedMillis, bootCount = now.bootCount)
    }

    /**
     * Start of [state]'s day expressed in the device's wall clock at [nowWall] (the frame Android's
     * usage history uses). Equal to dayStartMillis when the clock is honest; never after [nowWall].
     * [state] must come from advance() with a reading taken at [nowWall].
     */
    fun wallDayStart(state: DayState, nowWall: Long): Long =
        minOf(state.dayStartMillis + (nowWall - state.trustedWallMillis), nowWall)

    private fun dateOf(millis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private fun startOf(day: LocalDate, zone: ZoneId): Long =
        day.atStartOfDay(zone).toInstant().toEpochMilli()
}
