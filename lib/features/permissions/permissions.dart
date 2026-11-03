import 'package:flutter/material.dart';

import '../../app_state.dart';
import '../../design_system/components/buttons.dart';
import '../../design_system/components/surfaces.dart';
import '../../design_system/theme.dart';
import '../../design_system/tokens.dart';
import '../../engine/engine_api.g.dart';

/// A permission Unscroll asks for, with its prominent-disclosure text.
class PermissionInfo {
  const PermissionInfo({
    required this.page,
    required this.title,
    required this.summary,
    required this.disclosure,
    required this.granted,
    this.required = true,
    this.needsConsent = true,
  });

  final SettingsPage page;
  final String title;
  final String summary;
  final List<DisclosureItem> disclosure;
  final bool Function(PermissionStatus) granted;
  final bool required;

  /// Sensitive access needs an explicit "I agree" before the system screen.
  final bool needsConsent;
}

final permissionList = <PermissionInfo>[
  PermissionInfo(
    page: SettingsPage.usageAccess,
    title: 'Usage access',
    summary: 'Shows your screen time and enforces daily limits.',
    granted: (p) => p.usageAccess,
    disclosure: const [
      DisclosureItem(
        Icons.info_outline,
        'What Unscroll reads',
        'Which apps you used and for how long, from Android\'s own usage history.',
      ),
      DisclosureItem(
        Icons.shield_outlined,
        'How it\'s used',
        'To show your screen time and to know when a daily limit is reached, on this phone.',
      ),
      DisclosureItem(
        Icons.wifi_off_rounded,
        'What we never do',
        'Send it anywhere. Unscroll has no internet permission.',
      ),
    ],
  ),
  PermissionInfo(
    page: SettingsPage.accessibility,
    title: 'Accessibility access',
    summary: 'Notices when you open an app or site you chose to limit, and shows the pause or block screen.',
    // Switched on but not running (crash, restricted settings) needs fixing too.
    granted: (p) => p.accessibility && p.accessibilityRunning,
    disclosure: const [
      DisclosureItem(
        Icons.info_outline,
        'What Unscroll checks',
        'The name of the open app or screen, the address in your browser\'s address bar, and, in short-video apps you choose, which screen is open (by its name, not its content).',
      ),
      DisclosureItem(
        Icons.shield_outlined,
        'How it\'s used',
        'It\'s compared with your own rules, on this phone, to show a pause or block screen or to close a short-video feed.',
      ),
      DisclosureItem(
        Icons.wifi_off_rounded,
        'What we never do',
        'Record what you type or read, store screen content, or send anything anywhere.',
      ),
    ],
  ),
  PermissionInfo(
    page: SettingsPage.notifications,
    title: 'Notifications',
    summary:
        'Warns you 5 and 1 minutes before a limit, and shows the focus timer.',
    granted: (p) => p.notifications,
    required: false,
    needsConsent: false,
    disclosure: const [
      DisclosureItem(
        Icons.notifications_none_rounded,
        'What you\'ll see',
        'A quiet "protecting your time" note, limit warnings and focus updates. No ads, ever.',
      ),
    ],
  ),
  PermissionInfo(
    page: SettingsPage.battery,
    title: 'Battery optimization off',
    summary: 'Stops your phone from switching Unscroll off in the background.',
    granted: (p) => p.batteryUnrestricted,
    required: false,
    needsConsent: false,
    disclosure: const [
      DisclosureItem(
        Icons.battery_saver_outlined,
        'Why',
        'Some phones stop background apps to save power, which would pause your limits. Unscroll uses very little battery.',
      ),
      DisclosureItem(
        Icons.info_outline,
        'How',
        'In the list, find Unscroll and choose "Don\'t optimize" or "Unrestricted". On Xiaomi, Samsung, Oppo or Vivo, also allow "Autostart" for Unscroll in the phone\'s own battery settings.',
      ),
    ],
  ),
  PermissionInfo(
    page: SettingsPage.overlay,
    title: 'Display over other apps',
    summary:
        'A backup way to show the block screen if accessibility is turned off.',
    granted: (p) => p.overlay,
    required: false,
    disclosure: const [
      DisclosureItem(
        Icons.layers_outlined,
        'What it does',
        'Lets Unscroll draw the pause and block screens on top of other apps.',
      ),
      DisclosureItem(
        Icons.shield_outlined,
        'When it\'s used',
        'Only for those screens, when the accessibility service isn\'t available.',
      ),
      DisclosureItem(
        Icons.wifi_off_rounded,
        'What we never do',
        'Cover permission dialogs or show anything you didn\'t set up.',
      ),
    ],
  ),
];

/// Lists every permission with its status; tapping one opens its disclosure.
class PermissionList extends StatelessWidget {
  const PermissionList({super.key, required this.state});

  final AppState state;

  @override
  Widget build(BuildContext context) {
    final status = state.permissions;
    return Column(
      children: [
        for (final info in permissionList)
          _PermissionRow(
            info: info,
            granted: status != null && info.granted(status),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) =>
                    PermissionDisclosureScreen(state: state, info: info),
              ),
            ),
          ),
      ],
    );
  }
}

class _PermissionRow extends StatelessWidget {
  const _PermissionRow({
    required this.info,
    required this.granted,
    required this.onTap,
  });

  final PermissionInfo info;
  final bool granted;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Padding(
      padding: const EdgeInsets.only(bottom: Space.sm),
      child: UCard(
        onTap: granted ? null : onTap,
        padding: const EdgeInsets.symmetric(
          horizontal: Space.lg,
          vertical: Space.md,
        ),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    info.required ? info.title : '${info.title} (recommended)',
                    style: context.text.subtitle,
                  ),
                  const SizedBox(height: Space.xxs),
                  Text(info.summary, style: context.text.bodySmall),
                ],
              ),
            ),
            const SizedBox(width: Space.md),
            granted
                ? Semantics(
                    label: 'Granted',
                    child: Icon(Icons.check_circle, color: c.good),
                  )
                : Icon(
                    Icons.chevron_right,
                    color: c.textMuted,
                    semanticLabel: 'Set up',
                  ),
          ],
        ),
      ),
    );
  }
}

/// Prominent disclosure shown alone, before sending the user to Android settings.
class PermissionDisclosureScreen extends StatefulWidget {
  const PermissionDisclosureScreen({
    super.key,
    required this.state,
    required this.info,
  });

  final AppState state;
  final PermissionInfo info;

  @override
  State<PermissionDisclosureScreen> createState() =>
      _PermissionDisclosureScreenState();
}

class _PermissionDisclosureScreenState extends State<PermissionDisclosureScreen>
    with WidgetsBindingObserver {
  bool _agreed = false;
  bool _sentToSettings = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState lifecycle) async {
    if (lifecycle != AppLifecycleState.resumed || !_sentToSettings) return;
    await widget.state.refreshPermissions();
    final status = widget.state.permissions;
    if (mounted && status != null && widget.info.granted(status)) {
      Navigator.of(context).pop();
    }
  }

  @override
  Widget build(BuildContext context) {
    final info = widget.info;
    final canContinue = _agreed || !info.needsConsent;
    return Scaffold(
      appBar: AppBar(),
      body: SafeArea(
        child: Column(
          children: [
            Expanded(
              child: ListView(
                padding: const EdgeInsets.fromLTRB(
                  Space.xl,
                  0,
                  Space.xl,
                  Space.lg,
                ),
                children: [
                  Text(info.title, style: context.text.headline),
                  const SizedBox(height: Space.sm),
                  Text(
                    info.summary,
                    style: context.text.body.copyWith(
                      color: context.colors.text2,
                    ),
                  ),
                  const SizedBox(height: Space.lg),
                  DisclosureCard(items: info.disclosure),
                  if (info.page == SettingsPage.accessibility) ...[
                    const SizedBox(height: Space.lg),
                    UBanner(
                      text: 'If the switch is greyed out ("Restricted setting"), open Unscroll\'s app info, tap ⋮ and choose "Allow restricted settings", then come back. Android does this for apps installed outside the Play Store.',
                      action: 'App info',
                      onAction: () => widget.state.api.openSettings(
                        SettingsPage.appDetails,
                      ),
                    ),
                  ],
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(
                Space.xl,
                0,
                Space.xl,
                Space.lg,
              ),
              child: Column(
                children: [
                  // Consent sits right above the action so it's always visible.
                  if (info.needsConsent)
                    CheckboxListTile(
                      value: _agreed,
                      onChanged: (v) => setState(() => _agreed = v ?? false),
                      controlAffinity: ListTileControlAffinity.leading,
                      contentPadding: EdgeInsets.zero,
                      title: Text(
                        'I understand and agree',
                        style: context.text.subtitle,
                      ),
                    ),
                  PrimaryButton(
                    label: 'Open settings',
                    onPressed: canContinue
                        ? () {
                            _sentToSettings = true;
                            widget.state.api.openSettings(info.page);
                          }
                        : null,
                  ),
                  const SizedBox(height: Space.sm),
                  UButton(
                    label: 'Not now',
                    kind: UButtonKind.ghost,
                    expand: true,
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                  Text(
                    'You can turn this off anytime in Android settings.',
                    style: context.text.bodySmall,
                    textAlign: TextAlign.center,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
