# Privacy Policy

_Last updated: 2026-10-24_

**Summary: Unscroll collects no data. Everything stays on your device, and the app cannot connect to the internet.**

## 1. What we collect
Nothing. Unscroll has no servers, no accounts, no analytics, no crash reporting, no advertising and no third-party tracking SDKs. The app does not request Android's `INTERNET` permission, so it is technically unable to send data anywhere. Every build is checked automatically to make sure this stays true.

## 2. Data stored on your device
To work, Unscroll stores the following **only on your device**, in the app's private storage:
- App usage statistics (which apps you used and for how long)
- Your rules: blocked apps, limits, schedules, focus sessions, strict-mode settings
- Your progress: streaks, achievements, focus history

This data is removed when you uninstall the app or clear its data. It is excluded from Android cloud backups. You can export it to a file yourself, and that file stays wherever you choose to save it.

## 3. Permissions and why we need them
| Permission | Why |
|---|---|
| Usage access (`PACKAGE_USAGE_STATS`) | Shows your screen time and enforces time limits |
| Accessibility service | Detects which app or screen is open so it can block apps, short-video feeds and websites you chose. **Unscroll does not record, store or share what you type or the content of your screens.** It only checks app names, specific screen identifiers and browser addresses against your own block rules, on the device. |
| Display over other apps | Shows the pause screen and block screen |
| Notifications / foreground service | Keeps blocking active and shows timers |
| Device administrator (optional, strict mode only) | Prevents uninstalling during strict mode, if you turn this on |
| Run at startup | Restores your rules after the phone restarts |

Each sensitive permission is explained in the app before you grant it, and you can revoke it at any time in Android settings.

## 4. Children
Unscroll is meant for people aged 13 and older to manage their own phone use. It is not a parental-control app and is not directed at children.

## 5. Changes
Changes to this policy will be published in this file in the public source repository, with a new date.

## 6. Contact
Open an issue at https://github.com/aasifashraf/unscroll/issues. For security matters, see [SECURITY.md](SECURITY.md).
