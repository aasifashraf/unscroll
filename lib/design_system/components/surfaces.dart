import 'package:flutter/material.dart';

import '../theme.dart';
import '../tokens.dart';

/// Rounded surface card. [glow] adds the soft accent glow (hero cards only).
class UCard extends StatelessWidget {
  const UCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(Space.lg),
    this.glow = false,
    this.onTap,
  });

  final Widget child;
  final EdgeInsetsGeometry padding;
  final bool glow;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final shape = RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(Radii.xl),
      side: BorderSide(color: c.border),
    );
    return Material(
      color: c.surface,
      shape: shape,
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Container(
          decoration: glow
              ? BoxDecoration(
                  gradient: RadialGradient(
                    center: Alignment.topCenter,
                    radius: 1.2,
                    colors: [c.glow, Colors.transparent],
                  ),
                )
              : null,
          padding: padding,
          child: child,
        ),
      ),
    );
  }
}

class SectionTitle extends StatelessWidget {
  const SectionTitle(this.text, {super.key, this.trailing});

  final String text;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: Space.sm),
    child: Row(
      children: [
        Expanded(
          child: Semantics(
            header: true,
            child: Text(text, style: context.text.subtitle),
          ),
        ),
        ?trailing,
      ],
    ),
  );
}

class StatTile extends StatelessWidget {
  const StatTile({super.key, required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => MergeSemantics(
    child: Container(
      padding: const EdgeInsets.all(Space.md + 2),
      decoration: BoxDecoration(
        color: context.colors.surfaceSunken,
        borderRadius: BorderRadius.circular(Radii.lg),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: context.text.bodySmall),
          const SizedBox(height: Space.xs),
          Text(
            value,
            style: context.text.title.copyWith(fontWeight: FontWeight.w700),
          ),
        ],
      ),
    ),
  );
}

enum Status { good, warning, critical }

/// Status always carries an icon and a label, never color alone.
class StatusLine extends StatelessWidget {
  const StatusLine({super.key, required this.status, required this.text});

  final Status status;
  final String text;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final (color, icon) = switch (status) {
      Status.good => (c.good, Icons.check_circle_outline),
      Status.warning => (c.warning, Icons.warning_amber_rounded),
      Status.critical => (c.critical, Icons.error_outline),
    };
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 18, color: color),
        const SizedBox(width: Space.xs + 2),
        Flexible(
          child: Text(text, style: context.text.label.copyWith(color: color)),
        ),
      ],
    );
  }
}

class UBanner extends StatelessWidget {
  const UBanner({
    super.key,
    required this.text,
    this.status,
    this.action,
    this.onAction,
  });

  final String text;
  final Status? status;
  final String? action;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final (color, icon) = switch (status) {
      Status.good => (c.good, Icons.check_circle_outline),
      Status.warning => (c.warning, Icons.warning_amber_rounded),
      Status.critical => (c.critical, Icons.error_outline),
      null => (c.accentText, Icons.info_outline),
    };
    return Container(
      padding: const EdgeInsets.all(Space.md),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(Radii.lg),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 18, color: color),
          const SizedBox(width: Space.md),
          Expanded(
            child: Text(
              text,
              style: context.text.bodySmall.copyWith(color: c.text),
            ),
          ),
          if (action != null)
            TextButton(onPressed: onAction, child: Text(action!)),
        ],
      ),
    );
  }
}

/// One section of a prominent disclosure (what / why / never).
class DisclosureItem {
  const DisclosureItem(this.icon, this.title, this.body);

  final IconData icon;
  final String title;
  final String body;
}

class DisclosureCard extends StatelessWidget {
  const DisclosureCard({super.key, required this.items});

  final List<DisclosureItem> items;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: Space.lg),
      decoration: BoxDecoration(
        color: c.surface,
        borderRadius: BorderRadius.circular(Radii.xl),
        border: Border.all(color: c.borderStrong),
      ),
      child: Column(
        children: [
          for (var i = 0; i < items.length; i++) ...[
            if (i > 0) const Divider(),
            Padding(
              padding: const EdgeInsets.symmetric(vertical: Space.md),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(items[i].icon, color: c.accentText, size: 22),
                  const SizedBox(width: Space.md),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(items[i].title, style: context.text.label),
                        const SizedBox(height: Space.xxs),
                        Text(
                          items[i].body,
                          style: context.text.bodySmall.copyWith(
                            color: c.text2,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class EmptyState extends StatelessWidget {
  const EmptyState({
    super.key,
    required this.icon,
    required this.title,
    required this.body,
    this.action,
  });

  final IconData icon;
  final String title;
  final String body;
  final Widget? action;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: Space.xxl),
    child: Column(
      children: [
        Icon(icon, size: 40, color: context.colors.accentText),
        const SizedBox(height: Space.md),
        Text(title, style: context.text.subtitle, textAlign: TextAlign.center),
        const SizedBox(height: Space.xs),
        Text(body, style: context.text.bodySmall, textAlign: TextAlign.center),
        if (action != null) ...[const SizedBox(height: Space.lg), action!],
      ],
    ),
  );
}
