import 'package:flutter/material.dart';

import '../../app_state.dart';
import '../../design_system/components/buttons.dart';
import '../../design_system/components/surfaces.dart';
import '../../design_system/theme.dart';
import '../../design_system/tokens.dart';
import '../../engine/engine_api.g.dart';
import '../../util/format.dart';
import '../apps/app_detail_screen.dart';

class SchedulesScreen extends StatelessWidget {
  const SchedulesScreen({super.key, required this.state});

  final AppState state;

  void _edit(BuildContext context, ScheduleMsg? schedule) =>
      Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => ScheduleEditor(state: state, initial: schedule),
        ),
      );

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Schedules')),
    floatingActionButton: FloatingActionButton(
      tooltip: 'New schedule',
      onPressed: () => _edit(context, null),
      child: const Icon(Icons.add),
    ),
    body: ListenableBuilder(
      listenable: state,
      builder: (context, _) {
        final schedules = state.snapshot?.schedules ?? const [];
        if (schedules.isEmpty) {
          return Padding(
            padding: const EdgeInsets.all(Space.lg),
            child: EmptyState(
              icon: Icons.calendar_month_outlined,
              title: 'No schedules yet',
              body: 'Block apps automatically during work, study or bedtime.',
              action: UButton(
                label: 'New schedule',
                icon: Icons.add,
                onPressed: () => _edit(context, null),
              ),
            ),
          );
        }
        return ListView(
          padding: const EdgeInsets.fromLTRB(Space.lg, 0, Space.lg, 96),
          children: [
            for (final s in schedules)
              Padding(
                padding: const EdgeInsets.only(bottom: Space.sm),
                child: UCard(
                  onTap: () => _edit(context, s),
                  child: Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              s.name.isEmpty ? 'Schedule' : s.name,
                              style: context.text.subtitle,
                            ),
                            Text(
                              '${_days(s.days)} · ${formatMinuteOfDay(s.startMinute)}–${formatMinuteOfDay(s.endMinute)}',
                              style: context.text.bodySmall,
                            ),
                            Text(
                              '${s.packages.length} apps',
                              style: context.text.bodySmall,
                            ),
                          ],
                        ),
                      ),
                      Semantics(
                        // Read out as e.g. "Work schedule, switch, on".
                        label:
                            '${s.name.isEmpty ? 'Schedule' : s.name} schedule',
                        child: Switch(
                          value: s.enabled,
                          onChanged: (on) => state.edit((api) async {
                            // The latest version, in case it was edited meanwhile.
                            final latest = state.snapshot?.schedules
                                .where((x) => x.id == s.id)
                                .firstOrNull;
                            if (latest == null) return;
                            await api.setSchedule(
                              ScheduleMsg(
                                id: latest.id,
                                name: latest.name,
                                enabled: on,
                                days: latest.days,
                                startMinute: latest.startMinute,
                                endMinute: latest.endMinute,
                                packages: latest.packages,
                              ),
                            );
                          }),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
          ],
        );
      },
    ),
  );

  static String _days(List<int> days) {
    if (days.length == 7) return 'Every day';
    if (days.toSet().containsAll([1, 2, 3, 4, 5]) && days.length == 5) {
      return 'Mon–Fri';
    }
    if (days.toSet().containsAll([6, 7]) && days.length == 2) return 'Weekends';
    return days.map((d) => weekdayShort[d - 1]).join(', ');
  }
}

class ScheduleEditor extends StatefulWidget {
  const ScheduleEditor({super.key, required this.state, this.initial});

  final AppState state;
  final ScheduleMsg? initial;

  @override
  State<ScheduleEditor> createState() => _ScheduleEditorState();
}

class _ScheduleEditorState extends State<ScheduleEditor> {
  /// Fixed for this editor, so saving twice updates one schedule instead of making two.
  late final String _id =
      widget.initial?.id ?? DateTime.now().microsecondsSinceEpoch.toString();
  bool _saving = false;
  late final _name = TextEditingController(
    text: widget.initial?.name ?? 'Work',
  );
  late Set<int> _days = widget.initial?.days.toSet() ?? {1, 2, 3, 4, 5};
  late int _start = widget.initial?.startMinute ?? 9 * 60;
  late int _end = widget.initial?.endMinute ?? 17 * 60;
  late Set<String> _apps =
      widget.initial?.packages.toSet() ??
      (widget.state.snapshot?.rules.map((r) => r.packageName).toSet() ??
          <String>{});

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  Future<void> _pickTime(bool start) async {
    final minute = start ? _start : _end;
    final picked = await showTimePicker(
      context: context,
      initialTime: TimeOfDay(hour: minute ~/ 60, minute: minute % 60),
    );
    if (picked == null) return;
    setState(
      () => start
          ? _start = picked.hour * 60 + picked.minute
          : _end = picked.hour * 60 + picked.minute,
    );
  }

  Future<void> _save() async {
    if (_start == _end) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Start and end can\'t be the same time.')),
      );
      return;
    }
    setState(() => _saving = true);
    final error = await widget.state.edit(
      (api) => api.setSchedule(
        ScheduleMsg(
          id: _id,
          name: _name.text.trim(),
          enabled: widget.initial?.enabled ?? true,
          days: _days.toList()..sort(),
          startMinute: _start,
          endMinute: _end,
          packages: _apps.toList(),
        ),
      ),
    );
    if (!mounted) return;
    if (error != null) {
      setState(() => _saving = false);
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(error)));
    } else {
      Navigator.of(context).pop();
    }
  }

  @override
  Widget build(BuildContext context) {
    final overnight = _end <= _start;
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.initial == null ? 'New schedule' : 'Edit schedule'),
        actions: [
          if (widget.initial != null)
            IconButton(
              tooltip: 'Delete schedule',
              icon: const Icon(Icons.delete_outline),
              onPressed: _saving
                  ? null
                  : () async {
                      setState(() => _saving = true);
                      final error = await widget.state.edit(
                        (api) => api.removeSchedule(widget.initial!.id),
                      );
                      if (!context.mounted) return;
                      if (error == null) {
                        Navigator.of(context).pop();
                      } else {
                        setState(() => _saving = false);
                        ScaffoldMessenger.of(context)
                            .showSnackBar(SnackBar(content: Text(error)));
                      }
                    },
            ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Space.lg, 0, Space.lg, Space.xxl),
        children: [
          TextField(
            controller: _name,
            maxLength: 40,
            decoration: const InputDecoration(labelText: 'Name'),
          ),
          const SizedBox(height: Space.md),
          const SectionTitle('Days'),
          Wrap(
            spacing: Space.xs,
            runSpacing: Space.xs,
            children: [
              for (var d = 1; d <= 7; d++)
                FilterChip(
                  label: Text(weekdayShort[d - 1]),
                  selected: _days.contains(d),
                  showCheckmark: false,
                  onSelected: (on) => setState(() {
                    final next = {..._days};
                    on ? next.add(d) : next.remove(d);
                    if (next.isNotEmpty) _days = next;
                  }),
                ),
            ],
          ),
          const SizedBox(height: Space.xl),
          const SectionTitle('Time'),
          Row(
            children: [
              Expanded(
                child: UButton(
                  label: 'From ${formatMinuteOfDay(_start)}',
                  onPressed: () => _pickTime(true),
                ),
              ),
              const SizedBox(width: Space.sm),
              Expanded(
                child: UButton(
                  label: 'To ${formatMinuteOfDay(_end)}',
                  onPressed: () => _pickTime(false),
                ),
              ),
            ],
          ),
          if (overnight) ...[
            const SizedBox(height: Space.sm),
            Text(
              'Runs overnight, ending the next morning.',
              style: context.text.bodySmall,
            ),
          ],
          const SizedBox(height: Space.xl),
          SectionTitle(
            'Apps (${_apps.length})',
            trailing: TextButton(
              onPressed: () async {
                final picked = await pickApps(
                  context,
                  widget.state,
                  initial: _apps,
                  title: 'Apps to block',
                );
                if (picked != null) setState(() => _apps = picked);
              },
              child: const Text('Choose'),
            ),
          ),
          Text(
            _apps.isEmpty
                ? 'No apps chosen yet.'
                : _apps.map(widget.state.labelOf).join(', '),
            style: context.text.bodySmall,
          ),
          const SizedBox(height: Space.xxl),
          PrimaryButton(
            label: 'Save',
            onPressed: _apps.isEmpty || _saving ? null : _save,
          ),
        ],
      ),
    );
  }
}
