import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../../app_state.dart';
import '../../debug/component_gallery.dart';
import '../../design_system/components/surfaces.dart';
import '../../design_system/theme.dart';
import '../../design_system/tokens.dart';
import '../../util/format.dart';
import '../permissions/permissions.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key, required this.state});

  final AppState state;

  static const version = '0.1.0';

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: state,
    builder: (context, _) {
      final goal = state.snapshot?.dailyGoalMinutes ?? 180;
      return ListView(
        padding: const EdgeInsets.all(Space.lg),
        children: [
          Text('Settings', style: context.text.title),
          const SizedBox(height: Space.lg),
          const SectionTitle('Health check'),
          PermissionList(state: state),
          const SizedBox(height: Space.xl),
          const SectionTitle('Daily goal'),
          UCard(
            child: Row(
              children: [
                IconButton.filledTonal(
                  tooltip: 'Less',
                  onPressed: goal > 30
                      ? () => state.edit((api) => api.setDailyGoal(goal - 30))
                      : null,
                  icon: const Icon(Icons.remove),
                ),
                Expanded(
                  child: Text(
                    formatDuration(Duration(minutes: goal)),
                    textAlign: TextAlign.center,
                    style: context.text.title,
                  ),
                ),
                IconButton.filledTonal(
                  tooltip: 'More',
                  onPressed: goal < 720
                      ? () => state.edit((api) => api.setDailyGoal(goal + 30))
                      : null,
                  icon: const Icon(Icons.add),
                ),
              ],
            ),
          ),
          const SizedBox(height: Space.xl),
          const SectionTitle('About'),
          UCard(
            padding: const EdgeInsets.symmetric(
              horizontal: Space.lg,
              vertical: Space.xs,
            ),
            child: Column(
              children: [
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: Icon(
                    Icons.wifi_off_rounded,
                    color: context.colors.accentText,
                  ),
                  title: const Text('Privacy'),
                  subtitle: const Text(
                    'No internet permission. Everything stays on this phone.',
                  ),
                ),
                const Divider(),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: Icon(Icons.code, color: context.colors.accentText),
                  title: const Text('Open-source licenses'),
                  subtitle: const Text('Unscroll $version · GPL-3.0-or-later'),
                  trailing: Icon(
                    Icons.chevron_right,
                    color: context.colors.textMuted,
                  ),
                  onTap: () => showLicensePage(
                    context: context,
                    applicationName: 'Unscroll',
                    applicationVersion: version,
                    applicationLegalese: 'Free software under the GNU GPL v3 or later. No warranty.',
                  ),
                ),
                if (kDebugMode) ...[
                  const Divider(),
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: Icon(
                      Icons.palette_outlined,
                      color: context.colors.accentText,
                    ),
                    title: const Text('Component gallery'),
                    subtitle: const Text('Debug builds only'),
                    trailing: Icon(
                      Icons.chevron_right,
                      color: context.colors.textMuted,
                    ),
                    onTap: () => Navigator.of(context).push(
                      MaterialPageRoute<void>(
                        builder: (_) => const ComponentGalleryScreen(),
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(height: Space.lg),
          Text(
            'Unscroll is a self-help tool, not a medical device. It does not diagnose or treat any condition.',
            style: context.text.bodySmall,
          ),
        ],
      );
    },
  );
}
