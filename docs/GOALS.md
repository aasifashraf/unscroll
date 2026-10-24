# Goals & Roadmap

## Vision
One free, open-source, fully offline Android app that brings together everything people use the best screen-time apps for. It never asks you to trust a server, because there isn't one.

## Goals (in priority order)
1. **Privacy by construction**: no `INTERNET` permission, no analytics, ads or trackers, enforced by CI.
2. **Reliable enforcement**: blocks hold through reboots, OEM battery killers, manual clock or time-zone changes, and app updates.
3. **Complete feature set**: every capability in [FEATURES.md](FEATURES.md), with nothing behind a paywall.
4. **Humane, not punishing**: friction and reflection come first, hard blocks are opt-in, and strict mode always has a safe emergency exit.
5. **Legally clean**: original code, design and assets; GPL-3.0-compatible dependencies; store-policy compliant; clear disclaimers; DCO on every contribution.
6. **Production quality**: CI on every PR, signed reproducible releases, a security and edge-case review on every change.

## Non-goals
- Cloud sync, accounts or any server component
- iOS (Apple only allows blocking through the entitled Screen Time API; revisit after v1.0)
- Parental control or monitoring other people
- Monetization

## Success criteria for v1.0
- Available on GitHub Releases, F-Droid and Google Play
- 0 network permissions in every build
- All features in FEATURES.md marked v0.1–v0.3 shipped, with tests
- Enforcement survives the manual test matrix (see ARCHITECTURE.md → Test matrix)
- Trademark check and legal review done

## Roadmap
| Version | Scope |
|---|---|
| Phase 0 | Repo foundation: docs, CI/CD, legal and community files |
| Phase 1 | Design system + all screen designs, approved by the owner |
| v0.1 | Onboarding + disclosures, insights, app blocking, daily limits, mindful pause, focus timer |
| v0.2 | Schedules, short-form blocking, website blocking, growing plant, streaks, open-count limits |
| v0.3 | Strict mode + emergency unlock, encrypted backup, widget, QS tile, localization → F-Droid |
| v1.0 | Trademark + legal review → Play Store |

Detailed tasks: [todo.md](../todo.md).

## Key decisions
| Decision | Choice | Why |
|---|---|---|
| Platform | Android first | Only Android allows full offline blocking (Accessibility, overlays, Device Admin) |
| Stack | Flutter UI + Kotlin engine (Pigeon) | Fast UI work; the engine needs native APIs and must run without the UI |
| Package | `io.github.aasifashraf.unscroll` | No domain to own; matches the GitHub namespace |
| Builds | One build, no VPN | A DNS-filter VPN would need INTERNET; Accessibility URL-bar blocking keeps the zero-network promise and Play compliance |
| Strictness | Strong but escapable | Protects users and avoids store-policy and liability problems |
| License | GPL-3.0-or-later | Keeps every fork open source |
