# ADR-A06 — Storage Strategy (logical ROM location vs physical path)

- Status: accepted. Date: 2026-09-19.
- Context: `/sdcard` literals + `WRITE_EXTERNAL_STORAGE` break on API 29+;
  catalogs keyed by physical path break on USB/SD remap; no ROM/save loss
  is tolerable.
- Decision: `RomStorage` strategies ordered AppManaged (scoped) ->
  Removable/USB -> Legacy; `resolveRomFile` existence-checks every strategy;
  catalog identity migrates path -> `cloudId` (`CatalogStore.identityOf`,
  path fallback); SAF strategy + prefs migration follow before target raise.
- Consequences: no catalog may carry device-internal paths; cloud references
  logical locations only.
- Verification: `AppManagedStorageTest`; SAF + migration tests pending.
