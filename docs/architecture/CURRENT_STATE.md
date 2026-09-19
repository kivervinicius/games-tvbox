# Auditoria do Estado Atual — Games TV Box / Jogos Retro

**Data da Auditoria**: Setembro/2026  
**Branch Auditada**: `feature/cloud-admin` (comparada a `main` e `HEAD`)  
**Status**: Concluída com evidências de código

---

## 1. Visão Geral da Arquitetura Atual

O ecossistema `games-tvbox` é atualmente composto por 5 subsistemas principais:

```text
┌─────────────────────────┐       ┌────────────────────────┐
│   manager-windows/      │       │   importer-windows/    │
│  PowerShell 7 + Win32   │       │ .NET 8 WinForms App    │
└────────────┬────────────┘       └───────────┬────────────┘
             │ (ADB / Git)                    │ (Cloud Publisher API)
             ▼                                ▼
┌──────────────────────────────────────────────────────────┐
│              cloudflare/ (Cloud Plane)                   │
│  - Worker: API admin, API device, API importer           │
│  - KV: CATALOG_KV, DEVICE_KV, PAIRING_KV, RESERVATION_KV │
│  - R2: PRIVATE_ASSETS (games-tvbox-private)              │
│  - Cloudflare Access: autenticação admin JWT             │
└────────────────────────────┬─────────────────────────────┘
                             │ (HTTPS REST API + Signed URLs)
                             ▼
┌──────────────────────────────────────────────────────────┐
│              launcher-android/ (Client)                  │
│  - Android 9 (API 28), Leanback-first                    │
│  - MainActivity (~1.620 linhas monolíticas)              │
│  - Storage legado (/sdcard/roms, /storage)               │
│  - Execução RetroArch (com.retroarch.ra32 hardcoded)     │
└──────────────────────────────────────────────────────────┘
```

---

## 2. Comparativo Detalhado: `main` vs `feature/cloud-admin` vs `HEAD`

- **`main` (Baseline v0.6.2)**:
  - Focado exclusivamente em Fire TV Stick com RetroArch 32-bit (`com.retroarch.ra32`).
  - Distribuição via ADB local gerenciado por scripts PowerShell (`manager-windows/FireRetroManager.ps1`).
  - Catálogo público opcional via GitHub Pages (`catalog-site/`).
  - Build Android artesanal baseado em script PowerShell (`Build-FireRetro.ps1`) invocando ferramentas Windows.

- **`feature/cloud-admin` (13 commits à frente de `main`)**:
  - Introduziu Cloudflare Worker (`cloudflare/`) para servir como biblioteca privada e painel de administração (`/admin`).
  - Introduziu aplicativo desktop C# WinForms (`importer-windows/`) para converter e publicar jogos PlayStation em formato CHD.
  - Adicionou clientes de sincronização de nuvem no Android (`CloudDeviceClient.java`, `CloudLibrarySync.java`).
  - Adicionou suporte a instalação de APKs privados validados por assinatura de certificado (`AndroidAppInstaller.java`).
  - Adicionou temas customizáveis remotos e navegação de configurações.

---

## 3. Análise de Cada Subsistema

### 3.1 Cloudflare (Control Plane e Armazenamento)
- **Worker**: Implementado em JavaScript ESM puro (`worker/src/*.mjs`). Rotas divididas em `/api/admin/*`, `/api/device/*`, `/api/importer/*`, `/pair/`.
- **KV Storage**:
  - `CATALOG_KV`: Catálogo ativo serializado sob a chave `catalog:active`.
  - `DEVICE_KV`: Mapeamento `token:<hash>` -> `deviceId` e metadados em `device:<deviceId>`.
  - `PAIRING_KV`: Sessões temporárias `pair:<pairId>` e controle de concorrência `active:<deviceId>`.
  - `RESERVATION_KV`: Reservas de upload `upload:<id>`.
- **Falha Crítica (P0.1)**: O fluxo de pareamento permite autoaprovação porque a chave pública `/api/device/pair/approve` aceita o `pairId` e o `code` que foram entregues ao próprio dispositivo no `/start`. Não exige sessão de administrador autenticado para concluir. O cliente também pode enviar `clientType: "importer"` sem qualquer barreira de autorização.
- **Falha Crítica (P0.2)**: Uploads de arquivos criam chaves de objeto no R2 com UUID aleatório (`assets/<uuid>/<filename>`). Se o mesmo arquivo for enviado duas vezes, criam-se duas cópias físicas no R2 e duas entradas no catálogo.
- **Falha Crítica (P0.3)**: Todas as mutações concorrentes utilizam KV com padrão read-modify-write sem transação atômica. Leituras subsequentes podem retornar estados desatualizados gerando perda de atualizações.
- **Falha de Performance**: O cálculo de cota em `quota.mjs` lista todos os objetos R2 via `bucket.list()` a cada reserva de upload.

### 3.2 Launcher Android (`launcher-android`)
- **Arquitetura**:
  - Monólito centrado em `MainActivity.java` (1.621 linhas).
  - Concentra desenho em Canvas, loop do carrossel, inputs de teclado, controle e touch, download assíncrono via `HttpURLConnection`, verificação SHA-256 e chamadas de intent para o RetroArch.
- **Storage**:
  - Dependência estrita de caminhos raiz `/sdcard/roms`, `/sdcard/Android/data/com.kiver.fireretro/files/`.
  - Permissões legadas no manifesto: `READ_EXTERNAL_STORAGE` e `WRITE_EXTERNAL_STORAGE` com `targetSdkVersion="28"`.
- **Leanback e Form Factors**:
  - `android.software.leanback` com `required="true"`. Bloqueia instalação em tablets e celulares via Play Store / instaladores compatíveis.
  - Orientação fixa em `landscape`.
- **Input**:
  - Eventos de controle interceptados diretamente em `onKeyDown` e `dispatchGenericMotionEvent`.
  - Falta uma camada semântica de mapeamento (`GameAction`).

### 3.3 Importer Windows (`importer-windows`)
- **C# / .NET 8**:
  - WinForms para Windows 10/11.
  - Depende de ferramentas externas (`chdman.exe` e `7z.exe`).
  - Pipeline suporta somente PlayStation (`PreparePlayStationAsync`).
  - A interface lista outras plataformas ("NES", "SNES", "Mega Drive", "Game Boy Advance") sem suporte correspondente no pipeline.
  - Não valida SHA-256 contra identidade canônica de conteúdo global.

### 3.4 Manager PowerShell (`manager-windows`)
- Script PowerShell `FireRetroManager.ps1` com menu interativo para conexão ADB, verificação de integridade, envio de APK, temas e ROMs.
- `GitHubCatalog.ps1` armazena e lê catálogos usando commits da API do GitHub, exigindo token PAT do GitHub e dependendo de APIs DPAPI do Windows.

---

## 4. Matriz de Conformidade Inicial

| Requisito / Área | Estado Atual | Conformidade | Ação Necessária |
| :--- | :--- | :--- | :--- |
| **Pareamento Seguro** | Autoaprovável via código público | ❌ Não Conforme (P0.1) | Remover endpoint público; exigir admin autenticado + scopes |
| **Identidade de Conteúdo** | UUID aleatório por upload | ❌ Não Conforme (P0.2) | Restaurar `sha256:<hash>` e R2 content-addressing |
| **Persistência Cloud** | KV read-modify-write eventual | ❌ Não Conforme (P0.3) | Implementar LibraryCoordinator (Durable Object / SQLite) |
| **Cota e Escaneamento R2** | Full scan a cada upload | ❌ Não Conforme | Contadores transacionais atômicos + reconcile command |
| **Multi-device Profiles** | Somente Fire TV / Leanback hardcoded | ❌ Não Conforme | DeviceProfile (TV, Tablet, Phone, Gamer) |
| **Android Gamer Mode** | Inexistente | ❌ Não Conforme | Modo dedicado, dashboard, landscape, touch fallback |
| **Input Abstraction** | KeyCodes espalhados em MainActivity | ❌ Não Conforme | `InputManager` + `GameAction` desacoplado |
| **Storage Abstraction** | Hardcoded `/sdcard/roms` | ❌ Não Conforme | `RomStorage` com strategies (App, Scoped, Removable, Usb) |
| **Build Android** | Script PowerShell chamando binários Win32 | ❌ Não Conforme | Migrar para Gradle padrão (reproduzível em Linux/CI) |
| **Importer Multi-plataforma** | UI anuncia 5 plataformas, suporta 1 | ❌ Não Conforme | Centralizar PlatformRegistry; alinhar UI e Core |
