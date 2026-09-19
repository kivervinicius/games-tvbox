# Android Universal Gaming — Current State (HEAD 5812d13 + delta)

Branch: `feat/android-universal-gaming-redesign` (from `feature/cloud-admin` @ `5812d13`).
Prior audited HEAD `068bf51` is 8 commits behind; delta re-evaluated below.
Full architecture context lives in `docs/architecture/`; this file records only
what the universal-gaming mission revalidated or changed.

## Revalidated facts (evidence in FACTS_LEDGER.md)

- `MainActivity.java`: **1631 lines** (~90 methods). Still the monolith:
  rendering, navigation, search, settings, themes, controllers, downloads,
  cloud, pairing, APK install, RetroArch, storage, favorites, catalog, focus.
- Manifest: `minSdk 28 / targetSdk 28`, `leanback required=false`,
  `touchscreen required=false`, `resizeableActivity=true`,
  `MainActivity` landscape + alias `.FireTvHome` (LAUNCHER + LEANBACK_LAUNCHER).
  No mobile-only blocker, but no `gamepad`/`television` declarations either.
- Gradle (`launcher-android/`): AGP 8.2.2, `compileSdk/targetSdk 34`,
  `minSdk 28`, flavors `tv`/`gamer`, **no explicit debug/release blocks**
  (AGP defaults), `runJavaTests` wired to `tests/project.tests.ps1`.
- SDK divergence: manifest `target28` vs Gradle `target34` vs
  `scripts/Build-FireRetro.ps1` (`aapt2 --min 28 --target 28`, Windows-only).
  Effective behavior depends on which artifact pipeline built the APK.
- Cloud origin injection still mutates `CloudApiEndpoint.java` via regex at
  build time (`Build-FireRetro.ps1:48`).
- UI scaling: `uiScale = clamp(screenWidth/1920, screenHeight/1080)`,
  240× `scaled()`, 11× `COMPLEX_UNIT_PX`, `decodeFile` on UI thread (3 sites).

## Delta already present in 5812d13 (not redone)

P0.1 pairing scopes, P0.2 canonical `sha256:` identity + idempotent R2,
P0.3 `LibraryCoordinator`, PlatformRegistry/CompatibilityEngine,
`InputManager`/`GameAction`, `RomStorage` strategies, `EmulatorProvider`,
`DeviceProfile`, Gradle flavors, relaxed manifest, Gamer dashboard state.

## Delta added by this branch (strangler, MainActivity untouched)

- `Platform` (Fire OS = Android-derived), `ExperienceMode` (TV/GAMER/DOCKED
  + debug override), `DeviceCapabilities`, `DeviceProfileEngine`
  (fixes PHONE-unreachable: sw600dp split; TV profile never DOCKED).
- `AccessibleColorResolver` (WCAG AA guard, HIGH_CONTRAST constants).
- `RetroArchCoreCatalog` (coreId <-> .so), `RetroArchProvider.selectBest`
  + `isAvailable(set)` + coreId-aware `resolveCorePath`.
- `AnalogInputFilter` + router entry (deadzone 0.25 / threshold 0.5 /
  450ms hold / 140ms repeat).
- `AppManagedStorageStrategy` + `withAppManaged` (scoped first, legacy last).
- `CatalogStore.merge` keys by `cloudId`, path as legacy fallback.
- `AndroidAppInstaller`: `FLAG_IMMUTABLE` (API 31+ crash fix).
- Manifest: `gamepad` + `television` features (`required=false`).
- Harness: 6 new suites + `ThemeCustomizationTest` execution wired.

## Still open (see IMPLEMENTATION_REPORT / RISK_REGISTER)

Compose TV/Gamer shells, lazy library, async image pipeline, SAF strategy,
PendingIntent tests on-device, API 29+ scoped-storage migration, targetSdk
unification, screenshots/goldens, TalkBack pass, font-scale matrix.
