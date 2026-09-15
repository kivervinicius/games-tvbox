# Biblioteca privada do Fire Stick

O Manager importa a biblioteca já presente no Fire Stick sem alterar o aparelho. A importação usa somente `adb pull` para copiar ROMs, saves, catálogo, capas, slides e playlists para `%LOCALAPPDATA%\FireRetroManager\imports`. Cada execução cria uma pasta própria e um `import-report.json`; assim, uma cópia anterior não é sobrescrita.

## Preparação

1. Instale o ADB (Platform Tools), ative **ADB Debugging** no Fire TV e aceite a autorização na tela da TV.
2. No Manager, conecte-se ao endereço IP do Fire Stick.
3. Crie ou escolha um checkout Git privado fora da pasta deste projeto. Ele deve estar limpo, ter `origin` configurado para um repositório privado do GitHub e uma credencial Git funcional.
4. Abra **Importar e publicar privado**, escolha a raiz desse checkout e confirme a mensagem de preservação. A confirmação é obrigatória porque a publicação cria um commit de `catalog.private.json` no repositório privado.

O token do GitHub é salvo pelo Manager somente para o usuário atual do Windows, em `%LOCALAPPDATA%\FireRetroManager\github-token.xml`, protegido por DPAPI. Não copie esse arquivo, o token, o checkout privado, ROMs ou saves para este repositório público.

## O que a sincronização faz

A ação importa a biblioteca para a área privada, lê o inventário do Fire Stick, mescla o catálogo importado, aplica a sanitização do catálogo privado e publica somente `catalog.private.json` no checkout privado. Ela não chama `adb shell rm`, `mv` ou comandos de exclusão; não remove nem substitui ROMs, saves, favoritos ou configurações no Fire Stick.

O catálogo do Launcher é dinâmico. Em cada sincronização, o Manager varre novamente a pasta local selecionada, envia somente ROMs que ainda não existem no Fire Stick, atualiza capas baixadas no cache e grava um catálogo sem caminhos do Windows. Se já houver um catálogo remoto, mas ele não puder ser lido, o Manager interrompe a sincronização antes de qualquer envio para preservar a última cópia válida. Jogos, capas e metadados novos entram após essa sincronização; não é necessário recompilar o APK para adicionar esse conteúdo. Ao voltar ao Launcher, ele detecta alteração do catálogo externo e atualiza a tela, preservando o estado selecionado. Recompile o APK somente quando houver mudança no código do Launcher. Se o Fire Stick estiver offline, o Launcher continua usando o último catálogo sincronizado e, na ausência dele, o catálogo embutido.

## Limites e ações manuais

O GitHub Pages recebe apenas a galeria pública selecionada e itens legais para redistribuição, como homebrew, demos e domínio público. ROMs comerciais, saves, caminhos pessoais, tokens e links privados nunca são publicados. Pages é um site estático: ele não armazena sua biblioteca privada, não autentica o Fire Stick e não transfere ROMs.

Para atualizar a galeria, exporte pelo Manager o catálogo público revisado para `catalog-site/catalog.public.json` no checkout Pages configurado. Faça commit e push do JSON e apenas das capas autorizadas para a branch `main`, usando o Git ou uma automação externa configurada. O Manager faz a exportação pública; o push automático disponível nele é o da publicação privada. No repositório Pages, cada push em `main` executa automaticamente **Publish catalog Pages**; **Run workflow** permanece como opção manual. O workflow audita e publica somente `catalog-site/`, sem ROMs comerciais, saves ou conteúdo do checkout privado.

As escolhas feitas no diálogo **Catálogo GitHub** são guardadas no cache privado do Manager e reabrem na próxima revisão e sincronização. Esse arquivo não é exportado para Pages e não substitui a revisão manual dos direitos de publicação.

Ainda exigem ação manual: aceitar a autorização ADB na TV, parear e mapear controles Bluetooth, instalar/atualizar o APK quando o código mudar, revisar quais itens podem ser públicos, criar o checkout privado e garantir que o Git tenha credenciais para enviar o commit. Quando ADB, GitHub ou a internet falharem, mantenha a cópia importada e use o catálogo local; tente a publicação novamente apenas depois de corrigir a conexão ou a credencial.
