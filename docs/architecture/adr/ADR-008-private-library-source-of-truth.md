# ADR-008: Private Library Source of Truth & Control Plane

## Contexto
O ecossistema mantinha dois caminhos concorrentes de distribuição de catálogo:
1. `GitHubCatalog.ps1`: utilizava commits diretos em repositório GitHub para armazenar catálogo privado.
2. `Cloudflare Worker + R2`: gerenciava catálogo privado na nuvem via API REST e URLs pré-assinadas.
Essa duplicidade gerava confusão arquitetural, risco de exposição de links privados no GitHub e complexidade desnecessária no cliente Android (`RemoteLibrarySync.java` vs `CloudLibrarySync.java`).

## Decisão
Definir explicitamente:
- **Cloudflare Worker + R2 + Durable Objects** é a **única fonte da verdade** (Single Source of Truth) para a biblioteca privada de jogos, mídias e atualizações.
- O repositório e o GitHub Pages são reservados exclusivamente para o catálogo público, redistribuível e documentação de software livre.
- O sincronizador no Android converge para `CloudLibrarySync` (consumindo Cloudflare API v1), mantendo suporte offline por meio do cache local persistente em JSON (`games.json`).

## Consequências
- Fim da duplicidade de control planes.
- Segurança aprimorada: tokens temporários e assinaturas R2 substituem PATs do GitHub no cliente.
- Arquitetura consistente e limpa.

## Status
Aceito.
