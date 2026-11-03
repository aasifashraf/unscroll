import 'package:flutter/material.dart';

import '../../app_state.dart';
import '../../design_system/components/buttons.dart';
import '../../design_system/components/surfaces.dart';
import '../../design_system/theme.dart';
import '../../design_system/tokens.dart';
import '../../engine/engine_api.g.dart';
import '../../util/format.dart';
import '../apps/app_detail_screen.dart';

Future<void> openGroupEditor(
  BuildContext context,
  AppState state, [
  GroupMsg? group,
]) => Navigator.of(context).push(
  MaterialPageRoute<void>(
    builder: (_) => GroupEditorScreen(state: state, initial: group),
  ),
);

/// Several apps that share one daily limit. The engine adds up their time and enforces it.
class GroupEditorScreen extends StatefulWidget {
  const GroupEditorScreen({super.key, required this.state, this.initial});

  final AppState state;
  final GroupMsg? initial;

  @override
  State<GroupEditorScreen> createState() => _GroupEditorScreenState();
}

class _GroupEditorScreenState extends State<GroupEditorScreen> {
  static const _allDays = [1, 2, 3, 4, 5, 6, 7];
  static const _minLimit = 5;
  static const _maxLimit = 720;

  /// Fixed for this editor, so saving twice updates one group instead of making two.
  late final String _id =
      widget.initial?.id ?? 'g${DateTime.now().microsecondsSinceEpoch}';
  bool _saving = false;
  late final _name = TextEditingController(text: widget.initial?.name ?? '');
  late List<int> _days = widget.initial?.limitDays ?? _allDays;
  late int _limit = widget.initial?.dailyLimitMinutes ?? 60;
  late Set<String> _apps = widget.initial?.packages.toSet() ?? <String>{};

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_saving) return; // a second tap before the rebuild
    setState(() => _saving = true);
    final error = await widget.state.edit(
      (api) => api.setGroup(
        GroupMsg(
          id: _id,
          name: _name.text.trim(),
          packages: _apps.toList()..sort(),
          dailyLimitMinutes: _limit,
          limitDays: [..._days]..sort(),
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

  Future<void> _delete() async {
    if (_saving) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        content: const Text(
          'Delete this group? Its shared limit stops applying.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted || _saving) return;
    setState(() => _saving = true);
    final error = await widget.state.edit(
      (api) => api.removeGroup(widget.initial!.id),
    );
    if (!mounted) return;
    if (error == null) {
      Navigator.of(context).pop();
    } else {
      setState(() => _saving = false);
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(error)));
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: Text(widget.initial == null ? 'New group' : 'Edit group'),
      actions: [
        if (widget.initial != null)
          IconButton(
            tooltip: 'Delete group',
            icon: const Icon(Icons.delete_outline),
            onPressed: _saving ? null : _delete,
          ),
      ],
    ),
    body: ListView(
      padding: const EdgeInsets.fromLTRB(Space.lg, 0, Space.lg, Space.xxl),
      children: [
        TextField(
          controller: _name,
          maxLength: 40,
          textCapitalization: TextCapitalization.sentences,
          decoration: const InputDecoration(
            labelText: 'Name',
            hintText: 'For example, Social or Games',
          ),
        ),
        const SizedBox(height: Space.md),
        SectionTitle(
          'Apps (${_apps.length})',
          trailing: TextButton(
            onPressed: () async {
              final picked = await pickApps(
                context,
                widget.state,
                initial: _apps,
                title: 'Apps in this group',
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
        const SizedBox(height: Space.xl),
        const SectionTitle('Shared daily limit'),
        MinuteStepper(
          value: _limit,
          step: 5,
          min: _minLimit,
          max: _maxLimit,
          format: (m) => formatDuration(Duration(minutes: m)),
          semantic: 'Shared daily limit',
          onStep: (d) =>
              setState(() => _limit = (_limit + d).clamp(_minLimit, _maxLimit)),
        ),
        Text(
          'Time in all these apps adds up. When it reaches the limit, they all rest until tomorrow.',
          style: context.text.bodySmall,
        ),
        const SizedBox(height: Space.xl),
        const SectionTitle('Days'),
        DayPicker(days: _days, onChanged: (d) => setState(() => _days = d)),
        const SizedBox(height: Space.xxl),
        ListenableBuilder(
          listenable: _name,
          builder: (context, _) => PrimaryButton(
            label: 'Save',
            onPressed: _apps.isEmpty || _name.text.trim().isEmpty || _saving
                ? null
                : _save,
          ),
        ),
      ],
    ),
  );
}
