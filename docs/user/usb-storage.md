# Pendrive no Fire Stick

O Jogos Retro detecta automaticamente o primeiro volume removível montado pelo Fire OS e usa a pasta `roms` desse volume para a biblioteca. No aparelho configurado, o caminho é:

```text
/storage/3599-0073/roms
```

A configuração mantém o catálogo, temas, credencial, favoritos e preferências no armazenamento interno do aplicativo. Os caminhos do catálogo continuam usando `/sdcard/roms/...` como identificador lógico e são convertidos para o pendrive somente no aparelho, o que mantém a sincronização compatível.

Quando o pendrive não estiver conectado, os jogos que ainda existirem no armazenamento interno continuam disponíveis. A playlist de favoritos do RetroArch foi atualizada para o pendrive e a versão anterior foi preservada em `content_favorites.before-usb-20260918.lpl`.

Para usar outro pendrive, copie a pasta `roms` e mantenha o volume montado antes de abrir o launcher. Não remova o dispositivo durante um download ou enquanto um jogo estiver aberto.
