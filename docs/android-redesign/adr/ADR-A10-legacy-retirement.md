# ADR-A10 — Legacy UI Retirement (definition of done)

- Status: proposed. Date: 2026-09-19.
- Context: strangler only works with explicit retirement criteria, or two
  UIs ship forever.
- Decision: legacy retires when: Gradle build reproduces current APK;
  universal install on TV+mobile; engine+shells+lazy+async images+scoped
  storage+coreId resolution live; offline intact; Fire/TCL/phone/tablet/a11y
  matrices green; goldens stored; independent REVIEWER finds no P0.
  `uiGeneration` flag removed in the same MR that retires legacy.
- Consequences: until then every screen ships twice-gated (legacy intact).
- Verification: IMPLEMENTATION_REPORT checklist (30 items) + matrix evidence.
