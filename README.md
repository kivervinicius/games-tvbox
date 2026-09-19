# Games TV Box

Launcher familiar para Fire TV/Android TV que transforma um acervo autorizado em uma biblioteca navegável por controle, com capas, busca, filtros, favoritos e integração com RetroArch. A arquitetura em desenvolvimento leva administração privada, jogos, temas, aplicativos e atualizações para Cloudflare; o uso diário será independente do Windows quando o serviço for publicado.

## TL;DR

O Jogos Retro organiza plataformas e abre jogos no RetroArch. O Windows/ADB será necessário para a primeira preparação e recuperação; depois de publicado, o painel privado da Cloudflare administrará jogos, capas, temas, aplicativos e atualizações, e cada TV sincronizará sua cópia para continuar funcionando offline.

Com ele você pode:

- navegar por NES, SNES, Mega Drive, GBA e PlayStation;
- buscar jogos, filtrar plataformas e abrir favoritos pelo controle;
- personalizar tema, fundo, título, cores e capas sem editar o app;
- preservar ROMs, saves e configurações do RetroArch durante atualizações.
- sincronizar cards novos em várias TVs e baixar a ROM somente ao selecionar **Instalar**, com uso offline e progresso visível;
- parear cada TV com uma autorização revogável e baixar APKs privados com validação de hash e assinatura.

→ [Comece pelo Quick Start](docs/quick-start.md) · [Veja a demonstração](docs/assets/screenshots/launcher-interface.png) · [Leia o manual completo](docs/index.md)

## Demonstração visual

[![Interface do Jogos Retro](docs/assets/screenshots/launcher-interface.png)](https://github.com/kivervinicius/games-tvbox/raw/refs/heads/main/docs/assets/videos/games-tvbox-interactive-demo.mp4)

▶ [Assistir ao vídeo interativo](https://github.com/kivervinicius/games-tvbox/raw/refs/heads/main/docs/assets/videos/games-tvbox-interactive-demo.mp4) · [Ver todas as screenshots](docs/assets/screenshots/)

O vídeo mostra a navegação pelo controle, a troca de slides e plataformas, a seleção de um jogo e o retorno ao launcher.

## O que este produto melhora?

| Antes / problema | Com o Games TV Box |
|---|---|
| procurar cada jogo dentro do RetroArch | biblioteca visual agrupada por plataforma |
| configurar ADB manualmente a cada tentativa | preparação inicial por ADB e administração online planejada |
| capas e temas fixos | personalização local persistente e temas privados publicados |
| controle sem atalho claro de retorno | atalhos visíveis e retorno ao launcher |
| risco de misturar biblioteca e configuração | ROMs e saves permanecem fora do código público |

## O que está incluído

- `launcher-android/`: fonte do aplicativo Jogos Retro, catálogo de exemplo, temas e capas.
- `manager-windows/`: ferramenta de recuperação e publicação local por ADB, mantida enquanto o painel Cloud amadurece.
- `cloudflare/`: Worker, painel privado e API para biblioteca e dispositivos.
- `importer-windows/`: importador Windows autossuficiente para extrair, converter, buscar capa e publicar jogos do acervo privado.
- `scripts/`: build, auditoria e validações repetíveis.
- `examples/`: modelos neutros de catálogo, tema, RetroArch e controle.
- `docs/`: guias de usuário, desenvolvimento, operação, arquitetura e referência.

## Requisitos

- Android TV/Fire TV com Android 9/API 28 ou superior.
- Windows 10/11, PowerShell 7 e Android Platform Tools para preparação inicial/recuperação.
- Android SDK API 28 e Java 8+ para compilar o launcher.
- Fire TV/Android TV com depuração ADB habilitada.
- RetroArch e cores compatíveis com a arquitetura do aparelho.

## Documentação por objetivo

- [Índice da documentação](docs/index.md)
- [Quick Start](docs/quick-start.md)
- [Instalação detalhada](docs/installation.md)
- [Funcionalidades](docs/features/index.md)
- [Manual visual](docs/manual/README.md)
- [Configuração](docs/reference/configuration.md)
- [Exemplos de configuração](examples/README.md)
- [Solução de problemas](docs/troubleshooting.md)
- [Arquitetura](docs/architecture/overview.md)
- [Desenvolvimento](docs/development/build.md)
- [Cloudflare local e produção](cloudflare/README.md)
- [Importador público para Windows](importer-windows/README.md)

## Desenvolvimento

Execute `pwsh -File launcher-android/tests/project.tests.ps1` e `pwsh -File manager-windows/tests/Test-Manager.ps1`. Antes de enviar alterações, rode `pwsh -File scripts/Verify-PublicRelease.ps1`.

O build Android usa API 28 e aceita um keystore externo por `-KeystorePath` ou pelas variáveis `FIRERETRO_KEYSTORE`, `FIRERETRO_KEY_ALIAS` e `FIRERETRO_KEY_PASSWORD`. Nunca adicione chaves privadas ao Git.

Leia [CONTRIBUTING.md](CONTRIBUTING.md) e [SECURITY.md](SECURITY.md) antes de abrir uma alteração.
## Administração privada via Cloudflare

O painel/API privados e a preparação da conta estão em [`docs/cloudflare-admin.md`](docs/cloudflare-admin.md). O código web do painel fica em `cloudflare/public/admin/`; ROMs, APKs e credenciais devem permanecer no bucket R2 privado e nos segredos da conta. O Worker já está publicado, mas pareamento, sincronização remota e atualização pelo app só ficam ativos após configurar Access e as chaves do R2.
