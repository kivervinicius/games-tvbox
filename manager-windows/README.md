# FireRetro Manager

O Manager é a central do Windows para o launcher **Jogos Retro** no Fire TV. Execute `Start-FireRetroManager.cmd`, informe o IP do Fire Stick e use **Conectar via ADB**. A primeira conexão pode pedir autorização na televisão.

## O que cada área faz

- **Início:** conecta ao Fire Stick, mostra modelo e espaço livre, abre ou reinicia o launcher.
- **Slides e aparência:** troca as imagens do carrossel, títulos e legendas; permite adicionar mais slides, escolher a velocidade e a transparência do fundo. **Salvar no Fire Stick** envia o tema e reinicia apenas o FireRetro.
- **Jogos e pastas:** registra uma pasta de ROMs e monta um catálogo local sem mover, renomear ou apagar a biblioteca original.
- **Controle e Fire TV:** envia o perfil do controle para o RetroArch. O Manager cria um backup da configuração antes da alteração.

As preferências e o tema ficam em `%LOCALAPPDATA%\FireRetroManager`, fora da pasta do projeto. No Fire Stick, os arquivos de aparência ficam em `/sdcard/Android/data/com.kiver.fireretro/files/theme`; ROMs, saves e configurações já existentes não são removidos.

No launcher, os atalhos visíveis são: **←/→** troca slides ou abas, **↑/↓** navega, **A** abre o jogo, **B** volta para a lista e **Menu** informa como personalizar.
