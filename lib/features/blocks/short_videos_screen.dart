import 'package:flutter/material.dart';

import '../../app_state.dart';
import '../../design_system/components/app_tile.dart';
import '../../design_system/components/buttons.dart';
import '../../design_system/components/surfaces.dart';
import '../../design_system/theme.dart';
import '../../design_system/tokens.dart';
import '../apps/app_detail_screen.dart';

/// TikTok's packages (global and regional builds). The whole app is one feed, so it gets a limit.
const _tiktokPackages = [
  'com.zhiliaoapp.musically',
  'com.ss.android.ugc.trill',
];

/// Block the short-video feed of supported apps while the rest of each app keeps working.
class ShortVideosScreen extends StatefulWidget {
  const ShortVideosScreen({super.key, required this.state});

  final AppState state;

  @override
  State<ShortVideosScreen> createState() => _ShortVideosScreenState();
}

class _ShortVideosScreenState extends State<ShortVideosScreen> {
  AppState get state => widget.state;

  @override
  void initState() {
    super.initState();
    // Detection health may have changed while this screen was closed.
    state.refreshShortForm();
    state.loadApps();
  }

  /// Flips [pkg] against the engine's latest state when the edit runs, so quick taps never undo
  /// each other.
  Future<void> _toggle(String pkg) async {
    final error = await state.edit((api) {
      final current = state.shortForm.where((f) => f.packageName == pkg);
      final blocked = current.isNotEmpty && current.first.blocked;
      return api.setShortFormBlocked(pkg, !blocked);
    });
    if (error != null && mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(error)));
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Short videos')),
    body: ListenableBuilder(
      listenable: state,
      builder: (context, _) {
        final feeds = state.shortForm;
        final tiktok = _tiktokPackages
            .where(state.apps.containsKey)
            .firstOrNull;
        return ListView(
          padding: const EdgeInsets.fromLTRB(Space.lg, 0, Space.lg, Space.xxl),
          children: [
            Text(
              'Block the endless feeds. Keep messages, posts and search.',
              style: context.text.body.copyWith(color: context.colors.text2),
            ),
            const SizedBox(height: Space.lg),
            UCard(
              padding: const EdgeInsets.symmetric(
                horizontal: Space.lg,
                vertical: Space.xs,
              ),
              child: Column(
                children: [
                  for (final f in feeds)
                    SwitchListTile(
                      contentPadding: EdgeInsets.zero,
                      secondary: AppIcon(
                        label: f.label,
                        icon: state.apps[f.packageName]?.icon,
                      ),
                      title: Text(f.label, style: context.text.subtitle),
                      subtitle: f.installed
                          ? null
                          : Text(
                              'Not installed',
                              style: context.text.bodySmall,
                            ),
                      value: f.blocked,
                      onChanged: (_) => _toggle(f.packageName),
                    ),
                  if (tiktok != null) _TikTokRow(state: state, pkg: tiktok),
                ],
              ),
            ),
            const SizedBox(height: Space.lg),
            _health(),
          ],
        );
      },
    ),
  );

  Widget _health() {
    final p = state.permissions;
    if (p != null && !(p.accessibility && p.accessibilityRunning)) {
      return const UBanner(
        status: Status.warning,
        text: 'Blocking short videos needs accessibility access. You can turn it on in Settings.',
      );
    }
    final outdated = [
      for (final f in state.shortForm)
        if (f.blocked && f.maybeOutdated) f.label,
    ];
    if (outdated.isNotEmpty) {
      return UBanner(
        status: Status.warning,
        text:
            'Detection may be outdated for ${_join(outdated)}. Update Unscroll when a new version is out.',
      );
    }
    return const UBanner(
      status: Status.good,
      text: 'Detection works for your installed apps.',
    );
  }

  static String _join(List<String> names) => names.length < 2
      ? names.join()
      : '${names.sublist(0, names.length - 1).join(', ')} and ${names.last}';
}

class _TikTokRow extends StatelessWidget {
  const _TikTokRow({required this.state, required this.pkg});

  final AppState state;
  final String pkg;

  @override
  Widget build(BuildContext context) {
    final label = state.labelOf(pkg);
    return ConstrainedBox(
      constraints: const BoxConstraints(minHeight: Sizes.listRowTwoLine),
      child: Row(
        children: [
          AppIcon(label: label, icon: state.apps[pkg]?.icon),
          const SizedBox(width: Space.lg),
          Expanded(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(label, style: context.text.subtitle),
                Text(
                  'The whole app is a feed. Use a limit instead.',
                  style: context.text.bodySmall,
                ),
              ],
            ),
          ),
          Semantics(
            label: 'Set a limit for $label',
            excludeSemantics: true,
            button: true,
            child: UButton(
              label: 'Set limit',
              kind: UButtonKind.ghost,
              onPressed: () => openAppDetail(context, state, pkg),
            ),
          ),
        ],
      ),
    );
  }
}
