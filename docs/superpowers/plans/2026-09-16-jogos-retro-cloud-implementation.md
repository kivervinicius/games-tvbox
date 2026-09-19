# Jogos Retro Cloud Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move private library administration and Android TV synchronization to Cloudflare, with no automatic billing and no Windows dependency after initial ADB setup.

**Architecture:** Cloudflare Pages serves the Access-protected admin UI; a Worker API uses Access JWT for admins, per-device bearer credentials for TVs, KV for state, and private R2 for immutable assets. TV clients pair once, consume versioned manifests, and install signed updates with the platform's required user confirmation.

**Tech Stack:** Cloudflare Workers/Pages/KV/R2, vanilla HTML/CSS/ES modules, Node.js built-in test runner, Java Android API 28, PowerShell build and GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-16-jogos-retro-cloud-design.md`

## Global Constraints

- Do not publish ROMs, saves, personal themes, private APKs, tokens, or signing keys to public GitHub.
- Stay within free quotas: internal R2 cap 8 GB; stop publishing at the cap; do not enable automatic billing.
- Preserve existing ROMs, saves, favorites, controller settings, themes and app data.
- Android TV/Fire TV API 28 is the minimum supported platform in this release.
- Launcher updates retain package `com.kiver.fireretro` and the existing signing certificate.
- Cloudflare credentials and the signing keystore never enter source control or client APKs.

## File Map

- `cloudflare/worker/`: request routing, Cloudflare Access verification, device auth/pairing, manifests, R2 upload reservation/finalization and stream/download authorization.
- `cloudflare/admin/`: responsive admin interface for content, devices, themes, apps and releases.
- `cloudflare/tests/`: executable tests for quota, auth, publication and API behavior.
- `cloudflare/wrangler.toml` and `.github/workflows/cloudflare.yml`: local binding definitions and guarded deployment/build jobs.
- `launcher-android/app/src/main/java/com/kiver/fireretro/`: paired API client, asset queue, app/launcher installers, environment detection, category routing and saved per-theme/device state.
- `launcher-android/tests/`, `docs/`, `scripts/`: regression tests, setup/deployment guide, migration and validation.

### Task 1: Cloud API foundation and test harness

**Interfaces:** `fetch(request, env, ctx)` Worker entrypoint. Bindings: `ASSETS`, `CATALOG_KV`, `DEVICE_KV`, `PAIRING_KV`, `PRIVATE_ASSETS`, `ACCESS_TEAM_DOMAIN`, `ACCESS_AUD`, `ADMIN_EMAIL`, `R2_ACCOUNT_ID`, `R2_BUCKET_NAME`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `MAX_PRIVATE_BYTES`.

- [x] Add Node tests for Access JWT denial/allowance and signatures, JSON limits, and the 8 GB byte-budget guard.
- [x] Implement the Worker router, signed Access JWT verification, safe JSON responses and structured error codes. Browser uploads use same-origin API calls; R2 CORS is documented and templated separately.
- [x] Implement admin status, quota inspection, upload reservation, finalize and manifest publication with validation before switching the active manifest.
- [x] Implement bounded per-file signed R2 PUT URLs and authenticated short-lived downloads; reject unknown object IDs and expired reservations.
- [x] Run Worker unit tests for quota boundaries, invalid signatures, expired uploads, hash mismatch and unchanged catalog on failed publication.

### Task 2: Private admin panel

**Interfaces:** the panel calls only `/api/admin/*` on the same Worker origin; all mutations require valid Cloudflare Access identity.

- [ ] Add browser-level tests for every manifest category and app/theme compatibility state.
- [ ] Implement and validate full responsive pages for app inventory, theme editing/assignment and launcher releases. Current panel provides overview, library upload, basic device approval/revocation, and a clearly labeled integration-pending themes/apps screen.
- [x] Add file selection, streamed SHA-256, size preview, direct R2 upload, upload progress, finalize and publication failure handling.
- [ ] Add per-device theme assignment and app category/source/compatibility management.
- [x] Verify script syntax, streamed hash vectors, escaped metadata and Worker API failure responses. Real keyboard/browser testing remains pending.

### Task 3: Pairing and Android cloud client

**Interfaces:** TV calls `POST /api/device/pair/start`, polls `GET /api/device/pair/complete`, sends `Authorization: Bearer <device-token>`, fetches `/api/device/catalog`, `/api/device/download/<id>`, and `POST /api/device/state`.

- [ ] Add Android tests for pairing expiry, revoked token, manifest merge, allowed ROM paths, hash failure and no-space behavior.
- [ ] Connect the implemented one-time code/approval API to Android TV UI and Android Keystore. The endpoint returns a first-party pairing URL but a visual QR renderer is not yet included.
- [ ] Implement cloud-versioned catalog, local/bundled/remote merge, sequential queue, 350 MB reserve, atomic `.part` promotion and seven-day new badge.
- [ ] Preserve focus and playable local games while downloads progress; persist the last valid cloud catalog for offline use.
- [ ] Build APK and validate it on the connected Fire Stick after the API is deployed and stable signing configuration is available.

### Task 4: Themes, controls, games and Android apps

**Interfaces:** `LibraryItem.kind` is `rom|android-game|android-app|theme|launcher`; category is `game|app|theme|update`. Device capabilities include Android API, ABI list, store type and emulator package/path.

- [ ] Add failing tests for theme preferences keyed by theme id, per-device assignment, category navigation, capability filtering and exact-package launch.
- [ ] Implement distinct Jogos and Aplicativos sections plus complete Configurações inventory and installed/unavailable/incompatible states.
- [ ] Route store links through Amazon Appstore or Google Play based on detected device; route private APKs through integrity/package/certificate checks and user-approved PackageInstaller.
- [ ] Detect API/ABI/store/RetroArch/storage/controllers; remove hard-coded ARM32 package assumptions where device capability metadata can select a supported value.
- [ ] Save customization per theme and per device; preserve Kalel and Kath and offline availability.
- [ ] Improve per-device controller profiles, test D-pad/axes/triggers, Back-to-home and Menu action sheet with explicit Exit to TV.

### Task 5: Signed updates and release automation

**Interfaces:** release manifest carries version code/name, API floor, ABI set, object ID, size, SHA-256, signer certificate fingerprint and notes. GitHub secrets: `FIRERETRO_KEYSTORE_B64`, `FIRERETRO_KEY_ALIAS`, `FIRERETRO_KEY_PASSWORD`, `FIRERETRO_KEYSTORE_PASSWORD`, `FIRERETRO_SIGNER_SHA256`, Cloudflare deploy credentials and Worker URL.

- [ ] Add failing verifier tests for package id, monotonically increasing version, signer mismatch, wrong hash/size, ABI mismatch and unauthorized install result.
- [ ] Configure GitHub Actions to build with the existing signing key from protected secrets, verify its fingerprint and publish APK only to private R2 through the admin API.
- [ ] Implement release discovery, background download and hash/signature validation in the TV app; show ready/failed states and invoke Android PackageInstaller only after explicit confirmation.
- [ ] Verify a same-signer update preserves all user data; reject an APK with a different signer before opening the installer.
- [ ] Document the one-time Cloudflare bindings, Access OTP policy, R2 CORS, GitHub secrets and local ADB recovery steps without recording secret values.

### Task 6: Deployment and end-to-end validation

- [ ] Add a dry-run deployment checker for required bindings, 8 GB cap, Access audience/team, R2 privacy and expected signer fingerprint.
- [ ] Run Worker and Android tests, build, and public-repository private-content audit.
- [ ] Deploy only after Cloudflare account secrets, namespaces, bucket, and Access policy exist; confirm unauthenticated admin access fails and a paired TV can read the private manifest.
- [ ] Validate existing Fire Stick ARM32 and one Android TV ARM64 device; if a second device is unavailable, keep that acceptance item explicitly open.
- [ ] Capture final test evidence and list any setup requiring the account owner (Access policy, DNS if needed, GitHub secrets, Play/Amazon installer confirmation).
