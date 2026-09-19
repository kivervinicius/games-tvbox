# Active Spec — Gaming Platform Multi-Device

## Goal
Evoluir Games TV Box / Jogos Retro para uma plataforma sustentável de Gaming Multi-Device (Fire TV, Android TV, TVs TCL, tablets, smartphones e Android Gamer), eliminando vulnerabilidades de segurança (P0.1, P0.2, P0.3), unificando fontes de verdade na Cloudflare e estabelecendo contratos canônicos e código limpo desacoplado.

## In Scope
- Fechar autoaprovação pública de pareamento e implementar aprovação via admin autenticado com perfis e escopos granulares (`scopes`).
- Restaurar identidade estável e canônica por conteúdo (`contentId = sha256:...`) e publicação R2 idempotente.
- Substituir operações concorrentes em KV por coordenador transacional (`LibraryCoordinator` Durable Object / SQLite), com contagem atômica de cota.
- PlatformRegistry unificado de plataformas e emuladores.
- Handshake de Device Capabilities e CompatibilityEngine no backend.
- Abstrações no Android Core: InputManager (GameAction), RomStorage (Modular Strategies), EmulatorProvider.
- Android Gamer Mode com Gaming Dashboard, landscape imersivo, touch fallback e quick settings.
- Build Android padronizado via Gradle com variantes (`tv`, `gamer`).
- Importer Windows alinhado com o PlatformRegistry e status real na UI.
- Matriz de testes de regressão e aceitação automatizada.

## Out Of Scope
- Reescrita big-bang da UI em Jetpack Compose neste ciclo.
- Apagar ROMs, saves ou configurações locais dos dispositivos.
- Descontinuação do suporte ao RetroArch 32-bit ou aos dispositivos Fire TV existentes.

## Acceptance
- Bypass de autoaprovação de pareamento completamente fechado.
- Autorização de clientes baseada em escopos concedidos pelo admin.
- Conteúdo publicado de forma idempotente com chave `sha256:...`.
- Controle transacional de cota e pareamento sem scan integral no R2.
- DeviceProfile e CompatibilityEngine operacionais.
- Suporte comprovado a Fire TV, Android TV/TCL, Tablet, Smartphone e Android Gamer.
- InputManager funcional traduzindo teclas e eixos em GameAction.
- RomStorage suportando armazenamento local e externo/USB sem permissões arriscadas.
- MainActivity modularizada com delegação de responsabilidades.
- Build Gradle reproduzível e testes automatizados 100% verdes.

## Verification Plan
- Executar `node --test cloudflare/tests/*.test.mjs` cobrindo pareamento seguro, scopes, upload idempotente e coordinator.
- Compilar e executar testes Java cobrindo GameAction, InputManager, RomStorage, EmulatorProvider e DeviceProfile.
- Executar testes C# do Importer cobrindo o PlatformRegistry unificado.
- Executar scripts de validação de layout e documentação.

## Status
- Phase: execute
- Status: in_progress
- Next gate: test-verification
- Started at: 2026-09-19
