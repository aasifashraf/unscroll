import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'engine/engine_api.g.dart';

/// UI-side view of the engine. Holds no rules logic: it reads the engine's snapshot and
/// forwards edits. The engine (Kotlin) stays the source of truth.
class AppState extends ChangeNotifier {
  AppState(this.api);

  final EngineHostApi api;

  EngineSnapshot? snapshot;
  PermissionStatus? permissions;

  /// Last 7 days, oldest first; the last entry is today.
  List<DayUsage> week = const [];
  Map<String, InstalledApp> apps = const {};

  /// Short-video feeds Unscroll can block, with their state (from the engine).
  List<ShortFormAppMsg> shortForm = const [];

  /// False until the first usage read finishes (it can take a few seconds).
  bool usageLoaded = false;

  /// A usage read is in flight, so today's numbers may be about to change.
  bool usageRefreshing = false;
  int _usageRead = 0;

  bool appsLoaded = false;
  Object? loadError;

  /// Edits run one at a time, each against the latest snapshot, so quick taps never undo each other.
  Future<void> _edits = Future.value();

  /// Device time minus engine time when the snapshot was taken (the engine's clock ignores manual
  /// clock changes). Used to show engine times (focus end) correctly.
  int _engineOffsetMillis = 0;

  /// Refreshes when a focus session or quick block ends, so no screen shows it as still running.
  Timer? _expiry;

  bool get ready => snapshot != null && permissions != null;
  DayUsage? get today => week.isEmpty ? null : week.last;

  /// The engine's tamper-resistant "now".
  int get engineNow =>
      DateTime.now().millisecondsSinceEpoch - _engineOffsetMillis;

  /// An engine time (focus end, quick block end) as a time on the phone's clock.
  DateTime engineTimeToLocal(int engineMillis) =>
      DateTime.fromMillisecondsSinceEpoch(engineMillis + _engineOffsetMillis);

  /// Blocking can't work: no usage access, or neither accessibility nor the overlay fallback.
  bool get coreProtectionMissing {
    final p = permissions;
    return p != null && (!p.usageAccess || !(p.accessibility || p.overlay));
  }

  /// Accessibility is switched on in settings but not running (crash, restricted settings).
  bool get accessibilityStopped {
    final p = permissions;
    return p != null && p.accessibility && !p.accessibilityRunning;
  }

  void _setSnapshot(EngineSnapshot s) {
    snapshot = s;
    _engineOffsetMillis =
        DateTime.now().millisecondsSinceEpoch - s.engineNowMillis;
    _expiry?.cancel();
    final ends = [?s.focus?.endsAt, ?s.quickBlockUntil];
    if (ends.isNotEmpty) {
      final next = ends.reduce((a, b) => a < b ? a : b);
      _expiry = Timer(
        Duration(milliseconds: (next - engineNow).clamp(0, 1 << 31) + 500),
        refreshSnapshot,
      );
    }
  }

  Future<void> refresh() async {
    loadError = null; // a retry shows the spinner again
    notifyListeners();
    try {
      _setSnapshot(await api.getSnapshot());
      permissions = await api.getPermissionStatus();
      shortForm = await api.getShortFormApps();
    } catch (e) {
      // Any failure, not only platform errors, must reach the retry screen.
      loadError = e;
    }
    notifyListeners();
    await refreshUsage();
  }

  /// Re-reads only the rules and settings (cheap).
  Future<void> refreshSnapshot() async {
    try {
      _setSnapshot(await api.getSnapshot());
      notifyListeners();
    } on PlatformException {
      // keep the previous snapshot
    }
  }

  /// Re-reads usage: the last 7 days, or with [todayOnly] just today (after an edit).
  Future<void> refreshUsage({bool todayOnly = false}) async {
    final read = ++_usageRead;
    usageRefreshing = true;
    try {
      final days = todayOnly && week.length > 1 ? 1 : 7;
      final result = await api.getUsage(days);
      if (read != _usageRead) return; // a newer read started; it wins
      week = days == 1 && result.length == 1
          ? [...week.take(week.length - 1), result.single]
          : result;
    } on PlatformException {
      // keep what we had
    }
    if (read != _usageRead) return;
    usageRefreshing = false;
    usageLoaded = true;
    notifyListeners();
  }

  Future<void> loadApps({bool force = false}) async {
    if (appsLoaded && !force) return;
    try {
      final list = await api.getInstalledApps();
      apps = {for (final a in list) a.packageName: a};
      appsLoaded = true;
    } on PlatformException {
      appsLoaded = false;
    }
    notifyListeners();
  }

  /// App name; before the app list has loaded, a readable guess from the package name.
  String labelOf(String pkg) => apps[pkg]?.label ?? fallbackLabel(pkg);

  static String fallbackLabel(String pkg) {
    final last = pkg.split('.').last;
    return last.isEmpty ? pkg : last[0].toUpperCase() + last.substring(1);
  }

  /// Apps the user chose to limit: with a rule or in a group. Quick block covers these (the
  /// engine decides; this only shows the count) and focus starts with them.
  Set<String> get limitedApps => {
    for (final r in snapshot?.rules ?? const <AppRuleMsg>[]) r.packageName,
    for (final g in snapshot?.groups ?? const <GroupMsg>[]) ...g.packages,
  };

  AppRuleMsg? ruleFor(String pkg) {
    for (final r in snapshot?.rules ?? const <AppRuleMsg>[]) {
      if (r.packageName == pkg) return r;
    }
    return null;
  }

  /// Runs an engine edit after any earlier ones, then refreshes. [action] runs when its turn
  /// comes, so it can read the latest [snapshot]. Returns a user-facing error, or null.
  Future<String?> edit(Future<void> Function(EngineHostApi api) action) {
    final result = _edits.then((_) async {
      String? error;
      try {
        await action(api);
      } on PlatformException catch (e) {
        error = e.message ?? _genericError;
      } catch (_) {
        // Never let one failure jam the queue for every later edit.
        error = _genericError;
      }
      await refreshSnapshot();
      await refreshShortForm();
      // Limits and extensions change today's numbers.
      unawaited(refreshUsage(todayOnly: true));
      return error;
    });
    _edits = result.then((_) {});
    return result;
  }

  static const _genericError = 'Something went wrong. Please try again.';

  /// Re-reads the short-video feeds (detection health can change while the app is closed).
  Future<void> refreshShortForm() async {
    try {
      shortForm = await api.getShortFormApps();
      notifyListeners();
    } on PlatformException {
      // keep the previous list
    }
  }

  Future<void> refreshPermissions() async {
    try {
      permissions = await api.getPermissionStatus();
    } on PlatformException {
      // keep the previous value
    }
    notifyListeners();
  }

  @override
  void dispose() {
    _expiry?.cancel();
    super.dispose();
  }
}
