# ADR-R04: Migração para .NET 10 LTS e Suporte Cross-Platform (Windows + Linux)

## Contexto
O projeto `JogosRetroImporter.Core` e a aplicação original foram compilados mirando `net8.0-windows`. Isso inviabilizava completamente a execução, teste ou compilação em sistemas Linux (utilizados em servidores de ingestão, ambientes de desenvolvimento e pipelines de CI). Além disso, a versão LTS moderna .NET 10 traz melhorias substanciais de performance de IO e memória em streams criptográficos e manipulação de arquivos grandes.

## Decisão
1. **Migração do Core**:
   - Atualizar `JogosRetroImporter.Core` para `<TargetFramework>net10.0</TargetFramework>`, removendo qualquer dependência do Windows Desktop SDK.
2. **Abstração de Plataforma**:
   - Isolar todas as particularidades de sistema operacional sob interfaces:
     - `IPlatformServices`: detecção de SO e arquitetura.
     - `IAppPaths`: resolução de caminhos nativos (XDG no Linux, LocalAppData no Windows).
     - `ICredentialStore`: armazenamento seguro portátil.
     - `IToolchainResolver`: resolução de binários nativos (`chdman` ELF no Linux vs `chdman.exe` PE no Windows).
3. **Estratégia de UI**:
   - Manter `JogosRetroImporter` (Windows Forms) apenas para compatibilidade legada em `net10.0-windows`.
   - Desenvolver o novo frontend oficial cross-platform em Avalonia UI (`JogosRetro.Desktop`) mirando `net10.0`.

## Consequências
- Os mesmos binários de Core e Downloads rodam sem recompilação em Windows (x64/arm64) e Linux (x64/arm64).
- O pipeline de CI no Linux passa a compilar e executar todos os testes automatizados da solução.

## Status
Aceito.
