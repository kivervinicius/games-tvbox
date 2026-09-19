# ADR-A07 — Emulator Runtime Resolution (coreId, not paths)

- Status: accepted. Date: 2026-09-19.
- Context: `com.retroarch.ra32` default + `/data/.../cores/` paths assume one
  package and root-visible dirs; catalogs embed physical `core_path`.
- Decision: catalogs reference `coreId` (`RetroArchCoreCatalog`, 15 cores);
  device resolves via `selectBest(abi, installed)` (standard > ABI split >
  ABI fallback) + `isAvailable(set)`; absolute-path passthrough kept for
  legacy entries; `/data` paths never constructed without install proof.
- Consequences: MainActivity wiring + `<queries>` + core-presence check
  pending; ra32 default stays until then.
- Verification: `RetroArchCoreCatalogTest` (select/available/translate/parse).
