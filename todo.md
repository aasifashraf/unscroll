# Unscroll — TODO

Legend: `[x]` done · `[ ]` open · ⚠️ = gate (must pass before moving on)
Update this file at the end of every task.

## Phase 0 — Repository foundation
- [x] License (GPL-3.0), NOTICE, README with disclaimer
- [x] PRIVACY.md, SECURITY.md, CONTRIBUTING.md (DCO), CODE_OF_CONDUCT.md
- [x] .gitignore (secrets, build output), .gitattributes (LF)
- [x] docs/GOALS.md, docs/FEATURES.md, docs/ARCHITECTURE.md
- [x] CI/CD workflows: ci, codeql, dependency-review, release, dependabot
- [x] PR/issue templates, CODEOWNERS
- [ ] **Owner (your separate git plan):** create GitHub repo `aasifashraf/unscroll`, push, then enable: branch protection on `main` (required checks + review), private vulnerability reporting, Dependabot alerts, secret scanning + push protection
- [ ] Generate the release keystore **offline**, back it up in 2 safe places, and add it to GitHub Secrets (`RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`)

## Phase 1 — Design ⚠️ needs owner approval before Phase 2
- [x] Design system + all screens: `design/unscroll-design.html`
- [x] Design tokens: `design/tokens.json`, docs/DESIGN_SYSTEM.md
- [ ] Owner review → revisions → **approved**
- [ ] Original app icon + logo (our own artwork, no stock assets with unclear licenses)

## Phase 2 — v0.1 MVP
- [ ] Flutter scaffold: `flutter create --org io.github.aasifashraf --project-name unscroll --platforms android .`;  minSdk 26; release signing falls back to debug keys when no keystore exists (CI checks); `analysis_options.yaml`
- [ ] Verify CI goes green; verify a PR that adds INTERNET **fails** CI
- [ ] Un-comment the `pub` and `gradle` entries in `.github/dependabot.yml`
- [ ] Pigeon setup (`pigeons/engine_api.dart`) + codegen
- [ ] Kotlin engine skeleton: Room DB, foreground service, boot receiver, day-boundary/clock helper (+ tamper tests)
- [ ] `lib/design_system/` from tokens + debug component gallery
- [ ] Onboarding + separate prominent disclosures (Usage, Accessibility, Overlay, Notifications) + battery-optimization guide per OEM
- [ ] Insights: today dashboard, per-app list, week chart (UsageStatsManager)
- [ ] App blocking: block list + block overlay
- [ ] Daily limits per app/group + warnings at 5/1 min
- [ ] Mindful pause overlay (breathing, configurable wait, "why?" prompt)
- [ ] Focus session timer (blocks the chosen apps)
- [ ] About screen: disclaimer, licenses (`showLicensePage`), privacy
- [ ] Security & privacy review clean → tag `v0.1.0` → GitHub Release

## Phase 3 — v0.2
- [ ] Schedules (work/study/bedtime/custom, per weekday)
- [ ] Short-form blocking (Reels/Shorts/Spotlight/etc.) with per-app view-ID maps + a fallback heuristic
- [ ] Website blocking: Accessibility URL-bar check in supported browsers + keyword blocking
- [ ] Growing plant focus visuals, session history
- [ ] Streaks, achievements, daily goal
- [ ] Open-count limits, rising wait times, auto-close after N minutes

## Phase 4 — v0.3
- [ ] Strict mode: Device Admin uninstall protection, settings lock, cool-down before loosening rules
- [ ] Emergency unlock (long delay or typed challenge) — must always work
- [ ] Encrypted local backup export/import; CSV export
- [ ] Home widget, quick-settings tile, quick-start presets
- [ ] Localization (en + hi first), TalkBack pass
- [ ] F-Droid metadata (`fastlane/metadata/android/`) → submit to F-Droid

## Phase 5 — v1.0 ⚠️ legal gate
- [ ] ⚠️ Trademark search for "Unscroll" (USPTO, WIPO Global Brand DB, Indian IP office). Rename if there's a conflict.
- [ ] ⚠️ Lawyer review of the license, privacy policy, disclaimer and store listing
- [ ] Play Console: Accessibility declaration, data-safety form ("no data collected"), privacy policy URL, 13+ rating
- [ ] Play Store release (AAB)
