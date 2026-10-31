package io.github.aasifashraf.unscroll.engine.usage

/** A simplified UsageEvents.Event (adapter maps the Android constants to [Kind]). */
data class UsageEvent(
    val timeMillis: Long,
    val kind: Kind,
    val packageName: String?,
    val className: String?,
) {
    enum class Kind { RESUMED, PAUSED, SCREEN_OFF }
}

data class AppUsage(val millis: Long, val opens: Int)

/**
 * Turns raw usage events into per-app foreground time and open counts.
 *
 * Pairs each RESUMED with the matching PAUSED. A session without a PAUSED (the app crashed)
 * is closed by the next RESUMED or a screen-off, never left open for hours. Sessions are
 * clipped to [rangeStart, rangeEnd); only switches into an app inside the range count as opens.
 */
object UsageAggregator {
    fun aggregate(events: List<UsageEvent>, rangeStart: Long, rangeEnd: Long): Map<String, AppUsage> {
        val millis = HashMap<String, Long>()
        val opens = HashMap<String, Int>()
        var current: UsageEvent? = null
        var lastPackage: String? = null

        fun close(at: Long) {
            val open = current ?: return
            val start = maxOf(open.timeMillis, rangeStart)
            val end = minOf(at, rangeEnd)
            if (end > start) millis.merge(open.packageName!!, end - start, Long::plus)
            current = null
        }

        for (event in events.sortedBy { it.timeMillis }) {
            if (event.timeMillis >= rangeEnd) break
            when (event.kind) {
                UsageEvent.Kind.RESUMED -> {
                    val pkg = event.packageName ?: continue
                    close(event.timeMillis)
                    if (pkg != lastPackage && event.timeMillis >= rangeStart) opens.merge(pkg, 1, Int::plus)
                    current = event
                    lastPackage = pkg
                }
                UsageEvent.Kind.PAUSED -> {
                    val open = current
                    if (open != null && open.packageName == event.packageName && open.className == event.className) {
                        close(event.timeMillis)
                    }
                }
                UsageEvent.Kind.SCREEN_OFF -> {
                    close(event.timeMillis)
                    lastPackage = null
                }
            }
        }
        close(rangeEnd)

        return (millis.keys + opens.keys).associateWith { AppUsage(millis[it] ?: 0L, opens[it] ?: 0) }
            .filterValues { it.millis > 0 || it.opens > 0 }
    }

    /** The app in the foreground at [time] according to [events], or null (screen off / home unknown). */
    fun foregroundAt(events: List<UsageEvent>, time: Long): String? {
        var foreground: UsageEvent? = null
        for (event in events.sortedBy { it.timeMillis }) {
            if (event.timeMillis > time) break
            when (event.kind) {
                UsageEvent.Kind.RESUMED -> foreground = event
                UsageEvent.Kind.PAUSED ->
                    if (foreground?.packageName == event.packageName && foreground?.className == event.className) foreground = null
                UsageEvent.Kind.SCREEN_OFF -> foreground = null
            }
        }
        return foreground?.packageName
    }
}
