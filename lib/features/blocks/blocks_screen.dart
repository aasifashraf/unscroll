import 'package:flutter/material.dart';

import '../../app_state.dart';
import '../../design_system/components/app_tile.dart';
import '../../design_system/components/buttons.dart';
import '../../design_system/components/surfaces.dart';
import '../../design_system/theme.dart';
import '../../design_system/tokens.dart';
import '../../engine/engine_api.g.dart';
import '../../util/format.dart';
import '../apps/app_detail_screen.dart';
import 'quick_block_sheet.dart';
import 'schedules_screen.dart';
import 'websites_screen.dart';

class BlocksScreen extends StatelessWidget {
  const BlocksScreen({super.key, required this.state});

  final AppState state;

  Future<void> _addApps(BuildContext context) async {
    final picked = await pickApps(context, state, title: 'Add apps');
    if (picked == null) return;
    // One edit, checked against the rules as they are when it runs: apps that got a rule
    // meanwhile keep it.
    final error = await state.edit((api) async {
      for (final pkg in picked) {
        if (state.ruleFor(pkg) != null) continue;
        await api.setRule(
          AppRuleMsg(
            packageName: pkg,
            blocked: false,
            limitDays: const [1, 2, 3, 4, 5, 6, 7],
            pauseSeconds: 5,
          ),
        );
      }
    });
    if (error != null && context.mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(error)));
    }
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: state,
    builder: (context, _) {
      final snap = state.snapshot;
      final rules = [...?snap?.rules]
        ..sort(
          (a, b) => state
              .labelOf(a.packageName)
              .compareTo(state.labelOf(b.packageName)),
        );
      final quickUntil = snap?.quickBlockUntil;
      final activeSchedules =
          snap?.schedules.where((s) => s.enabled).length ?? 0;
      return ListView(
        padding: const EdgeInsets.all(Space.lg),
        children: [
          Text('Blocks', style: context.text.title),
          const SizedBox(height: Space.lg),
          UCard(
            glow: true,
            onTap: () => showQuickBlockSheet(context, state),
            child: Row(
              children: [
                Icon(Icons.bolt_outlined, color: context.colors.accentText),
                const SizedBox(width: Space.md),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Quick block', style: context.text.subtitle),
                      Text(
                        quickUntil == null
                            ? 'Block your apps right now'
                            : 'On until ${TimeOfDay.fromDateTime(state.engineTimeToLocal(quickUntil)).format(context)}',
                        style: context.text.bodySmall,
                      ),
                    ],
                  ),
                ),
                Icon(Icons.chevron_right, color: context.colors.textMuted),
              ],
            ),
          ),
          const SizedBox(height: Space.md),
          UCard(
            padding: const EdgeInsets.symmetric(
              horizontal: Space.lg,
              vertical: Space.xs,
            ),
            child: Column(
              children: [
                _NavRow(
                  icon: Icons.language,
                  title: 'Websites',
                  subtitle:
                      '${_count(snap?.blockedSites.length ?? 0, 'site')} · ${_count(snap?.blockedKeywords.length ?? 0, 'keyword')}',
                  onTap: () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) => WebsitesScreen(state: state),
                    ),
                  ),
                ),
                const Divider(),
                _NavRow(
                  icon: Icons.calendar_month_outlined,
                  title: 'Schedules',
                  subtitle: activeSchedules == 0
                      ? 'None on'
                      : '$activeSchedules on',
                  onTap: () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) => SchedulesScreen(state: state),
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: Space.xl),
          SectionTitle(
            'Apps with rules',
            trailing: TextButton.icon(
              onPressed: () => _addApps(context),
              icon: const Icon(Icons.add),
              label: const Text('Add'),
            ),
          ),
          if (rules.isEmpty)
            EmptyState(
              icon: Icons.apps_rounded,
              title: 'No apps yet',
              body: 'Add the apps that pull you in, then choose a pause, a limit or a full block.',
              action: UButton(
                label: 'Add apps',
                icon: Icons.add,
                onPressed: () => _addApps(context),
              ),
            )
          else
            UCard(
              padding: const EdgeInsets.symmetric(
                horizontal: Space.lg,
                vertical: Space.xs,
              ),
              child: Column(
                children: [
                  for (final r in rules)
                    InkWell(
                      onTap: () => openAppDetail(context, state, r.packageName),
                      child: ConstrainedBox(
                        constraints: const BoxConstraints(
                          minHeight: Sizes.listRowTwoLine,
                        ),
                        child: Row(
                          children: [
                            AppIcon(
                              label: state.labelOf(r.packageName),
                              icon: state.apps[r.packageName]?.icon,
                            ),
                            const SizedBox(width: Space.md),
                            Expanded(
                              child: Column(
                                mainAxisAlignment: MainAxisAlignment.center,
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    state.labelOf(r.packageName),
                                    style: context.text.subtitle,
                                  ),
                                  Text(
                                    _describe(r),
                                    style: context.text.bodySmall,
                                  ),
                                ],
                              ),
                            ),
                            Icon(
                              Icons.chevron_right,
                              color: context.colors.textMuted,
                            ),
                          ],
                        ),
                      ),
                    ),
                ],
              ),
            ),
          const SizedBox(height: Space.lg),
          Text(
            'Phone, Messages, your keyboard and home screen are never blocked.',
            style: context.text.bodySmall,
            textAlign: TextAlign.center,
          ),
        ],
      );
    },
  );

  static String _count(int n, String noun) => '$n $noun${n == 1 ? '' : 's'}';

  String _describe(AppRuleMsg r) {
    if (r.blocked) return 'Blocked';
    return [
      if (r.dailyLimitMinutes != null)
        '${formatDuration(Duration(minutes: r.dailyLimitMinutes!))} a day',
      if (r.pauseSeconds != null) '${r.pauseSeconds} s pause',
      if (r.openLimit != null) '${r.openLimit} opens',
    ].join(' · ');
  }
}

class _NavRow extends StatelessWidget {
  const _NavRow({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => ListTile(
    contentPadding: EdgeInsets.zero,
    leading: Icon(icon, color: context.colors.accentText),
    title: Text(title, style: context.text.subtitle),
    subtitle: Text(subtitle, style: context.text.bodySmall),
    trailing: Icon(Icons.chevron_right, color: context.colors.textMuted),
    onTap: onTap,
  );
}
