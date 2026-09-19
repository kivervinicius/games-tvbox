# ADR-009: Release Channels & Update Delivery

## Contexto
Atualizações do launcher e aplicativos auxiliares eram publicadas de forma única e linear, aplicando-se a todos os dispositivos cadastrados independentemente de sua estabilidade ou versão de hardware.

## Decisão
Introduzir canais de distribuição de lançamentos (*Release Channels*):
- `stable`: Canal padrão com versões amplamente testadas para usuários finais.
- `beta`: Canal para testes antecipados de novas funcionalidades e perfis.
- `canary`: Canal de integração contínua para validação de desenvolvimento.

O backend Cloudflare valida no handshake do dispositivo:
- Canal atribuído ao dispositivo ou grupo.
- Compatibilidade de ABI (`armeabi-v7a`, `arm64-v8a`).
- Nível de API Android mínimo.
- Assinatura criptográfica obrigatória (`EXPECTED_SIGNER_SHA256`).

## Consequências
- Rollout progressivo e seguro de novas versões do launcher.
- Prevenção de envio de APKs 64-bit para dispositivos 32-bit (e vice-versa).
- Capacidade de testar variantes como o modo Gamer em dispositivos selecionados antes de disponibilizar para todos.

## Status
Aceito.
