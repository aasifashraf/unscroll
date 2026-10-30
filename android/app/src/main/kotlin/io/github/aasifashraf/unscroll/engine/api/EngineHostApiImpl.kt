package io.github.aasifashraf.unscroll.engine.api

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings

/** Answers the Flutter UI's engine calls. Read-only: it never changes settings. */
class EngineHostApiImpl(appContext: Context) : EngineHostApi {
    private val context = appContext.applicationContext

    override fun getPermissionStatus() = PermissionStatus(
        usageAccess = hasUsageAccess(),
        accessibility = isAccessibilityServiceEnabled(),
        overlay = Settings.canDrawOverlays(context),
        notifications = context.getSystemService(NotificationManager::class.java)
            .areNotificationsEnabled(),
        batteryUnrestricted = context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName),
    )

    private fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
            )
        }
        // Some devices report a granted op as MODE_DEFAULT; the permission state decides then.
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT ->
                context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                    PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }

    /** True when any of this app's accessibility services is enabled in system settings. */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { it.substringBefore('/') == context.packageName }
    }
}
