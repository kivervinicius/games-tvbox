# Roteiro de Aceite — Fire TV / Android TV

## 1. Contexto e Equipamentos
- **Perfis Alvo**: `FIRE_TV`, `ANDROID_TV`, `ANDROID_TV_TCL`.
- **Dispositivos de Referência**: Amazon Fire TV Stick 4K (Fire OS 6/7, 32-bit), TCL Smart TV Google TV (Android 11, 32-bit / 64-bit), Box Android TV genérica.

## 2. Pré-requisitos
1. Launcher instalado via ADB ou pendrive (`com.kiver.fireretro`).
2. RetroArch (`com.retroarch.ra32`) instalado com cores necessários (`pcsx_rearmed`, `snes9x`, etc.).
3. Rede Wi-Fi ativa para pareamento inicial e sincronização.

## 3. Passo a Passo de Validação (Passo / Ação / Resultado Esperado)

| Passo | Ação | Resultado Esperado | Status |
|---|---|---|---|
| 1 | Iniciar o launcher após boot do televisor | A aplicação abre em modo paisagem, ocupando a tela inteira, com safe-zone de 48dp (Fire TV) ou 40dp (TCL) | OK |
| 2 | Navegação remota (sem touch) | O controle remoto padrão (D-pad) navega suavemente por categorias, carrossel e cards de jogos; o foco visual (glow ciano) é sempre nítido | OK |
| 3 | Conectar Gamepad Bluetooth/USB | O launcher detecta o controle imediatamente, exibe "CONTROLE CONECTADO" no cabeçalho e permite navegação via analógico e botões A/B/X/Y | OK |
| 4 | Pareamento com Cloudflare Control Plane | O aparelho exibe o código de pareamento de 6 dígitos. O administrador autenticado no Cloudflare Access aprova e concede os escopos `tv` (`catalog.read`, `asset.download`, `device.state.write`) | OK |
| 5 | Sincronização do Catálogo | O catálogo privado é baixado via JSON assinado, sem gastar writes em KV; cards remotos aparecem com indicador visual de nuvem | OK |
| 6 | Verificação de Compatibilidade | Itens incompatíveis com armazenamento disponível ou arquitetura são filtrados ou exibidos com badge de alerta | OK |
| 7 | Download sob demanda | O download de uma ROM remota inicia somente após confirmação do usuário (diálogo informativo com tamanho). O progresso percentual é exibido | OK |
| 8 | Verificação SHA-256 e gravação | O arquivo `.part` é verificado contra o hash SHA-256 da reserva; ao conferir, é movido para o storage (`/sdcard/roms` ou pendrive USB) | OK |
| 9 | Lançamento do RetroArch | O botão de jogar aciona o `RetroArchProvider`, repassando ROM, core (`LIBRETRO`) e caminho de config seguro (`retroarch.cfg`) | OK |
| 10 | Retorno ao Launcher | Ao sair do jogo ou pressionar Back, o usuário retorna diretamente ao launcher com o foco restaurado no card | OK |
| 11 | Operação Offline | Desconectando o Wi-Fi, o launcher continua abrindo todos os 99 jogos locais e ROMs baixadas, sem travamento ou telas brancas | OK |
