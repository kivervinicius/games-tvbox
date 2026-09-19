# ADR-A08 — Input Abstraction (GameAction + adapters)

- Status: accepted (partial). Date: 2026-09-19.
- Context: key handling split between `InputManager` and `MainActivity`
  (L1/R1/L2/R2/PAGE), SELECT unmapped, no analog path, no per-device adapters.
- Decision: `GameAction` extended with `PAGE_PREVIOUS/NEXT` (TAB_* kept for
  platform tabs); SELECT->MENU; PAGE_UP/DOWN mapped; `AnalogInputFilter`
  (deadzone 0.25/threshold 0.5/450ms/140ms) + router entry; full
  Dpad/Gamepad/Joystick/Keyboard/Touch/Mouse/Remote adapters deferred to
  shell wiring (no behavior fork by controller name; `woble` typo noted).
- Consequences: L2/R2 stay in MainActivity until migration (locked by test).
- Verification: `InputManagerExtendedTest`, `AnalogInputFilterTest`.
