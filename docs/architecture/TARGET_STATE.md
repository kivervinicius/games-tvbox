# Arquitetura Alvo — Gaming Platform Multi-Device

**Data**: Setembro/2026  
**Status**: Proposta Arquitetural e Contratos Canônicos

---

## 1. Visão Geral da Arquitetura Alvo

A evolução de **Games TV Box** para uma **Gaming Platform Multi-Device** fundamenta-se na separação estrita entre:

```text
Operating System / Platform (Android, Fire OS, futuros: Linux, Windows)
        ↓
Runtime Capabilities (ABI, Android API, display, gamepads, touch, storage livre)
        ↓
Device Profile (FIRE_TV, ANDROID_TV, ANDROID_TV_TCL, ANDROID_TABLET, ANDROID_PHONE, ANDROID_GAMER)
        ↓
UX / Input / Storage / Runtime Adaptations
```

```text
                 ┌──────────────────────────────────────┐
                 │       Cloud Control Plane            │
                 │   - LibraryCoordinator (DO/SQLite)   │
                 │   - Content-Addressed R2 Storage     │
                 │   - CompatibilityEngine              │
                 │   - Scoped Credentials & Admin RBAC  │
                 └──────────────────┬───────────────────┘
                                    │
                         Cloud API v1 (HTTPS REST)
                                    │
                                    ▼
       ┌─────────────────────────────────────────────────────────┐
       │             Shared Android Application Core             │
       ├─────────────────┬───────────────────┬───────────────────┤
       │  RomStorage     │   InputManager    │  EmulatorProvider │
       │  (Strategies)   │   (GameAction)    │  (RA32/RA64/etc.) │
       └────────┬────────┴─────────┬─────────┴─────────┬─────────┘
                │                  │                   │
    ┌───────────┴────────┐ ┌───────┴────────┐ ┌────────┴────────┐
    │      TV Mode       │ │   Gamer Mode   │ │  Adaptive UI    │
    │  - Fire TV         │ │  - Handhelds   │ │  - Tablets      │
    │  - Android TV/TCL  │ │  - Smartphones │ │  - Phones       │
    │  - D-pad / 10-foot │ │  - Dashboard   │ │  - Touch/Hybrid │
    └────────────────────┘ └────────────────┘ └─────────────────┘
```

---

## 2. Contratos Canônicos de Backend

### 2.1 Identidade Canônica de Conteúdo (`contentId`)
Todo item e blob de mídia/ROM possui identidade canônica e determinística baseada no hash SHA-256 do seu conteúdo:

```text
blobSha256 = SHA256(bytes)
contentId = "sha256:" + blobSha256
```

Estrutura de armazenamento no Cloudflare R2:
```text
blobs/
  sha256/
    ab/
      abcdef0123456789...
```
Dois uploads do mesmo arquivo utilizam o mesmo blob subjacente, tornando a publicação idempotente e imune a duplicações físicas.

### 2.2 Coordenador Transacional (`LibraryCoordinator`)
- Em vez de leituras e escritas não-atômicas em múltiplos namespaces KV, as operações de escrita, contagem de cota, aprovação de pareamento, criação de credenciais e publicação passam por um coordenador transacional com armazenamento fortemente consistente (Durable Object com SQLite embutido ou camada relacional transacional).
- KV é mantido como camada de cache e distribuição de leitura de alta velocidade (read-heavy), atualizado a partir de eventos do coordenador.
- Quota: contadores transacionais mantidos no estado (`usedBytes`, `reservedBytes`, `objectCount`), eliminando varreduras integrais no R2 (`list()`).

### 2.3 Fluxo Seguro de Pareamento e Escopos de Autorização
1. **Dispositivo** inicia pareamento:
   `POST /api/device/pair/start`
   - Envia: `deviceId`, `model`, `platform`, `capabilities`.
   - Recebe: `pairId`, `userCode` (código curto legível para exibição na tela), `expiresAt`.
   - **IMPORTANTE**: O dispositivo **não** recebe segredos nem credenciais nesta etapa.
2. **Administrador Autenticado** (via Cloudflare Access):
   `GET /api/admin/pairings` -> lista pendências.
   `POST /api/admin/pairings/{pairId}/approve`
   - Envia: `deviceName`, `profileId`, `assignedGroup`, `scopes` (ex: `["catalog.read", "asset.download", "device.state.write"]`).
   - Somente o administrador autenticado pode aprovar e conceder privilégios. O endpoint público de aprovação é sumariamente removido.
3. **Dispositivo** consulta conclusão:
   `POST /api/device/pair/complete`
   - Dispositivo envia `pairId` + prova de posse.
   - Recebe `deviceToken` com seus scopes e profile atribuídos.

### 2.4 Matriz de Escopos (Capabilities/Scopes)
- `tv`: `["catalog.read", "asset.download", "device.state.write"]`
- `gamer`: `["catalog.read", "asset.download", "device.state.write", "apps.install", "updates.read"]`
- `importer`: `["catalog.read", "asset.upload", "catalog.publish", "metadata.publish"]`
- `admin`: `["*"]`

---

## 3. Contratos de Dispositivo e Compatibilidade

### 3.1 Device Capabilities Handshake
Na inicialização e sincronização, o cliente envia suas capabilities de hardware/software:

```json
{
  "platform": "android",
  "formFactor": "tv|phone|tablet|handheld",
  "manufacturer": "TCL",
  "model": "50P725",
  "androidApi": 31,
  "abis": ["arm64-v8a", "armeabi-v7a"],
  "touch": false,
  "gamepad": true,
  "dpad": true,
  "leanback": true,
  "playStore": true,
  "amazonAppstore": false,
  "usbStorage": true,
  "removableStorage": false,
  "freeBytes": 8589934592,
  "display": {
    "width": 3840,
    "height": 2160,
    "refreshRates": [60.0]
  }
}
```

### 3.2 CompatibilityEngine
Motor executado no Cloudflare (e espelhado localmente para offline-first):
- Avalia itens do catálogo contra as capabilities do dispositivo:
  - ABI compatível (`arm64-v8a`, `armeabi-v7a`, `x86_64`)
  - Versão mínima de API Android
  - Espaço em disco suficiente (`freeBytes > item.size`)
  - Requisitos de input (Touch vs Gamepad vs D-pad)
- Status retornado para cada item:
  - `SUPPORTED`: Pronto para download e execução.
  - `PARTIALLY_SUPPORTED`: Funcional com ressalvas (ex: requer gamepad externo em tablet).
  - `RUNTIME_MISSING`: Requer emulador ou core adicional.
  - `INCOMPATIBLE_ABI`: APK incompatível com a CPU do aparelho.
  - `INCOMPATIBLE_API`: Versão do Android insuficiente.
  - `INSUFFICIENT_STORAGE`: Espaço insuficiente para instalação.
  - `INPUT_UNAVAILABLE`: Falta método de entrada obrigatório.

---

## 4. Arquitetura Android Alvo

### 4.1 Decomposição de `MainActivity`
A atividade principal deixa de acumular lógica de negócio e atua como orquestrador de UI/navegação:

```text
com.kiver.fireretro
├── core/
│   ├── model/ (Game, App, Theme, DeviceProfile, GameAction)
│   ├── usecase/ (SyncCatalogUseCase, LaunchGameUseCase, InstallAppUseCase)
│   └── compatibility/ (LocalCompatibilityEngine)
├── input/
│   ├── InputManager.java
│   ├── GameAction.java
│   ├── GamepadAdapter.java
│   ├── RemoteAdapter.java
│   └── TouchAdapter.java
├── storage/
│   ├── RomStorage.java (interface)
│   ├── AppStorageStrategy.java
│   ├── LegacyStorageStrategy.java
│   ├── RemovableStorageStrategy.java
│   └── UsbStorageStrategy.java
├── emulation/
│   ├── EmulatorProvider.java (interface)
│   ├── RetroArchProvider.java
│   └── StandaloneEmulatorProvider.java
├── ui/
│   ├── tv/ (10-foot focus navigation, safe areas)
│   ├── gamer/ (Landscape gaming dashboard, quick launch, battery/storage status)
│   ├── dialogs/
│   └── components/
└── data/
    ├── CloudDeviceClient.java
    ├── DownloadManager.java
    └── CatalogRepository.java
```

### 4.2 Abstração de Input (`GameAction`)
Eventos de hardware são traduzidos em ações lógicas do sistema:
- `UP`, `DOWN`, `LEFT`, `RIGHT`
- `ACCEPT` (A / Botão Sul / Enter)
- `BACK` (B / Botão Leste / Escape)
- `MENU` (Start / Menu)
- `SEARCH` (Y / Triângulo / Tecla de busca)
- `FAVORITE` (X / Quadrado)
- `QUICK_SETTINGS` (L1/R1 ou atalho dedicado)

### 4.3 Modo Android Gamer
- **Proposta**: Transformar smartphone, tablet ou console portátil (ex: Odin, Retroid, celular com controle telescópico USB-C/Bluetooth) em console dedicado.
- **Diretrizes**:
  - Immersive full-screen permanente.
  - Orientação prioritária Landscape.
  - Tela principal com Gaming Dashboard: jogos recentes, favoritos, status de armazenamento, status de bateria/carregamento, atalho para remapeamento de controles.
  - Suporte completo a navegação por controle físico como prioridade, com overlay ou controles de touch contextuais como fallback.

### 4.4 Build Android Padronizado (Gradle)
- Adição de `build.gradle` padrão com Android Gradle Plugin (AGP), permitindo compilação nativa em Linux, macOS e Windows sem depender de scripts artesanais.
- Configuração de build variants/flavors (`tv`, `gamer`) compartilhando o núcleo (`shared core`).
