# ADR-001 — Views Android nativas no launcher

## Contexto

O launcher precisa iniciar em um Fire TV com recursos limitados e oferecer navegação por controle.

## Decisão

Usar Java/Android Views nativas com API mínima 28, sem adicionar Compose nesta versão.

## Alternativas consideradas

Compose poderia acelerar a construção de telas modernas, mas adicionaria dependências e custo de inicialização ao alvo atual. Views nativas atendem ao layout implementado e ao foco D-pad.

## Consequências

O projeto mantém dependências pequenas e um build manual simples. Evoluções futuras precisam preservar foco, acessibilidade e compatibilidade com Fire OS.

## Status

Aceita retrospectivamente; baseada na implementação atual.
