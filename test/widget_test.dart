import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:unscroll/app_state.dart';
import 'package:unscroll/design_system/theme.dart';
import 'package:unscroll/engine/engine_api.g.dart';
import 'package:unscroll/features/apps/app_detail_screen.dart';
import 'package:unscroll/features/blocks/blocks_screen.dart';
import 'package:unscroll/features/blocks/websites_screen.dart';
import 'package:unscroll/features/home/home_screen.dart';
import 'package:unscroll/main.dart';

import 'dart:async';

import 'fake_engine.dart';

/// Fails to start once, then works: like an engine that wasn't ready yet.
class FlakyStartEngine extends FakeEngine {
  var failures = 1;

  @override
  Future<EngineSnapshot> getSnapshot() async {
    if (failures-- > 0) throw PlatformException(code: 'not_ready');
    return super.getSnapshot();
  }
}

class SlowUsageEngine extends FakeEngine {
  final _gate = Completer<void>();
  void release() => _gate.complete();

  @override
  Future<List<DayUsage>> getUsage(int days) async {
    await _gate.future;
    return super.getUsage(days);
  }
}

Future<void> pumpApp(WidgetTester tester, FakeEngine engine) async {
  tester.view.physicalSize = const Size(1080, 2400);
  tester.view.devicePixelRatio = 3;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(UnscrollApp(api: engine));
  await tester.pumpAndSettle();
}

Future<void> pumpScreen(WidgetTester tester, Widget screen) async {
  tester.view.physicalSize = const Size(1080, 2400);
  tester.view.devicePixelRatio = 3;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(theme: buildTheme(Brightness.dark), home: screen),
  );
  await tester.pumpAndSettle();
}

void main() {
  group('first run', () {
    testWidgets(
      'shows onboarding, saves goal and picked apps, then lands on Home',
      (tester) async {
        final engine = FakeEngine(onboardingDone: false);
        await pumpApp(tester, engine);

        expect(find.text('Take your time back.'), findsOneWidget);
        await tester.tap(find.text('Get started'));
        await tester.pumpAndSettle();
        expect(find.text('Nothing leaves your phone'), findsOneWidget);
        await tester.tap(find.text('Continue'));
        await tester.pumpAndSettle();
        await tester.tap(find.byTooltip('More')); // 3h → 3h 30m
        await tester.pumpAndSettle();
        await tester.tap(find.text('Continue'));
        await tester.pumpAndSettle();
        await tester.tap(find.text('Insta'));
        await tester.pumpAndSettle();
        await tester.tap(find.text('Continue (1)'));
        await tester.pumpAndSettle();
        expect(find.text('Turn on protection'), findsOneWidget);
        await tester.tap(find.text('Finish'));
        await tester.pumpAndSettle();

        expect(
          engine.calls,
          containsAllInOrder([
            'setDailyGoal 210',
            'setRule com.insta',
            'setOnboardingDone true',
          ]),
        );
        expect(engine.rules['com.insta']!.pauseSeconds, 5);
        expect(find.text('SCREEN TIME TODAY'), findsOneWidget);
      },
    );

    testWidgets(
      'a sensitive permission needs explicit consent before opening settings',
      (tester) async {
        final engine = FakeEngine(
          onboardingDone: false,
          permissions: FakeEngine.none(),
        );
        await pumpApp(tester, engine);
        for (final step in [
          'Get started',
          'Continue',
          'Continue',
          'Skip for now',
        ]) {
          await tester.tap(find.text(step));
          await tester.pumpAndSettle();
        }
        await tester.tap(find.text('Accessibility access'));
        await tester.pumpAndSettle();

        await tester.tap(find.text('Open settings'));
        await tester.pumpAndSettle();
        expect(
          engine.opened,
          isEmpty,
          reason: 'disabled until the user agrees',
        );

        await tester.tap(find.text('I understand and agree'));
        await tester.pumpAndSettle();
        await tester.tap(find.text('Open settings'));
        await tester.pumpAndSettle();
        expect(engine.opened, [SettingsPage.accessibility]);
      },
    );
  });

  group('home', () {
    testWidgets('warns when blocking cannot work', (tester) async {
      await pumpApp(tester, FakeEngine(permissions: FakeEngine.none()));
      expect(find.textContaining('Blocking is off'), findsOneWidget);
    });

    testWidgets('shows today\'s limits with time left', (tester) async {
      final engine = FakeEngine();
      engine.rules['com.insta'] = AppRuleMsg(
        packageName: 'com.insta',
        blocked: false,
        dailyLimitMinutes: 45,
        limitDays: [1, 2, 3, 4, 5, 6, 7],
      );
      await pumpApp(tester, engine);
      expect(find.text('Insta'), findsOneWidget);
      expect(find.text('15m left'), findsOneWidget);
    });

    testWidgets('empty limits state invites the next step', (tester) async {
      await pumpApp(tester, FakeEngine());
      expect(find.text('No limits yet'), findsOneWidget);
    });

    testWidgets("shows the engine's allowance, including extra minutes", (
      tester,
    ) async {
      final engine = _ExtendedEngine();
      engine.rules['com.insta'] = AppRuleMsg(
        packageName: 'com.insta',
        blocked: false,
        dailyLimitMinutes: 45,
        limitDays: [1, 2, 3, 4, 5, 6, 7],
      );
      await pumpApp(tester, engine);
      // 45 min + 5 extra - 30 used. Re-deriving from the rule would say 15m.
      expect(find.text('20m left'), findsOneWidget);
    });

    testWidgets('a limit for other days says so instead of "No limits yet"', (
      tester,
    ) async {
      final engine = _NoLimitsTodayEngine();
      engine.rules['com.insta'] = AppRuleMsg(
        packageName: 'com.insta',
        blocked: false,
        dailyLimitMinutes: 45,
        limitDays: [6, 7],
      );
      await pumpApp(tester, engine);
      expect(find.text('No limits today'), findsOneWidget);
    });

    testWidgets('warns when accessibility is on but not running', (
      tester,
    ) async {
      final engine = FakeEngine(
        permissions: PermissionStatus(
          usageAccess: true,
          accessibility: true,
          overlay: false,
          notifications: true,
          batteryUnrestricted: true,
          accessibilityRunning: false,
        ),
      );
      await pumpApp(tester, engine);
      expect(find.textContaining('not running'), findsOneWidget);
    });
  });

  group('startup', () {
    testWidgets('an engine error offers a retry that recovers', (tester) async {
      await pumpApp(tester, FlakyStartEngine());
      expect(find.textContaining("couldn't start"), findsOneWidget);
      await tester.tap(find.text('Try again'));
      await tester.pumpAndSettle();
      expect(find.text('SCREEN TIME TODAY'), findsOneWidget);
    });
  });

  group('edits', () {
    testWidgets('home shows a new limit without a manual refresh', (
      tester,
    ) async {
      final engine = FakeEngine();
      final state = AppState(engine);
      addTearDown(state.dispose);
      await state.refresh();
      await pumpScreen(
        tester,
        Scaffold(
          body: HomeScreen(state: state, onNavigate: (_) {}),
        ),
      );
      expect(find.text('No limits yet'), findsOneWidget);

      await state.edit(
        (api) => api.setRule(
          AppRuleMsg(
            packageName: 'com.insta',
            blocked: false,
            dailyLimitMinutes: 45,
            limitDays: [1, 2, 3, 4, 5, 6, 7],
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('15m left'), findsOneWidget);
      expect(find.text('No limits today'), findsNothing);
    });

    test("an unexpected error doesn't stop later edits", () async {
      final engine = FakeEngine();
      final state = AppState(engine);
      addTearDown(state.dispose);
      await state.refresh();

      final error = await state.edit((_) async => throw StateError('boom'));
      expect(error, isNotNull);
      await state.edit((api) => api.setDailyGoal(60));
      expect(engine.goal, 60);
    });

    testWidgets('an ended quick block disappears without a manual refresh', (
      tester,
    ) async {
      final engine = FakeEngine()
        ..quickBlockUntil = DateTime.now().millisecondsSinceEpoch + 1000;
      final state = AppState(engine);
      addTearDown(state.dispose);
      await state.refresh();
      expect(state.snapshot!.quickBlockUntil, isNotNull);

      engine.quickBlockUntil = null; // the engine ended it
      await tester.pump(const Duration(seconds: 2));
      expect(state.snapshot!.quickBlockUntil, isNull);
    });

    testWidgets('adding apps keeps a rule set meanwhile', (tester) async {
      final engine = FakeEngine();
      final state = AppState(engine);
      addTearDown(state.dispose);
      await state.refresh();
      await pumpScreen(tester, BlocksScreen(state: state));

      await tester.tap(find.text('Add').first);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Insta'));
      await tester.pump();
      // While the picker is open, a limit is set elsewhere.
      engine.rules['com.insta'] = AppRuleMsg(
        packageName: 'com.insta',
        blocked: false,
        dailyLimitMinutes: 30,
        limitDays: [1, 2, 3, 4, 5, 6, 7],
      );
      await state.refreshSnapshot();
      await tester.tap(find.textContaining('Done'));
      await tester.pumpAndSettle();
      expect(engine.rules['com.insta']!.dailyLimitMinutes, 30);
    });
  });

  group('engine clock', () {
    test("engine times are shown on the phone's clock", () async {
      final engine = FakeEngine()..engineBehindMillis = 3600000;
      final state = AppState(engine);
      await state.refresh();
      final local = state.engineTimeToLocal(1000000000000);
      expect(
        local.millisecondsSinceEpoch,
        closeTo(1000000000000 + 3600000, 1000),
      );
      expect(
        state.engineNow,
        closeTo(DateTime.now().millisecondsSinceEpoch - 3600000, 1000),
      );
    });
  });

  group('loading', () {
    test(
      'before the app list loads, labels are readable, not package names',
      () {
        expect(AppState.fallbackLabel('com.google.android.youtube'), 'Youtube');
        expect(AppState.fallbackLabel('weird'), 'Weird');
      },
    );

    testWidgets('home shows a loading state, not 0m, until usage arrives', (
      tester,
    ) async {
      final engine = SlowUsageEngine();
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 3;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(UnscrollApp(api: engine));
      await tester.pump(const Duration(milliseconds: 50));
      expect(find.text('0m'), findsNothing);
      expect(find.bySemanticsLabel('Loading screen time'), findsOneWidget);
      engine.release();
      await tester.pumpAndSettle();
      expect(find.text('SCREEN TIME TODAY'), findsOneWidget);
    });
  });

  group('app detail', () {
    testWidgets('turning on a daily limit saves a 45-minute rule', (
      tester,
    ) async {
      final engine = FakeEngine();
      final state = AppState(engine);
      await state.refresh();
      await pumpScreen(
        tester,
        AppDetailScreen(state: state, packageName: 'com.insta'),
      );

      await tester.tap(find.text('Daily limit'));
      await tester.pumpAndSettle();
      expect(engine.rules['com.insta']!.dailyLimitMinutes, 45);

      await tester.tap(find.byTooltip('More').first);
      await tester.pumpAndSettle();
      expect(engine.rules['com.insta']!.dailyLimitMinutes, 50);
    });

    testWidgets('two quick taps both count', (tester) async {
      final engine = FakeEngine();
      engine.rules['com.insta'] = AppRuleMsg(
        packageName: 'com.insta',
        blocked: false,
        dailyLimitMinutes: 45,
        limitDays: [1, 2, 3, 4, 5, 6, 7],
      );
      final state = AppState(engine);
      await state.refresh();
      await pumpScreen(
        tester,
        AppDetailScreen(state: state, packageName: 'com.insta'),
      );

      // No rebuild between taps: both see 45 on screen.
      await tester.tap(find.byTooltip('More').first);
      await tester.tap(find.byTooltip('More').first);
      await tester.pumpAndSettle();
      expect(engine.rules['com.insta']!.dailyLimitMinutes, 55);
    });

    testWidgets('a quick tap on another setting keeps the first change', (
      tester,
    ) async {
      final engine = FakeEngine();
      final state = AppState(engine);
      await state.refresh();
      await pumpScreen(
        tester,
        AppDetailScreen(state: state, packageName: 'com.insta'),
      );

      await tester.tap(find.text('Daily limit'));
      await tester.tap(find.text('Mindful pause'));
      await tester.pumpAndSettle();
      final rule = engine.rules['com.insta']!;
      expect(rule.dailyLimitMinutes, 45);
      expect(rule.pauseSeconds, 5);
    });

    testWidgets('turning every rule off removes the app rule', (tester) async {
      final engine = FakeEngine();
      engine.rules['com.insta'] = AppRuleMsg(
        packageName: 'com.insta',
        blocked: false,
        pauseSeconds: 5,
        limitDays: [1, 2, 3, 4, 5, 6, 7],
      );
      final state = AppState(engine);
      await state.refresh();
      await pumpScreen(
        tester,
        AppDetailScreen(state: state, packageName: 'com.insta'),
      );

      await tester.tap(find.text('Mindful pause'));
      await tester.pumpAndSettle();
      expect(engine.calls.last, 'removeRule com.insta');
    });

    testWidgets('an engine refusal is shown to the user', (tester) async {
      final engine = FakeEngine();
      final state = AppState(engine);
      await state.refresh();
      await pumpScreen(
        tester,
        AppDetailScreen(state: state, packageName: 'com.launcher'),
      );
      await tester.tap(find.text('Block completely'));
      await tester.pumpAndSettle();
      expect(find.textContaining('can\'t be limited'), findsOneWidget);
    });
  });

  group('websites', () {
    testWidgets('invalid site shows a hint; valid site is added', (
      tester,
    ) async {
      final engine = FakeEngine();
      final state = AppState(engine);
      await state.refresh();
      await pumpScreen(tester, WebsitesScreen(state: state));

      await tester.enterText(find.byType(TextField).first, 'not a site');
      await tester.tap(find.byTooltip('Add site'));
      await tester.pumpAndSettle();
      expect(
        find.textContaining('doesn\'t look like a web address'),
        findsOneWidget,
      );

      await tester.enterText(find.byType(TextField).first, 'reddit.com');
      await tester.tap(find.byTooltip('Add site'));
      await tester.pumpAndSettle();
      expect(engine.sites, {'reddit.com'});
      expect(find.text('reddit.com'), findsOneWidget);
    });
  });

  group('focus', () {
    testWidgets(
      'starts with the apps that have rules, then counts down and can end early',
      (tester) async {
        final engine = FakeEngine();
        engine.rules['com.insta'] = AppRuleMsg(
          packageName: 'com.insta',
          blocked: false,
          pauseSeconds: 5,
          limitDays: [1, 2, 3, 4, 5, 6, 7],
        );
        await pumpApp(tester, engine);
        await tester.tap(find.text('Focus').last);
        await tester.pumpAndSettle();

        await tester.tap(find.text('Start focus'));
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 100));
        expect(engine.calls, contains('startFocus 25 com.insta'));
        expect(find.text('End early'), findsOneWidget);

        await tester.tap(find.text('End early'));
        await tester.pumpAndSettle();
        await tester.tap(find.text('End early').last); // dialog
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 100));
        expect(engine.calls, contains('stopFocus'));
      },
    );
  });

  group('quick block', () {
    testWidgets('is disabled until an app has a rule', (tester) async {
      await pumpApp(tester, FakeEngine());
      await tester.tap(find.text('Quick block'));
      await tester.pumpAndSettle();
      expect(find.textContaining('Add an app in Blocks first'), findsOneWidget);
    });
  });
}

class _ExtendedEngine extends FakeEngine {
  @override
  Future<List<DayUsage>> getUsage(int days) async {
    final week = await super.getUsage(days);
    final today = week.last;
    return [
      ...week.take(week.length - 1),
      DayUsage(
        dayStartMillis: today.dayStartMillis,
        totalMillis: today.totalMillis,
        unlocks: today.unlocks,
        apps: today.apps,
        limits: [
          for (final l in today.limits)
            LimitStatus(
              packageName: l.packageName,
              usedMillis: l.usedMillis,
              allowedMillis: l.allowedMillis + 5 * 60000,
            ),
        ],
      ),
    ];
  }
}

class _NoLimitsTodayEngine extends FakeEngine {
  @override
  Future<List<DayUsage>> getUsage(int days) async => [
    for (final d in await super.getUsage(days))
      DayUsage(
        dayStartMillis: d.dayStartMillis,
        totalMillis: d.totalMillis,
        unlocks: d.unlocks,
        apps: d.apps,
        limits: [],
      ),
  ];
}
