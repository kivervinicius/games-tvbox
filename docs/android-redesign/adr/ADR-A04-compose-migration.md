# ADR-A04 — Compose Migration (screen-by-screen strangler)

- Status: proposed (tooling unresolved: no SDK/Compose deps in this env).
- Context: programmatic `MainActivity` UI can't deliver lazy lists, focus
  tokens, or font-scale-safe typography economically.
- Decision: migrate C1 design-system preview -> C2..C5 TV (home/library/
  downloads/settings) -> C6..C9 Gamer; legacy stays behind
  `uiGeneration=legacy|adaptive`; Java+Kotlin mixed module allowed;
  domain never rewritten for Compose's sake.
- Consequences: Compose (TV + Material) deps, screenshot/golden harness,
  retirement ADR-A10 criteria before flag removal.
- Verification: per-screen REVIEWER checklist + goldens (pending).
