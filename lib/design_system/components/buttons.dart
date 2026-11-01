import 'package:flutter/material.dart';

import '../theme.dart';
import '../tokens.dart';

/// The one gradient call-to-action per screen.
class PrimaryButton extends StatelessWidget {
  const PrimaryButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.icon,
  });

  final String label;
  final VoidCallback? onPressed;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final enabled = onPressed != null;
    return Semantics(
      button: true,
      enabled: enabled,
      child: Opacity(
        opacity: enabled ? 1 : 0.45,
        child: DecoratedBox(
          decoration: BoxDecoration(
            gradient: c.gradient,
            borderRadius: BorderRadius.circular(Radii.pill),
          ),
          child: Material(
            type: MaterialType.transparency,
            child: InkWell(
              borderRadius: BorderRadius.circular(Radii.pill),
              onTap: onPressed,
              child: ConstrainedBox(
                constraints: const BoxConstraints(
                  minHeight: Sizes.primaryButton,
                  minWidth: double.infinity,
                ),
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: Space.xl,
                    vertical: Space.md,
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (icon != null) ...[
                        Icon(icon, size: 20, color: c.onAccent),
                        const SizedBox(width: Space.sm),
                      ],
                      Flexible(
                        child: Text(
                          label,
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            color: c.onAccent,
                            fontSize: 15,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

enum UButtonKind { secondary, ghost, danger }

/// Secondary, ghost (text) and danger buttons. 48 dp tall.
class UButton extends StatelessWidget {
  const UButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.kind = UButtonKind.secondary,
    this.icon,
    this.expand = false,
  });

  final String label;
  final VoidCallback? onPressed;
  final UButtonKind kind;
  final IconData? icon;
  final bool expand;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final (fg, side) = switch (kind) {
      UButtonKind.secondary => (c.text, BorderSide(color: c.borderStrong)),
      UButtonKind.ghost => (c.accentText, BorderSide.none),
      UButtonKind.danger => (
        c.critical,
        BorderSide(color: c.critical.withValues(alpha: 0.5)),
      ),
    };
    final style = OutlinedButton.styleFrom(
      foregroundColor: fg,
      side: side,
      minimumSize: Size(expand ? double.infinity : 64, Sizes.button),
      shape: const StadiumBorder(),
      textStyle: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15),
      padding: const EdgeInsets.symmetric(horizontal: Space.xl),
    );
    return icon == null
        ? OutlinedButton(onPressed: onPressed, style: style, child: Text(label))
        : OutlinedButton.icon(
            onPressed: onPressed,
            style: style,
            icon: Icon(icon, size: 18),
            label: Text(label),
          );
  }
}
