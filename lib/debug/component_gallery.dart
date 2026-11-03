import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../design_system/components/app_tile.dart';
import '../design_system/components/buttons.dart';
import '../design_system/components/charts.dart';
import '../design_system/components/surfaces.dart';
import '../design_system/theme.dart';
import '../design_system/tokens.dart';

/// Debug-only screen that shows every design-system component in both themes.
class ComponentGalleryScreen extends StatefulWidget {
  const ComponentGalleryScreen({super.key});

  @override
  State<ComponentGalleryScreen> createState() => _ComponentGalleryScreenState();
}

// A valid 1x1 PNG so AppIcon can show the "real icon" path.
final Uint8List _sampleIcon = base64Decode(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==',
);

class _ComponentGalleryScreenState extends State<ComponentGalleryScreen> {
  Brightness _brightness = Brightness.light;

  @override
  Widget build(BuildContext context) => Theme(
    data: buildTheme(_brightness),
    child: Builder(
      builder: (context) => Scaffold(
        appBar: AppBar(title: const Text('Component gallery')),
        body: ListView(
          padding: const EdgeInsets.all(Space.lg),
          children: [
            Semantics(
              label: 'Theme',
              container: true,
              child: SegmentedButton<Brightness>(
                segments: const [
                  ButtonSegment(
                    value: Brightness.light,
                    label: Text('Light'),
                    icon: Icon(Icons.light_mode_outlined),
                  ),
                  ButtonSegment(
                    value: Brightness.dark,
                    label: Text('Dark'),
                    icon: Icon(Icons.dark_mode_outlined),
                  ),
                ],
                selected: {_brightness},
                onSelectionChanged: (s) =>
                    setState(() => _brightness = s.first),
              ),
            ),
            const SizedBox(height: Space.xl),
            const SectionTitle('Colors'),
            _Colors(context.colors),
            const SizedBox(height: Space.xl),
            const SectionTitle('Text styles'),
            _TextStyles(context.text),
            const SizedBox(height: Space.xl),
            const SectionTitle('Buttons'),
            PrimaryButton(label: 'Primary', onPressed: () {}),
            const SizedBox(height: Space.sm),
            PrimaryButton(
              label: 'Primary with icon',
              icon: Icons.play_arrow_rounded,
              onPressed: () {},
            ),
            const SizedBox(height: Space.sm),
            const PrimaryButton(label: 'Primary disabled', onPressed: null),
            const SizedBox(height: Space.md),
            Wrap(
              spacing: Space.sm,
              runSpacing: Space.sm,
              children: [
                for (final kind in UButtonKind.values)
                  UButton(
                    label: 'Button ${kind.name}',
                    kind: kind,
                    onPressed: () {},
                  ),
                for (final kind in UButtonKind.values)
                  UButton(
                    label: 'Off ${kind.name}',
                    kind: kind,
                    onPressed: null,
                  ),
                UButton(label: 'With icon', icon: Icons.add, onPressed: () {}),
              ],
            ),
            const SizedBox(height: Space.sm),
            UButton(label: 'Expanded button', expand: true, onPressed: () {}),
            const SizedBox(height: Space.xl),
            const SectionTitle('Cards'),
            const UCard(child: Text('Plain card')),
            const SizedBox(height: Space.sm),
            const UCard(glow: true, child: Text('Glow card')),
            const SizedBox(height: Space.sm),
            UCard(onTap: () {}, child: const Text('Tappable card')),
            const SizedBox(height: Space.xl),
            SectionTitle(
              'Section title with trailing',
              trailing: Text('See all', style: context.text.label),
            ),
            const Row(
              children: [
                Expanded(
                  child: StatTile(label: 'Today', value: '2h 14m'),
                ),
                SizedBox(width: Space.sm),
                Expanded(
                  child: StatTile(label: 'Pickups', value: '37'),
                ),
              ],
            ),
            const SizedBox(height: Space.xl),
            const SectionTitle('Status'),
            for (final s in Status.values)
              Padding(
                padding: const EdgeInsets.only(bottom: Space.xs),
                child: StatusLine(status: s, text: 'Status ${s.name}'),
              ),
            const SizedBox(height: Space.xl),
            const SectionTitle('Banners'),
            const UBanner(text: 'Plain banner'),
            const SizedBox(height: Space.sm),
            for (final s in Status.values) ...[
              UBanner(
                text: 'Banner ${s.name}',
                status: s,
                action: 'Fix',
                onAction: () {},
              ),
              const SizedBox(height: Space.sm),
            ],
            const SizedBox(height: Space.md),
            const SectionTitle('Disclosure card'),
            const DisclosureCard(
              items: [
                DisclosureItem(
                  Icons.visibility_outlined,
                  'What it sees',
                  'Which app is in front.',
                ),
                DisclosureItem(
                  Icons.wifi_off_rounded,
                  'What it never does',
                  'Send anything off the phone.',
                ),
              ],
            ),
            const SizedBox(height: Space.xl),
            const SectionTitle('Empty state'),
            EmptyState(
              icon: Icons.inbox_outlined,
              title: 'Nothing here yet',
              body: 'Add something and it will show up.',
              action: UButton(label: 'Add', onPressed: () {}),
            ),
            const SizedBox(height: Space.xl),
            const SectionTitle('App icon'),
            Wrap(
              spacing: Space.md,
              runSpacing: Space.md,
              children: [
                const AppIcon(label: 'Notes'),
                const AppIcon(label: ''),
                AppIcon(label: 'Photos', icon: _sampleIcon),
                const AppIcon(label: 'Large', size: Space.huge),
              ],
            ),
            const SizedBox(height: Space.xl),
            const SectionTitle('Usage bar'),
            for (final f in const [0.0, 0.5, 0.85, 1.2])
              Padding(
                padding: const EdgeInsets.only(bottom: Space.md),
                child: Row(
                  children: [
                    SizedBox(
                      width: Sizes.touchTarget + Space.sm,
                      child: Text('$f', style: context.text.bodySmall),
                    ),
                    Expanded(child: UsageBar(fraction: f)),
                  ],
                ),
              ),
            const SizedBox(height: Space.md),
            const SectionTitle('Progress ring'),
            Center(
              child: ProgressRing(
                fraction: 0.65,
                size: Space.huge * 2,
                semanticLabel: '65 percent of the daily goal',
                center: Text('65%', style: context.text.title),
              ),
            ),
            const SizedBox(height: Space.xl),
            const SectionTitle('Week bars'),
            WeekBars(
              values: const [1.5, 2.0, 3.5, 1.0, 4.2, 2.8, 2.2],
              labels: const ['M', 'T', 'W', 'T', 'F', 'S', 'S'],
              goal: 3,
              format: (h) => '${h.toStringAsFixed(1)} h',
            ),
            const SizedBox(height: Space.xxxl),
          ],
        ),
      ),
    ),
  );
}

class _Colors extends StatelessWidget {
  const _Colors(this.c);

  final UColors c;

  @override
  Widget build(BuildContext context) {
    final entries = <(String, Color)>[
      ('bg', c.bg),
      ('surface', c.surface),
      ('surfaceRaised', c.surfaceRaised),
      ('surfaceSunken', c.surfaceSunken),
      ('border', c.border),
      ('borderStrong', c.borderStrong),
      ('text', c.text),
      ('text2', c.text2),
      ('textMuted', c.textMuted),
      ('teal', c.teal),
      ('violet', c.violet),
      ('accentText', c.accentText),
      ('onAccent', c.onAccent),
      ('good', c.good),
      ('warning', c.warning),
      ('critical', c.critical),
      for (var i = 0; i < c.series.length; i++)
        ('series ${i + 1}', c.series[i]),
      ('other', c.other),
      ('chartSingle', c.chartSingle),
      ('chartGrid', c.chartGrid),
      ('chartBase', c.chartBase),
      ('glow', c.glow),
    ];
    return Wrap(
      spacing: Space.sm,
      runSpacing: Space.sm,
      children: [
        for (final (name, color) in entries)
          Semantics(
            label: 'Color $name',
            container: true,
            child: ExcludeSemantics(
              child: SizedBox(
                width: Sizes.listRow * 2,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    DecoratedBox(
                      decoration: BoxDecoration(
                        color: color,
                        borderRadius: BorderRadius.circular(Radii.sm),
                        border: Border.all(color: c.borderStrong),
                      ),
                      child: const SizedBox(
                        height: Sizes.touchTarget,
                        width: double.infinity,
                      ),
                    ),
                    const SizedBox(height: Space.xs),
                    Text(name, style: context.text.bodySmall),
                  ],
                ),
              ),
            ),
          ),
      ],
    );
  }
}

class _TextStyles extends StatelessWidget {
  const _TextStyles(this.t);

  final UText t;

  @override
  Widget build(BuildContext context) {
    final styles = <(String, TextStyle)>[
      ('Display', t.display),
      ('Headline', t.headline),
      ('Title', t.title),
      ('Subtitle', t.subtitle),
      ('Body', t.body),
      ('Body small', t.bodySmall),
      ('Label', t.label),
      ('Overline', t.overline),
    ];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (final (name, style) in styles)
          Padding(
            padding: const EdgeInsets.only(bottom: Space.sm),
            child: Text(name, style: style),
          ),
      ],
    );
  }
}
