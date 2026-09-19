# ADR-R07: Publicação Atômica na Cloud (Assets + Catalog)

## Contexto
A publicação de um jogo na nuvem (Cloudflare R2 + API do catálogo) envolve o envio de múltiplos artefatos: o arquivo da ROM/CHD (centenas de megabytes) e a capa/screenshot (poucos kilobytes), seguidos pelo registro dos metadados no catálogo central. Na implementação anterior, cada arquivo gerava uma chamada isolada de publicação. Em caso de instabilidade na conexão após o upload da capa, mas antes do upload da ROM, o catálogo ficava em estado inconsistente ou órfão.

## Decisão
Implementar a publicação atômica transacional no cliente `CloudPublisherClient`:
1. **Fase de Reserva e Upload de Blobs**:
   - Cada artefato (ROM canônica e imagem de capa) é reservado via `api/importer/uploads` e enviado diretamente ao Cloudflare R2 com hash SHA-256 verificado.
   - Os uploads são finalizados via `api/importer/uploads/{id}/finalize`.
2. **Commit Atômico do Catálogo**:
   - A publicação no catálogo ocorre em uma única chamada HTTP POST para `api/importer/publications`, enviando o batch completo de uploads e os metadados do jogo vinculados.
   - O backend valida a presença e o hash de todos os IDs de upload referenciados antes de criar o registro no catálogo e atualizar a revisão global.
   - Se qualquer upload falhar ou estiver ausente, a publicação é rejeitada sem modificar o catálogo.

## Consequências
- Garantia de consistência: o catálogo nunca exibe um jogo sem que seus binários e capas estejam disponíveis e verificados no armazenamento R2.
- Idempotência: caso o operador reimporte o mesmo jogo, o backend detecta a existência prévia pelo hash canônico e evita duplicações desnecessárias.

## Status
Aceito.
