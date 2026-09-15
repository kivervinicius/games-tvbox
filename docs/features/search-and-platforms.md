# Busca e plataformas

## Status

Disponível.

## Como usar

Selecione uma aba de plataforma ou o campo `BUSCAR JOGO`. Digite parte do nome; os cards são redesenhados com os itens correspondentes. A aba `FAVORITOS` usa a playlist do RetroArch.

## Implementação e evidência

O filtro está em `MainActivity.java`, com `selectedPlatform`, `searchQuery` e `renderSections`. Os testes confirmam as plataformas NES, SNES, Mega Drive, GBA e PlayStation.
