# Games TV Box

Launcher familiar para Fire TV/Android TV, com navegação por controle, capas, busca, filtros por plataforma e Manager para Windows.

O projeto é distribuído como código-fonte e ferramentas de configuração. Ele não inclui ROMs, saves, dumps de aparelhos, APKs de terceiros ou chaves privadas. Use somente conteúdo que você tem direito de utilizar.

## O que está incluído

- `launcher-android/`: aplicativo Jogos Retro e seus recursos.
- `manager-windows/`: Manager para conexão ADB, catálogo e aparência.
- `scripts/`: atalhos de build e auditorias.
- `examples/`: modelos neutros de catálogo, tema e controle.
- `docs/manual/`: manual ilustrado e solução de problemas.

## Requisitos

- Windows 10/11 para o Manager e o build.
- PowerShell 7, Java 8+ e Android Platform Tools.
- Fire TV/Android TV com depuração ADB habilitada.
- RetroArch e cores compatíveis com a arquitetura do aparelho.

## Começo rápido

1. Baixe ou clone este repositório.
2. Instale o ADB e confirme que `adb version` funciona.
3. Abra `manager-windows/Start-FireRetroManager.cmd`.
4. Informe o endereço do aparelho, aceite a autorização na TV e escolha a pasta do seu acervo.
5. Gere o APK com `pwsh -File scripts/Build-FireRetro.ps1` usando um keystore externo.
6. Instale o APK pelo Manager e abra `Jogos Retro`.

O passo a passo completo está no [manual](docs/manual/README.md). Para criar configurações, consulte [examples](examples/README.md).

## Build Android

O script usa Android API 28 e ferramentas locais. Baixe o SDK, configure o caminho no script de build e forneça uma chave de assinatura fora do Git. Nunca adicione keystores privados ao repositório.

## Desenvolvimento

Execute `pwsh -File launcher-android/tests/project.tests.ps1` e `pwsh -File manager-windows/tests/Test-Manager.ps1`. Antes de enviar alterações, rode `pwsh -File scripts/Verify-PublicRelease.ps1`.

Leia [CONTRIBUTING.md](CONTRIBUTING.md) para o fluxo de revisão.
