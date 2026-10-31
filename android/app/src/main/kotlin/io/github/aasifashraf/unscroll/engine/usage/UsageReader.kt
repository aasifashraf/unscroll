package io.github.aasifashraf.unscroll.engine.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/** Reads the system's usage history (the source of truth) and maps it to [UsageEvent]s. */
class UsageReader(context: Context) {
    private val manager = context.getSystemService(UsageStatsManager::class.java)

    data class Result(val events: List<UsageEvent>, val unlocks: Int)

    /** Events in [from, to). Empty if usage access is missing. */
    fun read(from: Long, to: Long): Result {
        val raw = try {
            manager.queryEvents(from, to)
        } catch (_: SecurityException) {
            return Result(emptyList(), 0)
        } ?: return Result(emptyList(), 0)
        val events = ArrayList<UsageEvent>()
        var unlocks = 0
        val e = UsageEvents.Event()
        while (raw.hasNextEvent()) {
            raw.getNextEvent(e)
            val kind = when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageEvent.Kind.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED -> UsageEvent.Kind.PAUSED
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.KEYGUARD_SHOWN,
                UsageEvents.Event.DEVICE_SHUTDOWN -> UsageEvent.Kind.SCREEN_OFF
                UsageEvents.Event.KEYGUARD_HIDDEN -> {
                    unlocks++
                    null
                }
                else -> null
            } ?: continue
            events += UsageEvent(e.timeStamp, kind, e.packageName, e.className)
        }
        return Result(events, unlocks)
    }

    /** Usage per app between [start] and [end]; reads a little earlier to catch sessions already open at [start]. */
    fun usage(start: Long, end: Long): Map<String, AppUsage> =
        UsageAggregator.aggregate(read(start - LOOKBACK_MILLIS, end).events, start, end)

    companion object {
        const val LOOKBACK_MILLIS = 6L * 60 * 60 * 1000
    }
}
