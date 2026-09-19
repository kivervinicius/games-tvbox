# FireRetro Manager

O Manager é a central do Windows para o launcher **Jogos Retro** no Fire TV. Execute `Start-FireRetroManager.cmd`, informe o IP do Fire Stick e use **Conectar via ADB**. A primeira conexão pode pedir autorização na televisão.

## O que cada área faz

- **Início:** conecta ao Fire Stick, mostra modelo e espaço livre, abre ou reinicia o launcher.
- **Temas e aparência:** cria pacotes de tema com fundo, cor, contraste e disposição aprovada; **Criar tema do fundo** valida o pacote e **Publicar tema GitHub** o envia ao checkout privado. A escolha final é feita pelo controle na TV.
- **Jogos e pastas:** registra uma pasta de ROMs e monta um catálogo local sem mover, renomear ou apagar a biblioteca original.
- **Controle e Fire TV:** envia o perfil do controle para o RetroArch. O Manager cria um backup da configuração antes da alteração.

As preferências e o tema ficam em `%LOCALAPPDATA%\FireRetroManager`, fora da pasta do projeto. No Fire Stick, os arquivos de aparência ficam em `/sdcard/Android/data/com.kiver.fireretro/files/theme`; ROMs, saves e configurações já existentes não são removidos.

No launcher, as setas navegam, **A** abre o jogo, **B** volta para a lista e **Menu** abre a escolha de aparência. O indicador de controle abre a tela de dispositivos, perfis e testes.
