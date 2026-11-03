# Architecture

## Overview
```
┌──────────────── Flutter UI (Dart) ────────────────┐
│ lib/features/*      screens                       │
│ lib/app_state.dart  thin view of engine state     │
│ lib/design_system/  tokens, theme, components     │
└───────────────▲───────────────────────────────────┘
                │ Pigeon (typed, generated from pigeons/engine_api.dart)
┌───────────────┴──────── Kotlin engine ────────────────────────────────┐
│ engine/api       EngineHostApiImpl: validates and applies UI requests │
│ engine/store     RulesStore: one JSON document in an AtomicFile        │
│ engine/clock     DayClock (pure): tamper-resistant "today"             │
│ engine/rules     RuleEngine, UrlRules (pure): what to do for an app/URL│
│ engine/usage     UsageReader + UsageAggregator (pure): system history  │
│ engine/core      Engine: glue, timers, dedupe; one worker thread       │
│ engine/access    AccessibilityService: foreground app, browser URL bar │
│ engine/overlay   Block + pause screens                                 │
│ engine/service   GuardService (FGS), BootReceiver, Notifications       │
│ engine/system    SystemApps: launchable apps, never-block list         │
└───────────────────────────────────────────────────────────────────────┘
```
Package root: `io.github.aasifashraf.unscroll`

## Ownership
- **The engine is the single source of truth.** Rules and settings live in `state.json` (app-private `no_backup` directory), written atomically. Flutter never stores rules; it calls the engine.
- **Usage history is not stored by Unscroll.** It is read from Android's own usage history (`UsageStatsManager.queryEvents`), which survives our process dying and stays accurate.
- **The decision logic is pure Kotlin** (`DayClock`, `RuleEngine`, `UrlRules`, `UsageAggregator`, `StateCodec`) and fully unit-tested on the JVM.
- **Android components are thin adapters** that turn system events into rule inputs and decisions into actions.

## Detection: two sources
1. **Accessibility service (primary, instant).** `TYPE_WINDOW_STATE_CHANGED` gives the foreground app; browser address bars are read by view ID for website and keyword blocking.
2. **Usage-stats polling (fallback).** When the accessibility service is off, crashed, or revoked (e.g. Android 16+ Advanced Protection), `GuardService` polls the system usage history every 1.5 s while the screen is on, so blocking keeps working.

## Event flow (app opens)
1. A source reports `(package, window class)` to `Engine.onForeground`; work moves to the engine's worker thread.
2. Unscroll's own screen dismisses any block screen; system windows (keyboard, shade, dialogs) are ignored; duplicates within 500 ms are dropped.
3. `DayClock` decides today; today's usage for the app comes from the system history.
4. `RuleEngine.decide` returns `Allow(remaining)`, `Pause(seconds)` or `Block(reason, until)`.
5. The overlay shows the pause or block screen. For `Allow` with a limit, timers fire limit warnings (5 and 1 minutes) and re-check exactly when the limit runs out, so an app open at that moment is blocked.
6. Session limits: sessions and cool-downs are timed with `elapsedRealtime`, so no clock change (manual, auto-time correction or time zone) can shorten them. A session starts when the engine first sees the app in front; leaving it (another app, the launcher, screen off, Unscroll) pauses it, and coming back within 60 s continues it, otherwise a new one starts. `Allow.nextChangeAt` re-checks when the session runs out; then `Block(SESSION_LIMIT, now + 10 min)` is shown. The cool-down is also saved in `state.json` in trusted time (capped at 10 min from now when read back), so switching apps, a restarted process or a reboot doesn't skip it. While the phone rings or is in a call (`AudioManager` mode ringtone / in call / in communication) the session limit and the pause wait, re-checked every 30 s. Known limit: in split screen or picture-in-picture only the app the system reports as in front is tracked, so a session can end (or not start) while the other window keeps playing.
7. Pause screen: "I don't need it", Back, or leaving for another app or the home screen counts one "chose not to open" (`declinedToday`, reset with the day, and `declinedTotal`). Screen off, a call or opening Unscroll don't count.

## Short-video feeds (SF-1/2/3)
- `engine/shortform/ShortFormMap` (pure) lists, per supported app (Instagram, YouTube, Facebook, Snapchat), view IDs that exist only on the full-screen player, plus activity-name hints. It ships in the app and changes only with app updates; `REVISION` is bumped with every change.
- `ShortFormWatcher` (main thread, inside the accessibility service) runs only for apps the user turned on (`EngineState.shortFormBlocked`). On window-state, content and `TYPE_VIEW_SCROLLED` events it checks at most 4 times a second (a window change waits 300 ms so the engine's block or pause decision lands first). A mapped ID counts when the node is visible and at least 60% of the screen tall. It reads view IDs, class names, bounds and the activity name only, never text.
- Fallback (`ShortFormRules.fallbackMayApply`): only when the map never matched in the installed app version, after 3 vertical page turns (a scroll that settles on exactly one new item) within 20 s, and only if a visible `ViewPager`/`RecyclerView` covers at least 90% of the screen. A fallback match marks the app version as "may be outdated".
- Action: one global Back plus a short toast, at most once per 1.5 s per app. A 4th Back within 10 s trips the loop guard: no more Backs in that app for 10 minutes, and the version is marked "may be outdated". Never in Unscroll, never-block apps, or while a block or pause screen is up.
- `state.json` keeps `shortFormLastSeen` (trusted time), `shortFormSuspect` and `shortFormMatchedVersion` (app version codes) and `shortFormMapRevision`; a new map revision clears the suspect and matched versions, and toggling a feed clears its suspect mark. The UI shows "Detection may be outdated" only while the suspect version is still installed.

## Overlays
- With the accessibility service: `TYPE_ACCESSIBILITY_OVERLAY` (needs no extra permission).
- Without it: `TYPE_APPLICATION_OVERLAY` ("display over other apps"); otherwise a notification.
- Opaque, focusable (Back = go home; on the pause screen it also counts as "chose not to open"), one at a time. "Back at HH:MM" is shown on the phone's clock (trusted time converted with the current wall-clock offset), dismissed when the user leaves the app or opens Unscroll. Never `FLAG_SECURE` (users must be able to screenshot for bug reports).

## Time & tamper resistance
- `DayClock` is the only place that decides when a new day starts. Within one boot with auto-time off, real time advances by `elapsedRealtime`, so changing the clock by hand does nothing. With auto-time on, trusted time follows the synced clock: corrections backwards and forward corrections of up to 10 minutes are accepted, so trusted time can step back or jump ahead slightly. Durations that must not be shortened by such a step (session limits and their cool-downs) use `elapsedRealtime` directly. Across a reboot, trusted time never moves backwards.
- The day boundary uses the time zone the day started in; a zone change applies from the next day. A day can't be shorter than 20 hours, so zone-hopping can't mint extra days (DST's 23-hour day still works).
- Known limit: after a reboot with auto-time off, only the wall clock is available.

## Single build, no VPN
One APK/AAB is shipped everywhere (GitHub, F-Droid, Play). Website blocking reads the browser URL bar through the accessibility service. A local DNS VpnService was rejected: forwarding allowed lookups would need `INTERNET`, which breaks the core privacy promise, and it runs into Play's VpnService policy.

## Threads
- Accessibility callbacks and overlays: main thread, no disk I/O. Unexpected errors in callbacks are caught: Android switches off a crashing accessibility service, which would silently stop blocking.
- Engine decisions, usage queries, store writes: one `HandlerThread` ("unscroll-engine").
- Pigeon calls run on the platform thread; slow ones (`getInstalledApps`, `getUsage`) run on a background task queue.

## Security model (threat model summary)
| Threat | Mitigation |
|---|---|
| Data exfiltration | No INTERNET permission (CI-enforced); no network libraries |
| Another app abusing our components | Everything `exported=false` except the launcher activity and the accessibility service, which is guarded by `BIND_ACCESSIBILITY_SERVICE` |
| Intent spoofing | Explicit intents only; `PendingIntent.FLAG_IMMUTABLE` |
| Data at rest | App-private `no_backup` storage; `allowBackup=false` + `dataExtractionRules` exclude everything |
| Bad input from the UI | `EngineHostApiImpl` validates every value and refuses rules on never-block apps |
| Corrupt state file | AtomicFile writes; decoding never throws, skips broken entries, clamps values |
| Trapping the user | Phone, SMS, launcher, keyboard, Settings and Unscroll are never blocked; opening Unscroll always removes a block screen; strict mode (planned) keeps an emergency unlock |
| Supply chain | SHA-pinned actions, Dependabot, dependency review, CodeQL, SBOM, provenance attestation |

## Test matrix (manual, every release)
Android 8, 10, 12, 14, 16 · Pixel/emulator + one Samsung + one Xiaomi device
- Block on app open · limit triggers mid-session · pause screen · website + keyword block in Chrome/Firefox
- Accessibility off (fallback detection) · reboot · force-stop · battery saver · manual clock +1 day · time-zone change · DST
- Opening Unscroll over a block screen · Back key on overlays · dialer/Settings never blocked
- Split-screen, PiP, opening a blocked app from a notification or deep link, work profile
