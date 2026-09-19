# ADR-002: Cloud State Coordination

## Contexto
O backend Cloudflare utilizava Workers KV para persistência transacional (`CATALOG_KV`, `DEVICE_KV`, `PAIRING_KV`, `RESERVATION_KV`). Como o KV possui consistência eventual, operações concorrentes de reserva de cota, aprovação de pareamento e publicação sofriam com *race conditions*, perda de atualizações e varreduras integrais de bucket R2 para contagem de cota.

## Decisão
Introduzir um coordenador transacional (`LibraryCoordinator`) com armazenamento fortemente consistente (Durable Objects com persistência relacional SQLite) para gerenciar o estado autoritativo de dispositivos, credenciais com escopos, reservas de upload, contadores de cota em tempo real e catálogo.
O Workers KV permanece exclusivamente como cache de leitura de alta velocidade (read-heavy) e distribuição descentralizada.

## Consequências
- Eliminação de race conditions em reservas concorrentes e aprovações de pareamento.
- Quota mantida via contadores incrementais atômicos (`usedBytes`, `reservedBytes`), eliminando scans de R2 no caminho crítico.
- Histórico e auditoria estruturados diretamente no banco relacional.

## Status
Aceito.
