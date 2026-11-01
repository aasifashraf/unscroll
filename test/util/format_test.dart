import 'package:flutter_test/flutter_test.dart';
import 'package:unscroll/util/format.dart';

void main() {
  test('durations read naturally', () {
    expect(formatDuration(Duration.zero), '0m');
    expect(formatDuration(const Duration(seconds: 20)), 'under 1m');
    expect(formatDuration(const Duration(minutes: 45)), '45m');
    expect(formatDuration(const Duration(hours: 2)), '2h');
    expect(formatDuration(const Duration(hours: 2, minutes: 41)), '2h 41m');
  });

  test('countdowns pad and never go negative', () {
    expect(formatCountdown(const Duration(minutes: 18, seconds: 42)), '18:42');
    expect(
      formatCountdown(const Duration(hours: 1, minutes: 2, seconds: 3)),
      '1:02:03',
    );
    expect(formatCountdown(const Duration(seconds: -5)), '00:00');
  });

  test('minute of day', () {
    expect(formatMinuteOfDay(0), '00:00');
    expect(formatMinuteOfDay(23 * 60 + 5), '23:05');
  });
}
