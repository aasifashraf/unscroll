import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../theme.dart';
import '../tokens.dart';

/// An app's real icon (from the phone) or a neutral letter tile when it has none.
class AppIcon extends StatelessWidget {
  const AppIcon({
    super.key,
    required this.label,
    this.icon,
    this.size = Sizes.appIcon,
  });

  final String label;
  final Uint8List? icon;
  final double size;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final image = icon;
    return ExcludeSemantics(
      child: SizedBox.square(
        dimension: size,
        child: image != null
            ? ClipRRect(
                borderRadius: BorderRadius.circular(size * 0.25),
                child: Image.memory(
                  image,
                  gaplessPlayback: true,
                  fit: BoxFit.cover,
                ),
              )
            : DecoratedBox(
                decoration: BoxDecoration(
                  color: c.surfaceRaised,
                  borderRadius: BorderRadius.circular(size * 0.3),
                  border: Border.all(color: c.borderStrong),
                ),
                child: Center(
                  child: Text(
                    label.isEmpty ? '?' : label.characters.first.toUpperCase(),
                    style: TextStyle(
                      color: c.text2,
                      fontWeight: FontWeight.w700,
                      fontSize: size * 0.4,
                    ),
                  ),
                ),
              ),
      ),
    );
  }
}

/// Horizontal progress toward a limit; turns warning at ≥ 80 %, critical when over.
class UsageBar extends StatelessWidget {
  const UsageBar({super.key, required this.fraction});

  final double fraction;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final f = fraction.clamp(0.0, 1.0);
    return ExcludeSemantics(
      child: ClipRRect(
        borderRadius: BorderRadius.circular(Radii.pill),
        child: SizedBox(
          height: 8,
          // Full width: a start-aligned Column would shrink it to the fill.
          width: double.infinity,
          child: Stack(
            children: [
              Positioned.fill(child: ColoredBox(color: c.surfaceSunken)),
              FractionallySizedBox(
                widthFactor: f,
                heightFactor: 1, // fill the bar's height (a Stack gives loose constraints)
                alignment: AlignmentDirectional.centerStart,
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    gradient: fraction >= 0.8 ? null : c.gradient,
                    color: fraction >= 1
                        ? c.critical
                        : fraction >= 0.8
                        ? c.warning
                        : null,
                    borderRadius: BorderRadius.circular(Radii.pill),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
