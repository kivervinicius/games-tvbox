# API Level Migration (target 28 -> modern)

Validated 2026-09: Gradle already declares `compileSdk/targetSdk 34`
while manifest + `Build-FireRetro.ps1` still target 28. Effective behavior
follows the artifact pipeline, not the docs. Suggested goal `minSdk ~25`
is REJECTED for now: Fire OS 6 (API 22-25) share is legacy and scoped
storage (API 29), FUSE, and background-start limits make minSdk 25 a
support burden; keep `minSdk 28`, revisit with telemetry.

## Behavior changes 28 -> 34 affecting this app

| Area | Change | Impact here |
|---|---|---|
| Scoped storage (29) | `/sdcard` direct + `WRITE_EXTERNAL_STORAGE` neutered | Catalog/covers/ROMs break -> `withAppManaged` + SAF migration REQUIRED first |
| PendingIntent mutability (31) | Must declare `IMMUTABLE/MUTABLE` | FIXED (`FLAG_IMMUTABLE`); stale target-28 APKs masked the crash |
| Package visibility (30) | `queryIntentActivities` filtered | `RetroArchProvider.isAvailable(set)` must use `<queries>` in manifest (pending) |
| `getPackageArchiveInfo` deprecation (33) | Signature flags path | `verifyPrivateApk` uses modern API already; recheck on Tiramisu devices |
| Notification runtime perm (33) | `POST_NOTIFICATIONS` | Download progress notifications need request (pending) |
| Foreground service types (34) | Must declare `dataSync` etc. | If downloads move to FGS, declare type (pending) |
| Orientation/edge-to-edge (35) | 16KB pages, predictive back | Out of cycle; predictive-back + 16KB audit before target 35+ |

## Order (do not reorder)

1. Land scoped-storage migration (AppManaged + SAF + favorites migration).
2. Add `<queries>` for RetroArch + installer packages.
3. Unify manifest/Gradle/script targets (single source of truth, ADR-A05).
4. Raise `targetSdk` stepwise (28->29->31->33->34) with matrix runs.
5. Only then consider lowering `minSdk`.
