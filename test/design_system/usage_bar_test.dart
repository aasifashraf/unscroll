import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:unscroll/design_system/components/app_tile.dart';
import 'package:unscroll/design_system/theme.dart';

void main() {
  Future<Size> fillSize(WidgetTester tester, double fraction) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: buildTheme(Brightness.dark),
        home: Scaffold(
          body: SizedBox(width: 200, child: UsageBar(fraction: fraction)),
        ),
      ),
    );
    return tester.getSize(find.byType(FractionallySizedBox));
  }

  testWidgets('the fill is as tall as the bar and as wide as the fraction', (
    tester,
  ) async {
    expect(await fillSize(tester, 0.5), const Size(100, 8));
  });

  testWidgets('over the limit the bar is full, never wider', (tester) async {
    expect(await fillSize(tester, 1.7), const Size(200, 8));
  });

  testWidgets('in a start-aligned column the track still spans the width', (
    tester,
  ) async {
    // Like a list row: a Column with start alignment gives loose width.
    await tester.pumpWidget(
      MaterialApp(
        theme: buildTheme(Brightness.dark),
        home: Scaffold(
          body: SizedBox(
            width: 200,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [UsageBar(fraction: 0)],
            ),
          ),
        ),
      ),
    );
    expect(tester.getSize(find.byType(UsageBar)), const Size(200, 8));
  });
}
