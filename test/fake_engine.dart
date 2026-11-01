import 'package:flutter/services.dart';
import 'package:unscroll/engine/engine_api.g.dart';

/// In-memory engine for widget tests. Records calls so tests can assert on them.
class FakeEngine extends EngineHostApi {
  FakeEngine({
    this.onboardingDone = true,
    PermissionStatus? permissions,
    List<InstalledApp>? apps,
    List<DayUsage>? usage,
  }) : permissions = permissions ?? granted(),
       apps =
           apps ??
           [
             InstalledApp(packageName: 'com.insta', label: 'Insta'),
             InstalledApp(packageName: 'com.tube', label: 'Tube'),
           ],
       usage = usage ?? sampleWeek();

  bool onboardingDone;
  PermissionStatus permissions;
  List<InstalledApp> apps;
  List<DayUsage> usage;
  final rules = <String, AppRuleMsg>{};
  final schedules = <String, ScheduleMsg>{};
  final sites = <String>{};
  List<String> keywords = [];
  FocusMsg? focus;
  int? quickBlockUntil;
  int goal = 180;
  final calls = <String>[];

  /// How far the engine's clock is behind the phone's (after a manual clock change).
  int engineBehindMillis = 0;
  final opened = <SettingsPage>[];

  static PermissionStatus granted() => PermissionStatus(
    usageAccess: true,
    accessibility: true,
    overlay: true,
    notifications: true,
    batteryUnrestricted: true,
    accessibilityRunning: true,
  );

  static PermissionStatus none() => PermissionStatus(
    usageAccess: false,
    accessibility: false,
    overlay: false,
    notifications: false,
    batteryUnrestricted: false,
    accessibilityRunning: false,
  );

  static List<DayUsage> sampleWeek() {
    final start = DateTime(2026, 10, 26).millisecondsSinceEpoch;
    return [
      for (var i = 0; i < 7; i++)
        DayUsage(
          dayStartMillis: start + i * 86400000,
          totalMillis: (2 + i % 3) * 3600000,
          unlocks: 40 + i,
          apps: [
            AppUsageInfo(
              packageName: 'com.insta',
              millis: 30 * 60000,
              opens: 6,
            ),
          ],
          limits: [],
        ),
    ];
  }

  @override
  Future<PermissionStatus> getPermissionStatus() async => permissions;

  @override
  Future<void> openSettings(SettingsPage page) async => opened.add(page);

  @override
  Future<List<InstalledApp>> getInstalledApps() async => apps;

  @override
  Future<List<DayUsage>> getUsage(int days) async => [
    ...usage.take(usage.length - 1),
    if (usage.isNotEmpty) _withLimits(usage.last),
  ].skip(days < usage.length ? usage.length - days : 0).toList();

  /// Like the engine: today's limits, with time used from today's usage.
  DayUsage _withLimits(DayUsage day) => DayUsage(
    dayStartMillis: day.dayStartMillis,
    totalMillis: day.totalMillis,
    unlocks: day.unlocks,
    apps: day.apps,
    limits: [
      for (final r in rules.values)
        if (!r.blocked && r.dailyLimitMinutes != null)
          LimitStatus(
            packageName: r.packageName,
            usedMillis: day.apps
                .where((a) => a.packageName == r.packageName)
                .fold(0, (sum, a) => sum + a.millis),
            allowedMillis: r.dailyLimitMinutes! * 60000,
          ),
    ],
  );

  @override
  Future<EngineSnapshot> getSnapshot() async => EngineSnapshot(
    rules: rules.values.toList(),
    schedules: schedules.values.toList(),
    focus: focus,
    quickBlockUntil: quickBlockUntil,
    blockedSites: sites.toList(),
    blockedKeywords: keywords,
    onboardingDone: onboardingDone,
    dailyGoalMinutes: goal,
    dayStartMillis: 0,
    focusHistory: [],
    engineNowMillis: DateTime.now().millisecondsSinceEpoch - engineBehindMillis,
  );

  @override
  Future<void> setRule(AppRuleMsg rule) async {
    calls.add('setRule ${rule.packageName}');
    if (rule.packageName == 'com.launcher') {
      throw PlatformException(
        code: 'never_block',
        message: 'This app can\'t be limited',
      );
    }
    rules[rule.packageName] = rule;
  }

  @override
  Future<void> removeRule(String packageName) async {
    calls.add('removeRule $packageName');
    rules.remove(packageName);
  }

  @override
  Future<void> setSchedule(ScheduleMsg schedule) async =>
      schedules[schedule.id] = schedule;

  @override
  Future<void> removeSchedule(String id) async => schedules.remove(id);

  @override
  Future<bool> addBlockedSite(String site) async {
    if (!site.contains('.') || site.contains(' ')) return false;
    sites.add(site.toLowerCase());
    return true;
  }

  @override
  Future<void> removeBlockedSite(String site) async => sites.remove(site);

  @override
  Future<void> setBlockedKeywords(List<String> keywords) async =>
      this.keywords = keywords;

  @override
  Future<void> startFocus(int minutes, List<String> packages) async {
    calls.add('startFocus $minutes ${packages.join(',')}');
    final now = DateTime.now().millisecondsSinceEpoch;
    focus = FocusMsg(
      startedAt: now,
      endsAt: now + minutes * 60000,
      packages: packages,
    );
  }

  @override
  Future<void> stopFocus() async {
    calls.add('stopFocus');
    focus = null;
  }

  @override
  Future<void> startQuickBlock(int minutes) async {
    calls.add('startQuickBlock $minutes');
    quickBlockUntil = DateTime.now().millisecondsSinceEpoch + minutes * 60000;
  }

  @override
  Future<void> stopQuickBlock() async => quickBlockUntil = null;

  @override
  Future<void> setOnboardingDone(bool done) async {
    calls.add('setOnboardingDone $done');
    onboardingDone = done;
  }

  @override
  Future<void> setDailyGoal(int minutes) async {
    calls.add('setDailyGoal $minutes');
    goal = minutes;
  }
}
