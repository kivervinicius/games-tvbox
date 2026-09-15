# Games TV Box — Publicação do produto

## Objetivo

Transformar o protótipo FireRetro em um projeto público, reproduzível e personalizável para Fire TV/Android TV, com launcher Android, Manager Windows, scripts de instalação e documentação ilustrada.

## Escopo do repositório

O repositório conterá apenas código, scripts, testes, assets licenciáveis ou criados para o projeto, exemplos de configuração e documentação. ROMs comerciais, saves, dumps do Fire Stick, APKs de terceiros, chaves privadas, caminhos pessoais e dados de rede do usuário ficarão fora do Git.

## Componentes

- `launcher-android/`: fonte do aplicativo FireRetro, catálogo de exemplo, temas, ícone e recursos visuais.
- `manager-windows/`: Manager PowerShell, cache fora do projeto, personalização de capas/slides e integração ADB.
- `scripts/`: preparação do ambiente, build, instalação e validações repetíveis.
- `docs/`: manual do usuário, guia de desenvolvimento, solução de problemas, imagens e roteiro de vídeo.
- `examples/`: configurações neutras e modelos para ROMs, controles e temas.

## Experiência de instalação

O caminho documentado será: instalar Platform Tools, conectar por ADB, apontar o catálogo local de ROMs do usuário, compilar ou baixar o launcher, instalar no Fire Stick e abrir o Manager para personalizar. O primeiro pareamento ADB e o pareamento Bluetooth do controle serão identificados como ações manuais quando necessários.

## Personalização

O usuário poderá trocar slides, títulos, legendas, capas e agrupamentos sem editar o código. Configurações persistentes do Manager serão mantidas em `%LOCALAPPDATA%\\FireRetroManager`; o repositório terá apenas exemplos. O launcher continuará funcionando com um tema padrão quando nenhuma personalização existir.

## Manual e mídia

O manual incluirá capturas da interface real, diagramas de fluxo, atalhos do controle, instalação, atualização, rollback e diagnóstico. A documentação de vídeo usará links ou gravações demonstrativas que não contenham dados pessoais, ROMs comerciais ou credenciais.

## Qualidade e segurança

Antes do push serão executados os testes existentes, validação de sintaxe PowerShell, build assinado do APK, verificação do manifesto e uma auditoria de arquivos para impedir publicação acidental de ROMs, saves, backups, chaves e configurações pessoais.
