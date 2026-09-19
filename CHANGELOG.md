# Changelog

## 0.6.2 — 2026-09-19

### Added

- importador Windows reproduzível para 7z/zip, BIN/CUE, ISO e CHD, com cache protegido, capas e metadados revisáveis;
- biblioteca remota sob demanda com tamanho, SHA-256, reserva de espaço e funcionamento offline;
- navegação de TV com foco estável por linha e diagnóstico de controles.

### Changed

- saída do jogo usa somente Select + Start; atalhos diretos e L3 não encerram mais o jogo;
- leituras do catálogo não gravam telemetria, usam ETag e atualizam presença do aparelho de forma limitada;
- builds de release publicam checksums, SBOM e preservam a assinatura estável do aplicativo.

### Fixed

- esquerda/direita permanecem na linha de cards em vez de saltar para o menu lateral;
- repetição de teclas e foco são tratados por um coordenador único;
- limite diário de KV é exibido como estado recuperável e não impede o catálogo local.

## 0.3.2 — 2026-09-14

### Added

- launcher Android com biblioteca visual, filtros, busca, favoritos e carrossel;
- Manager Windows com ADB, cache local e personalização;
- exemplos de catálogo, tema, RetroArch e controle;
- manual, arquitetura, troubleshooting e auditorias públicas.

### Changed

- capas foram otimizadas para JPG para reduzir o tamanho do APK;
- build passou a exigir keystore externo, sem chave privada no projeto;
- tema público padrão não contém nomes ou imagens pessoais.

### Fixed

- caminhos do Manager foram tornados relativos ao checkout público;
- catálogo e capas do Manager passaram a ficar no cache local.
## 0.6.1

- Detecta armazenamento removível do Fire TV e usa o pendrive para ROMs e downloads da biblioteca online.
- Migra referências lógicas do catálogo sem apagar a cópia interna.
- Atualiza favoritos do RetroArch com backup da playlist anterior.
