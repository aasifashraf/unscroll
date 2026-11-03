# Privacy Policy

_Last updated: 2026-11-02_

**Summary: Unscroll collects no data. Everything stays on your device, and the app cannot connect to the internet.**

## 1. What we collect
Nothing. Unscroll has no servers, no accounts, no analytics, no crash reporting, no advertising and no third-party tracking SDKs. The app does not request Android's `INTERNET` permission, so it is technically unable to send data anywhere. Every build is checked automatically to make sure this stays true.

## 2. Data stored on your device
To work, Unscroll stores the following **only on your device**, in the app's private storage:
- Your rules and settings: blocked apps and websites, limits, app groups, schedules, focus sessions, your daily goal
- Your focus history
- How many times you chose not to open an app on the pause screen (today and in total), and when a session limit's 10-minute break ends. The reason you pick on the pause screen ("What are you here for?") is never stored.
- A record of when your current day started, so changing the phone's clock can't reset your limits

Screen time is **read** from Android's own usage history each time it's shown or checked; Unscroll doesn't keep its own copy of it.

This data is removed when you uninstall the app or clear its data. It is excluded from Android cloud backups and device-to-device transfer. A feature to export your data to a file is planned; when it arrives, the file will stay wherever you choose to save it.

## 3. Permissions and why we need them
| Permission | Why |
|---|---|
| Usage access (`PACKAGE_USAGE_STATS`) | Shows your screen time and enforces time limits |
| Accessibility service | Detects which app is open, and the address in supported browsers' address bars, so it can show the pause or block screen for apps and websites you chose. In short-video apps where you turned on short-video blocking (Instagram, YouTube, Facebook, Snapchat), it also checks which screen is open, by the names, types and sizes of its views (never their text or content), so it can close the endless feed with a Back action. **Unscroll does not record, store or share what you type or the content of your screens.** It only checks app and screen names and browser addresses against your own rules, on the device. It keeps only when a feed was last closed and whether detection may be outdated for an app version. |
| Display over other apps (optional) | A backup way to show the pause and block screens when the accessibility service isn't running |
| Notifications, foreground service | Keeps blocking active in the background, warns before a limit runs out and shows timers |
| Run at startup | Restores blocking after the phone restarts |
| Ignore battery optimization (you choose, in Android settings) | Stops the phone from switching blocking off to save power |

Planned, not in the app yet: an optional device administrator for strict mode, to prevent uninstalling while strict mode is on. It will be explained in the app before it's ever requested.

Each sensitive permission is explained in the app before you grant it, and you can revoke it at any time in Android settings.

## 4. Children
Unscroll is meant for people aged 13 and older to manage their own phone use. It is not a parental-control app and is not directed at children.

## 5. Changes
Changes to this policy will be published in this file in the public source repository, with a new date.

## 6. Contact
Open an issue at https://github.com/aasifashraf/unscroll/issues. For security matters, see [SECURITY.md](SECURITY.md).
