/// Human-readable durations: "2h 41m", "45m", "under 1m".
String formatDuration(Duration d) {
  final minutes = d.inMinutes;
  if (minutes < 1) return d.inSeconds > 0 ? 'under 1m' : '0m';
  final h = minutes ~/ 60;
  final m = minutes % 60;
  if (h == 0) return '${m}m';
  return m == 0 ? '${h}h' : '${h}h ${m}m';
}

String formatMillis(int millis) =>
    formatDuration(Duration(milliseconds: millis));

/// "mm:ss" (or "h:mm:ss") for countdowns.
String formatCountdown(Duration d) {
  final total = d.isNegative ? 0 : d.inSeconds;
  final h = total ~/ 3600;
  final m = (total % 3600) ~/ 60;
  final s = total % 60;
  final mm = m.toString().padLeft(2, '0');
  final ss = s.toString().padLeft(2, '0');
  return h > 0 ? '$h:$mm:$ss' : '$mm:$ss';
}

/// "14:05" from minutes after midnight.
String formatMinuteOfDay(int minute) =>
    '${(minute ~/ 60).toString().padLeft(2, '0')}:${(minute % 60).toString().padLeft(2, '0')}';

const weekdayShort = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const weekdayLetter = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];
