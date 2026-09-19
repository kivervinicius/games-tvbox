# ADR-A03 — TV and Gamer Experience Separation

- Status: accepted. Date: 2026-09-19.
- Context: TV (10-foot, D-pad, focus-unmissable) and Gamer (touch, IME,
  adaptive grid/rail) have contradictory layout, typography and input needs.
- Decision: `TV (TvActivity->TvShell)` and `GAMER (GamerActivity->GamerShell)`
  share domain/data/device/input contracts, never layout components; tokens
  shared only at brand level. `DOCKED` (phone+display+gamepad) reuses TV
  shell contracts. Mapping: TV->TV, PHONE/TABLET/HANDHELD->GAMER.
  Manual override in Settings for debug only.
- Consequences: two shells to build and screenshot; `uiGeneration` flag
  during migration; legacy retired only after acceptance matrix passes.
- Verification: visual regression per profile + focus test suites (pending).
