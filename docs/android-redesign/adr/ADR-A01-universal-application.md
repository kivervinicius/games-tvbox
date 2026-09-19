# ADR-A01 — Universal Android Application (single APK, adaptive shells)

- Status: accepted. Date: 2026-09-19.
- Context: one codebase serves TV, phone, tablet, handheld, docked. Separate
  APKs per form factor would fork the domain and the cloud contract.
- Decision: single `applicationId` with `tv`/`gamer` Gradle flavors as
  build-time tuning only; runtime adaptation via Platform -> Capabilities
  -> Profile -> ExperienceMode.
- Alternatives: per-form-factor APKs (rejected: catalog/cloud drift);
  `if (manufacturer)` branches (rejected: untestable matrix).
- Consequences: manifest must stay universal (leanback/touchscreen/gamepad/
  television all `required=false`); flavors must never carry behavior forks.
- Verification: manifest guards in `project.tests.ps1`; install smoke on TV
  and phone emulator before MR close. Follow-up: `<queries>` block.
