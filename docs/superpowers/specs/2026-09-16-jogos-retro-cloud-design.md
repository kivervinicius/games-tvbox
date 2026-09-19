# Jogos Retro Cloud — Design

## Objetivo

Mover a administração e a sincronização da biblioteca para a nuvem Cloudflare, deixando o Windows necessário somente para preparação inicial por ADB e recuperação. O GitHub público conterá código, documentação e automação de build, nunca ROMs, APKs privados, temas pessoais, credenciais ou chave de assinatura.

## Componentes

- Cloudflare Pages serve um painel responsivo protegido por Cloudflare Access com código de acesso enviado por e-mail.
- Um Worker fornece as APIs administrativas e de aparelho; valida Access JWT em toda rota administrativa e usa autenticação individual por aparelho nas rotas de TV.
- Workers KV guarda catálogo publicado, pareamentos pendentes, dispositivos e estado não secreto. R2 Standard permanece privado e armazena biblioteca, capas, temas, APKs e releases.
- O painel solicita URLs temporárias para upload direto ao R2. Publicação passa por reserva de espaço, verificação de tamanho/hash e ativação atômica do manifesto.
- Jogos Retro na TV pareia por QR/código temporário, guarda um token revogável no Android Keystore e baixa arquivos somente com autorização da API.

## Limites de custo e dados

- Nunca habilitar billing automático. Aplicar limite interno de 8 GB para armazenamento privado, parar uploads antes de ultrapassá-lo e nunca remover arquivos automaticamente.
- O painel mostra tamanho estimado, espaço usado, restante e itens maiores. O limite interno é conservador em relação à franquia R2 gratuita e pode ser ajustado por configuração sem alterar ROMs.
- KV não é autoridade para bloqueio de cobrança por si só; o Worker calcula uso de objetos e reserva a cota antes de emitir URL. Apenas um administrador publica conteúdo; uploads são serializados no painel.
- Arquivos nunca são listáveis publicamente. Cada arquivo é identificado por ID opaco e conteúdo imutável por hash.

## Interfaces

- `DeviceRecord`: `id`, `label`, `model`, `androidApi`, `abi`, `launcherVersion`, `freeBytes`, `lastSeenAt`, `revokedAt`, `profileId`.
- `LibraryItem`: `id`, `kind`, `category`, `label`, `platform`, `version`, `objectKey`, `size`, `sha256`, `coverId`, `requirements`, `visibility`, `publishedAt`.
- `AndroidAppItem`: `LibraryItem` mais `packageName`, `sourceType`, `storeLinks`, `abis`, `certificateSha256`.
- `LauncherRelease`: `versionCode`, `versionName`, `minAndroidApi`, `abis`, `objectKey`, `size`, `sha256`, `signerSha256`, `notes`.
- `ThemeProfile`: `id`, `version`, `title`, `subtitle`, `backgroundId`, `palette`, `textStyle`, `cardDensity`, `deviceIds`.
- APIs: `/api/admin/*` para catálogo, uploads, dispositivos, temas e releases; `/api/device/*` para parear, consultar catálogo, reportar estado e obter downloads autorizados.

## Regras de plataforma

- Interface TV permanece focada em Android TV e Fire TV desde API 28. Detectar ABI, Android, armazenamento, loja e pacote RetroArch.
- ADB prepara o primeiro aparelho, RetroArch, permissões e importação inicial; depois fica como recuperação.
- Atualizações do launcher mantêm o mesmo pacote e assinatura, validam APK e abrem o instalador Android com confirmação. A instalação silenciosa não é prometida.
- Jogos e jogos Android aparecem em Jogos; apps comuns aparecem em Aplicativos; Configurações mostra inventário e compatibilidade de ambos.
- Loja abre Amazon Appstore ou Google Play quando disponível; APK privado exige hash, pacote, versão, ABI e certificado esperados.
- Catálogo offline mescla conteúdo embutido, local e online e nunca substitui saves, favoritos ou configuração.

## Segurança e publicação

- Nenhum segredo da conta Cloudflare vai ao navegador persistente, APK, catálogo ou log. Credenciais de TV são individuais, hash no servidor, criptografadas na TV e revogáveis.
- Uploads diretos usam URL temporária por objeto, hash SHA-256 validado e etapa finalize. O catálogo ativo só muda depois que todos os objetos passaram na validação.
- Rejeitar URL/caminhos de objetos que não pertençam à aplicação, chaves desconhecidas, conteúdo fora da cota e metadados inválidos.
- O build assinado no GitHub Actions usa segredo protegido para a chave existente e compara o certificado com o fingerprint fixo esperado. Não colocar o keystore no repositório.

## Validação

Testar autenticação admin, token de aparelho, pareamento expirado/aprovado/revogado, URL temporária, reserva de cota e limite, hash/tamanho inválido, publicação incompleta, offline e integridade do catálogo. No Android, validar preservação e atualização assinada no Fire Stick ARM32 e pelo menos um Android TV ARM64, lojas/APKs, foco/controle e retorno ao Fire TV.
