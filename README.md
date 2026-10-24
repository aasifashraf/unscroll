# Unscroll

**Take your time back. Free, open source, and 100% offline.**

Unscroll is an Android app that helps you control your phone use. It shows where your time goes, adds a mindful pause before distracting apps, blocks short-video feeds, runs focus sessions, and can lock you out on a schedule. You don't need an account, there's no subscription, and **the app has no internet permission at all**, so your data physically cannot leave your phone.

> ⚠️ **Status:** pre-alpha, in design. Not ready for use yet. See [todo.md](todo.md).

## Why Unscroll
- **Private by construction.** No `INTERNET` permission, no analytics, no ads, no trackers. Our CI fails any build that adds network access.
- **Everything in one app.** Insights, limits, mindful pauses, short-form feed blocking, website blocking, focus sessions, schedules and strict mode.
- **Free forever.** GPL-3.0 licensed. No paywalls and no "premium" tier.

## Features (planned)
| | |
|---|---|
| 📊 **Insights** | Screen time, unlocks, per-app charts, weekly trends, focus score |
| ⏱️ **Limits** | Daily time and open-count limits per app or group |
| 🌬️ **Mindful pause** | A breathing pause before a distracting app opens |
| 🎬 **Short-form blocking** | Blocks short-video feeds while the rest of the app keeps working |
| 🌐 **Website blocking** | Blocks distracting or adult sites |
| 🌱 **Focus sessions** | A plant grows while you focus and wilts if you leave |
| 🗓️ **Schedules** | Work, study and bedtime modes |
| 🔒 **Strict mode** | Uninstall protection, with a safe emergency exit |

See [docs/FEATURES.md](docs/FEATURES.md) for the full specification.

## Building from source
Requirements: Flutter (version pinned in [`.github/workflows/ci.yml`](.github/workflows/ci.yml)), JDK 17, Android SDK.
```bash
flutter pub get
flutter run
```

## Contributing
Contributions are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) first: every commit needs a DCO `Signed-off-by` line. Report security problems privately as described in [SECURITY.md](SECURITY.md).

## Privacy
See [PRIVACY.md](PRIVACY.md). Short version: we collect nothing, because the app cannot connect to the internet.

## Disclaimer
Unscroll is a self-help productivity tool. It is **not** a medical, psychological or therapeutic device, and it does not diagnose or treat any condition. Results aren't guaranteed. If you're struggling with compulsive phone use or your mental health, please talk to a qualified professional. The software is provided "as is", without warranty of any kind; see the [LICENSE](LICENSE).

Unscroll is an independent project and is not affiliated with or endorsed by any other screen-time or productivity app or company. All trademarks belong to their respective owners.

## License
[GNU General Public License v3.0 or later](LICENSE) © Unscroll contributors.
