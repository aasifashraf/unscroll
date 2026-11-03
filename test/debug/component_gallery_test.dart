import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:unscroll/debug/component_gallery.dart';
import 'package:unscroll/design_system/theme.dart';

void main() {
  Future<void> pumpGallery(WidgetTester tester) async {
    tester.view.physicalSize = const Size(360, 780);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      MaterialApp(
        theme: buildTheme(Brightness.light),
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context)
              .copyWith(textScaler: const TextScaler.linear(2)),
          child: child!,
        ),
        home: const ComponentGalleryScreen(),
      ),
    );
    await tester.pump();
  }

  Future<void> scrollThrough(WidgetTester tester) async {
    final list = find.byType(ListView);
    for (var i = 0; i < 40; i++) {
      await tester.drag(list, const Offset(0, -600));
      await tester.pump();
    }
  }

  testWidgets('renders in light and dark at 360dp and 200% text', (
    tester,
  ) async {
    await pumpGallery(tester);
    expect(tester.takeException(), isNull);
    expect(find.text('Component gallery'), findsOneWidget);
    expect(find.text('Light'), findsOneWidget);

    await scrollThrough(tester);
    expect(tester.takeException(), isNull);
    expect(find.text('Week bars'), findsWidgets);

    tester
        .state<ScrollableState>(
          find.descendant(
            of: find.byType(ListView),
            matching: find.byType(Scrollable),
          ),
        )
        .position
        .jumpTo(0);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Dark'));
    await tester.pump();
    expect(tester.takeException(), isNull);

    await scrollThrough(tester);
    expect(tester.takeException(), isNull);
    expect(find.text('Week bars'), findsWidgets);
  });

  testWidgets('component labels are present', (tester) async {
    await pumpGallery(tester);
    await tester.scrollUntilVisible(find.text('Primary disabled'), 300);
    expect(find.text('Primary disabled'), findsOneWidget);
    await tester.scrollUntilVisible(find.text('Plain card'), 300);
    expect(find.text('Plain card'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
