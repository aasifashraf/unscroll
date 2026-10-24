# Architecture

## Overview
```
┌──────────────── Flutter UI (Dart) ────────────────┐
│ lib/features/*  screens, view state               │
│ lib/design_system/  tokens, theme, components     │
└───────────────▲───────────────────────────────────┘
                │ Pigeon (typed, generated from pigeons/)
┌───────────────┴──────── Kotlin engine ────────────────────────────────┐
│ engine/db         Room: rules, groups, schedules, usage, sessions     │
│ engine/rules      Pure rule evaluation (unit-tested, no Android deps) │
│ engine/clock      Day boundaries + tamper-resistant time              │
│ engine/access     AccessibilityService: foreground app, view IDs, URLs│
│ engine/overlay    Block + pause overlays (TYPE_APPLICATION_OVERLAY)    │
│ engine/service    Foreground service, BootReceiver, AlarmManager       │
│ engine/usage      UsageStatsManager queries                            │
│ engine/admin      DeviceAdminReceiver (strict mode)                    │
└───────────────────────────────────────────────────────────────────────┘
```
Package root: `io.github.aasifashraf.unscroll`

## Ownership
- **Room DB (Kotlin) is the single source of truth.** Flutter never stores rules itself; it calls the engine.
- **`engine/rules` is pure Kotlin.** Input: current state + event. Output: decision (`Allow`, `Pause(seconds)`, `Block(reason)`, `GoBack`). It is fully unit-tested on the JVM.
- **Android components are thin adapters** that turn system events into rule inputs and decisions into actions.

## Event flow (app open)
1. The AccessibilityService receives `TYPE_WINDOW_STATE_CHANGED` with a package or class name.
2. It loads a cached rule snapshot (in memory, refreshed when the DB changes).
3. `rules.evaluate(event, snapshot, clock)` returns a decision.
4. Overlay shows the pause or block screen, or calls `performGlobalAction(GLOBAL_ACTION_BACK/HOME)`.
5. Usage counters update in the DB (batched).

## Time & tamper resistance
- Usage accumulates using `SystemClock.elapsedRealtime()` deltas, never wall-clock differences.
- A "day" is identified by local date, but the engine records `(wallClock, elapsedRealtime, bootCount)` checkpoints. If wall-clock jumps don't match elapsed time, the day is **not** advanced early, and the time-change event is logged locally for the health screen.
- `ACTION_TIME_CHANGED`, `ACTION_TIMEZONE_CHANGED` and `ACTION_DATE_CHANGED` are handled by one `DayClock` class.

## Single build, no VPN
One APK/AAB is shipped everywhere (GitHub, F-Droid, Play). Website blocking reads the browser URL bar through the AccessibilityService. A local DNS VpnService was rejected: forwarding allowed lookups would need `INTERNET`, which breaks the core privacy promise. It also runs into Play's VpnService policy.

## Security model (threat model summary)
| Threat | Mitigation |
|---|---|
| Data exfiltration | No INTERNET permission (CI-enforced); no network libraries |
| Another app abusing our components | Everything `exported=false` except system-bound services, which are guarded by `BIND_ACCESSIBILITY_SERVICE`, `BIND_DEVICE_ADMIN` and `BIND_QUICK_SETTINGS_TILE` |
| Intent spoofing | Explicit intents only; `PendingIntent.FLAG_IMMUTABLE` |
| Data at rest | App-private storage; `allowBackup=false` + `dataExtractionRules` exclude everything; backups only via user-exported AES-GCM files |
| User bypass (designed limits) | Strict mode + Device Admin + settings-page blocking; documented limits: safe mode, ADB, factory reset |
| Trapping the user | Emergency unlock is always available; phone/SMS/emergency are never blocked |
| Supply chain | SHA-pinned actions, Dependabot, dependency review, CodeQL, SBOM, provenance attestation |

## Test matrix (manual, every release)
Android 8, 10, 12, 14, 16 · Pixel/emulator + one Samsung + one Xiaomi device
- Block on app open · limit triggers · pause screen · Shorts-only block · website block in Chrome/Firefox
- Reboot mid-session · force-stop · battery saver · manual clock +1 day · time-zone change · DST
- Strict mode: uninstall attempt, Accessibility-disable attempt, emergency unlock
- Split-screen, PiP, opening a blocked app from a notification or deep link, work profile
