# ADR-A05 — API Level Strategy

- Status: accepted. Date: 2026-09-19.
- Context: manifest target 28 vs Gradle target 34 vs script `--target 28`
  means behavior depends on pipeline, not intent.
- Decision: keep `minSdk 28` (reject min 25: scoped-storage/FUSE burden);
  do NOT raise effective target before (1) scoped-storage migration,
  (2) `<queries>` block, (3) single-source target unification; then raise
  stepwise 28->29->31->33->34 with matrix runs.
- Consequences: `uses-sdk` manifest tag stays until Windows-toolchain
  verification; release CI must log effective target.
- Verification: `API_LEVEL_MIGRATION.md` matrix; install/launch suites per step.
