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
import '../blocks/quick_block_sheet.dart';
import 'home_shell.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key, required this.state, required this.onNavigate});

  final AppState state;
  final ValueChanged<int> onNavigate;

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: state,
    builder: (context, _) {
      final today = state.today;
      final goal = (state.snapshot?.dailyGoalMinutes ?? 180) * 60000;
      final used = today?.totalMillis ?? 0;
      final previous = state.week.length > 1
          ? state.week.sublist(0, state.week.length - 1)
          : const <DayUsage>[];
      final average = previous.isEmpty
          ? null
          : previous.map((d) => d.totalMillis).reduce((a, b) => a + b) ~/
                previous.length;
      // Only picks the empty-state message; the limits shown come from the engine.
      final hasLimits = (state.snapshot?.rules ?? []).any(
        (r) => r.dailyLimitMinutes != null && !r.blocked,
      );
      // Computed by the engine: only limits that apply today, extensions included.
      final limits = today?.limits ?? const <LimitStatus>[];
      return RefreshIndicator(
        onRefresh: state.refresh,
        child: ListView(
          padding: const EdgeInsets.all(Space.lg),
          children: [
            Text(_greeting(), style: context.text.title),
            const SizedBox(height: Space.lg),
            if (state.coreProtectionMissing) ...[
              UBanner(
                status: Status.warning,
                text: 'Blocking is off until you allow usage access and accessibility.',
                action: 'Fix',
                onAction: () => onNavigate(Tabs.settings),
              ),
              const SizedBox(height: Space.lg),
            ] else if (state.accessibilityStopped) ...[
              UBanner(
                status: Status.warning,
                text: 'Accessibility is on but not running. Turn it off and on again to restart blocking.',
                action: 'Fix',
                onAction: () => onNavigate(Tabs.settings),
              ),
              const SizedBox(height: Space.lg),
            ],
            if (!state.usageLoaded)
              const UCard(
                glow: true,
                child: SizedBox(
                  height: 104,
                  child: Center(
                    child: CircularProgressIndicator(
                      semanticsLabel: 'Loading screen time',
                    ),
                  ),
                ),
              )
            else
              UCard(
                glow: true,
                child: Row(
                  children: [
                    ProgressRing(
                      fraction: goal == 0 ? 0 : used / goal,
                      size: 104,
                      stroke: 9,
                      semanticLabel:
                          'Used ${formatMillis(used)} of your ${formatMillis(goal)} goal',
                      center: Text(
                        '${(goal == 0 ? 0 : used * 100 ~/ goal).clamp(0, 999)}%',
                        style: context.text.title.copyWith(
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                    const SizedBox(width: Space.lg),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'SCREEN TIME TODAY',
                            style: context.text.overline,
                          ),
                          const SizedBox(height: Space.xs),
                          Text(
                            formatMillis(used),
                            style: context.text.headline,
                          ),
                          const SizedBox(height: Space.xs),
                          if (average != null && average > 0)
                            StatusLine(
                              status: used <= average
                                  ? Status.good
                                  : Status.warning,
                              text: used <= average
                                  ? '${formatMillis(average - used)} less than usual'
                                  : '${formatMillis(used - average)} more than usual',
                            )
                          else
                            Text(
                              'Goal ${formatMillis(goal)}',
                              style: context.text.bodySmall,
                            ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            const SizedBox(height: Space.lg),
            Row(
              children: [
                _QuickAction(
                  icon: Icons.timer_outlined,
                  label: 'Focus',
                  onTap: () => onNavigate(Tabs.focus),
                ),
                const SizedBox(width: Space.sm),
                _QuickAction(
                  icon: Icons.bolt_outlined,
                  label: 'Quick block',
                  onTap: () => showQuickBlockSheet(context, state),
                ),
                const SizedBox(width: Space.sm),
                _QuickAction(
                  icon: Icons.bedtime_outlined,
                  label: 'Schedules',
                  onTap: () => onNavigate(Tabs.blocks),
                ),
              ],
            ),
            const SizedBox(height: Space.xl),
            SectionTitle(
              'Limits today',
              trailing: TextButton(
                onPressed: () => onNavigate(Tabs.blocks),
                child: const Text('Manage'),
              ),
            ),
            if (!hasLimits)
              EmptyState(
                icon: Icons.hourglass_empty_rounded,
                title: 'No limits yet',
                body: 'Add a daily limit to an app from Insights or Blocks.',
              )
            else if (!state.usageLoaded ||
                (limits.isEmpty && state.usageRefreshing))
              const SizedBox.shrink() // a limit just added is on its way
            else if (limits.isEmpty)
              EmptyState(
                icon: Icons.event_available_outlined,
                title: 'No limits today',
                body: 'Your limits are set for other days.',
              )
            else
              UCard(
                padding: const EdgeInsets.symmetric(
                  horizontal: Space.lg,
                  vertical: Space.xs,
                ),
                child: Column(
                  children: [
                    for (final limit in limits)
                      _LimitRow(
                        label: state.labelOf(limit.packageName),
                        icon: state.apps[limit.packageName]?.icon,
                        usedMillis: limit.usedMillis,
                        allowedMillis: limit.allowedMillis,
                        onTap: () =>
                            openAppDetail(context, state, limit.packageName),
                      ),
                  ],
                ),
              ),
          ],
        ),
      );
    },
  );

  String _greeting() {
    final h = DateTime.now().hour;
    if (h < 5) return 'Still up?';
    if (h < 12) return 'Good morning';
    if (h < 18) return 'Good afternoon';
    return 'Good evening';
  }
}

class _QuickAction extends StatelessWidget {
  const _QuickAction({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Expanded(
    child: UCard(
      onTap: onTap,
      padding: const EdgeInsets.symmetric(
        vertical: Space.md,
        horizontal: Space.xs,
      ),
      child: Column(
        children: [
          Icon(icon, color: context.colors.accentText),
          const SizedBox(height: Space.xs),
          Text(label, style: context.text.label, textAlign: TextAlign.center),
        ],
      ),
    ),
  );
}

class _LimitRow extends StatelessWidget {
  const _LimitRow({
    required this.label,
    required this.icon,
    required this.usedMillis,
    required this.allowedMillis,
    required this.onTap,
  });

  final String label;
  final Uint8List? icon;
  final int usedMillis;
  final int allowedMillis;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final left = allowedMillis - usedMillis;
    final fraction = allowedMillis <= 0 ? 1.0 : usedMillis / allowedMillis;
    return InkWell(
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
                      Text(
                        left <= 0 ? 'Used up' : '${formatMillis(left)} left',
                        style: context.text.label.copyWith(
                          color: fraction >= 1
                              ? context.colors.critical
                              : fraction >= 0.8
                              ? context.colors.warning
                              : context.colors.textMuted,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: Space.sm),
                  UsageBar(fraction: fraction),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
