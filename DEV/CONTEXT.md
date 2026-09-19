# Current Context — Games TV Box Gaming Platform

## State
- Project: `games-tvbox`
- Branch base: `feature/cloud-admin`
- Active spec: `DEV/SPECS/ACTIVE.md`
- Active handoff: `DEV/HANDOFF.md`
- Architecture docs: `docs/architecture/` (CURRENT_STATE.md, TARGET_STATE.md, MIGRATION_PLAN.md, RISK_REGISTER.md, FACTS_LEDGER.md, adr/*)

## Commands
- Cloudflare Tests: `node --test cloudflare/tests/*.test.mjs`
- Android Unit Tests (Java): Compilação via `javac` e execução com `java -cp` dos arquivos em `launcher-android/tests/`
- Importer Tests (C#): `dotnet run --project importer-windows/tests/JogosRetroImporter.Tests/JogosRetroImporter.Tests.csproj`
- PowerShell Tests: `pwsh -File manager-windows/tests/Test-Manager.ps1`
- Android Build (Gradle): `gradle assembleDebug`

## Constraints And Risks
- Não apagar ROMs, saves ou configurações locais dos dispositivos.
- Não utilizar APIs root ou permissões intrusivas silenciosamente.
- Respeitar quotas da Cloudflare (eliminar full scan no R2).
- Proteger segredos em logs e auditoria.

## Next Context
Tv/Gamer shell scaffolding, MainActivity wiring, SAF storage strategy, and emulator/device matrix on branch feat/android-universal-gaming-redesign. Foundation (engine, contrast guard, coreId runtime, analog input, app-managed storage, PendingIntent fix) implemented and verified 23/23; changes uncommitted per rules.md #1.
