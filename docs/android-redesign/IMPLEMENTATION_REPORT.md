# Implementation Report — Android Universal Gaming foundation

Branch: `feat/android-universal-gaming-redesign` (from `feature/cloud-admin` @ `5812d13`).
Prior audited HEAD `068bf51` superseded: 8 commits of P0/cloud/device work
already landed; this cycle revalidated and continued, it did not redo.

## Executive Summary

Baseline captured (CURRENT_STATE, FEATURE_MATRIX, UI, RISKS, FACTS A001-A017,
10 ADRs). Six additive domain modules + four surgical legacy fixes landed
with zero `MainActivity` edits, zero behavior regressions, 23/23 suites
green. Compose shells, lazy library, async images, SAF, and on-device matrix
remain explicitly open with owners and gates.

## Baseline

- `MainActivity` 1631 lines; manifest min/target 28; Gradle target/compile
  34 + flavors tv/gamer; regex cloud-origin injection; uiScale 1920x1080;
  11 PX texts; UI-thread decodeFile x3; /sdcard x8; ra32 default.
- Tests pre-branch: 16 suites green; Gradle APK build already unreproducible
  here (AGP needs Java 17, only 11; no SDK).

## Architecture Before -> After

Before: Activity owns domain; brand-string branching; path-keyed catalog;
package-hardcoded runtime; key handling split; server-only contrast check.
After: Platform -> Capabilities -> Profile -> ExperienceMode chain;
`DeviceProfileEngine` (strangler over frozen `inferProfile`);
`cloudId`-keyed merge; `coreId` catalog; unified `GameAction`+analog;
on-device `AccessibleColorResolver`; scoped-first storage ordering.
`MainActivity` untouched: pure strangler.

## Build Migration

No UI change. Gradle file already migrated pre-branch; this cycle wired 6
new suites + `ThemeCustomizationTest` execution + `FLAG_IMMUTABLE` and
gamepad manifest guards. `uses-sdk` manifest tag kept pending
Windows-toolchain ADR (risk R-A06). debug/release = AGP defaults (recorded).

## API Level Migration

See `API_LEVEL_MIGRATION.md`: keep minSdk 28, stepwise 28->29->31->33->34
after scoped-storage migration + `<queries>` + target unification.

## Device Profiles / TV / Gamer Experience

Engine + mapping + DOCKED contract + debug override shipped with tests.
Shells (TvActivity/GamerActivity, Compose) NOT built this cycle: tooling
absent (no SDK/Compose), flagged in R-A12. Legacy UI remains the only shell.

## Accessibility

`AccessibleColorResolver` + HIGH_CONTRAST constants + tests shipped.
TalkBack audit, live-region announcer with throttling, 48dp enforcement,
font-scale matrix: open, P0-gated at MR close.

## Input

SELECT->MENU, PAGE_UP/DOWN, analog filter + router entry, all tested.
Adapters (touch/mouse/remote/joystick split) and Activity wiring: open.
L2/R2 behavior frozen in MainActivity, locked by test until migration.

## Storage

`AppManagedStorageStrategy` + `withAppManaged` (scoped first, legacy last,
existence-checked). SAF strategy, favorites prefs migration: open. No data
touched.

## Emulator Runtime

`RetroArchCoreCatalog` (15 coreIds), `selectBest`, `isAvailable(set)`,
coreId-aware resolution, all tested. MainActivity still defaults ra32
(deliberate until wiring + `<queries>` land).

## Performance

Budget unmeasured here (no emulator/profiler). Known hot spots documented
(decodeFile x3, ScrollView catalog). Async pipeline + lazy lists: open.

## Test Matrix

29/29 pwsh+javac suites green on Linux. Increment 2 added: SafLocation,
DownloadStateMachine, AccessibilityAnnouncer, FavoriteIdentity,
WindowSizeClass, ThemeTokens. Missing: on-device install/focus/TalkBack/
font/device-farm runs.

## Visual Evidence

None this cycle (no shells built, no emulator). Goldens required per
ADR-A04/A10 before legacy retirement; `docs/assets/screenshots/` holds
legacy captures only.

## Compatibility Matrix

See `BASELINE_FEATURE_MATRIX.md` (TV/phone/tablet/handheld/docked x
feature, OK/PARTIAL/LEGACY/MISSING).

## Known Limitations

No SDK, no Java 17, no emulator, no Compose deps, no screenshots, no
TalkBack run. Landed since: SAF logical layer (`SafLocation` + `SAF_TREE`
type; OS grant wiring pending), download state machine + announcer policy
(UI wiring pending), favorites identity migration helper (prefs wiring
pending), `<queries>` for RetroArch packages, WindowSizeClass + ThemeTokens.
Still open: neutral pairing copy, native IME, notification perm (33), FGS
types (34).

## Technical Debt (carried, not created)

Monolith intact; regex origin injection; uses-sdk divergence; TV-centric
copy; ScrollView catalog; path-keyed favorites prefs; mouse/keyboard
adapters; `woble` typo in `ControllerRegistry:38`.

## Commits

Uncommitted by constitution (rules.md #1: human validates production
changes) on `feature/cloud-admin` working tree per maestro (no branch
switch). Proposed sequence when maestro approves:
`docs(android): capture universal-gaming baseline` (docs/ only), then
`feat(device): ...`, `feat(accessibility): ...`, `feat(runtime): ...`,
`feat(input): ...`, `feat(storage): ...`, `fix(android): ...pendingintent+manifest`.

## Merge Readiness

NOT ready: foundation only. P0 close gates (RISK_REGISTER) all still open
at shell level. Next: shell scaffolding + wiring + emulator matrix.

## Reproduction Commands

- `git checkout feat/android-universal-gaming-redesign`
- `pwsh -NoProfile -File launcher-android/tests/project.tests.ps1` (expect 23 PASS, exit 0)
- `gradle :app:assembleDebug` (requires Java 17 + Android SDK; blocked here, see FACT-A015)
