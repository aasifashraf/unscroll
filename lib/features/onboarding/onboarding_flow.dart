import 'package:flutter/material.dart';

import '../../app_state.dart';
import '../../design_system/components/app_tile.dart';
import '../../design_system/components/buttons.dart';
import '../../design_system/theme.dart';
import '../../design_system/tokens.dart';
import '../../engine/engine_api.g.dart';
import '../../util/format.dart';
import '../permissions/permissions.dart';

/// First run: welcome → privacy → daily goal → pick apps → permissions.
class OnboardingFlow extends StatefulWidget {
  const OnboardingFlow({super.key, required this.state});

  final AppState state;

  @override
  State<OnboardingFlow> createState() => _OnboardingFlowState();
}

class _OnboardingFlowState extends State<OnboardingFlow>
    with WidgetsBindingObserver {
  static const _steps = 5;
  int _step = 0;
  int _goalMinutes = 180;
  final Set<String> _picked = {};
  bool _finishing = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _goalMinutes = widget.state.snapshot?.dailyGoalMinutes ?? 180;
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState lifecycle) {
    if (lifecycle == AppLifecycleState.resumed) {
      widget.state.refreshPermissions();
    }
  }

  void _next() {
    if (_step == 2) widget.state.loadApps();
    setState(() => _step++);
  }

  Future<void> _finish() async {
    setState(() => _finishing = true);
    final state = widget.state;
    final error = await state.edit((api) async {
      await api.setDailyGoal(_goalMinutes);
      for (final pkg in _picked) {
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
      // Last, so a failure above leaves onboarding open to try again.
      await api.setOnboardingDone(true);
    });
    if (error != null) {
      if (!mounted) return;
      setState(() => _finishing = false);
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(error)));
      return;
    }
    await state.refresh();
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: _step == 0,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && _step > 0) setState(() => _step--);
      },
      child: Scaffold(
        body: SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(
              Space.xl,
              Space.lg,
              Space.xl,
              Space.lg,
            ),
            child: Column(
              children: [
                _Progress(step: _step, of: _steps),
                const SizedBox(height: Space.xl),
                Expanded(child: _page(context)),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _page(BuildContext context) => switch (_step) {
    0 => _Page(
      body: [
        Icon(
          Icons.radio_button_unchecked,
          size: 72,
          color: context.colors.accentText,
        ),
        const SizedBox(height: Space.xl),
        Text(
          'Take your time back.',
          style: context.text.display,
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: Space.lg),
        Text(
          'Unscroll helps you use your phone on purpose. Gently, privately, and for free.',
          style: context.text.body.copyWith(color: context.colors.text2),
          textAlign: TextAlign.center,
        ),
      ],
      centered: true,
      button: PrimaryButton(label: 'Get started', onPressed: _next),
    ),
    1 => _Page(
      body: [
        Text('Nothing leaves your phone', style: context.text.headline),
        const SizedBox(height: Space.xl),
        const _Promise(
          'No internet permission',
          'Android itself stops Unscroll from going online.',
        ),
        const _Promise(
          'No account, ads or tracking',
          'Your habits are nobody\'s business.',
        ),
        const _Promise(
          'Open source',
          'Anyone can read the code and check these promises.',
        ),
      ],
      button: PrimaryButton(label: 'Continue', onPressed: _next),
    ),
    2 => _Page(
      body: [
        Text('Your daily goal', style: context.text.headline),
        const SizedBox(height: Space.sm),
        Text(
          'How much screen time feels right? You can change it later.',
          style: context.text.body.copyWith(color: context.colors.text2),
        ),
        const SizedBox(height: Space.xxl),
        _GoalStepper(
          minutes: _goalMinutes,
          onChanged: (m) => setState(() => _goalMinutes = m),
        ),
      ],
      button: PrimaryButton(label: 'Continue', onPressed: _next),
    ),
    3 => _PickApps(
      state: widget.state,
      picked: _picked,
      onToggle: (pkg) => setState(
        () => _picked.contains(pkg) ? _picked.remove(pkg) : _picked.add(pkg),
      ),
      onNext: _next,
    ),
    _ => _Page(
      body: [
        Text('Turn on protection', style: context.text.headline),
        const SizedBox(height: Space.sm),
        Text(
          'Each one is explained before Android asks. You can skip and do it later in Settings.',
          style: context.text.body.copyWith(color: context.colors.text2),
        ),
        const SizedBox(height: Space.lg),
        ListenableBuilder(
          listenable: widget.state,
          builder: (context, _) => PermissionList(state: widget.state),
        ),
      ],
      button: PrimaryButton(
        label: _finishing ? 'Setting up…' : 'Finish',
        onPressed: _finishing ? null : _finish,
      ),
    ),
  };
}

class _Progress extends StatelessWidget {
  const _Progress({required this.step, required this.of});

  final int step;
  final int of;

  @override
  Widget build(BuildContext context) => Semantics(
    label: 'Step ${step + 1} of $of',
    child: Row(
      children: [
        for (var i = 0; i < of; i++)
          Expanded(
            child: Container(
              height: 4,
              margin: const EdgeInsets.symmetric(horizontal: 2),
              decoration: BoxDecoration(
                color: i <= step
                    ? context.colors.violet
                    : context.colors.surfaceSunken,
                borderRadius: BorderRadius.circular(Radii.pill),
              ),
            ),
          ),
      ],
    ),
  );
}

class _Page extends StatelessWidget {
  const _Page({
    required this.body,
    required this.button,
    this.centered = false,
  });

  final List<Widget> body;
  final Widget button;
  final bool centered;

  @override
  Widget build(BuildContext context) => Column(
    children: [
      Expanded(
        child: ListView(
          children: [
            if (centered) const SizedBox(height: Space.huge),
            Column(
              crossAxisAlignment: centered
                  ? CrossAxisAlignment.center
                  : CrossAxisAlignment.start,
              children: body,
            ),
          ],
        ),
      ),
      const SizedBox(height: Space.lg),
      button,
    ],
  );
}

class _Promise extends StatelessWidget {
  const _Promise(this.title, this.body);

  final String title;
  final String body;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: Space.lg),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(Icons.check_circle, color: context.colors.teal),
        const SizedBox(width: Space.md),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: context.text.subtitle),
              Text(
                body,
                style: context.text.bodySmall.copyWith(
                  color: context.colors.text2,
                ),
              ),
            ],
          ),
        ),
      ],
    ),
  );
}

class _GoalStepper extends StatelessWidget {
  const _GoalStepper({required this.minutes, required this.onChanged});

  final int minutes;
  final ValueChanged<int> onChanged;

  @override
  Widget build(BuildContext context) => Row(
    mainAxisAlignment: MainAxisAlignment.center,
    children: [
      IconButton.filledTonal(
        tooltip: 'Less',
        onPressed: minutes > 30 ? () => onChanged(minutes - 30) : null,
        icon: const Icon(Icons.remove),
      ),
      SizedBox(
        width: 140,
        child: Text(
          formatDuration(Duration(minutes: minutes)),
          textAlign: TextAlign.center,
          style: context.text.headline,
        ),
      ),
      IconButton.filledTonal(
        tooltip: 'More',
        onPressed: minutes < 720 ? () => onChanged(minutes + 30) : null,
        icon: const Icon(Icons.add),
      ),
    ],
  );
}

class _PickApps extends StatelessWidget {
  const _PickApps({
    required this.state,
    required this.picked,
    required this.onToggle,
    required this.onNext,
  });

  final AppState state;
  final Set<String> picked;
  final ValueChanged<String> onToggle;
  final VoidCallback onNext;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text('Which apps pull you in?', style: context.text.headline),
      const SizedBox(height: Space.sm),
      Text(
        'They\'ll get a short pause before opening, a moment to decide. You can add limits later.',
        style: context.text.body.copyWith(color: context.colors.text2),
      ),
      const SizedBox(height: Space.md),
      Expanded(
        child: ListenableBuilder(
          listenable: state,
          builder: (context, _) {
            if (!state.appsLoaded) {
              return const Center(
                child: CircularProgressIndicator(
                  semanticsLabel: 'Loading apps',
                ),
              );
            }
            final apps = state.apps.values.toList();
            return ListView.builder(
              itemCount: apps.length,
              itemBuilder: (context, i) {
                final app = apps[i];
                return CheckboxListTile(
                  value: picked.contains(app.packageName),
                  onChanged: (_) => onToggle(app.packageName),
                  secondary: AppIcon(label: app.label, icon: app.icon),
                  title: Text(app.label, style: context.text.subtitle),
                  contentPadding: EdgeInsets.zero,
                );
              },
            );
          },
        ),
      ),
      const SizedBox(height: Space.md),
      PrimaryButton(
        label: picked.isEmpty ? 'Skip for now' : 'Continue (${picked.length})',
        onPressed: onNext,
      ),
    ],
  );
}
