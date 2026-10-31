package io.github.aasifashraf.unscroll.engine.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

/** Knows which apps are user-facing and which must never be blocked. */
class SystemApps(private val context: Context) {
    private val pm = context.packageManager

    /** Packages with a launcher icon (what users think of as "apps"). */
    fun launchablePackages(): Set<String> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .mapTo(HashSet()) { it.activityInfo.packageName }

    fun launcherPackages(): Set<String> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            .mapTo(HashSet()) { it.activityInfo.packageName }

    /**
     * Call, emergency-alert and SOS screens. They count as a switch even without a launcher icon,
     * so a block screen is always removed when they appear.
     */
    fun urgentPackages(): Set<String> {
        val set = HashSet<String>(URGENT)
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let(set::add)
        return set
    }

    /** Phone, SMS, emergency, launchers, keyboards, system UI, settings and installers are always allowed. */
    fun neverBlock(): Set<String> {
        val set = HashSet<String>(ALWAYS_ALLOWED)
        set += URGENT
        set += context.packageName
        set += launcherPackages()
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let(set::add)
        Telephony.Sms.getDefaultSmsPackage(context)?.let(set::add)
        context.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.forEach { set += it.packageName }
        return set
    }

    fun label(packageName: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }

    companion object {
        private val URGENT = setOf(
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.samsung.android.incallui",
            "com.samsung.android.dialer",
            "com.android.cellbroadcastreceiver",
            "com.google.android.cellbroadcastreceiver",
            "com.android.cellbroadcastreceiver.module",
            "com.android.emergency",
            "com.google.android.apps.safetyhub",
            "com.sec.android.app.safetyassurance",
            "com.samsung.android.emergency",
        )
        private val ALWAYS_ALLOWED = setOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.emergency",
            "com.google.android.dialer",
            "com.android.dialer",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.deskclock",
            "com.google.android.deskclock",
        )
    }
}
