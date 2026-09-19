# ROM Intake Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement one deterministic ROM intake pipeline that prepares metadata, installs locally through ADB, and publishes to Cloudflare when available without duplicating or inventing catalog data.

**Architecture:** The .NET core owns the versioned platform registry, ROM identity, metadata result, manifest schema, Cloudflare publication/readback, and pending queue. A small command-line host exposes the pipeline to scripts and the Windows Manager; the Manager supplies device selection/UI but does not reimplement classification. Cloudflare deduplicates durable catalog items by content ID, while ADB remains an independent local destination after the manifest is valid.

**Tech Stack:** C#/.NET 8, `HttpClient`, `System.Text.Json`, existing `SharpCompress`, PowerShell Manager scripts, Cloudflare Worker JavaScript, Node `node:test`, Android Debug Bridge.

**Spec:** `docs/superpowers/specs/2026-09-19-rom-intake-pipeline-design.md`

## Global Constraints

- “O pipeline não depende de LLM.”
- “O arquivo original continuará somente leitura e terá seu hash revalidado ao final.”
- “Cloudflare opcional, mas consistente.”
- “Não haverá fallback para ‘publicado’ baseado em uma tentativa parcial.”
- “Não usará `rm`, `mv` ou limpeza automática.”
- “Nenhuma sinopse será inventada; se não houver fonte, ficará pendente.”
- “A instalação local pode terminar offline e fica claramente pendente na fila Cloudflare.”
- “O mesmo registro de plataforma/core é usado no modo local e Cloudflare.”
- Preserve all existing worktree changes; do not restore `importer-windows/README.md` or `scripts/Convert-PlayStationFolder.ps1`.

## Review Focus

- A ZIP with two ROMs or traversal entries must stop before staging; covered by Task 1 archive tests.
- A `.bin` without an explicit platform must be reported ambiguous rather than guessed; covered by Task 1 identity tests.
- An N64 item whose core is not present on the connected ARM32 Fire TV must remain `runtimeUnavailable`; covered by Task 4 ADB tests.
- A Cloudflare outage after successful ADB installation must produce `cloudPending` and a retryable manifest; covered by Task 5 queue tests.
- A provider response missing synopsis or returning a conflicting title must not overwrite trusted identity; covered by Task 2 metadata tests.

### Task 1: Add the versioned platform and manifest contracts

**Files:**
- Create: `importer-windows/src/JogosRetroImporter.Core/PlatformProfiles.json`
- Create: `importer-windows/src/JogosRetroImporter.Core/RomPipelineModels.cs`
- Create: `importer-windows/src/JogosRetroImporter.Core/RomManifestValidator.cs`
- Modify: `importer-windows/src/JogosRetroImporter.Core/GameModels.cs`
- Modify: `importer-windows/src/JogosRetroImporter.Core/JogosRetroImporter.Core.csproj`
- Test: `importer-windows/tests/JogosRetroImporter.Tests/Program.cs`

**Interfaces:**
- Consumes: input file path, platform profile JSON, optional existing manifest JSON.
- Produces: `RomIdentity`, `PlatformProfile`, `RomManifest`, `RomDeliveryState`, and `RomManifestValidator.Validate(RomManifest)`.

- [ ] **Step 1: Write the failing contract tests**

Add these assertions to the existing executable test before implementation:

```csharp
var n64 = PlatformRegistry.Load().Single(profile => profile.Id == "n64");
Assert(n64.Extensions.Contains(".z64") && n64.Extensions.Contains(".n64") && n64.Extensions.Contains(".v64"), "N64 extensions are incomplete");
Assert(n64.RemoteDirectory == "/sdcard/roms/n64", "N64 remote directory is wrong");
Assert(PlatformRegistry.TryResolve("Super Mario 64 (U) [!].z64", out var identity), "N64 identity was not resolved");
Assert(identity.Platform == "N64" && identity.Region == "U", "N64 identity fields are wrong");

var manifest = RomManifest.Create(identity, sourcePath, artifactPath, "sha256:" + new string('a', 64));
RomManifestValidator.Validate(manifest);
AssertThrows(() => RomManifestValidator.Validate(manifest with { Artifact = manifest.Artifact with { Sha256 = "bad" } }), "invalid manifest hash was accepted");
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run from `importer-windows`:

```powershell
dotnet run --project tests/JogosRetroImporter.Tests/JogosRetroImporter.Tests.csproj
```

Expected: FAIL because the registry, records, factory, and validator do not exist.

- [ ] **Step 3: Implement the registry and records**

Define the JSON profiles with `n64`, `ps1`, `nes`, `snes`, `megadrive`, and `gba`. Each profile must include `id`, `displayName`, `extensions`, `remoteDirectory`, `coreCandidates`, and `format`. Include N64 candidates as data, not as a claim that a device has them:

```json
{
  "id": "n64",
  "displayName": "N64",
  "extensions": [".z64", ".n64", ".v64"],
  "remoteDirectory": "/sdcard/roms/n64",
  "coreCandidates": ["mupen64plus_next_libretro_android.so", "parallel_n64_libretro_android.so"],
  "format": "n64"
}
```

Implement immutable records with these exact members:

```csharp
public sealed record PlatformProfile(string Id, string DisplayName, string[] Extensions, string RemoteDirectory, string[] CoreCandidates, string Format);
public sealed record RomIdentity(string Title, string Region, string Platform, string Category, string ContentExtension);
public sealed record RomArtifact(string Path, long Size, string Sha256, string Format);
public sealed record RomSource(string Path, long Size, string Sha256);
public sealed record RomManifest(string SchemaVersion, string ContentId, RomSource Source, RomArtifact Artifact, RomIdentity Identity, RomRuntime Runtime, RomMetadata Metadata, RomDelivery Delivery, RomHistoryEntry[] History);
public sealed record RomRuntime(string CorePath, string RemotePath);
public sealed record RomMetadata(MetadataField Title, MetadataField Cover, MetadataField Synopsis);
public sealed record MetadataField(string Value, string Source, string RetrievedAt, bool Reviewed, bool Pending);
public sealed record RomDelivery(string Adb, string Cloud);
public sealed record RomHistoryEntry(string At, string Operation, string Status, string Code, string Message);
```

Use `contentId = "sha256:" + artifact.Sha256` and derive the remote path only from the profile directory and sanitized filename.

- [ ] **Step 4: Implement deterministic identity and validation**

Extend `PlatformDetector` to load the registry once and expose `PlatformRegistry.TryResolve(string path, out RomIdentity identity)`. Parse known region tokens, accept N64 header extensions, reject unknown extensions, and return an explicit ambiguity result for `.bin` unless the caller supplies a platform hint that matches the profile. `RomManifestValidator.Validate` must reject missing hashes, non-positive sizes, category values other than `game`, remote paths outside `/sdcard/roms/`, and core paths that are not `/data/user/0/<package>/cores/<safe-name>.so`.

- [ ] **Step 5: Copy the registry as a runtime asset and rerun tests**

Set `CopyToOutputDirectory` and `CopyToPublishDirectory` to `PreserveNewest` in the core project. Run the focused test again and expect PASS for N64 detection, manifest creation, and invalid-hash rejection.

- [ ] **Step 6: Commit the contract independently**

```powershell
git -c safe.directory='*' -C C:\Kiver\Projeto\games-tvbox-cloud-admin add importer-windows/src/JogosRetroImporter.Core importer-windows/tests/JogosRetroImporter.Tests/Program.cs
git -c safe.directory='*' -C C:\Kiver\Projeto\games-tvbox-cloud-admin commit -m "feat: add deterministic ROM manifest contract"
```

### Task 2: Make metadata capture explicit and non-hallucinatory

**Files:**
- Create: `importer-windows/src/JogosRetroImporter.Core/MetadataPipeline.cs`
- Modify: `importer-windows/src/JogosRetroImporter.Core/LibretroMetadataClient.cs`
- Modify: `importer-windows/src/JogosRetroImporter.Core/RomPipelineModels.cs`
- Test: `importer-windows/tests/JogosRetroImporter.Tests/Program.cs`
- Modify: `manager-windows/MetadataProviders.ps1`
- Test: `manager-windows/tests/Test-CatalogSync.ps1`

**Interfaces:**
- Consumes: `RomIdentity`, provider configuration, `HttpMessageHandler` test doubles.
- Produces: `MetadataPipeline.ResolveAsync(RomIdentity, CancellationToken)`, returning `RomMetadata` with per-field source/status.

- [ ] **Step 1: Add failing metadata tests**

Use a fake `HttpMessageHandler` and assert that complete, partial, timeout, and conflicting responses behave as follows:

```csharp
var metadata = await new MetadataPipeline(fakeProvider).ResolveAsync(identity);
Assert(metadata.Title.Value == "Super Mario 64", "provider title was not used");
Assert(!metadata.Cover.Pending && metadata.Cover.Source == "libretro-thumbnails", "cover provenance was lost");
Assert(metadata.Synopsis.Pending && metadata.Synopsis.Value == "", "missing synopsis must remain pending");
```

Also add a PowerShell regression asserting `Get-GameMetadata` returns a fallback with `Pending=$true` when the configured provider times out, never a fabricated synopsis.

- [ ] **Step 2: Run the focused C# and PowerShell tests to verify failure**

```powershell
dotnet run --project tests/JogosRetroImporter.Tests/JogosRetroImporter.Tests.csproj
powershell -NoProfile -ExecutionPolicy Bypass -File manager-windows/tests/Test-CatalogSync.ps1
```

Expected: FAIL because metadata fields do not carry provenance/pending state.

- [ ] **Step 3: Implement provider adapters**

Refactor `LibretroMetadataClient` so cover lookup returns a `MetadataField` with URL/download hash and no silent “confidence means valid” fallback. Add a configured JSON provider for title/synopsis; accept only HTTPS URLs, bounded response size, valid JSON, and a title/synopsis string within the manifest limits. Keep parser-derived title as identity, and only replace the display title when the provider response is non-empty and not conflicting with the parsed title beyond the configured review policy.

- [ ] **Step 4: Persist metadata artifacts safely**

Download covers into `%LOCALAPPDATA%\JogosRetro\RomPipeline\covers\<contentId>.<ext>`, compute SHA-256 after download, and store the source URL and retrieval time. Store synopsis text only in the private local manifest/cache, never in a public catalog unless explicitly marked public. Never write provider credentials to a manifest or log.

- [ ] **Step 5: Rerun tests and commit**

Run the C# test executable and `Test-CatalogSync.ps1`; expect PASS for complete, partial, timeout, and invalid-URL cases. Commit only the metadata files and tests with:

```powershell
git -c safe.directory='*' -C C:\Kiver\Projeto\games-tvbox-cloud-admin add importer-windows/src/JogosRetroImporter.Core manager-windows/MetadataProviders.ps1 manager-windows/tests/Test-CatalogSync.ps1
git -c safe.directory='*' -C C:\Kiver\Projeto\games-tvbox-cloud-admin commit -m "feat: record ROM metadata provenance"
```

### Task 3: Build the local manifest/staging pipeline

**Files:**
- Create: `importer-windows/src/JogosRetroImporter.Core/RomIntakeService.cs`
- Create: `importer-windows/src/JogosRetroImporter.Core/RomManifestStore.cs`
- Modify: `importer-windows/src/JogosRetroImporter.Core/ImportPipeline.cs`
- Modify: `importer-windows/src/JogosRetroImporter.Core/ArchiveExtractor.cs`
- Test: `importer-windows/tests/JogosRetroImporter.Tests/Program.cs`

**Interfaces:**
- Consumes: source path, optional platform hint, metadata providers, staging root.
- Produces: `RomPreparationResult` with `RomManifest`, final artifact path, metadata status, and conversion status.

- [ ] **Step 1: Add failing staging tests**

Test a raw N64 `.z64`, the Mario ZIP, a ZIP with two ROMs, an archive traversal entry, a PlayStation CUE conversion, and source-hash preservation:

```csharp
var result = await service.PrepareAsync(marioZip, new RomPreparationOptions(null, allowMetadataPending: true));
Assert(result.Manifest.Identity.Platform == "N64", "Mario ZIP was not classified as N64");
Assert(result.Manifest.Source.Sha256 == await FileHash.Sha256Async(marioZip), "source hash changed");
Assert(result.Manifest.Delivery.Adb == "pending", "preparation must precede delivery");
AssertThrowsAsync(() => service.PrepareAsync(twoRomZip, options), "ambiguous archive was accepted");
```

- [ ] **Step 2: Run tests and verify failure**

```powershell
dotnet run --project tests/JogosRetroImporter.Tests/JogosRetroImporter.Tests.csproj
```

Expected: FAIL because the generic service and manifest store do not exist.

- [ ] **Step 3: Implement safe preparation**

Use the existing `SharpCompress` extractor with bounded entry count, bounded decompressed bytes, and traversal protection. For a single raw ROM, stage a copy; for PlayStation, retain the existing CUE/BIN/ISO/CHD conversion and wrap its output in the generic result. For N64 and cartridge formats, do not convert bytes. Re-hash the original after staging/conversion and throw `source_changed` if it differs.

- [ ] **Step 4: Implement manifest storage and state transitions**

Write manifests atomically to `%LOCALAPPDATA%\JogosRetro\RomPipeline\manifests\<contentId>.json` using a temporary file and replace operation. Implement `Load`, `Save`, `ListPendingCloud`, and `AppendHistory`; each transition must verify the current manifest before writing the next one.

- [ ] **Step 5: Rerun tests and commit**

Run the importer tests and verify PASS for N64 staging, Mario ZIP extraction, archive limits, PlayStation compatibility, and original preservation. Commit with:

```powershell
git -c safe.directory='*' -C C:\Kiver\Projeto\games-tvbox-cloud-admin add importer-windows/src/JogosRetroImporter.Core importer-windows/tests/JogosRetroImporter.Tests/Program.cs
git -c safe.directory='*' -C C:\Kiver\Projeto\games-tvbox-cloud-admin commit -m "feat: add ROM staging and manifest store"
```

### Task 4: Add canonical Cloudflare publication and readback

**Files:**
- Modify: `importer-windows/src/JogosRetroImporter.Core/CloudPublisherClient.cs`
- Modify: `cloudflare/worker/src/admin-api.mjs`
- Modify: `cloudflare/worker/src/importer-api.mjs`
- Test: `cloudflare/tests/admin-publication.test.mjs`
- Test: `cloudflare/tests/cloud-contract.test.mjs`
- Modify: `importer-windows/tests/JogosRetroImporter.Tests/Program.cs`

**Interfaces:**
- Consumes: `RomManifest`, protected importer token, verified artifact/cover files.
- Produces: `CloudPublicationResult` with `ContentId`, `Revision`, canonical item, and `Verified`.

- [ ] **Step 1: Write failing Worker idempotency tests**

Add tests that publish the same content twice and assert one catalog item, then publish the same content ID with a different hash/path and assert a conflict:

```javascript
const first = await publishRom(bindings, romEntry('sha256:abc'));
const second = await publishRom(bindings, romEntry('sha256:abc'));
assert.equal(second.item.id, first.item.id);
assert.equal((await bindings.CATALOG_KV.get('catalog:active', 'json')).items.length, 1);
await assert.rejects(publishRom(bindings, romEntry('sha256:abc', { sha256: 'different' })), { code: 'content_conflict' });
```

Add a client test for `GetImporterCatalogAsync` and readback mismatch rejection.

- [ ] **Step 2: Run Cloudflare tests and verify failure**

```powershell
node --test cloudflare/tests/*.test.mjs
```

Expected: FAIL because the Worker currently keys published ROMs by upload UUID and the client has no canonical readback method.

- [ ] **Step 3: Implement stable content publication**

Carry `contentId` in the ROM item. In `validateManifestItem`, require a valid `contentId` for `kind=rom`; in `publish`, find an existing ROM by `contentId`, return it when all immutable fields match, and throw `content_conflict` when size, hash, path, or core differs. Keep upload reservation UUIDs ephemeral. Add `GET /api/importer/catalog` through the existing importer authorization route and return the same canonical item shape used by the device catalog.

- [ ] **Step 4: Implement client publication/readback**

Add these methods to `CloudPublisherClient`:

```csharp
Task<JsonObject> GetImporterCatalogAsync(string token, CancellationToken cancellationToken = default);
Task<CloudPublicationResult> PublishRomAsync(string token, RomManifest manifest, CancellationToken cancellationToken = default);
Task VerifyCanonicalRomAsync(RomManifest manifest, JsonObject canonicalItem);
```

`PublishRomAsync` must publish the cover first when available, publish the artifact, read the catalog, select by `contentId`, and compare hash, size, category, platform, remote path, and core. It must return `CloudPending` data only through a thrown typed error; callers decide whether to queue retry.

- [ ] **Step 5: Rerun tests and commit**

Run all Worker tests and the importer executable. Expect PASS for idempotency, conflict, readback mismatch, invalid token, and existing API behavior. Commit with:

```powershell
git -c safe.directory='*' -C C:\Kiver\Projeto\games-tvbox-cloud-admin add cloudflare/worker/src cloudflare/tests importer-windows/src/JogosRetroImporter.Core importer-windows/tests/JogosRetroImporter.Tests/Program.cs
git -c safe.directory='*' -C C:\Kiver\Projeto\games-tvbox-cloud-admin commit -m "feat: make Cloudflare ROM publication canonical"
```

### Task 5: Implement local-first ADB delivery and retry queue

**Files:**
- Create: `importer-windows/src/JogosRetroImporter.Core/AdbRomInstaller.cs`
- Create: `importer-windows/src/JogosRetroImporter.Core/RomPipelineService.cs`
- Create: `importer-windows/src/JogosRetroImporter.Core/RomPipelineCli.cs`
- Create: `importer-windows/src/JogosRetroRomPipeline/RomPipeline.csproj`
- Create: `importer-windows/src/JogosRetroRomPipeline/Program.cs`
- Modify: `importer-windows/JogosRetroImporter.sln`
- Test: `importer-windows/tests/JogosRetroImporter.Tests/Program.cs`

**Interfaces:**
- Consumes: prepared `RomManifest`, ADB executable/serial, optional Cloudflare client.
- Produces: `PipelineResult`, updated manifest, and process exit codes `0=verified`, `2=local verified/cloud pending`, `10=blocked validation`, `20=transport failure`.

- [ ] **Step 1: Add failing fake-ADB tests**

Create an `IAdbRunner` fake that records arguments. Assert that delivery uses only `mkdir`, `push --sync`, `ls/stat`, and `sha256sum`; special names remain one argument; mismatched remote hashes are reported; no call contains `rm`, `mv`, or `move`.

```csharp
var result = await installer.InstallAsync(manifest, "fake:5555", fakeRunner);
Assert(result.Verified, "ADB install was not verified");
Assert(fakeRunner.Calls.Any(call => call.Contains("push") && call.Contains("--sync")), "ADB must use push --sync");
Assert(!fakeRunner.Calls.SelectMany(call => call).Any(arg => arg is "rm" or "mv" or "move"), "ADB installer must be non-destructive");
```

- [ ] **Step 2: Run the importer test and verify failure**

```powershell
dotnet run --project tests/JogosRetroImporter.Tests/JogosRetroImporter.Tests.csproj
```

Expected: FAIL because the ADB installer and pipeline service do not exist.

- [ ] **Step 3: Implement the ADB adapter**

Use `ProcessStartInfo.ArgumentList` rather than a concatenated shell command. Verify the selected device is present, query ABI/model, create the profile remote directory, push artifact and cover with `--sync`, and verify remote size/hash. Select a core only from the profile candidate list and a verified device inventory; otherwise set `runtimeUnavailable` without claiming launch success.

- [ ] **Step 4: Implement the service order and queue behavior**

`RomPipelineService.ProcessAsync` must execute `Prepare → Metadata → SaveManifest → ADB → optional Cloudflare`. If ADB succeeds and Cloudflare fails, append `cloud_pending` history and return exit code `2`; never remove the local artifact. `RetryPendingCloudAsync` must load manifests by content ID, re-read the source/artifact hash, and call only `PublishRomAsync`.

- [ ] **Step 5: Add the CLI commands**

Implement these commands in `RomPipelineCli`:

```text
rom-pipeline inspect <source> [--platform <id>]
rom-pipeline install <source> --serial <serial> [--platform <id>] [--cloud <origin>]
rom-pipeline retry-cloud [--cloud <origin>]
rom-pipeline verify-manifest <manifest>
```

The CLI reads the importer token only through the existing `SecureTokenStore`, never from command-line arguments. JSON reports go to stdout; secrets and signed URLs are redacted.

- [ ] **Step 6: Rerun tests and commit**

Run the importer tests, then execute the CLI against the fake ADB runner fixture. Expect PASS for local-only, local-plus-cloud, cloud-pending, retry, hash mismatch, and destructive-command rejection. Commit with:

```powershell
git -c safe.directory='*' -C C:\Kiver\Projeto\games-tvbox-cloud-admin add importer-windows/src importer-windows/JogosRetroImporter.sln importer-windows/tests/JogosRetroImporter.Tests/Program.cs
git -c safe.directory='*' -C C:\Kiver\Projeto\games-tvbox-cloud-admin commit -m "feat: add local-first ROM pipeline and ADB delivery"
```

### Task 6: Route Manager and Importer UI through the same pipeline

**Files:**
- Create: `manager-windows/RomPipeline.ps1`
- Modify: `manager-windows/FireRetroManager.ps1`
- Modify: `manager-windows/README.md`
- Modify: `importer-windows/src/JogosRetroImporter/Form1.cs`
- Modify: `importer-windows/src/JogosRetroImporter.Core/ImportPipeline.cs`
- Test: `manager-windows/tests/Test-CatalogSync.ps1`
- Test: `manager-windows/tests/Test-Manager.ps1`

**Interfaces:**
- Consumes: the `RomPipelineCli` executable and existing Manager settings/ADB serial.
- Produces: one Manager action for new/update ROMs, with visible `localComplete`, `cloudPending`, `runtimeUnavailable`, or `complete` status.

- [ ] **Step 1: Add failing integration-contract tests**

Assert that the Manager source contains the CLI invocation and no new direct ROM copy path bypasses it. Assert that the Importer UI uses the generic pipeline for N64 and retains the existing PlayStation conversion entry point. Add a fixture invoking `RomPipeline.ps1` with a fake CLI and verify the source path, serial, and cloud origin are passed as separate arguments.

- [ ] **Step 2: Implement the script adapter**

`RomPipeline.ps1` must resolve its CLI path from `$PSScriptRoot`, validate that the source is a file, pass arguments through an array, capture the JSON report, and return the CLI exit code. It must not construct `adb shell` commands or delete staging files.

- [ ] **Step 3: Replace Manager ROM delivery calls**

Change `Sync-CatalogToFireStick`/`Copy-LocalRomFilesToFireStick` integration so new or changed ROMs call the pipeline script, while existing catalog-only synchronization remains available for metadata-only updates. Preserve current remote inventory and non-destructive import behavior. Display the manifest state and pending-cloud reason in the Manager details panel.

- [ ] **Step 4: Extend Importer UI platform choices**

Populate the platform selector from the registry, add N64, call generic preparation for cartridge ROMs, and keep PlayStation conversion behind the same `RomPreparationResult`. Do not enable Publish until the manifest is valid; allow local installation when Cloudflare is unpaired, with the status explicitly showing pending publication.

- [ ] **Step 5: Rerun tests and commit**

Run `Test-CatalogSync.ps1`, `Test-Manager.ps1`, and the importer tests. Expect PASS for Manager routing, non-destructive behavior, N64 UI availability, and offline local completion. Commit with:

```powershell
git -c safe.directory='*' -C C:\Kiver\Projeto\games-tvbox-cloud-admin add manager-windows importer-windows/src/JogosRetroImporter importer-windows/src/JogosRetroImporter.Core/ImportPipeline.cs
git -c safe.directory='*' -C C:\Kiver\Projeto\games-tvbox-cloud-admin commit -m "feat: route ROM tools through canonical pipeline"
```

### Task 7: Validate the Mario 64 case and update documentation

**Files:**
- Create: `scripts/Test-RomPipeline.ps1`
- Modify: `docs/quick-start.md`
- Modify: `docs/features/remote-sync.md`
- Modify: `docs/operations/private-library.md`
- Modify: `docs/architecture/overview.md`
- Modify: `manager-windows/README.md`
- Test fixture: `importer-windows/tests/fixtures/Super Mario 64 (U) [!].zip` generated in the test temp directory, not committed from Downloads.

**Interfaces:**
- Consumes: the CLI, fake provider, fake ADB, and optional real ADB serial.
- Produces: reproducible local report showing source hash, artifact hash, N64 identity, metadata status, ADB status, Cloudflare status, and core availability.

- [ ] **Step 1: Write the end-to-end script checks**

`Test-RomPipeline.ps1` must create a temporary one-ROM N64 ZIP and a two-ROM ZIP, run `inspect`, assert the first produces N64 and the second blocks, then run `verify-manifest` on a tampered manifest and assert nonzero exit. It must clean only its own temporary directory.

- [ ] **Step 2: Run offline end-to-end validation**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Test-RomPipeline.ps1
```

Expected: PASS without network, Cloudflare credentials, or a physical Fire TV.

- [ ] **Step 3: Run real-device validation for the existing Mario ROM**

Use the explicit source path and the confirmed ADB serial only after the offline checks pass:

```powershell
dotnet run --project importer-windows/src/JogosRetroRomPipeline/RomPipeline.csproj -- install "C:\Users\kiver.teixeira\Downloads\Super Mario 64 (U) [!].zip" --serial 192.168.1.121:5555
```

Require the report to show source/artifact hash equality, `platform=N64`, remote hash equality, and either `runtime=verified` or `runtimeUnavailable` with the missing core named. Cloudflare may be `cloudPending` if unpaired/offline.

- [ ] **Step 4: Update documentation with actual states**

Document the sequence, local-only mode, Cloudflare retry, secure token pairing, N64 core prerequisite, and the fact that a successful ADB copy alone is not a complete metadata/cloud validation.

- [ ] **Step 5: Run the complete available verification suite**

```powershell
node --test cloudflare/tests/*.test.mjs
dotnet run --project importer-windows/tests/JogosRetroImporter.Tests/JogosRetroImporter.Tests.csproj
powershell -NoProfile -ExecutionPolicy Bypass -File manager-windows/tests/Test-CatalogSync.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File manager-windows/tests/Test-Manager.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Test-RomPipeline.ps1
```

Run each command from both the repository root and its module directory where supported; report unavailable toolchains separately from test failures.

- [ ] **Step 6: Final review and commit**

Inspect `git diff --check`, verify no secrets or Downloads files were added, and commit only the pipeline implementation/documentation. Do not stage the pre-existing `importer-windows/README.md` modification or `scripts/Convert-PlayStationFolder.ps1` deletion unless the user separately requests those changes.

## Plan Self-Review

- Spec coverage: manifest and registry are Task 1; metadata provenance is Task 2; conversion/staging and preservation are Task 3; Cloudflare optional publication/readback/idempotency is Task 4; ADB safety and retry are Task 5; Manager/Importer convergence is Task 6; Mario validation and documentation are Task 7.
- Placeholder scan: no task relies on an unspecified provider, core installation, or manual “later” step; provider and core absence have explicit states and tests.
- Type consistency: `RomManifest`, `RomMetadata`, `RomRuntime`, `RomPipelineService`, `CloudPublicationResult`, and CLI exit codes are defined before later tasks consume them.
- Review focus coverage: archive ambiguity and `.bin` ambiguity are Task 1/3; N64 core absence is Task 5; Cloudflare outage is Task 5; partial/conflicting metadata is Task 2.
- Safety review: all file writes are staging/cache/manifest writes; original ROMs and existing device data are never removed or moved.
