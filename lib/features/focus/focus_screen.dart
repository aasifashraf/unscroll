import 'dart:async';

import 'package:flutter/material.dart';

import '../../app_state.dart';
import '../../design_system/components/buttons.dart';
import '../../design_system/components/charts.dart';
import '../../design_system/components/surfaces.dart';
import '../../design_system/theme.dart';
import '../../design_system/tokens.dart';
import '../../util/format.dart';
import '../apps/app_detail_screen.dart';

class FocusScreen extends StatefulWidget {
  const FocusScreen({super.key, required this.state});

  final AppState state;

  @override
  State<FocusScreen> createState() => _FocusScreenState();
}

class _FocusScreenState extends State<FocusScreen> {
  int _minutes = 25;
  Set<String>? _apps;
  Timer? _tick;
  bool _starting = false;

  @override
  void initState() {
    super.initState();
    // AppState refreshes when the session ends. Re-syncing every 30 s also corrects the
    // countdown if the phone's clock is changed meanwhile.
    _tick = Timer.periodic(const Duration(seconds: 1), (t) {
      if (widget.state.snapshot?.focus == null || !mounted) return;
      if (t.tick % 30 == 0) widget.state.refreshSnapshot();
      setState(() {});
    });
  }

  @override
  void dispose() {
    _tick?.cancel();
    super.dispose();
  }

  Set<String> get _chosen => _apps ?? widget.state.limitedApps;

  Future<void> _start() async {
    if (_starting) return; // double tap
    setState(() => _starting = true);
    final error = await widget.state.edit(
      (api) => api.startFocus(_minutes, _chosen.toList()),
    );
    if (mounted) {
      setState(() => _starting = false);
      if (error != null) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(error)));
      }
    }
  }

  Future<void> _endEarly() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('End this session early?'),
        content: const Text('That\'s okay. You can start a new one anytime.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Keep going'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('End early'),
          ),
        ],
      ),
    );
    if (ok == true) await widget.state.edit((api) => api.stopFocus());
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.state,
    builder: (context, _) {
      final snap = widget.state.snapshot;
      final focus = snap?.focus;
      // Focus times are in the engine's clock, which ignores manual clock changes.
      final now = widget.state.engineNow;
      final completed =
          snap?.focusHistory.where((f) => f.completed).length ?? 0;
      if (focus != null && focus.endsAt > now) {
        final total = focus.endsAt - focus.startedAt;
        final left = focus.endsAt - now;
        return ListView(
          padding: const EdgeInsets.all(Space.xl),
          children: [
            Text(
              'FOCUS',
              style: context.text.overline,
              textAlign: TextAlign.center,
            ),
            Text(
              '${focus.packages.length} apps paused',
              style: context.text.bodySmall,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: Space.xxl),
            Center(
              child: ProgressRing(
                fraction: 1 - left / total,
                size: 240,
                stroke: 12,
                semanticLabel:
                    '${formatDuration(Duration(milliseconds: left))} left',
                center: Text(
                  formatCountdown(Duration(milliseconds: left)),
                  style: context.text.display,
                ),
              ),
            ),
            const SizedBox(height: Space.xxl),
            Text(
              'Opening a paused app shows a gentle block screen until the timer ends.',
              style: context.text.body.copyWith(color: context.colors.text2),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: Space.xxl),
            UButton(
              label: 'End early',
              kind: UButtonKind.danger,
              expand: true,
              onPressed: _endEarly,
            ),
          ],
        );
      }
      final chosen = _chosen;
      return ListView(
        padding: const EdgeInsets.all(Space.lg),
        children: [
          Text('Focus', style: context.text.title),
          const SizedBox(height: Space.xs),
          Text(
            'Pause your distracting apps for a while.',
            style: context.text.bodySmall,
          ),
          const SizedBox(height: Space.xl),
          const SectionTitle('How long'),
          Wrap(
            spacing: Space.sm,
            runSpacing: Space.sm,
            children: [
              for (final m in const [15, 25, 50, 90])
                ChoiceChip(
                  label: Text('$m min'),
                  selected: _minutes == m,
                  onSelected: (_) => setState(() => _minutes = m),
                ),
            ],
          ),
          const SizedBox(height: Space.xl),
          SectionTitle(
            'Apps to pause (${chosen.length})',
            trailing: TextButton(
              onPressed: () async {
                final picked = await pickApps(
                  context,
                  widget.state,
                  initial: chosen,
                  title: 'Apps to pause',
                );
                if (picked != null) setState(() => _apps = picked);
              },
              child: const Text('Choose'),
            ),
          ),
          Text(
            chosen.isEmpty
                ? 'Choose at least one app.'
                : chosen.map(widget.state.labelOf).join(', '),
            style: context.text.bodySmall,
          ),
          const SizedBox(height: Space.xxl),
          PrimaryButton(
            label: _starting ? 'Starting…' : 'Start focus',
            icon: Icons.timer_outlined,
            onPressed: chosen.isEmpty || _starting ? null : _start,
          ),
          const SizedBox(height: Space.xl),
          Row(
            children: [
              Expanded(
                child: StatTile(
                  label: 'Sessions completed',
                  value: '$completed',
                ),
              ),
              const SizedBox(width: Space.sm),
              Expanded(
                child: StatTile(
                  label: 'Focused in total',
                  value: formatMillis(
                    snap?.focusHistory.fold<int>(
                          0,
                          (sum, f) =>
                              sum + (f.endedAt - f.startedAt).clamp(0, 1 << 40),
                        ) ??
                        0,
                  ),
                ),
              ),
            ],
          ),
        ],
      );
    },
  );
}
