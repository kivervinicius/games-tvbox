# ADR-010: Android Gamer Mode

## Contexto
Usuários que executam o aplicativo em smartphones, tablets ou consoles portáteis (handhelds como Odin, Retroid, ou celulares com controles tipo Razer Kishi / Gamesir) recebiam a interface 10-foot projetada para TV de sala. Isso gerava desperdício de espaço de tela, ausência de suporte a controles por toque quando o controle físico estivesse desconectado e falta de um Gaming Dashboard portátil.

## Decisão
Criar o modo **Android Gamer** como uma experiência dedicada de produto dentro do núcleo compartilhado do aplicativo:
- Modo imersivo em tela cheia (*immersive sticky mode*) e orientação prioritária *landscape*.
- **Gaming Dashboard**:
  - Acesso rápido a jogos recentes e favoritos com contadores de tempo de jogo.
  - Indicadores de status do sistema: bateria, status de carregamento, armazenamento livre.
  - Painel de atalhos rápidos (*Quick Settings*): remapeamento de botões, alternância de emuladores e gerenciador de downloads.
- Navegação prioritária por controle físico com suporte a toque contextual (*touch fallback*).
- O modo não utiliza APIs privilegiadas (root) nem hacks de fabricantes, garantindo conformidade com as diretrizes Android.

## Consequências
- Transforma smartphones e tablets em verdadeiros consoles portáteis dedicados a jogos.
- Não duplica a base de código do launcher: compartilha o catálogo, persistência, download manager e infraestrutura de rede.
- Proporciona experiência fluida e nativa tanto com controle acoplado quanto via tela sensível ao toque.

## Status
Aceito.
