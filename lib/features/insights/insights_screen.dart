import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../../app_state.dart';
import '../../design_system/components/app_tile.dart';
import '../../design_system/components/charts.dart';
import '../../design_system/components/surfaces.dart';
import '../../design_system/theme.dart';
import '../../design_system/tokens.dart';
import '../../engine/engine_api.g.dart';
import '../../util/format.dart';
import '../apps/app_detail_screen.dart';

enum _Range { today, week }

class InsightsScreen extends StatefulWidget {
  const InsightsScreen({super.key, required this.state});

  final AppState state;

  @override
  State<InsightsScreen> createState() => _InsightsScreenState();
}

class _InsightsScreenState extends State<InsightsScreen> {
  _Range _range = _Range.today;

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.state,
    builder: (context, _) {
      final state = widget.state;
      final week = state.week;
      if (state.permissions != null && !state.permissions!.usageAccess) {
        return ListView(
          padding: const EdgeInsets.all(Space.lg),
          children: [
            Text('Insights', style: context.text.title),
            EmptyState(
              icon: Icons.query_stats_rounded,
              title: 'Usage access is off',
              body:
                  'Allow usage access in Settings to see where your time goes.',
            ),
          ],
        );
      }
      if (!state.usageLoaded) {
        return const Center(
          child: CircularProgressIndicator(
            semanticsLabel: 'Loading screen time',
          ),
        );
      }
      final apps = _range == _Range.today
          ? (state.today?.apps ?? const <AppUsageInfo>[])
          : _mergeWeek(week);
      final total = _range == _Range.today
          ? (state.today?.totalMillis ?? 0)
          : week.fold<int>(0, (sum, d) => sum + d.totalMillis);
      final unlocks = _range == _Range.today
          ? (state.today?.unlocks ?? 0)
          : week.fold<int>(0, (sum, d) => sum + d.unlocks);
      final opens = apps.fold<int>(0, (sum, a) => sum + a.opens);
      return RefreshIndicator(
        onRefresh: state.refreshUsage,
        child: ListView(
          padding: const EdgeInsets.all(Space.lg),
          children: [
            Text('Insights', style: context.text.title),
            const SizedBox(height: Space.lg),
            SegmentedButton<_Range>(
              segments: const [
                ButtonSegment(value: _Range.today, label: Text('Today')),
                ButtonSegment(value: _Range.week, label: Text('Last 7 days')),
              ],
              selected: {_range},
              showSelectedIcon: false,
              onSelectionChanged: (s) => setState(() => _range = s.first),
            ),
            const SizedBox(height: Space.lg),
            UCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(formatMillis(total), style: context.text.headline),
                  if (_range == _Range.week && week.isNotEmpty)
                    Text(
                      '${formatMillis(total ~/ week.length)} a day on average',
                      style: context.text.bodySmall,
                    ),
                  if (week.isNotEmpty) ...[
                    const SizedBox(height: Space.md),
                    WeekBars(
                      values: [for (final d in week) d.totalMillis / 3600000],
                      labels: [
                        for (final d in week)
                          weekdayShort[DateTime.fromMillisecondsSinceEpoch(
                                d.dayStartMillis,
                              ).weekday -
                              1],
                      ],
                      goal: (state.snapshot?.dailyGoalMinutes ?? 180) / 60,
                      format: (h) => formatMillis((h * 3600000).round()),
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(height: Space.md),
            Row(
              children: [
                Expanded(
                  child: StatTile(label: 'Unlocks', value: '$unlocks'),
                ),
                const SizedBox(width: Space.sm),
                Expanded(
                  child: StatTile(label: 'App opens', value: '$opens'),
                ),
              ],
            ),
            const SizedBox(height: Space.xl),
            const SectionTitle('Most used'),
            if (apps.isEmpty)
              EmptyState(
                icon: Icons.phone_android_rounded,
                title: 'Nothing yet',
                body: 'App usage will show up here.',
              )
            else
              UCard(
                padding: const EdgeInsets.symmetric(
                  horizontal: Space.lg,
                  vertical: Space.xs,
                ),
                child: Column(
                  children: [
                    for (final a in apps.take(30))
                      _AppRow(
                        label: state.labelOf(a.packageName),
                        icon: state.apps[a.packageName]?.icon,
                        millis: a.millis,
                        opens: a.opens,
                        share: total == 0 ? 0 : a.millis / total,
                        hasRule: state.ruleFor(a.packageName) != null,
                        onTap: () =>
                            openAppDetail(context, state, a.packageName),
                      ),
                  ],
                ),
              ),
          ],
        ),
      );
    },
  );

  List<AppUsageInfo> _mergeWeek(List<DayUsage> week) {
    final millis = <String, int>{};
    final opens = <String, int>{};
    for (final d in week) {
      for (final a in d.apps) {
        millis[a.packageName] = (millis[a.packageName] ?? 0) + a.millis;
        opens[a.packageName] = (opens[a.packageName] ?? 0) + a.opens;
      }
    }
    return [
      for (final e in millis.entries)
        AppUsageInfo(
          packageName: e.key,
          millis: e.value,
          opens: opens[e.key] ?? 0,
        ),
    ]..sort((a, b) => b.millis.compareTo(a.millis));
  }
}

class _AppRow extends StatelessWidget {
  const _AppRow({
    required this.label,
    required this.icon,
    required this.millis,
    required this.opens,
    required this.share,
    required this.hasRule,
    required this.onTap,
  });

  final String label;
  final Uint8List? icon;
  final int millis;
  final int opens;
  final double share;
  final bool hasRule;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    child: ConstrainedBox(
      constraints: const BoxConstraints(minHeight: Sizes.listRowTwoLine),
      child: Row(
        children: [
          AppIcon(label: label, icon: icon),
          const SizedBox(width: Space.md),
          Expanded(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        label,
                        style: context.text.subtitle,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    if (hasRule)
                      Padding(
                        padding: const EdgeInsets.only(right: Space.xs),
                        child: Icon(
                          Icons.shield_outlined,
                          size: 16,
                          color: context.colors.accentText,
                          semanticLabel: 'Has a rule',
                        ),
                      ),
                    Text(formatMillis(millis), style: context.text.label),
                  ],
                ),
                const SizedBox(height: Space.xs),
                Text(
                  '$opens ${opens == 1 ? 'open' : 'opens'}',
                  style: context.text.bodySmall,
                ),
              ],
            ),
          ),
        ],
      ),
    ),
  );
}
