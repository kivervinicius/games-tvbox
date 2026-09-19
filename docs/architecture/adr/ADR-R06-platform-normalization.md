# ADR-R06: Normalização de Plataformas e Estratégias de Conversão

## Contexto
O importador original processava exclusivamente PlayStation 1 através de um método monolítico `PreparePlayStationAsync`. Sistemas de cartucho (Nintendo Entertainment System, Super Nintendo, Sega Genesis/Mega Drive, Game Boy Advance) possuem requisitos distintos de validação (ex.: inspeção de cabeçalhos iNES, checagem de tamanho sem conversão para CHD). Para suportar o catálogo multi-plataforma do Retrostic, o motor de ingestão deve ser extensível e determinístico.

## Decisão
Implementar o padrão Strategy para processamento de plataformas no `IntakePipeline`:
```text
IntakePipeline.ProcessAsync(sourceFile, platformId)
        ↓
PlatformStrategyRegistry.GetStrategy(platformId)
   ┌────┴──────────────────────────┐
   ↓                               ↓
PlayStationImportStrategy        CartridgeImportStrategy
- Extração de arquivo            - Extração de arquivo
- Validação de CueSheet/BIN      - Validação de extensão/header
- Conversão chdman createcd      - Passthrough sem conversão
- Verificação chdman verify      - Validação de tamanho e hash
- Libretro metadata fetch        - Libretro metadata fetch
   └────┬──────────────────────────┘
        ↓
CanonicalArtifact (FilePath, CanonicalSha256, SourceSha256, SizeBytes, Metadata)
```

1. **PlayStation (PS1)**:
   - Converte pares CUE/BIN ou imagens ISO para CHD utilizando o runner do `chdman`.
   - Executa `chdman verify` para atestar a integridade física do disco compactado.
2. **Cartuchos (NES, SNES, Mega Drive, GBA)**:
   - Extrai o arquivo do archive `.7z`/`.zip`.
   - Valida extensão e sanidade do arquivo.
   - Aplica passthrough (cópia direta do arquivo canônico sem compressão com perda).
   - Extrai metadados oficiais e calcula o hash canônico.

## Consequências
- Código limpo, testável isoladamente por plataforma.
- Adição de novos sistemas (ex: Nintendo 64, PSP, Dreamcast) requer apenas a criação de uma nova classe que implementa `IPlatformImportStrategy`.

## Status
Aceito.
