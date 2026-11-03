// Contract between the Flutter UI and the Kotlin engine.
// Edit this file, then regenerate with: dart run pigeon --input pigeons/engine_api.dart
import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(
  PigeonOptions(
    dartOut: 'lib/engine/engine_api.g.dart',
    dartOptions: DartOptions(),
    kotlinOut: 'android/app/src/main/kotlin/io/github/aasifashraf/unscroll/engine/api/EngineApi.g.kt',
    kotlinOptions: KotlinOptions(
      package: 'io.github.aasifashraf.unscroll.engine.api',
    ),
    dartPackageName: 'unscroll',
  ),
)
/// Which special-access permissions Unscroll currently holds.
class PermissionStatus {
  PermissionStatus({
    required this.usageAccess,
    required this.accessibility,
    required this.overlay,
    required this.notifications,
    required this.batteryUnrestricted,
    required this.accessibilityRunning,
  });

  /// Usage access (screen time and limits).
  bool usageAccess;

  /// Unscroll's accessibility service is enabled (app, feed and site blocking).
  bool accessibility;

  /// Display over other apps (pause and block screens without accessibility).
  bool overlay;

  /// Notifications are allowed (limit warnings, foreground service).
  bool notifications;

  /// Battery optimization is off, so the system won't stop blocking.
  bool batteryUnrestricted;

  /// The accessibility service is actually connected right now (it can be switched on in
  /// settings but stopped, e.g. after a crash or by Android's restricted settings).
  bool accessibilityRunning;
}

/// Today's state of one app's daily limit, computed by the engine (the UI never re-derives it).
class LimitStatus {
  LimitStatus({
    required this.packageName,
    required this.usedMillis,
    required this.allowedMillis,
  });
  String packageName;
  int usedMillis;

  /// Limit plus any "5 more minutes" taken today.
  int allowedMillis;
}

/// Today's state of one app group's shared limit, computed by the engine.
class GroupLimitStatus {
  GroupLimitStatus({
    required this.groupId,
    required this.name,
    required this.usedMillis,
    required this.allowedMillis,
  });
  String groupId;
  String name;

  /// Today's usage of all the group's apps added up.
  int usedMillis;

  /// Limit plus any "5 more minutes" taken for the group today.
  int allowedMillis;
}

/// A system settings screen the user is sent to after an in-app disclosure.
enum SettingsPage {
  usageAccess,
  accessibility,
  overlay,
  notifications,
  battery,
  appDetails,
}

class InstalledApp {
  InstalledApp({required this.packageName, required this.label, this.icon});
  String packageName;
  String label;

  /// PNG, 96×96 px; null if the app has no icon.
  Uint8List? icon;
}

class AppUsageInfo {
  AppUsageInfo({
    required this.packageName,
    required this.millis,
    required this.opens,
  });
  String packageName;
  int millis;
  int opens;
}

class DayUsage {
  DayUsage({
    required this.dayStartMillis,
    required this.totalMillis,
    required this.unlocks,
    required this.apps,
    required this.limits,
    required this.groupLimits,
  });
  int dayStartMillis;
  int totalMillis;
  int unlocks;

  /// Sorted by time, most used first.
  List<AppUsageInfo> apps;

  /// Only filled for today: limits that apply today.
  List<LimitStatus> limits;

  /// Only filled for today: group limits that apply today.
  List<GroupLimitStatus> groupLimits;
}

class AppRuleMsg {
  AppRuleMsg({
    required this.packageName,
    required this.blocked,
    this.dailyLimitMinutes,
    required this.limitDays,
    this.pauseSeconds,
    this.openLimit,
    this.askIntention = false,
    this.sessionLimitMinutes,
  });
  String packageName;
  bool blocked;
  int? dailyLimitMinutes;

  /// ISO weekdays 1 (Mon) … 7 (Sun) the limit applies on.
  List<int> limitDays;
  int? pauseSeconds;
  int? openLimit;

  /// Ask "What are you here for?" on the pause screen (the answer is never stored).
  bool askIntention;

  /// Close the app after this many minutes in one open (5–120), then a 10-minute cool-down.
  int? sessionLimitMinutes;
}

class ScheduleMsg {
  ScheduleMsg({
    required this.id,
    required this.name,
    required this.enabled,
    required this.days,
    required this.startMinute,
    required this.endMinute,
    required this.packages,
  });
  String id;
  String name;
  bool enabled;
  List<int> days;
  int startMinute;
  int endMinute;
  List<String> packages;
}

/// Several apps sharing one daily limit.
class GroupMsg {
  GroupMsg({
    required this.id,
    required this.name,
    required this.packages,
    required this.dailyLimitMinutes,
    required this.limitDays,
  });
  String id;
  String name;
  List<String> packages;
  int dailyLimitMinutes;

  /// ISO weekdays 1 (Mon) … 7 (Sun) the limit applies on.
  List<int> limitDays;
}

class FocusMsg {
  FocusMsg({
    required this.startedAt,
    required this.endsAt,
    required this.packages,
  });
  int startedAt;
  int endsAt;
  List<String> packages;
}

class FocusRecordMsg {
  FocusRecordMsg({
    required this.startedAt,
    required this.endedAt,
    required this.completed,
  });
  int startedAt;
  int endedAt;
  bool completed;
}

/// One app whose short-video feed Unscroll can block.
class ShortFormAppMsg {
  ShortFormAppMsg({
    required this.packageName,
    required this.label,
    required this.installed,
    required this.blocked,
    this.lastDetectedAt,
    required this.maybeOutdated,
  });
  String packageName;

  /// The feed's name, e.g. "Instagram Reels".
  String label;
  bool installed;

  /// The user turned blocking on for this feed.
  bool blocked;

  /// When the feed was last closed, in engine time (see [EngineSnapshot.engineNowMillis]).
  int? lastDetectedAt;

  /// Detection looked wrong in the installed version of the app (it may have changed its screens).
  bool maybeOutdated;
}

/// Everything the UI shows that the engine owns.
class EngineSnapshot {
  EngineSnapshot({
    required this.rules,
    required this.schedules,
    required this.groups,
    this.focus,
    this.quickBlockUntil,
    required this.blockedSites,
    required this.blockedKeywords,
    required this.onboardingDone,
    required this.dailyGoalMinutes,
    required this.dayStartMillis,
    required this.focusHistory,
    required this.engineNowMillis,
    required this.declinedToday,
    required this.declinedTotal,
  });
  List<AppRuleMsg> rules;
  List<ScheduleMsg> schedules;
  List<GroupMsg> groups;
  FocusMsg? focus;
  int? quickBlockUntil;
  List<String> blockedSites;
  List<String> blockedKeywords;
  bool onboardingDone;
  int dailyGoalMinutes;
  int dayStartMillis;
  List<FocusRecordMsg> focusHistory;

  /// The engine's tamper-resistant "now"; focus and quick-block times are in this frame.
  int engineNowMillis;

  /// Times the user chose not to open an app on the pause screen today.
  int declinedToday;

  /// The same, since Unscroll was installed.
  int declinedTotal;
}

@HostApi()
abstract class EngineHostApi {
  /// Read-only check of every permission the engine depends on.
  PermissionStatus getPermissionStatus();

  /// Opens the matching Android settings screen.
  void openSettings(SettingsPage page);

  /// Apps with a launcher icon that can be limited, sorted by name (excludes Unscroll and
  /// apps that are never blocked, like Phone and Messages).
  /// Runs off the main thread (icon loading is slow).
  @TaskQueue(type: TaskQueueType.serialBackgroundThread)
  List<InstalledApp> getInstalledApps();

  /// The last [days] days of usage, oldest first; the last entry is today.
  @TaskQueue(type: TaskQueueType.serialBackgroundThread)
  List<DayUsage> getUsage(int days);

  EngineSnapshot getSnapshot();

  void setRule(AppRuleMsg rule);

  void removeRule(String packageName);

  void setSchedule(ScheduleMsg schedule);

  void removeSchedule(String id);

  /// Creates or replaces the group with [group]'s id.
  void setGroup(GroupMsg group);

  void removeGroup(String id);

  /// Returns false when [site] is not a valid web address.
  bool addBlockedSite(String site);

  void removeBlockedSite(String site);

  void setBlockedKeywords(List<String> keywords);

  void startFocus(int minutes, List<String> packages);

  void stopFocus();

  void startQuickBlock(int minutes);

  void stopQuickBlock();

  void setOnboardingDone(bool done);

  void setDailyGoal(int minutes);

  /// The supported short-video feeds (Instagram, YouTube, Facebook, Snapchat), in a fixed order.
  List<ShortFormAppMsg> getShortFormApps();

  /// Turns blocking of [packageName]'s short-video feed on or off. Only supported apps.
  void setShortFormBlocked(String packageName, bool blocked);
}
