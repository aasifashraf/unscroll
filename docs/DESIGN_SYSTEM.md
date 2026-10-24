# Design System

> Status: **draft v0.1, awaiting owner approval** (todo.md, Phase 1).
> Visual reference: open [`design/unscroll-design.html`](../design/unscroll-design.html) in a browser (works offline; light/dark toggle top-right).
> Values: [`design/tokens.json`](../design/tokens.json) is the single source of truth.

## Direction
Calm, premium, trustworthy. Dark-first ink base, **one** teal-to-violet gradient accent, a soft glow on hero cards only, rounded cards (24 px), generous spacing. The tone is kind and never shaming.

## Tokens (summary)
| Group | Values |
|---|---|
| Surfaces | `bg`, `surface`, `surfaceRaised`, `surfaceSunken`, `border`, `borderStrong` |
| Text | `textPrimary` (15.4:1), `textSecondary` (8.7:1), `textMuted` (5.8:1); light theme ≥ 4.8:1 |
| Accent | `gradient` = primary action/progress only; `accentTeal`, `accentViolet`, `accentText` |
| Status | `good`, `warning`, `critical`, **always with an icon + label** |
| Charts | 4 fixed category slots (Social, Video, Games, Browsing) + `other`; `single` for one-series charts. Validated for CVD separation and ≥ 3:1 contrast in both themes |
| Type | Inter (OFL-1.1, bundled): display 44, headline 28, title 20, subtitle 16, body 15, bodySmall 13, label 13, overline 11 |
| Space | 4-pt grid: 2, 4, 8, 12, 16, 20, 24, 32, 48 |
| Radius | 8, 12, 16, 24 (cards), pill (buttons, chips) |
| Motion | 120 / 240 / 400 ms; breath cycle 4 s in, 1 s hold, 5 s out; with reduced motion → fades only |

## Components
Button (primary / secondary / ghost / danger), chip, segmented control, toggle (`role=switch`), input, stepper, weekday picker, list row, app tile, icon tile, progress bar (normal / warning / over), stat tile, status line, banner (info / good / warn), week bar chart, category share bar + legend, progress ring, bottom navigation, bottom sheet, dialog, prominent-disclosure card, breathing orb, growing plant.

## Rules
1. Feature code uses tokens only. No raw colors, sizes or text styles.
2. The gradient means "primary action or progress", nothing else.
3. Color is never the only signal: status = icon + label; charts = legend + values; a "view as table" alternative exists for charts.
4. Charts: one axis only; categories keep their color regardless of rank; a 5th+ category folds into "Other"; 4 px rounded data ends anchored to the baseline; 2 px gaps between segments; only the key value is labeled; hover/press shows a tooltip.
5. Accessibility: WCAG AA contrast, ≥ 48 dp targets, visible focus, semantics labels, 200% font scale, reduced motion.
6. Copy: kind and plain. "Instagram is resting", not "Blocked!". No medical claims.
7. Originality: our own layouts, icons and artwork. Mockups show third-party apps only as plain letter tiles with their names. In the real app, icons come from the device's installed apps at runtime.

## Flutter mapping (Phase 2)
`lib/design_system/`
- `tokens.dart`: constants mirrored from `tokens.json`
- `theme.dart`: `ThemeData` for dark and light + `ThemeExtension`s (`UnscrollColors`, `UnscrollText`)
- `components/`: one widget per component above, each with a widget test
- Debug-only `ComponentGallery` screen mirrors section 02 of the HTML
