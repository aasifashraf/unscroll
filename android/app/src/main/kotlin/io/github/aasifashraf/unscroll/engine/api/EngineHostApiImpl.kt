package io.github.aasifashraf.unscroll.engine.api

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import io.github.aasifashraf.unscroll.engine.core.Engine
import io.github.aasifashraf.unscroll.engine.rules.AppRule
import io.github.aasifashraf.unscroll.engine.rules.FocusSession
import io.github.aasifashraf.unscroll.engine.rules.RuleEngine
import io.github.aasifashraf.unscroll.engine.rules.Schedule
import io.github.aasifashraf.unscroll.engine.rules.UrlRules
import io.github.aasifashraf.unscroll.engine.store.FocusRecord
import io.github.aasifashraf.unscroll.engine.usage.UsageAggregator
import io.github.aasifashraf.unscroll.engine.usage.UsageReader
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Answers the Flutter UI. Validates every input: the UI is not trusted to send sane values. */
class EngineHostApiImpl(appContext: Context) : EngineHostApi {
    private val context = appContext.applicationContext
    private val store get() = Engine.store

    init {
        Engine.init(context)
    }

    override fun getPermissionStatus() = PermissionStatus(
        usageAccess = hasUsageAccess(),
        accessibility = isAccessibilityServiceEnabled(),
        overlay = Settings.canDrawOverlays(context),
        notifications = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled(),
        batteryUnrestricted = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
        accessibilityRunning = Engine.isAccessibilityConnected,
    )

    override fun openSettings(page: SettingsPage) {
        val pkgUri = Uri.parse("package:${context.packageName}")
        val intent = when (page) {
            SettingsPage.USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            SettingsPage.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            SettingsPage.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri)
            SettingsPage.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            SettingsPage.BATTERY -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            SettingsPage.APP_DETAILS -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun getInstalledApps(): List<InstalledApp> {
        val pm = context.packageManager
        val neverBlock = Engine.system.neverBlock()
        return Engine.system.launchablePackages()
            .filter { it != context.packageName && it !in neverBlock }
            .map { pkg -> InstalledApp(pkg, Engine.system.label(pkg), iconPng(pm, pkg)) }
            .sortedBy { it.label.lowercase() }
    }

    override fun getUsage(days: Long): List<DayUsage> {
        val count = days.coerceIn(1, 31).toInt()
        val today = Engine.today()
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val hidden = Engine.system.launcherPackages() + context.packageName + "com.android.systemui"
        val todayDate = Instant.ofEpochMilli(today.dayStartMillis).atZone(today.zone).toLocalDate()
        return (count - 1 downTo 0).map { back ->
            val start = if (back == 0) today.dayStartMillis else startOf(todayDate.minusDays(back.toLong()), zone)
            val end = if (back == 0) now else if (back == 1) today.dayStartMillis else startOf(todayDate.minusDays(back - 1L), zone)
            val read = Engine.usage.read(start - UsageReader.LOOKBACK_MILLIS, end)
            val apps = UsageAggregator.aggregate(read.events, start, end)
                .filterKeys { it !in hidden }
                .map { (pkg, u) -> AppUsageInfo(pkg, u.millis, u.opens.toLong()) }
                .sortedByDescending { it.millis }
            val unlocks = Engine.usage.read(start, end).unlocks
            DayUsage(start, apps.sumOf { it.millis }, unlocks.toLong(), apps, if (back == 0) limitsToday(apps) else emptyList())
        }
    }

    /** Limits that apply today, with the allowance including today's extensions. */
    private fun limitsToday(apps: List<AppUsageInfo>): List<LimitStatus> {
        val day = Engine.today() // first: a new day resets today's extra minutes
        val state = store.state
        val weekday = Instant.ofEpochMilli(day.trustedWallMillis).atZone(day.zone).dayOfWeek
        return state.engine.rules.values
            .mapNotNull { r ->
                RuleEngine.allowedTodayMillis(r, state.engine, weekday)?.let { allowed ->
                    LimitStatus(r.packageName, apps.firstOrNull { it.packageName == r.packageName }?.millis ?: 0L, allowed)
                }
            }
            .sortedBy { it.allowedMillis - it.usedMillis } // least time left first
    }

    override fun getSnapshot(): EngineSnapshot {
        val now = Engine.trustedNow()
        val s = store.state
        return EngineSnapshot(
            rules = s.engine.rules.values.map { r ->
                AppRuleMsg(r.packageName, r.blocked, r.dailyLimitMinutes?.toLong(), r.limitDays.map { it.value.toLong() }.sorted(), r.pauseSeconds?.toLong(), r.openLimit?.toLong())
            },
            schedules = s.engine.schedules.map { sc ->
                ScheduleMsg(sc.id, sc.name, sc.enabled, sc.days.map { it.value.toLong() }.sorted(), sc.startMinute.toLong(), sc.endMinute.toLong(), sc.packages.sorted())
            },
            focus = s.engine.focus?.takeIf { it.endsAt > now }?.let { FocusMsg(it.startedAt, it.endsAt, it.packages.sorted()) },
            quickBlockUntil = s.engine.quickBlockUntil?.takeIf { it > now },
            blockedSites = s.engine.blockedSites.sorted(),
            blockedKeywords = s.engine.blockedKeywords.sorted(),
            onboardingDone = s.settings.onboardingDone,
            dailyGoalMinutes = s.settings.dailyGoalMinutes.toLong(),
            dayStartMillis = Engine.today().dayStartMillis,
            focusHistory = s.focusHistory.takeLast(100).map { FocusRecordMsg(it.startedAt, it.endedAt, it.completed) },
            engineNowMillis = now,
        )
    }

    override fun setRule(rule: AppRuleMsg) {
        if (rule.packageName.isBlank() || rule.packageName.length > 255) throw FlutterError("invalid", "Unknown app")
        requireBlockable(rule.packageName)
        val clean = AppRule(
            packageName = rule.packageName,
            blocked = rule.blocked,
            dailyLimitMinutes = rule.dailyLimitMinutes?.toInt()?.takeIf { it in 1..24 * 60 },
            limitDays = rule.limitDays.mapNotNull { d -> d.toInt().takeIf { it in 1..7 }?.let(DayOfWeek::of) }.toSet()
                .ifEmpty { DayOfWeek.entries.toSet() },
            pauseSeconds = rule.pauseSeconds?.toInt()?.takeIf { it in 1..RuleEngine.MAX_PAUSE_SECONDS },
            openLimit = rule.openLimit?.toInt()?.takeIf { it in 1..500 },
        )
        mutate { it.copy(engine = it.engine.copy(rules = it.engine.rules + (clean.packageName to clean))) }
    }

    override fun removeRule(packageName: String) =
        mutate { it.copy(engine = it.engine.copy(rules = it.engine.rules - packageName)) }

    override fun setSchedule(schedule: ScheduleMsg) {
        val start = schedule.startMinute.toInt()
        val end = schedule.endMinute.toInt()
        if (start !in 0 until 1440 || end !in 0 until 1440 || start == end) throw FlutterError("invalid", "Schedule times are invalid")
        if (schedule.id.isBlank() || schedule.id.length > 64) throw FlutterError("invalid", "Schedule id is invalid")
        val clean = Schedule(
            id = schedule.id,
            name = schedule.name.trim().take(40),
            enabled = schedule.enabled,
            days = schedule.days.mapNotNull { d -> d.toInt().takeIf { it in 1..7 }?.let(DayOfWeek::of) }.toSet(),
            startMinute = start,
            endMinute = end,
            packages = schedule.packages.filterNot(::isNeverBlock).toSet(),
        )
        mutate { st -> st.copy(engine = st.engine.copy(schedules = st.engine.schedules.filterNot { it.id == clean.id } + clean)) }
    }

    override fun removeSchedule(id: String) =
        mutate { st -> st.copy(engine = st.engine.copy(schedules = st.engine.schedules.filterNot { it.id == id })) }

    override fun addBlockedSite(site: String): Boolean {
        val host = UrlRules.normalizeSite(site) ?: return false
        mutate { it.copy(engine = it.engine.copy(blockedSites = it.engine.blockedSites + host)) }
        return true
    }

    override fun removeBlockedSite(site: String) =
        mutate { it.copy(engine = it.engine.copy(blockedSites = it.engine.blockedSites - site)) }

    override fun setBlockedKeywords(keywords: List<String>) {
        val clean = keywords.map { it.trim().lowercase() }.filter { it.length in 3..40 && ' ' !in it }.toSet()
        mutate { it.copy(engine = it.engine.copy(blockedKeywords = clean)) }
    }

    override fun startFocus(minutes: Long, packages: List<String>) {
        if (minutes !in 1..600) throw FlutterError("invalid", "Focus length must be 1–600 minutes")
        val apps = packages.filterNot(::isNeverBlock).toSet()
        if (apps.isEmpty()) throw FlutterError("invalid", "Pick at least one app")
        val now = Engine.trustedNow()
        mutate { st ->
            // Starting over a running session ends it first, so history stays complete.
            val history = st.engine.focus?.takeIf { it.endsAt > now }?.let { st.focusHistory + FocusRecord(it.startedAt, now, completed = false) }
            st.copy(engine = st.engine.copy(focus = FocusSession(now, now + minutes * 60_000, apps)), focusHistory = history ?: st.focusHistory)
        }
    }

    override fun stopFocus() {
        val now = Engine.trustedNow()
        mutate { st ->
            val focus = st.engine.focus ?: return@mutate st
            st.copy(
                engine = st.engine.copy(focus = null),
                focusHistory = st.focusHistory + FocusRecord(focus.startedAt, now, completed = now >= focus.endsAt),
            )
        }
    }

    override fun startQuickBlock(minutes: Long) {
        if (minutes !in 1..720) throw FlutterError("invalid", "Quick block must be 1–720 minutes")
        mutate { it.copy(engine = it.engine.copy(quickBlockUntil = Engine.trustedNow() + minutes * 60_000)) }
    }

    override fun stopQuickBlock() = mutate { it.copy(engine = it.engine.copy(quickBlockUntil = null)) }

    override fun setOnboardingDone(done: Boolean) = mutate { it.copy(settings = it.settings.copy(onboardingDone = done)) }

    override fun setDailyGoal(minutes: Long) {
        if (minutes !in 15..1440) throw FlutterError("invalid", "Goal must be 15–1440 minutes")
        mutate { it.copy(settings = it.settings.copy(dailyGoalMinutes = minutes.toInt())) }
    }

    private fun mutate(transform: (io.github.aasifashraf.unscroll.engine.store.PersistedState) -> io.github.aasifashraf.unscroll.engine.store.PersistedState) {
        store.update(transform)
        Engine.onStateChanged()
    }

    private fun isNeverBlock(pkg: String) = pkg in Engine.system.neverBlock()

    private fun requireBlockable(pkg: String) {
        if (isNeverBlock(pkg)) throw FlutterError("never_block", "This app can't be limited: it's needed for calls, messages or your phone to work")
    }

    private fun iconPng(pm: PackageManager, pkg: String): ByteArray? = try {
        val drawable = pm.getApplicationIcon(pkg)
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun startOf(date: LocalDate, zone: ZoneId) = date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        // Some devices report a granted op as MODE_DEFAULT; the permission state decides then.
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT ->
                context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }

    /** True when this app's accessibility service is enabled in system settings. */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.substringBefore('/') == context.packageName }
    }
}
