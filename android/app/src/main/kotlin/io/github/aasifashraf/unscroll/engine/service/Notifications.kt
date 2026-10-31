package io.github.aasifashraf.unscroll.engine.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.aasifashraf.unscroll.MainActivity
import io.github.aasifashraf.unscroll.R

/** All notifications Unscroll posts. Without notification permission they are simply hidden. */
object Notifications {
    const val CHANNEL_GUARD = "guard"
    const val CHANNEL_ALERTS = "alerts"
    const val ID_GUARD = 1
    private const val ID_WARNING = 2
    private const val ID_FOCUS_DONE = 3
    private const val ID_BLOCKED = 4

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_GUARD, context.getString(R.string.channel_guard), NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun guard(context: Context, text: String, countdownUntil: Long?): Notification =
        Notification.Builder(context, CHANNEL_GUARD)
            .setSmallIcon(R.drawable.ic_stat_unscroll)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openApp(context))
            .apply {
                if (countdownUntil != null) {
                    setUsesChronometer(true)
                    setChronometerCountDown(true)
                    setWhen(countdownUntil)
                    setShowWhen(true)
                }
            }
            .build()

    fun limitWarning(context: Context, appLabel: String, minutesLeft: Long) = post(
        context,
        ID_WARNING,
        context.resources.getQuantityString(R.plurals.limit_warning, minutesLeft.toInt(), appLabel, minutesLeft.toInt()),
        null,
    )

    fun focusDone(context: Context) =
        post(context, ID_FOCUS_DONE, context.getString(R.string.focus_done_title), context.getString(R.string.focus_done_text))

    fun blocked(context: Context, title: String, text: String) = post(context, ID_BLOCKED, title, text)

    private fun post(context: Context, id: Int, title: String, text: String?) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return
        ensureChannels(context)
        nm.notify(
            id,
            Notification.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_unscroll)
                .setContentTitle(title)
                .apply { text?.let { setContentText(it) } }
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openApp(context))
                .build(),
        )
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
