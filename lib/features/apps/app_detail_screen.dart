import 'package:flutter/material.dart';

import '../../app_state.dart';
import '../../design_system/components/app_tile.dart';
import '../../design_system/components/buttons.dart';
import '../../design_system/components/surfaces.dart';
import '../../design_system/theme.dart';
import '../../design_system/tokens.dart';
import '../../engine/engine_api.g.dart';
import '../../util/format.dart';

Future<void> openAppDetail(BuildContext context, AppState state, String pkg) =>
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => AppDetailScreen(state: state, packageName: pkg),
      ),
    );

/// Limit, pause, open limit and full block for one app. Every change saves immediately.
class AppDetailScreen extends StatelessWidget {
  const AppDetailScreen({
    super.key,
    required this.state,
    required this.packageName,
  });

  final AppState state;
  final String packageName;

  static const _allDays = [1, 2, 3, 4, 5, 6, 7];

  AppRuleMsg _current() =>
      state.ruleFor(packageName) ??
      AppRuleMsg(packageName: packageName, blocked: false, limitDays: _allDays);

  /// Applies [change] to the rule as it is when the edit runs (not as it was drawn), so quick
  /// taps never undo each other.
  Future<void> _save(
    BuildContext context,
    AppRuleMsg Function(AppRuleMsg) change,
  ) async {
    final error = await state.edit((api) {
      final rule = change(_current());
      final empty =
          !rule.blocked &&
          rule.dailyLimitMinutes == null &&
          rule.pauseSeconds == null &&
          rule.openLimit == null;
      return empty ? api.removeRule(packageName) : api.setRule(rule);
    });
    if (error != null && context.mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(error)));
    }
  }

  AppRuleMsg _copy(
    AppRuleMsg r, {
    bool? blocked,
    Object? limit = _keep,
    List<int>? days,
    Object? pause = _keep,
    Object? opens = _keep,
  }) => AppRuleMsg(
    packageName: r.packageName,
    blocked: blocked ?? r.blocked,
    dailyLimitMinutes: identical(limit, _keep)
        ? r.dailyLimitMinutes
        : limit as int?,
    limitDays: days ?? r.limitDays,
    pauseSeconds: identical(pause, _keep) ? r.pauseSeconds : pause as int?,
    openLimit: identical(opens, _keep) ? r.openLimit : opens as int?,
  );

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: state,
    builder: (context, _) {
      final rule = _current();
      final label = state.labelOf(packageName);
      final usage = state.today?.apps
          .where((a) => a.packageName == packageName)
          .firstOrNull;
      return Scaffold(
        appBar: AppBar(),
        body: ListView(
          padding: const EdgeInsets.fromLTRB(Space.lg, 0, Space.lg, Space.xxl),
          children: [
            Row(
              children: [
                AppIcon(
                  label: label,
                  icon: state.apps[packageName]?.icon,
                  size: 56,
                ),
                const SizedBox(width: Space.md),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(label, style: context.text.title),
                      Text(
                        '${formatMillis(usage?.millis ?? 0)} · ${usage?.opens ?? 0} opens today',
                        style: context.text.bodySmall,
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: Space.xl),
            UCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _SwitchRow(
                    title: 'Daily limit',
                    subtitle: rule.dailyLimitMinutes == null
                        ? 'Off'
                        : 'Resets when your day starts',
                    value: rule.dailyLimitMinutes != null,
                    onChanged: rule.blocked
                        ? null
                        : (on) => _save(
                            context,
                            (r) => _copy(r, limit: on ? 45 : null),
                          ),
                  ),
                  if (rule.dailyLimitMinutes != null) ...[
                    _MinuteStepper(
                      value: rule.dailyLimitMinutes!,
                      step: 5,
                      min: 5,
                      max: 720,
                      format: (m) => formatDuration(Duration(minutes: m)),
                      semantic: 'Daily limit',
                      onStep: (d) => _save(
                        context,
                        (r) => _copy(
                          r,
                          limit: ((r.dailyLimitMinutes ?? 45) + d).clamp(
                            5,
                            720,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: Space.sm),
                    _DayPicker(
                      days: rule.limitDays,
                      onChanged: (d) =>
                          _save(context, (r) => _copy(r, days: d)),
                    ),
                  ],
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
                  _SwitchRow(
                    title: 'Mindful pause',
                    subtitle: rule.pauseSeconds == null
                        ? 'Breathe before opening'
                        : '${rule.pauseSeconds} s, a little longer each open',
                    value: rule.pauseSeconds != null,
                    onChanged: rule.blocked
                        ? null
                        : (on) => _save(
                            context,
                            (r) => _copy(r, pause: on ? 5 : null),
                          ),
                  ),
                  if (rule.pauseSeconds != null)
                    _MinuteStepper(
                      value: rule.pauseSeconds!,
                      step: 5,
                      min: 5,
                      max: 30,
                      format: (s) => '$s s',
                      semantic: 'Pause length',
                      onStep: (d) => _save(
                        context,
                        (r) => _copy(
                          r,
                          pause: ((r.pauseSeconds ?? 5) + d).clamp(5, 30),
                        ),
                      ),
                    ),
                  const Divider(),
                  _SwitchRow(
                    title: 'Open limit',
                    subtitle: rule.openLimit == null
                        ? 'Off'
                        : 'Max ${rule.openLimit} opens a day',
                    value: rule.openLimit != null,
                    onChanged: rule.blocked
                        ? null
                        : (on) => _save(
                            context,
                            (r) => _copy(r, opens: on ? 10 : null),
                          ),
                  ),
                  if (rule.openLimit != null)
                    _MinuteStepper(
                      value: rule.openLimit!,
                      step: 1,
                      min: 1,
                      max: 100,
                      format: (n) => '$n opens',
                      semantic: 'Open limit',
                      onStep: (d) => _save(
                        context,
                        (r) => _copy(
                          r,
                          opens: ((r.openLimit ?? 10) + d).clamp(1, 100),
                        ),
                      ),
                    ),
                ],
              ),
            ),
            const SizedBox(height: Space.xl),
            rule.blocked
                ? UButton(
                    label: 'Unblock',
                    expand: true,
                    onPressed: () =>
                        _save(context, (r) => _copy(r, blocked: false)),
                  )
                : UButton(
                    label: 'Block completely',
                    kind: UButtonKind.danger,
                    expand: true,
                    onPressed: () =>
                        _save(context, (r) => _copy(r, blocked: true)),
                  ),
            if (state.ruleFor(packageName) != null) ...[
              const SizedBox(height: Space.sm),
              UButton(
                label: 'Remove all rules for this app',
                kind: UButtonKind.ghost,
                expand: true,
                onPressed: () async {
                  final error = await state.edit(
                    (api) => api.removeRule(packageName),
                  );
                  if (!context.mounted) return;
                  if (error != null) {
                    ScaffoldMessenger.of(context)
                        .showSnackBar(SnackBar(content: Text(error)));
                  } else if (ModalRoute.of(context)!.isCurrent) {
                    // A second tap must not pop the screen underneath too.
                    Navigator.of(context).pop();
                  }
                },
              ),
            ],
          ],
        ),
      );
    },
  );
}

const _keep = Object();

class _SwitchRow extends StatelessWidget {
  const _SwitchRow({
    required this.title,
    required this.subtitle,
    required this.value,
    required this.onChanged,
  });

  final String title;
  final String subtitle;
  final bool value;
  final ValueChanged<bool>? onChanged;

  @override
  Widget build(BuildContext context) => SwitchListTile(
    contentPadding: EdgeInsets.zero,
    title: Text(title, style: context.text.subtitle),
    subtitle: Text(subtitle, style: context.text.bodySmall),
    value: value,
    onChanged: onChanged,
  );
}

class _MinuteStepper extends StatelessWidget {
  const _MinuteStepper({
    required this.value,
    required this.step,
    required this.min,
    required this.max,
    required this.format,
    required this.semantic,
    required this.onStep,
  });

  final int value;
  final int step;
  final int min;
  final int max;
  final String Function(int) format;
  final String semantic;

  /// Called with +step or -step; the caller applies it to the latest value.
  final ValueChanged<int> onStep;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: Space.sm),
    child: Row(
      children: [
        IconButton.filledTonal(
          tooltip: 'Less',
          onPressed: value - step >= min ? () => onStep(-step) : null,
          icon: const Icon(Icons.remove),
        ),
        Expanded(
          child: Semantics(
            label: semantic,
            value: format(value),
            child: Text(
              format(value),
              textAlign: TextAlign.center,
              style: context.text.subtitle,
            ),
          ),
        ),
        IconButton.filledTonal(
          tooltip: 'More',
          onPressed: value + step <= max ? () => onStep(step) : null,
          icon: const Icon(Icons.add),
        ),
      ],
    ),
  );
}

class _DayPicker extends StatelessWidget {
  const _DayPicker({required this.days, required this.onChanged});

  final List<int> days;
  final ValueChanged<List<int>> onChanged;

  @override
  Widget build(BuildContext context) => Wrap(
    spacing: Space.xs,
    runSpacing: Space.xs,
    children: [
      for (var d = 1; d <= 7; d++)
        FilterChip(
          label: Text(weekdayShort[d - 1]),
          selected: days.contains(d),
          showCheckmark: false,
          onSelected: (on) {
            final next = {...days};
            on ? next.add(d) : next.remove(d);
            if (next.isNotEmpty) onChanged(next.toList()..sort());
          },
        ),
    ],
  );
}

/// Pick one or more apps. Returns the chosen package names.
Future<Set<String>?> pickApps(
  BuildContext context,
  AppState state, {
  Set<String> initial = const {},
  String title = 'Choose apps',
}) {
  state.loadApps();
  return Navigator.of(context).push<Set<String>>(
    MaterialPageRoute(
      builder: (_) => _AppPicker(state: state, initial: initial, title: title),
    ),
  );
}

class _AppPicker extends StatefulWidget {
  const _AppPicker({
    required this.state,
    required this.initial,
    required this.title,
  });

  final AppState state;
  final Set<String> initial;
  final String title;

  @override
  State<_AppPicker> createState() => _AppPickerState();
}

class _AppPickerState extends State<_AppPicker> {
  late final Set<String> _picked = {...widget.initial};
  String _query = '';

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(widget.title)),
    body: Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(Space.lg, 0, Space.lg, Space.sm),
          child: TextField(
            decoration: const InputDecoration(
              prefixIcon: Icon(Icons.search),
              hintText: 'Search apps',
            ),
            onChanged: (q) => setState(() => _query = q.toLowerCase()),
          ),
        ),
        Expanded(
          child: ListenableBuilder(
            listenable: widget.state,
            builder: (context, _) {
              if (!widget.state.appsLoaded) {
                return const Center(
                  child: CircularProgressIndicator(
                    semanticsLabel: 'Loading apps',
                  ),
                );
              }
              final apps = widget.state.apps.values
                  .where((a) => a.label.toLowerCase().contains(_query))
                  .toList();
              return ListView.builder(
                itemCount: apps.length,
                itemBuilder: (context, i) {
                  final app = apps[i];
                  return CheckboxListTile(
                    value: _picked.contains(app.packageName),
                    onChanged: (_) => setState(
                      () => _picked.contains(app.packageName)
                          ? _picked.remove(app.packageName)
                          : _picked.add(app.packageName),
                    ),
                    secondary: AppIcon(label: app.label, icon: app.icon),
                    title: Text(app.label),
                  );
                },
              );
            },
          ),
        ),
        SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(Space.lg),
            child: PrimaryButton(
              label: 'Done (${_picked.length})',
              onPressed: () => Navigator.of(context).pop(_picked),
            ),
          ),
        ),
      ],
    ),
  );
}
