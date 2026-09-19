# ADR-R05: Separação Conceitual entre Source Artifact SHA-256 e Canonical Artifact SHA-256

## Contexto
Jogos retrô distribuídos em portais como Retrostic são disponibilizados como arquivos compactados (geralmente `.7z` ou `.zip`). O arquivo compactado possui um checksum SHA-256 específico (`sourceArtifactSha256`). No entanto, o arquivo que o emulador e a plataforma executam é o artefato final canônico (ex.: uma imagem de disco `.chd` convertida a partir de múltiplos arquivos `.bin`/`.cue`, ou uma ROM `.nes` descompactada e sanitizada). No modelo anterior, havia ambiguidade sobre qual hash representava a identidade do jogo no catálogo da nuvem.

## Decisão
Estabelecer a separação estrita e imutável de identidades criptográficas:
1. **`sourceArtifactSha256`**:
   - Checksum SHA-256 do arquivo bruto de distribuição obtido da fonte (ex.: `.7z` baixado do Retrostic).
   - Utilizado exclusivamente para verificação de integridade pós-download e deduplicação de cache de aquisição no staging.
2. **`canonicalArtifactSha256`**:
   - Checksum SHA-256 do arquivo final pronto para execução (ex.: `.chd` verificado pelo `chdman verify`, ou ROM limpa).
   - Este hash define o `contentId` global no catálogo do ecossistema:
     ```text
     contentId = $"sha256:{canonicalArtifactSha256.ToLowerInvariant()}"
     ```
3. **Imutabilidade e Rastreabilidade**:
   - O manifesto publicado no catálogo armazena ambos os hashes, registrando a linhagem (proveniência) do artefato: de qual arquivo fonte ele foi gerado e qual ferramenta/versão de conversão foi empregada.

## Consequências
- Fim da ambiguidade: clientes de execução (Fire TV, Android TV, Android Gamer) buscam e verificam o jogo via `canonicalArtifactSha256`.
- Reproduzibilidade: múltiplos downloads de fontes diferentes que resultem na mesma ROM canônica colidem positivamente no mesmo `contentId`, economizando armazenamento e tráfego de nuvem.

## Status
Aceito.
