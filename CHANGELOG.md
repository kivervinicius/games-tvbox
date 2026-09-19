# Changelog

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
