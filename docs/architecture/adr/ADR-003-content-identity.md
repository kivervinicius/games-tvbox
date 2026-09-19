# ADR-003: Content Identity & Idempotence

## Contexto
No estado anterior de `feature/cloud-admin`, cada upload recebia um UUID aleatório e gerava um novo caminho em `assets/<uuid>/<filename>`, desconectando o conteúdo de sua identidade criptográfica. Múltiplos uploads do mesmo arquivo geravam duplicação no R2 e entradas duplicadas no catálogo.

## Decisão
Restaurar a identidade canônica orientada a conteúdo (*content-addressed storage*):
- `blobSha256 = SHA256(bytes)`
- `contentId = "sha256:" + blobSha256`
- Estrutura de armazenamento no Cloudflare R2:
  `blobs/sha256/{prefix}/{blobSha256}`

A identidade do blob físico é estritamente separada dos metadados descritivos mutáveis (título, categoria, ano, capa). Se o mesmo blob for submetido novamente, a operação é idempotente: o blob existente é reutilizado e apenas os metadados são versionados.

## Consequências
- Idempotência garantida para retentativas de upload.
- Economia de armazenamento e eliminação de duplicação física no R2.
- Verificação universal de integridade no cliente usando o mesmo hash como chave primária.

## Status
Aceito.
