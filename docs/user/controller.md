# Controle e navegação

## Mapeamento

A aba **Controles** lista os dispositivos reconhecidos pelo Fire OS, mostra conexão, perfil aplicado e resultado do teste. O app reaplica os padrões de navegação, analógicos e saída do RetroArch quando identifica que foram perdidos, sempre mantendo um backup.

Abra **Controles** pelo indicador no topo para conferir cada dispositivo. Teste os botões e analógicos antes de iniciar um jogo. O launcher recebe comandos pelo foco padrão do Android TV: as setas navegam, A abre, B volta e Menu abre Aparência.

No RetroArch, abra `Settings > Input > RetroPad Binds > Port 1 Controls`, escolha `Set All Controls`, pressione cada botão e salve o perfil. O modo analógico para direcional fica no exemplo `examples/controller-profile.example.cfg`.

## Sair do jogo

Configure no RetroArch a combinação Start + Select para `input_quit_gamepad_combo = "4"`. Durante o jogo, pressione os dois botões para voltar ao conteúdo do launcher. Se o controle parar de responder, use o controle original do Fire TV para retornar à Home e abra `Jogos Retro` novamente.

## Dois controles

Mapeie o primeiro controle na porta 1 e o segundo na porta 2. Teste cada analógico, direcional, A, B, Start e Select antes de iniciar um jogo.
