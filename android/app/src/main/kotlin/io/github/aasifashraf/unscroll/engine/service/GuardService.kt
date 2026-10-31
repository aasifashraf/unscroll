package io.github.aasifashraf.unscroll.engine.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import io.github.aasifashraf.unscroll.R
import io.github.aasifashraf.unscroll.engine.core.Engine
import io.github.aasifashraf.unscroll.engine.usage.UsageEvent

/**
 * Keeps the engine alive and, when the accessibility service isn't available (turned off,
 * crashed, or revoked by Android's Advanced Protection), detects the foreground app by
 * polling the system usage history so blocking keeps working.
 */
class GuardService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastPoll = 0L
    private var lastForeground: String? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Engine.onScreenOff()
                    lastForeground = null
                    handler.removeCallbacks(poll)
                }
                Intent.ACTION_SCREEN_ON -> schedulePoll()
                Intent.ACTION_USER_PRESENT -> Engine.onUserPresent()
                Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REMOVED -> Engine.refreshSystemApps()
            }
        }
    }

    private val poll = object : Runnable {
        override fun run() {
            if (!Engine.isAccessibilityConnected) {
                val now = System.currentTimeMillis()
                val from = if (lastPoll == 0L) now - 60 * 60_000L else lastPoll - 2_000L
                val latest = Engine.usage.read(from, now).events.lastOrNull { it.kind == UsageEvent.Kind.RESUMED }
                lastPoll = now
                if (latest?.packageName != null && latest.packageName != lastForeground) {
                    lastForeground = latest.packageName
                    Engine.onForeground(latest.packageName, latest.className)
                }
            }
            schedulePoll()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Engine.init(this)
        Notifications.ensureChannels(this)
        if (!goForeground()) return
        registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
        )
        registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            },
        )
        schedulePoll()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun schedulePoll() {
        handler.removeCallbacks(poll)
        if (!getSystemService(PowerManager::class.java).isInteractive) return
        // Poll fast without accessibility; otherwise check now and then in case it drops.
        handler.postDelayed(poll, if (Engine.isAccessibilityConnected) IDLE_CHECK_MILLIS else POLL_MILLIS)
    }

    private var stopped = false

    /** Returns false if Android refused (e.g. a background restart on Android 12+); then we stop. */
    private fun goForeground(): Boolean {
        if (stopped) return false
        val now = System.currentTimeMillis()
        val focusEnd = Engine.store.state.engine.focus?.endsAt?.takeIf { it > now }
        val text = getString(if (focusEnd != null) R.string.guard_focus else R.string.guard_text)
        val notification = Notifications.guard(this, text, focusEnd)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(Notifications.ID_GUARD, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(Notifications.ID_GUARD, notification)
            }
            true
        } catch (e: RuntimeException) {
            // Must not crash: this process also hosts the accessibility service.
            Log.w("UnscrollGuard", "Foreground start refused; the accessibility service keeps blocking", e)
            stopped = true
            stopSelf()
            false
        }
    }

    companion object {
        private const val POLL_MILLIS = 1_500L
        private const val IDLE_CHECK_MILLIS = 15_000L

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, GuardService::class.java))
            } catch (_: IllegalStateException) {
                // Background start not allowed right now; accessibility or the next app launch restarts us.
            } catch (_: SecurityException) {
            }
        }

        /** Updates the notification (e.g. the focus countdown). */
        fun refresh(context: Context) = start(context)
    }
}
