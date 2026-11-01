import 'package:flutter/material.dart';

import 'app_state.dart';
import 'design_system/components/buttons.dart';
import 'design_system/theme.dart';
import 'design_system/tokens.dart';
import 'engine/engine_api.g.dart';
import 'features/home/home_shell.dart';
import 'features/onboarding/onboarding_flow.dart';

void main() {
  runApp(UnscrollApp(api: EngineHostApi()));
}

class UnscrollApp extends StatefulWidget {
  const UnscrollApp({super.key, required this.api});

  final EngineHostApi api;

  @override
  State<UnscrollApp> createState() => _UnscrollAppState();
}

class _UnscrollAppState extends State<UnscrollApp> {
  late final AppState state = AppState(widget.api)..refresh();

  @override
  void dispose() {
    state.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Unscroll',
      debugShowCheckedModeBanner: false,
      theme: buildTheme(Brightness.light),
      darkTheme: buildTheme(Brightness.dark),
      themeMode: ThemeMode.system,
      home: ListenableBuilder(
        listenable: state,
        builder: (context, _) {
          final snapshot = state.snapshot;
          if (snapshot == null) {
            return Scaffold(
              body: Center(
                child: state.loadError == null
                    ? const CircularProgressIndicator(
                        semanticsLabel: 'Starting Unscroll',
                      )
                    : Padding(
                        padding: const EdgeInsets.all(Space.xxl),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const Text(
                              "Unscroll couldn't start. Your rules are safe on this phone.",
                              textAlign: TextAlign.center,
                            ),
                            const SizedBox(height: Space.lg),
                            UButton(
                              label: 'Try again',
                              onPressed: state.refresh,
                            ),
                          ],
                        ),
                      ),
              ),
            );
          }
          return snapshot.onboardingDone
              ? HomeShell(state: state)
              : OnboardingFlow(state: state);
        },
      ),
    );
  }
}
