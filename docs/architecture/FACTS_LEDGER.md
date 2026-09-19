# Facts Ledger — Jogos Retro / Games TV Box Gaming Platform

Este documento registra fatos técnicos comprovados por evidência direta no código-fonte, testes e histórico de branches. Não registrar hipóteses ou suposições sem evidência.

---

### FACT-001: MainActivity é um monólito de ~1.620 linhas
- **Evidência**: `launcher-android/app/src/main/java/com/kiver/fireretro/MainActivity.java` possui exatamente 1.621 linhas.
- **Impacto**: Acopla ciclo de vida de UI, input handling (D-pad, controle, touch), renderização manual com Canvas/Bitmaps, requisições de rede, orquestração de download, inicialização de intents de emuladores e persistência local (`SharedPreferences` e arquivos JSON).

### FACT-002: Fluxo de pareamento permite autoaprovação e autoatribuição de privilégios
- **Evidência**: Em `cloudflare/worker/src/device-api.mjs`, `startPairing` gera `pairId` e `code` e os retorna diretamente na resposta HTTP do cliente (`approvalRequired: true`). A rota `POST /api/device/pair/approve` (exposta publicamente em `index.mjs` sem autenticação Cloudflare Access) recebe `{ pairId, code, name }` e marca o pareamento como `approved`.
- **Evidência adicional**: O campo `clientType` é fornecido pelo próprio cliente na chamada `startPairing` (`const clientType = body.clientType === 'importer' ? 'importer' : 'tv';`), permitindo que qualquer cliente solicite credencial com privilégios de importador/publicador.

### FACT-003: Importer exibe plataformas na UI sem suporte no pipeline
- **Evidência**: Em `importer-windows/src/JogosRetroImporter/Form1.cs` (linha 34), a combobox lista `["PlayStation", "NES", "SNES", "Mega Drive", "Game Boy Advance"]`. Porém, `JogosRetroImporter.Core/ImportPipeline.cs` implementa exclusivamente `PreparePlayStationAsync` e valida apenas extensões de disco PlayStation (.cue, .bin, .iso, .chd).
- **Impacto**: Promessa de funcionalidade não cumprida para plataformas de cartucho que não requerem conversão CHD.

### FACT-004: Duplo control plane de catálogo (Cloudflare vs GitHub privado)
- **Evidência**: Coexistem `manager-windows/GitHubCatalog.ps1` (que faz commit/push direto em repositório GitHub para atualizar catálogo) e `cloudflare/worker/src/admin-api.mjs` (que gerencia catálogo privado em Cloudflare KV e R2).
- **Impacto**: Clientes Android possuem dois mecanismos de sync (`RemoteLibrarySync.java` via GitHub Pages/raw e `CloudLibrarySync.java` via Cloudflare Worker API), gerando inconsistência de arquitetura e distribuição.

### FACT-005: Identidade de conteúdo instável e publicação não-idempotente no Cloudflare
- **Evidência**: Em `cloudflare/worker/src/admin-api.mjs`, cada chamada a `reserveUpload` cria um novo UUID (`const id = crypto.randomUUID()`) e armazena em `assets/${id}/${upload.filename}`. O identificador do item no catálogo final assume esse `upload.id` aleatório.
- **Impacto**: Dois uploads da mesma ROM geram dois objetos R2 distintos, dois IDs distintos e desperdício de cota de armazenamento sem detecção de duplicação.

### FACT-006: KV utilizado como repositório com operações concorrentes sem transação forte
- **Evidência**: `cloudflare/worker/src/admin-api.mjs` e `device-api.mjs` realizam leituras seguidas de escritas em `CATALOG_KV`, `DEVICE_KV`, `PAIRING_KV` e `RESERVATION_KV` (`getJson` -> modificação -> `put`).
- **Impacto**: Workers KV possui consistência eventual (eventual consistency) com latência de propagação global. Operações concorrentes de reserva de cota, aprovação e atualização de catálogo sofrem com *lost updates* e *race conditions*.

### FACT-007: Cálculo de cota R2 realiza varredura exaustiva a cada reserva
- **Evidência**: Em `cloudflare/worker/src/quota.mjs`, `calculateStorage(bucket)` executa um loop `do { bucket.list(...) } while(cursor)` varrendo todos os objetos com prefixo `assets/` a cada chamada de `reserveUpload`.
- **Impacto**: Esgota limites de operações R2 Class A/B gratuitas e introduz latência severa em catálogos em crescimento.

### FACT-008: Dependência rígida de caminhos legados `/sdcard` e permissões de storage antigas
- **Evidência**: `MainActivity.java` e `StoragePaths.java` codificam caminhos fixos: `/sdcard/roms/`, `/sdcard/RetroArch/playlists/...`, `/sdcard/Android/data/...`. `AndroidManifest.xml` define `targetSdkVersion="28"` com `READ_EXTERNAL_STORAGE` e `WRITE_EXTERNAL_STORAGE`.
- **Impacto**: Impede execução confiável em Android 10+ (API 29+ scoped storage) e dispositivos modernos sem suporte a caminhos emulados legados.

### FACT-009: Leanback exigido inviabiliza execução nativa em tablets e smartphones
- **Evidência**: `AndroidManifest.xml` linha 3 declara `<uses-feature android:name="android.software.leanback" android:required="true" />`.
- **Impacto**: Impede que a Google Play Store ou instaladores padrão ofereçam o app para tablets, smartphones e consoles portáteis (handhelds) sem interface Leanback nativa.

### FACT-010: Build Android artesanal restrito a Windows
- **Evidência**: `launcher-android/scripts/Build-FireRetro.ps1` invoca executáveis Windows específicos (`aapt2.exe`, `d8.bat`, `zipalign.exe`, `apksigner.bat`) com caminhos absolutos para JDK (`C:\Program Files\JetBrains\...`), impossibilitando execução em ambientes Linux/CI e exigindo listar manualmente cada classe Java compilada.

### FACT-011: Ausência de abstração de input e acoplamento a botões físicos
- **Evidência**: Em `MainActivity.java`, os métodos `onKeyDown` e `dispatchGenericMotionEvent` tratam dezenas de `KeyEvent.KEYCODE_*` e `MotionEvent.AXIS_*` espalhados diretamente no código da atividade, sem mapeamento para ações semânticas desacopladas (`GameAction`).

### FACT-012: RetroArch hardcoded como único emulador
- **Evidência**: `MainActivity.java` referencia `com.retroarch.ra32` de forma direta e estática para iniciar jogos e ler playlists. Não há contrato de provedor de emulação (`EmulatorProvider`) para suportar `ra64`, emuladores standalone (ex: DuckStation, PPSSPP, Mupen64Plus) ou outros runtimes.
