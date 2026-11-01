import 'package:flutter/material.dart';

import '../../app_state.dart';
import '../../design_system/components/buttons.dart';
import '../../design_system/theme.dart';
import '../../design_system/tokens.dart';
import '../../engine/engine_api.g.dart';

/// "Block my distracting apps for N minutes". Distracting = every app with a rule.
Future<void> showQuickBlockSheet(BuildContext context, AppState state) =>
    showModalBottomSheet<void>(
      context: context,
      builder: (_) => _QuickBlockSheet(state: state),
    );

class _QuickBlockSheet extends StatefulWidget {
  const _QuickBlockSheet({required this.state});

  final AppState state;

  @override
  State<_QuickBlockSheet> createState() => _QuickBlockSheetState();
}

class _QuickBlockSheetState extends State<_QuickBlockSheet> {
  int _minutes = 60;
  bool _busy = false;

  /// Runs one edit at a time and closes the sheet when it succeeds.
  Future<void> _run(Future<void> Function(EngineHostApi api) action) async {
    setState(() => _busy = true);
    final error = await widget.state.edit(action);
    if (!mounted) return;
    if (error == null) {
      Navigator.of(context).pop();
    } else {
      setState(() => _busy = false);
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(error)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = widget.state;
    final until = state.snapshot?.quickBlockUntil;
    final ruleCount = state.snapshot?.rules.length ?? 0;
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(Space.xl, 0, Space.xl, Space.xl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Quick block', style: context.text.title),
            const SizedBox(height: Space.xs),
            Text(
              ruleCount == 0
                  ? 'Add an app in Blocks first: quick block covers every app you\'ve set a rule for.'
                  : 'Blocks your $ruleCount ${ruleCount == 1 ? 'app' : 'apps'} with rules right now.',
              style: context.text.bodySmall,
            ),
            const SizedBox(height: Space.lg),
            if (until != null) ...[
              Text(
                'On until ${TimeOfDay.fromDateTime(state.engineTimeToLocal(until)).format(context)}',
                style: context.text.subtitle,
              ),
              const SizedBox(height: Space.lg),
              UButton(
                label: 'Stop quick block',
                expand: true,
                onPressed: _busy
                    ? null
                    : () => _run((api) => api.stopQuickBlock()),
              ),
            ] else ...[
              Wrap(
                spacing: Space.sm,
                children: [
                  for (final m in const [30, 60, 120])
                    ChoiceChip(
                      label: Text(m < 60 ? '$m min' : '${m ~/ 60} h'),
                      selected: _minutes == m,
                      onSelected: (_) => setState(() => _minutes = m),
                    ),
                ],
              ),
              const SizedBox(height: Space.lg),
              PrimaryButton(
                label: 'Start',
                onPressed: ruleCount == 0 || _busy
                    ? null
                    : () => _run((api) => api.startQuickBlock(_minutes)),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
