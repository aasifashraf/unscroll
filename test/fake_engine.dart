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
  final groups = <String, GroupMsg>{};
  final sites = <String>{};
  List<String> keywords = [];
  FocusMsg? focus;
  int? quickBlockUntil;
  int goal = 180;
  int declinedToday = 0;
  int declinedTotal = 0;
  final calls = <String>[];

  /// Feeds the user blocked, and feeds whose detection may be outdated.
  final shortFormBlocked = <String>{};
  final shortFormOutdated = <String>{};

  static const shortFormFeeds = {
    'com.instagram.android': 'Instagram Reels',
    'com.google.android.youtube': 'YouTube Shorts',
    'com.facebook.katana': 'Facebook Reels',
    'com.snapchat.android': 'Snapchat Spotlight',
  };

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
          groupLimits: [],
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
    groupLimits: [
      for (final g in groups.values)
        GroupLimitStatus(
          groupId: g.id,
          name: g.name,
          usedMillis: day.apps
              .where((a) => g.packages.contains(a.packageName))
              .fold(0, (sum, a) => sum + a.millis),
          allowedMillis: g.dailyLimitMinutes * 60000,
        ),
    ],
  );

  @override
  Future<EngineSnapshot> getSnapshot() async => EngineSnapshot(
    rules: rules.values.toList(),
    schedules: schedules.values.toList(),
    groups: groups.values.toList(),
    focus: focus,
    quickBlockUntil: quickBlockUntil,
    blockedSites: sites.toList(),
    blockedKeywords: keywords,
    onboardingDone: onboardingDone,
    dailyGoalMinutes: goal,
    dayStartMillis: 0,
    focusHistory: [],
    engineNowMillis: DateTime.now().millisecondsSinceEpoch - engineBehindMillis,
    declinedToday: declinedToday,
    declinedTotal: declinedTotal,
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
  Future<void> setGroup(GroupMsg group) async {
    calls.add('setGroup ${group.id}');
    groups[group.id] = group;
  }

  @override
  Future<void> removeGroup(String id) async {
    calls.add('removeGroup $id');
    groups.remove(id);
  }

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

  @override
  Future<List<ShortFormAppMsg>> getShortFormApps() async => [
    for (final e in shortFormFeeds.entries)
      ShortFormAppMsg(
        packageName: e.key,
        label: e.value,
        installed: true,
        blocked: shortFormBlocked.contains(e.key),
        maybeOutdated: shortFormOutdated.contains(e.key),
      ),
  ];

  @override
  Future<void> setShortFormBlocked(String packageName, bool blocked) async {
    calls.add('setShortFormBlocked $packageName $blocked');
    if (!shortFormFeeds.containsKey(packageName)) {
      throw PlatformException(code: 'invalid', message: 'Not supported');
    }
    if (blocked) {
      shortFormBlocked.add(packageName);
    } else {
      shortFormBlocked.remove(packageName);
    }
  }
}
