# todo

My running checklist for Unscroll. Ticking things off as I go.

## setup
- [x] license (GPL-3.0), NOTICE, README + disclaimer
- [x] privacy policy, security policy, contributing guide, code of conduct
- [x] .gitignore / .gitattributes
- [x] goals, feature list, architecture docs
- [x] CI workflows (ci, codeql, dependency review, release) + dependabot
- [x] issue/PR templates, CODEOWNERS
- [x] create the GitHub repo and push
- [ ] repo settings: protect `main` (required checks), private vulnerability reporting, dependabot alerts, secret scanning + push protection
- [ ] make the release keystore offline, keep 2 backups, add it to GitHub secrets (`RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`)

## design (finish before writing app code)
- [x] design system + all screens in `design/unscroll-design.html`
- [x] tokens in `design/tokens.json`, notes in `docs/DESIGN_SYSTEM.md`
- [x] go through the design, fix what I don't like, sign it off
- [ ] app icon + logo (my own, no stock stuff with unclear licenses)

## v0.1
- [x] flutter project (`io.github.aasifashraf`, minSdk 26, release build falls back to debug signing when there's no keystore, `analysis_options.yaml`)
- [ ] get CI green, then check that a PR adding INTERNET actually fails
- [x] turn the `pub` and `gradle` entries in `.github/dependabot.yml` back on
- [ ] pigeon setup (`pigeons/engine_api.dart`)
- [ ] kotlin engine skeleton: room db, foreground service, boot receiver, day/clock helper + tests for clock tampering
- [ ] `lib/design_system/` from the tokens + a debug gallery screen
- [ ] onboarding + a separate disclosure screen for each permission (usage, accessibility, overlay, notifications) + battery-optimization help per phone brand
- [ ] insights: today, per-app list, week chart
- [ ] app blocking: block list + block screen
- [ ] daily limits per app/group, warnings at 5 and 1 min
- [ ] mindful pause screen (breathing, wait time, "why are you opening this?")
- [ ] focus timer that blocks the chosen apps
- [ ] about screen: disclaimer, licenses, privacy
- [ ] security/privacy review → tag `v0.1.0` → GitHub release

## v0.2
- [ ] schedules (work / study / bedtime / custom, per weekday)
- [ ] block reels/shorts/spotlight but keep the rest of the app usable (per-app view-id maps + fallback)
- [ ] website + keyword blocking through the browser address bar
- [ ] growing plant for focus sessions, session history
- [ ] streaks, achievements, daily goal
- [ ] open limits, longer waits each open, auto-close after N minutes

## v0.3
- [ ] strict mode: uninstall protection, lock settings, delay before rules can be loosened
- [ ] emergency unlock (long wait or typing challenge). This one has to always work
- [ ] encrypted backup export/import, CSV export
- [ ] home widget, quick settings tile, presets
- [ ] english + hindi, TalkBack pass
- [ ] F-Droid metadata (`fastlane/metadata/android/`) and submit

## before v1.0
- [ ] trademark search for "Unscroll" (USPTO, WIPO, Indian IP office), rename if it clashes
- [ ] get a lawyer to look at the license, privacy policy, disclaimer and store listing
- [ ] Play Console: accessibility declaration, data safety ("no data collected"), privacy policy link, 13+ rating
- [ ] Play Store release
