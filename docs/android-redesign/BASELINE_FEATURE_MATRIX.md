# Baseline Feature Matrix (HEAD 5812d13 + branch delta)

Legend: OK = works, PARTIAL = works with known gaps, MISSING = absent,
LEGACY = works via legacy path kept by strangler.

| Area | TV (Fire/TCL/ATV) | Phone/Tablet/Handheld | Notes |
|---|---|---|---|
| Install/launcher tile | OK (alias LAUNCHER+LEANBACK) | OK (alias LAUNCHER; no blocker) | `gamepad`/`television` now declared `required=false` |
| D-pad/remote nav | OK | PARTIAL (no remote) | `ControllerInputRouter` + focus coordinator; analog added |
| Gamepad | OK (mapped) | OK (mapped) | SELECT->MENU, PAGE_UP/DOWN added; L2/R2 still in MainActivity |
| Analog stick nav | MISSING -> PARTIAL | MISSING -> PARTIAL | `AnalogInputFilter` in domain; Activity wiring pending |
| Touch/mouse/keyboard | LEGACY (touch fallback flag) | OK touch; PARTIAL mouse | `onSwipe` exists; mouse adapter pending |
| Search | OK (TV editor) | PARTIAL (no native IME) | IME adoption pending (Gamer shell) |
| Favorites | LEGACY (path-keyed) | LEGACY | `identityOf` migrates merge to cloudId; prefs migration pending |
| Library 100-3000 items | PARTIAL (ScrollView) | PARTIAL | Lazy rendering pending (Compose phase) |
| Covers | LEGACY (decodeFile UI thread) | LEGACY | Async pipeline pending; documented P0 perf |
| Downloads/states | PARTIAL (no paused/queued UI) | PARTIAL | SHA/.part atomic OK; state machine UI pending |
| Offline-first | OK (cache+ETag cloud path) | OK | Remote path has no ETag fallback (gap kept) |
| Pairing | OK (scoped, TV copy) | PARTIAL (TV copy) | Neutral copy pending (Gamer shell) |
| APK install | OK + FLAG_IMMUTABLE fix | OK | Receiver `exported=false` kept |
| RetroArch launch | LEGACY (ra32 default) | LEGACY | `selectBest`+coreId ready; MainActivity wiring pending |
| Storage | LEGACY (/sdcard+perm) | LEGACY | `withAppManaged` ready; SAF pending; no data migration yet |
| Themes | OK custom | OK custom | Contrast guard server-only -> on-device resolver added, UI wiring pending |
| High contrast | MISSING -> PARTIAL | MISSING -> PARTIAL | Constants+resolver+tests; profile UI pending |
| TalkBack/live regions | MISSING | MISSING | Announcer pending; no spam-throttle yet |
| Font scale 100-200% | PARTIAL (PX texts) | PARTIAL | SP migration pending with Compose |
| Docked mode | n/a | PARTIAL (contract) | `isDocked`+DOCKED mapping; multi-display out of cycle |
| Flavors tv/gamer | OK | OK | debug/release = AGP defaults (documented) |
