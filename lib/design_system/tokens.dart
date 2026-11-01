import 'package:flutter/material.dart';

/// Colors from design/tokens.json. Keep both files in sync.
@immutable
class UColors {
  const UColors({
    required this.bg,
    required this.surface,
    required this.surfaceRaised,
    required this.surfaceSunken,
    required this.border,
    required this.borderStrong,
    required this.text,
    required this.text2,
    required this.textMuted,
    required this.teal,
    required this.violet,
    required this.accentText,
    required this.onAccent,
    required this.good,
    required this.warning,
    required this.critical,
    required this.series,
    required this.other,
    required this.chartSingle,
    required this.chartGrid,
    required this.chartBase,
    required this.glow,
  });

  final Color bg;
  final Color surface;
  final Color surfaceRaised;
  final Color surfaceSunken;
  final Color border;
  final Color borderStrong;
  final Color text;
  final Color text2;
  final Color textMuted;
  final Color teal;
  final Color violet;
  final Color accentText;
  final Color onAccent;
  final Color good;
  final Color warning;
  final Color critical;

  /// Category colors in fixed order: Social, Video, Games, Browsing.
  final List<Color> series;
  final Color other;
  final Color chartSingle;
  final Color chartGrid;
  final Color chartBase;
  final Color glow;

  LinearGradient get gradient => LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [teal, violet],
  );

  static const dark = UColors(
    bg: Color(0xFF0B0E1A),
    surface: Color(0xFF171B2E),
    surfaceRaised: Color(0xFF1F2440),
    surfaceSunken: Color(0xFF10132A),
    border: Color(0x14FFFFFF),
    borderStrong: Color(0x29FFFFFF),
    text: Color(0xFFF2F3F8),
    text2: Color(0xFFB4B8CC),
    textMuted: Color(0xFF8F95B2),
    teal: Color(0xFF2DD4BF),
    violet: Color(0xFF8B7CF6),
    accentText: Color(0xFFA79BFA),
    onAccent: Color(0xFF0B0E1A),
    good: Color(0xFF3DD68C),
    warning: Color(0xFFFAB219),
    critical: Color(0xFFF07171),
    series: [
      Color(0xFF7F6EEB),
      Color(0xFFD2683A),
      Color(0xFF1FA08E),
      Color(0xFFB98A1C),
    ],
    other: Color(0xFF4A5070),
    chartSingle: Color(0xFF8B7CF6),
    chartGrid: Color(0xFF262B45),
    chartBase: Color(0xFF3A4060),
    glow: Color(0x478B7CF6),
  );

  static const light = UColors(
    bg: Color(0xFFF4F5FA),
    surface: Color(0xFFFFFFFF),
    surfaceRaised: Color(0xFFFFFFFF),
    surfaceSunken: Color(0xFFECEEF6),
    border: Color(0x1412152A),
    borderStrong: Color(0x2912152A),
    text: Color(0xFF12152A),
    text2: Color(0xFF4A4F68),
    textMuted: Color(0xFF666B88),
    teal: Color(0xFF0D7F74),
    violet: Color(0xFF5B47D6),
    accentText: Color(0xFF5B47D6),
    onAccent: Color(0xFFFFFFFF),
    good: Color(0xFF0F8A4F),
    warning: Color(0xFF9A6700),
    critical: Color(0xFFC62F2F),
    series: [
      Color(0xFF6A55E0),
      Color(0xFFD9622B),
      Color(0xFF14917F),
      Color(0xFFB98500),
    ],
    other: Color(0xFFB9BDD0),
    chartSingle: Color(0xFF6A55E0),
    chartGrid: Color(0xFFE6E8F0),
    chartBase: Color(0xFFC3C7D6),
    glow: Color(0x245B47D6),
  );
}

/// 4-pt spacing grid.
abstract final class Space {
  static const xxs = 2.0;
  static const xs = 4.0;
  static const sm = 8.0;
  static const md = 12.0;
  static const lg = 16.0;
  static const xl = 20.0;
  static const xxl = 24.0;
  static const xxxl = 32.0;
  static const huge = 48.0;
}

abstract final class Radii {
  static const sm = 8.0;
  static const md = 12.0;
  static const lg = 16.0;
  static const xl = 24.0;
  static const pill = 999.0;
}

/// Component sizes (dp). See docs/DESIGN_SYSTEM.md for the reasoning behind them.
abstract final class Sizes {
  static const touchTarget = 48.0;
  static const primaryButton = 52.0;
  static const button = 48.0;
  static const chip = 36.0;
  static const appIcon = 40.0;
  static const bottomNav = 72.0;
  static const listRow = 56.0;
  static const listRowTwoLine = 72.0;
}

abstract final class Motion {
  static const fast = Duration(milliseconds: 120);
  static const standard = Duration(milliseconds: 240);
  static const emphasized = Duration(milliseconds: 400);
  static const curve = Cubic(0.2, 0, 0, 1);
}
