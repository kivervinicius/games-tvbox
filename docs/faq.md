# FAQ

## O projeto inclui ROMs?

Não. O repositório contém código, capas de exemplo e modelos. Use somente ROMs que você tem direito de utilizar.

## Preciso do RetroArch?

Sim para executar jogos. O launcher organiza a biblioteca e entrega o caminho da ROM e do core ao RetroArch.

## O app substitui a Home da Amazon?

Não. Ele aparece como aplicativo Leanback; a Home do Fire TV continua sob controle do sistema.

## O Manager exige internet?

A conexão entre Manager e Fire TV usa a rede local. Internet pode ser necessária para obter SDK ou dependências, mas não existe servidor do Games TV Box.

## Preciso recompilar o APK quando adiciono um jogo ou capa?

Não. Use a sincronização do catálogo no Manager: o Launcher lê o catálogo atualizado e mantém o último catálogo válido quando estiver offline. Recompile o APK somente para mudanças de código.

## Posso importar o que já está no Fire Stick?

Sim. **Importar e publicar privado** faz somente cópias por ADB para `%LOCALAPPDATA%\FireRetroManager\imports`, sem remover ROMs, saves, favoritos ou configurações. A publicação exige confirmação, um checkout privado fora deste projeto e uma credencial GitHub protegida por DPAPI.

## O GitHub Pages recebe meus jogos?

Não. Pages recebe apenas itens que você revisou como legais para redistribuição, como homebrew, demos e domínio público. ROMs comerciais, saves e seu catálogo privado permanecem fora do site público. Pages é estático e não substitui ADB, o checkout privado nem a autorização na TV.

## Posso usar outro controle?

Sim, desde que o Fire OS o reconheça. Faça o mapeamento no RetroArch e valide o atalho de saída.
