import 'package:flutter_test/flutter_test.dart';
import 'package:unscroll/main.dart';

void main() {
  testWidgets('app starts and shows its name', (tester) async {
    await tester.pumpWidget(const UnscrollApp());

    expect(find.text('Unscroll'), findsOneWidget);
  });
}
