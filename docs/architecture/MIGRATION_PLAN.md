# Plano de Migração Incremental — Games TV Box Gaming Platform

**Estratégia**: Evolução sem reescrita big-bang. Todas as etapas preservam dados locais, retrocompatibilidade e integridade do catálogo e ROMs existentes.

---

## Fases da Migração

```text
┌─────────────────────────────────────────────────────────────┐
│ FASE 0: Correções Críticas de Segurança e Dados (P0)        │
│ 1. Fechar autoaprovação pública de pareamento               │
│ 2. Introduzir Scopes e Controle Administrativo de Pareamento│
│ 3. Restaurar identidade canônica por SHA-256 e idempotência │
│ 4. Coordenador transacional (Durable Object / SQLite)        │
│ 5. Eliminar varredura exaustiva de R2 na cota               │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ FASE 1: Contratos Canônicos e Compatibility Engine          │
│ 1. PlatformRegistry unificado                               │
│ 2. Contrato de Device Capabilities e DeviceProfile          │
│ 3. CompatibilityEngine no Cloud Control Plane               │
│ 4. Padronização de erros ({ error: { code, message... } })  │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ FASE 2: Abstrações do Cliente Android                       │
│ 1. InputManager e mapeamento semântico GameAction           │
│ 2. RomStorage e estratégias de armazenamento                │
│ 3. EmulatorProvider (RetroArch 32/64 + standalone)          │
│ 4. Decomposição incremental de MainActivity                 │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ FASE 3: Experiências Multi-Device e Modo Android Gamer      │
│ 1. DeviceProfiles (FIRE_TV, ANDROID_TV, GAMER, TABLET...)   │
│ 2. Implementação dedicada de Gamer Mode                     │
│ 3. Ajuste de manifesto (Leanback opcional, permissões)      │
│ 4. DownloadManager robusto e verificação de integridade     │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ FASE 4: Importer Windows e Build Padronizado                │
│ 1. PlatformRegistry compartilhado no Importer .NET          │
│ 2. Alinhamento da UI com suporte real                       │
│ 3. Build Gradle reproduzível no Android                     │
│ 4. Matriz de testes de regressão e CI                       │
└─────────────────────────────────────────────────────────────┘
```

---

## Detalhamento das Etapas e Commits Sequenciais

### Fase 0 — P0 Fixes
1. **P0.1 Pareamento e Scopes**:
   - Deletar rota pública `/api/device/pair/approve`.
   - Exigir autorização administrativa em `/api/admin/pairings/{pairId}/approve`.
   - O admin seleciona perfil e escopos.
   - O dispositivo não dita seu `clientType`.
   - Teste: verificar bloqueio de autoaprovação e concessão de token com escopo.
   - Commit: `fix(security): close public pairing approval and enforce admin authorization`

2. **P0.2 Identidade Canônica por SHA-256**:
   - `contentId = sha256:<hash>`.
   - Chaves R2 estruturadas em `blobs/sha256/{prefix}/{hash}`.
   - Reenvio do mesmo hash reutiliza o blob e atualiza metadados sem duplicar bytes.
   - Commit: `feat(catalog): restore content-addressed identity and idempotent publication`

3. **P0.3 Coordenador Transacional e Cota**:
   - Implementar `LibraryCoordinator` para gerenciar estado de devices, pairings, reservas e cotas atômicas.
   - Atualizar contador `usedBytes` e `reservedBytes` sem scan R2.
   - Adicionar comando/função de conciliação `reconcileStorage`.
   - Commit: `feat(cloud): add transactional library coordinator and atomic quota tracking`

### Fase 1 — Contratos Canônicos e Compatibilidade
4. **PlatformRegistry Canônico**:
   - Definir schema canônico de plataformas (NES, SNES, Genesis, GBA, PS1, N64) com extensões, cores recomendados, nomes e requisitos de runtime.
   - Commit: `feat(platform): create canonical platform registry`

5. **Device Capabilities e CompatibilityEngine**:
   - Criar modelo de capabilities (ABI, API, inputs, storage, display).
   - Implementar `CompatibilityEngine` que avalia compatibilidade de ROMs, cores e APKs contra o dispositivo.
   - Commit: `feat(cloud): introduce device capabilities and compatibility engine`

### Fase 2 — Abstrações Android
6. **Input Abstraction**:
   - Criar enum `GameAction` (UP, DOWN, LEFT, RIGHT, ACCEPT, BACK, MENU, SEARCH, FAVORITE, QUICK_SETTINGS).
   - Criar `InputManager` para desacoplar KeyEvents e Axis Motions da UI.
   - Commit: `feat(input): add game action abstraction and input manager`

7. **Storage Abstraction**:
   - Criar interface `RomStorage` com implementações: `AppStorageStrategy`, `LegacyExternalStorageStrategy`, `RemovableStorageStrategy`, `UsbStorageStrategy`.
   - Commit: `feat(storage): introduce modular rom storage strategies`

8. **Emulator Provider Abstraction**:
   - Criar interface `EmulatorProvider` e implementação `RetroArchProvider` (suportando ra32 e ra64).
   - Commit: `feat(emulation): add emulator provider abstraction`

### Fase 3 — Android Multi-Device & Gamer Mode
9. **DeviceProfile & Gamer Mode**:
   - Implementar classes de profile no Android (`DeviceProfile`, `GamerProfile`, `TvProfile`).
   - Criar UI adaptativa para o Gamer Mode (Gaming Dashboard, landscape-first, touch fallback, status de sistema).
   - Atualizar `AndroidManifest.xml` para tornar leanback opcional (`required="false"`).
   - Commit: `feat(android): implement multi-device profiles and dedicated gamer mode`

10. **Decomposição da MainActivity**:
    - Extrair coordenação de navegação, sync e ciclo de vida para componentes especializados.
    - Commit: `refactor(android): decompose main activity into modular components`

### Fase 4 — Importer, Build & Verificação
11. **Importer .NET Core Refactoring**:
    - Integrar `PlatformRegistry` no Importer C#.
    - Adequar UI para exibir status real de cada plataforma.
    - Commit: `feat(importer): integrate canonical platform registry and accurate ui status`

12. **Build Android com Gradle**:
    - Configurar `build.gradle` e `settings.gradle` com AGP para compilação multiplataforma.
    - Commit: `build(android): configure gradle build system for tv and gamer variants`

13. **Testes, Documentação e Matriz de Aceitação**:
    - Test matrix cobrindo Fire TV, TCL TV, Tablet, Smartphone e Android Gamer.
    - Atualizar ADRs e documentação de entrega.
    - Commit: `docs: complete multi-device gaming platform architecture and test matrix`
