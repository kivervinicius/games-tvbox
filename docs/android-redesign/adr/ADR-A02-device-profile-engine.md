# ADR-A02 — Device Profile Engine

- Status: accepted. Date: 2026-09-19.
- Context: legacy `inferProfile` keys off brand strings and can never yield
  PHONE; manufacturer checks don't scale to TCL/handheld/docked variety.
- Decision: `DeviceCapabilities` (24 fields: platform/api/model/form/touch/
  mouse/keyboard/dpad/remote/gamepad/joystick/abis/sw-dp/density/fontScale/
  orientation/externalDisplay/removableStorage/availableStorage/stores/
  lowRam/flavor) -> `DeviceProfileEngine.resolve` -> `DeviceProfile`
  (legacy type kept) -> `ExperienceMode`. Phone/tablet split by sw600dp.
  `LOW_MEMORY` is a performance class (`lowRam || api<=25`), never a model.
- Consequences: legacy `inferProfile` frozen; engine is the strangler path.
- Verification: `DeviceProfileEngineTest` (incl. docked + override + low-mem).
