import 'package:flutter/material.dart';

import 'tokens.dart';

/// Unscroll text styles (design/tokens.json → typography).
@immutable
class UText {
  const UText(this._color, this._muted);

  final Color _color;
  final Color _muted;

  TextStyle get display => TextStyle(
    fontSize: 44,
    fontWeight: FontWeight.w700,
    height: 1.05,
    letterSpacing: -1.2,
    color: _color,
  );
  TextStyle get headline => TextStyle(
    fontSize: 28,
    fontWeight: FontWeight.w700,
    height: 1.15,
    letterSpacing: -0.6,
    color: _color,
  );
  TextStyle get title => TextStyle(
    fontSize: 20,
    fontWeight: FontWeight.w600,
    height: 1.25,
    letterSpacing: -0.2,
    color: _color,
  );
  TextStyle get subtitle => TextStyle(
    fontSize: 16,
    fontWeight: FontWeight.w600,
    height: 1.3,
    color: _color,
  );
  TextStyle get body => TextStyle(fontSize: 15, height: 1.5, color: _color);
  TextStyle get bodySmall =>
      TextStyle(fontSize: 13, height: 1.45, color: _muted);
  TextStyle get label => TextStyle(
    fontSize: 13,
    fontWeight: FontWeight.w600,
    height: 1.2,
    letterSpacing: 0.1,
    color: _color,
  );
  TextStyle get overline => TextStyle(
    fontSize: 11,
    fontWeight: FontWeight.w700,
    height: 1.2,
    letterSpacing: 1.2,
    color: _muted,
  );
}

/// Exposes [UColors] through the theme so widgets can read `context.colors`.
class UnscrollTheme extends ThemeExtension<UnscrollTheme> {
  const UnscrollTheme(this.colors);

  final UColors colors;

  @override
  UnscrollTheme copyWith({UColors? colors}) =>
      UnscrollTheme(colors ?? this.colors);

  @override
  UnscrollTheme lerp(UnscrollTheme? other, double t) =>
      t < 0.5 ? this : (other ?? this);
}

extension UnscrollContext on BuildContext {
  UColors get colors => Theme.of(this).extension<UnscrollTheme>()!.colors;
  UText get text => UText(colors.text, colors.textMuted);
}

ThemeData buildTheme(Brightness brightness) {
  final c = brightness == Brightness.dark ? UColors.dark : UColors.light;
  final scheme = ColorScheme(
    brightness: brightness,
    primary: c.violet,
    onPrimary: c.onAccent,
    secondary: c.teal,
    onSecondary: c.onAccent,
    error: c.critical,
    onError: c.onAccent,
    surface: c.surface,
    onSurface: c.text,
  );
  return ThemeData(
    useMaterial3: true,
    brightness: brightness,
    colorScheme: scheme,
    scaffoldBackgroundColor: c.bg,
    materialTapTargetSize: MaterialTapTargetSize.padded,
    extensions: [UnscrollTheme(c)],
    appBarTheme: AppBarTheme(
      backgroundColor: c.bg,
      foregroundColor: c.text,
      elevation: 0,
      scrolledUnderElevation: 0,
      toolbarHeight: 64,
      titleTextStyle: UText(c.text, c.textMuted).title,
    ),
    dividerTheme: DividerThemeData(color: c.border, space: 1, thickness: 1),
    switchTheme: SwitchThemeData(
      thumbColor: WidgetStateProperty.resolveWith(
        (s) => s.contains(WidgetState.selected) ? Colors.white : c.textMuted,
      ),
      trackColor: WidgetStateProperty.resolveWith(
        (s) => s.contains(WidgetState.selected) ? c.violet : c.surfaceSunken,
      ),
      trackOutlineColor: WidgetStateProperty.resolveWith(
        (s) => s.contains(WidgetState.selected) ? c.violet : c.borderStrong,
      ),
    ),
    navigationBarTheme: NavigationBarThemeData(
      height: Sizes.bottomNav,
      backgroundColor: c.surface,
      indicatorColor: c.violet.withValues(alpha: 0.22),
      labelTextStyle: WidgetStatePropertyAll(
        TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: c.text),
      ),
      iconTheme: WidgetStateProperty.resolveWith(
        (s) => IconThemeData(
          color: s.contains(WidgetState.selected) ? c.accentText : c.textMuted,
        ),
      ),
    ),
    segmentedButtonTheme: SegmentedButtonThemeData(
      style: ButtonStyle(
        minimumSize: const WidgetStatePropertyAll(Size(0, 40)),
        backgroundColor: WidgetStateProperty.resolveWith(
          (s) => s.contains(WidgetState.selected)
              ? c.violet.withValues(alpha: 0.18)
              : Colors.transparent,
        ),
        foregroundColor: WidgetStatePropertyAll(c.text),
        side: WidgetStatePropertyAll(BorderSide(color: c.borderStrong)),
      ),
    ),
    chipTheme: ChipThemeData(
      backgroundColor: Colors.transparent,
      selectedColor: c.violet.withValues(alpha: 0.18),
      side: BorderSide(color: c.borderStrong),
      labelStyle: TextStyle(color: c.text, fontWeight: FontWeight.w600),
      shape: const StadiumBorder(),
      showCheckmark: true,
      checkmarkColor: c.accentText,
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: c.surfaceSunken,
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(Radii.lg),
        borderSide: BorderSide(color: c.borderStrong),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(Radii.lg),
        borderSide: BorderSide(color: c.borderStrong),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(Radii.lg),
        borderSide: BorderSide(color: c.accentText, width: 2),
      ),
      hintStyle: TextStyle(color: c.textMuted),
    ),
    snackBarTheme: SnackBarThemeData(
      backgroundColor: c.surfaceRaised,
      contentTextStyle: TextStyle(color: c.text),
      behavior: SnackBarBehavior.floating,
    ),
    bottomSheetTheme: BottomSheetThemeData(
      backgroundColor: c.surfaceRaised,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(Radii.xl)),
      ),
      showDragHandle: true,
    ),
    dialogTheme: DialogThemeData(
      backgroundColor: c.surfaceRaised,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(Radii.xl),
      ),
    ),
  );
}
