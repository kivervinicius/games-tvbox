# Inventário funcional

| Funcionalidade | Descrição | Problema resolvido | Tipo | Status | Implementação | Acesso | Dependências / permissões | Limitações | Testes / evidência | Documentação |
|---|---|---|---|---|---|---|---|---|---|---|
| Biblioteca visual | Cards com capa, nome e plataforma | navegar em menus do emulador | UI | Disponível | `MainActivity.renderSections`, `addCard` | abrir `Jogos Retro` | Android TV | arquivos precisam existir | `project.tests.ps1`, screenshot | `features/library.md` |
| Busca | filtra por nome | localizar jogos rapidamente | UI | Disponível | `searchQuery`, `renderSections` | campo BUSCAR JOGO | controle/teclado | busca local | `project.tests.ps1` | `features/search-and-platforms.md` |
| Abas de plataforma | NES, SNES, Mega Drive, GBA, PlayStation | separar bibliotecas | UI | Disponível | `platformButton`, `selectedPlatform` | botões de plataforma | catálogo JSON | somente plataformas cadastradas | `project.tests.ps1` | `features/search-and-platforms.md` |
| Favoritos | abre playlist de favoritos | evitar procura repetida | integração | Disponível | leitura de `content_favorites.lpl` | aba FAVORITOS | RetroArch | depende da playlist | `project.tests.ps1` | `features/library.md` |
| Slides | carrossel automático e manual | identidade visual fixa | UI | Disponível | `ThemeState`, `readTheme`, `showSlide` | setas / intervalo | imagens PNG/JPG | Home do Fire TV tem layout próprio | `ThemeStateTest` | `features/themes.md` |
| Personalização | envia tema e capas pelo Manager | editar APK para mudar visual | operação | Disponível | `Push-ThemeToFireStick`, cache local | Manager | ADB | autorização na TV | `Test-Manager.ps1` | `features/manager.md` |
| Abertura no RetroArch | envia ROM, core e config | configurar cada jogo manualmente | integração | Disponível | `launch` | A no card | RetroArch instalado | core compatível | `project.tests.ps1` | `user/catalog.md` |
| Atalho de saída | Start + Select / menu RetroArch | ficar preso dentro do jogo | controle | Disponível com configuração | `retroarch.cfg.example` | RetroArch | controle mapeado | pode exigir remapeamento | configuração exemplo | `user/controller.md` |
| ADB e diagnóstico | conecta e exibe modelo/ABI/espaço | descobrir falhas de conexão | operação | Disponível | funções ADB do Manager | tela inicial do Manager | Platform Tools | rede local | `Test-Manager.ps1` | `operations/diagnostics.md` |

Não foram encontradas API web, banco, autenticação, Docker, mensageria ou CLI independente.
