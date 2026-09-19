# Risk Register — Android Universal Gaming

| ID | Risk | Likelihood / Impact | Mitigation (status) |
|---|---|---|---|
| R-A01 | ROM/save/config loss during storage migration | Low / Critical | Strangler: resolver checks existence everywhere; legacy last; no destructive migration in this cycle |
| R-A02 | `FLAG_IMMUTABLE` omission crashes installs on API 31+ | High / High | FIXED + harness guard; on-device install test still pending |
| R-A03 | Scoped storage breaks `/sdcard` catalog on target raise | High / High | `withAppManaged` ready; SAF strategy + migration path pending; DO NOT raise target before fix |
| R-A04 | ra32-only assumption bricks 64-bit/standard installs | Med / High | `selectBest`+coreId ready; MainActivity wiring pending; keep ra32 default until wired+tested |
| R-A05 | Monolith edits regress Fire TV (only working shell) | High / High | No MainActivity edits this cycle; new code is additive + pure-Java tested |
| R-A06 | Gradle vs script artifact divergence (SDK 28 vs 34) | Med / Med | Documented; manifest `uses-sdk` kept until Windows-toolchain ADR; CI must log effective target |
| R-A07 | Regex cloud-origin injection hides release config | Med / Med | Kept (harness depends); release audit must log effective origin without leaking secrets |
| R-A08 | Inaccessible themes ship (server validates, device accepts) | High / Med | `AccessibleColorResolver` added; UI wiring + TalkBack pass pending; P0 gate for MR close |
| R-A09 | Focus loss on TV (color-only, no scale/outline token) | Med / High | Focus token system pending with Compose TV shell |
| R-A10 | 1000+ item library jank (ScrollView + UI decode) | High / Med | Lazy + async pipeline pending; perf budget unmeasured (no emulator/SDK here) |
| R-A11 | Favorites lost on storage remap (path-keyed prefs) | Med / High | Merge migrated to cloudId; SharedPreferences migration pending |
| R-A12 | No SDK/emulator in this environment; claims unverified on-device | High / Med | All device claims marked; matrix requires emulator/profile runs before merge |

P0 close gates: PX critical texts, weak focus contrast, <48dp targets,
inaccessible dynamic states, no contrast guard wiring, font-scale breakage,
leanback-blocked mobile, uncompilable modern target, legacy-only storage,
mandatory ra32, 1920x1080-bound layouts. None of these may remain at MR close.
