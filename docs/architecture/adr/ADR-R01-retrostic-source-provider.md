# ADR-R01: Retrostic como Source Provider Primário e Desacoplamento do Pipeline

## Contexto
O importador original operava estritamente sobre arquivos locais pré-baixados pelo operador via drag-and-drop. Para profissionalizar a aquisição de jogos e viabilizar catálogos dinâmicos integrados, o portal `https://www.retrostic.com/roms` foi definido como o provedor oficial de catálogo e download do ecossistema `games-tvbox`. Contudo, acoplar diretamente o pipeline de preparação e publicação às particularidades do website criaria fragilidade operacional e impediria o reaproveitamento futuro em outros clientes (ex: Android Gamer).

## Decisão
Desacoplar a aquisição em uma interface formal `IGameSourceProvider`:
```text
Catalog / UI
    ↓
IGameSourceProvider (SearchAsync, GetDetailsAsync, ResolveDownloadAsync)
    ↓
RetrosticSourceProvider (Implementação oficial Retrostic)
    ↓
DownloadDescriptor (URL, Headers, Size, Checksum, Resumable)
    ↓
DownloadManager (Staging de transferência)
    ↓
IntakePipeline (Descompactação, conversão CHD e validação)
    ↓
CloudPublisherClient (R2 + Catalog API)
```
- A camada de pipeline nunca sabe se o arquivo veio de download do Retrostic ou do disco local; ela consome exclusivamente descritores canônicos.
- O `RetrosticSourceProvider` encapsula autenticação, rotas de busca, paginação, parsing e resolução de links do Retrostic.

## Consequências
- Isolamento completo: alterações no layout ou CDN do Retrostic afetam unicamente o provider, sem tocar no pipeline ou no armazenamento.
- Facilidade de teste: mocks de `IGameSourceProvider` permitem testar 100% da ingestão e publicação sem rede externa.
- Reusabilidade: o mesmo contrato pode ser portado para Kotlin/Android no Android Gamer.

## Status
Aceito.
