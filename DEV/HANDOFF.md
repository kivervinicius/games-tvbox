# Current Handoff — Gaming Platform Multi-Device

## Snapshot
- Updated: 2026-09-19
- Entry: .NET 10 LTS Migration and Platform-Neutral Abstractions completed and verified
- Spec: DEV/SPECS/ACTIVE.md
- Changed: importer-windows/src/JogosRetroImporter.Core/*, importer-windows/src/JogosRetroImporter/*, importer-windows/tests/JogosRetroImporter.Tests/*
- Verified: Importer Tests executed on Linux on .NET 10 (100% assertions pass), DEV gates passed (`orquestrador-maestro check-dev-gates --strict`)
- Next context: Implementing Game Source Provider contracts and Retrostic adapters (API, HTML, and Browser resolvers)

## Latest Work
Migrada a solução `JogosRetroImporter` para .NET 10 LTS (`net10.0`). Criadas as abstrações de plataforma `IPlatformServices`, `IAppPaths` (compatível com Linux XDG e Windows LocalAppData), `ICredentialStore` (AES-256-GCM autenticado no Linux e DPAPI no Windows) e `IToolchainResolver` (resolução dinâmica de executáveis `chdman`/`chdman.exe` e permissões de execução). Desacoplados `SecureTokenStore`, `ToolchainManager` e `ImportPipeline`. Testes executados e validados no Linux com 100% de sucesso.

## Recent Entries
- 2026-09-19: Multi-Device Gaming Architecture and Acceptance Scenarios completed and verified.
- 2026-09-19: Cloud Control Plane (Device groups, Release channels, Assignments) implemented and verified.
- 2026-09-19: Importer PlatformRegistry, Gradle build flavors, and AndroidManifest relaxed.
- 2026-09-19: Android Core Abstractions implemented and verified.
- 2026-09-19: Platform Registry and Compatibility Engine implemented and verified.
- 2026-09-19: P0.2 and P0.3 content identity, idempotent publication, and transactional coordinator.
- 2026-09-19: P0.1 pairing security fix, admin scope enforcement and tests.
- 2026-09-19: Baseline real audit, facts ledger, risk register, migration plan and 10 ADRs established.
