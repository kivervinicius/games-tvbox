# ADR-A09 — Accessibility Design System (contrast guard + tokens)

- Status: accepted (foundation). Date: 2026-09-19.
- Context: server validates theme contrast but the device accepts anything;
  PX typography, <48dp targets, color-only focus, silent dynamic states.
- Decision: theme owns palette, `AccessibleColorResolver` owns minimums
  (AA 4.5 / large 3.0 / focus 3.0) via `contrastRatio/isAccessible/
  bestForeground/sanitizeTheme`; `HIGH_CONTRAST` constants defined;
  semantics/live-region throttling (10/25/50/75/100%) and 48dp enforcement
  land with shell work; font scale 200% is a close gate.
- Consequences: no theme ships without guard pass; REVIEWER checks per screen.
- Verification: `AccessibleColorResolverTest`; device TalkBack/font/high-
  contrast/reduced-motion matrix pending.
