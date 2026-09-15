# Independent Fire Stick Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Let each Fire Stick authenticate read-only to a private GitHub library, download new ROMs and covers locally, keep working offline, and show non-blocking progress and seven-day “Novo” badges.

**Architecture:** The Windows Manager publishes a private manifest and GitHub Release assets. The Android launcher polls the manifest while foregrounded, downloads one asset at a time into a temporary file, verifies SHA-256 and atomically installs it, then refreshes the local catalog. Existing local files and saves are preserved. Pages remains a metadata-only public gallery.

**Tech Stack:** Kotlin/Java Android API 28, Java networking and SharedPreferences, GitHub Releases HTTP API, PowerShell Manager, existing JSON catalog format, PowerShell and JVM tests.

**Spec:** `docs/superpowers/specs/2026-09-15-online-catalog-design.md`

## Global Constraints

- Preserve existing ROMs, saves, favorites, themes, and RetroArch configuration.
- Do not place private tokens, commercial ROMs, saves, or private URLs in Pages or public repository files.
- Keep Android baseline at Fire TV Stick Lite Android 9 / `armeabi-v7a` and RetroArch ARM32 compatibility.
- Never expose a GitHub token in logs, intents, catalog JSON, or UI text.
- Downloads must be resumable/retryable, hash-verified, atomic, and never block game browsing.

---

### Task 1: Define private remote manifest and Manager publishing contract

**Files:**
- Modify: `manager-windows/GitHubCatalog.ps1`
- Modify: `manager-windows/FireRetroManager.ps1`
- Test: `manager-windows/tests/Test-RemoteLibraryManifest.ps1`

**Interfaces:**
- Produce `library.manifest.json` with `version`, `generatedAt`, `repository`, and `items[]` containing `id`, `label`, `platform`, `path`, `core_path`, `image`, `size`, `sha256`, `assetName`, `releaseTag`, and `downloadUrl`.
- Produce `Publish-RemoteLibraryManifest -RepoPath -Catalog -ReleaseTag` and `New-RemoteLibraryManifest -Catalog -AssetRoot`.

- [ ] Add failing tests for manifest field allowlisting, SHA-256 formatting, ROM path normalization, and rejection of public/private mixing.
- [ ] Implement deterministic manifest generation and private checkout publication without altering existing `Export-PublicCatalog` behavior.
- [ ] Add a Manager action that publishes the merged Windows/Fire Stick catalog to the configured private checkout and release staging directory.
- [ ] Run the manifest tests and existing GitHub catalog tests.

### Task 2: Add per-device credential and remote library settings

**Files:**
- Modify: `launcher-android/app/src/main/AndroidManifest.xml`
- Modify: `launcher-android/app/src/main/java/com/kiver/fireretro/MainActivity.java`
- Create: `launcher-android/app/src/main/java/com/kiver/fireretro/RemoteLibrarySettings.java`
- Test: `launcher-android/tests/remote-settings.tests.ps1`

**Interfaces:**
- `RemoteLibrarySettings.load(Context)` / `save(Context, Settings)` with repository API URL, release asset base URL, encrypted token storage, and enabled flag.

- [ ] Test defaults, round-trip persistence, and that token values never appear in serialized diagnostics.
- [ ] Add INTERNET permission and a controller-accessible settings dialog for repository URL and token entry.
- [ ] Store the token in Android Keystore-backed encrypted preferences; never put it in the catalog or Intent extras.
- [ ] Run parser, manifest, and settings tests.

### Task 3: Implement foreground manifest polling and safe downloads

**Files:**
- Create: `launcher-android/app/src/main/java/com/kiver/fireretro/RemoteLibrarySync.java`
- Modify: `launcher-android/app/src/main/java/com/kiver/fireretro/CatalogStore.java`
- Modify: `launcher-android/app/src/main/java/com/kiver/fireretro/MainActivity.java`
- Test: `launcher-android/tests/remote-sync.tests.ps1`

**Interfaces:**
- `RemoteLibrarySync.start(Context, Listener)` / `stop()`; Listener callbacks `onProgress`, `onItemInstalled`, `onError`, `onIdle`.

- [ ] Add tests for empty manifest, new item, bad hash, interrupted download, insufficient space, expired credential, offline fallback, and duplicate suppression.
- [ ] Fetch manifest on app open and every 15 minutes while foregrounded, with bounded timeouts and one retry.
- [ ] Reserve configurable free space, download sequentially to `.part`, verify length and SHA-256, then rename atomically into `/sdcard/roms` and update covers/catalog.
- [ ] Keep the last valid catalog when network or authentication fails.
- [ ] Run JVM/static tests and verify API 28 compatibility.

### Task 4: Add non-blocking progress, pending states, and seven-day badges

**Files:**
- Modify: `launcher-android/app/src/main/java/com/kiver/fireretro/MainActivity.java`
- Modify: `launcher-android/app/src/main/java/com/kiver/fireretro/LauncherState.java`
- Test: `launcher-android/tests/download-ui.tests.ps1`

**Interfaces:**
- Persist per-game `downloadedAt`, `downloadState`, and `lastError` in app-private preferences; expose UI state from the sync listener.

- [ ] Test badge expiry at seven days, progress updates, focus preservation, and installed-game launch during another download.
- [ ] Add a compact top status area with queue count, current game, percentage, pause/error text, and retry action.
- [ ] Add card states `INSTALLED`, `DOWNLOADING`, `PENDING_SPACE`, `FAILED`, and `NEW`; disable launch only while that card is incomplete.
- [ ] Keep scrolling, platform filters, search, and controller navigation active throughout downloads.

### Task 5: Documentation, release packaging, and validation

**Files:**
- Modify: `docs/operations/private-library.md`
- Modify: `docs/manual/README.md`
- Modify: `README.md`
- Create: `docs/features/remote-sync.md`
- Test: existing Manager, launcher, documentation, and public-layout suites

- [ ] Document private repository/release setup, per-device token scope, revocation, offline behavior, space policy, and troubleshooting.
- [ ] Document that Pages is metadata-only and that ROM rights remain the user's responsibility.
- [ ] Run all existing tests plus the new remote-sync/UI tests, PowerShell parser checks, `git diff --check`, and an unsigned API-28 build.
- [ ] Install the APK on the available Fire Stick, configure one read-only credential, test a small legal asset, and capture ADB evidence for manifest, ROM, cover, progress, offline launch, and badge expiry.
- [ ] Record the second-device validation as pending until another Fire Stick is available; do not claim it passed.
