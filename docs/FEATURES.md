# Feature Specification

Unscroll combines the capabilities people get from the major screen-time, focus and app-blocking apps into one offline, open-source app.
The "Common in" column notes which kinds of apps usually offer each capability, so we can check that nothing is missing. Never copy any other product's code, text, visuals or layouts, and never name other products in the app or store listing (see CONTRIBUTING.md).

Phase tags: **v0.1**, **v0.2**, **v0.3**, **later**.
Every feature works offline. "AC" = acceptance criteria.

---

## 1. Insights
| ID | Feature | Phase | Common in |
|---|---|---|---|
| IN-1 | Today's total screen time + comparison with yesterday and the 7-day average | v0.1 | Built-in OS tools, Screen-time trackers |
| IN-2 | Per-app usage list (time, opens), sorted, with the app icon | v0.1 | Built-in OS tools, Screen-time trackers |
| IN-3 | Week chart (daily bars), tap a day to drill in | v0.1 | Screen-time trackers |
| IN-4 | Unlock count and first-pickup time | v0.2 | Built-in OS tools |
| IN-5 | Notification count per app | v0.2 | Built-in OS tools, Screen-time trackers |
| IN-6 | Hourly timeline (when you use the phone) | v0.2 | Screen-time trackers |
| IN-7 | App categories (social, video, games…) with category totals; user-editable | v0.2 | Screen-time trackers |
| IN-8 | **Focus score** (0–100): a daily score from limits kept, focus minutes and short-form avoided | v0.2 | Focus & blocking apps |
| IN-9 | "Time saved" vs. your baseline week | v0.2 | Focus & blocking apps, Short-video blockers |
| IN-10 | Month view + history kept for 1 year (configurable) | v0.3 | Screen-time trackers |
| IN-11 | CSV export of usage | v0.3 | Screen-time trackers |

AC (IN-1/2): numbers match Android's `UsageStatsManager` within ±1 min; the screen loads in < 500 ms with 1 year of data; works without Accessibility granted.

## 2. Limits
| ID | Feature | Phase | Common in |
|---|---|---|---|
| LI-1 | Daily time limit per app | v0.1 | Built-in OS tools, App blockers |
| LI-2 | App groups with a shared limit | v0.1 | App blockers |
| LI-3 | Warning notifications at 5 min and 1 min left | v0.1 | App blockers |
| LI-4 | Open-count limit (max opens per day) | v0.2 | App blockers, Mindful-pause apps |
| LI-5 | Different limits per weekday | v0.2 | App blockers |
| LI-6 | Session limit: auto-close after N minutes per open, with a cool-down | v0.2 | Mindful-pause apps, App blockers |
| LI-7 | "5 more minutes" extension (configurable count; disabled in strict mode) | v0.2 | Built-in OS tools |

AC: limits reset at local midnight once per real day. **Changing the manual clock or time zone doesn't reset or extend a limit.** Limits survive reboot and force-stop (restored on boot or next service start). When a limit is reached while the app is open, the block screen appears within 1 s.

## 3. Mindful friction
| ID | Feature | Phase | Common in |
|---|---|---|---|
| MF-1 | Pause screen before opening chosen apps: breathing animation + wait (default 5 s) | v0.1 | Mindful-pause apps |
| MF-2 | "Continue" vs. "I don't want to open it" choice; counts the times you chose not to | v0.1 | Mindful-pause apps |
| MF-3 | Optional "Why are you opening this?" intention prompt | v0.2 | Mindful-pause apps |
| MF-4 | Rising wait time with each open that day | v0.2 | Mindful-pause apps |
| MF-5 | Scroll-break nudge after N continuous minutes in an app | v0.2 | Mindful-pause apps, Short-video blockers |
| MF-6 | Grayscale guidance (links to Android Bedtime/grayscale settings) | v0.3 | Built-in OS tools |

AC: the pause screen appears before the target app's content is usable; Back/Home dismisses the pause screen and doesn't open the app; works on Android 8–16.

## 4. Short-form feed blocking
| ID | Feature | Phase | Common in |
|---|---|---|---|
| SF-1 | Block Instagram Reels, YouTube Shorts, Facebook Reels, Snapchat Spotlight, TikTok-style feeds while the rest of the app works | v0.2 | Short-video blockers, Mindful-pause apps |
| SF-2 | Per-app view-ID map, shipped in the app and updatable through app updates only (no network) | v0.2 | — |
| SF-3 | Fallback heuristic when IDs change (vertical full-screen video pager detection) | v0.2 | — |
| SF-4 | Optional: block Explore/Search feeds | v0.3 | Short-video blockers |
| SF-5 | Daily short-form allowance (e.g., 10 min) instead of a full block | v0.3 | Short-video blockers |

AC: when a short-form view is detected, the user is sent back (global Back) and sees a gentle toast or overlay. DMs, profile and normal posts keep working. A broken detection map fails **open** (no crash, no false block loop) and shows "detection may be outdated" in the app.

## 5. Blocking
| ID | Feature | Phase | Common in |
|---|---|---|---|
| BL-1 | Block list: chosen apps can't be opened (block overlay) | v0.1 | App blockers |
| BL-2 | Allowlist mode: only chosen apps work (phone, SMS and emergency are always allowed) | v0.2 | App blockers, Focus & blocking apps |
| BL-3 | Website blocking via the browser URL bar (Chrome, Firefox, Brave, Edge, Samsung Internet…) | v0.2 | Website blockers, App blockers |
| BL-5 | Keyword blocking in URLs | v0.2 | App blockers |
| BL-6 | Built-in adult-content block list (bundled, offline) | v0.3 | App blockers |
| BL-7 | Hide notifications from blocked apps while blocked | v0.3 | App blockers |
| BL-8 | Quick block: one-tap "block for 30/60/120 min" | v0.2 | Website blockers, Focus & blocking apps |

AC: the dialer, emergency calls, Settings → Accessibility (except in strict mode) and Unscroll itself are **never** blocked. The block overlay can't be dismissed into the blocked app.

## 6. Focus sessions
| ID | Feature | Phase | Common in |
|---|---|---|---|
| FO-1 | Focus timer (preset 25/50/90 min or custom) that blocks the chosen apps | v0.1 | Focus & blocking apps, Website blockers |
| FO-2 | Pomodoro cycles with break timers | v0.2 | Focus timers |
| FO-3 | Growing plant visual: grows during the session and wilts if you open a blocked app and choose "give up" | v0.2 | Focus timers |
| FO-4 | Session history + garden of completed plants | v0.2 | Focus timers |
| FO-5 | Session tags (study, work, reading) + stats per tag | v0.3 | Focus timers, Focus & blocking apps |

AC: the session keeps running if the UI is killed; the countdown is correct after a reboot (the session resumes, or is marked interrupted if time passed).

## 7. Schedules
| ID | Feature | Phase | Common in |
|---|---|---|---|
| SC-1 | Scheduled block profiles (Work, Study, Bedtime, custom) by weekday + time range | v0.2 | App blockers |
| SC-2 | Bedtime mode: block + Do Not Disturb guidance + wind-down screen | v0.2 | Built-in OS tools |
| SC-3 | Overnight ranges (22:00–07:00) handled correctly | v0.2 | — |
| SC-4 | Triggers: when charging / on a specific Wi-Fi (on-device only) | later | App blockers |

AC: a schedule starts and ends within 1 min of the target time, including across DST changes and reboots.

## 8. Strict mode
| ID | Feature | Phase | Common in |
|---|---|---|---|
| ST-1 | Strict mode on for a duration or on a schedule | v0.3 | App blockers |
| ST-2 | Uninstall protection via Device Admin while strict mode is on | v0.3 | App blockers |
| ST-3 | Locks: no editing or deleting rules, no disabling the service, Unscroll's App Info/Accessibility settings pages blocked | v0.3 | App blockers |
| ST-4 | Cool-down: loosening any rule takes effect only after a delay (e.g., 24 h); tightening is instant | v0.3 | — |
| ST-5 | **Emergency unlock (always available)**: a long wait (configurable 15–60 min, during which the app stays in the foreground) or a long typed challenge | v0.3 | — |

AC: there is no state the user can't leave without a factory reset. The emergency unlock works after a reboot, a clock change and low battery. Phone, SMS and emergency calls are never blocked.

## 9. Motivation
| ID | Feature | Phase | Common in |
|---|---|---|---|
| MO-1 | Daily goal (e.g., < 2 h screen time) + streak | v0.2 | Short-video blockers, Focus & blocking apps |
| MO-2 | Achievements (first week under goal, 10 focus sessions…) | v0.2 | Focus timers, Focus & blocking apps |
| MO-3 | Gentle daily summary notification (opt-in) | v0.2 | Screen-time trackers |
| MO-4 | Weekly reflection screen | v0.3 | Focus & blocking apps |

## 10. Data & settings
| ID | Feature | Phase | Common in |
|---|---|---|---|
| DA-1 | Encrypted backup export/import to a user-chosen file (password-based, AES-GCM) | v0.3 | — |
| DA-2 | Quick-start presets (Student, Deep work, Digital detox) | v0.3 | Focus & blocking apps |
| DA-3 | Home-screen widget (today's time + focus button) | v0.3 | Screen-time trackers |
| DA-4 | Quick-settings tile: start focus / quick block | v0.3 | Website blockers |
| DA-5 | Dark/light/system theme | v0.1 | — |
| DA-6 | Languages: English, Hindi first | v0.3 | — |
| DA-7 | About: disclaimer, open-source licenses, privacy policy | v0.1 | — |

## 11. Onboarding & permissions
| ID | Feature | Phase |
|---|---|---|
| ON-1 | 3-screen intro (what it does, privacy promise, pick goals) | v0.1 |
| ON-2 | **Separate prominent disclosure** before each sensitive permission (Usage, Accessibility, Overlay, Device Admin) with explicit consent | v0.1 |
| ON-3 | OEM battery-optimization guide (Xiaomi, Samsung, Oppo/Realme, Vivo, OnePlus, Huawei) | v0.1 |
| ON-4 | Health check screen: shows which permissions or services are off and how to fix them | v0.1 |
