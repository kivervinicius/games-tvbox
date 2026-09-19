# Jogos Retro Cloud: preparação e publicação

Este guia prepara o painel administrativo e a API privada na Cloudflare. O código-fonte público não contém ROMs, capas pessoais, APKs, tokens nem a chave de assinatura.

## O que já está no projeto

- Painel responsivo em `cloudflare/public/admin/` para ver armazenamento, publicar arquivos verificados e aprovar ou revogar TVs.
- Worker com autenticação Cloudflare Access para administração, armazenamento R2 privado, histórico de catálogos e API de pareamento individual.
- O limite publicado no Worker nunca passa de 8 GB. Nenhum arquivo é apagado automaticamente.
- Os downloads usam URLs R2 temporárias e a resposta do catálogo da TV omite a chave interna do objeto.

O serviço ainda não foi publicado na conta Cloudflare. A URL do Worker, identificadores de KV, credenciais R2 e política Access pertencem à conta do administrador e precisam ser configurados antes da primeira conexão da TV.

## Requisitos na conta

Use o plano gratuito e não habilite serviços pagos nem recarga automática. A cota informada pela Cloudflare para R2 Standard inclui 10 GB-mês de armazenamento, 1 milhão de operações Classe A e 10 milhões Classe B; este projeto para novas publicações em 8 GB para deixar margem. O alerta de orçamento da Cloudflare apenas avisa e não interrompe cobranças. Confira os limites atuais antes de ativar a conta: [preços do R2](https://developers.cloudflare.com/r2/pricing/), [limites do Workers](https://developers.cloudflare.com/workers/platform/limits/) e [alertas de orçamento](https://developers.cloudflare.com/billing/manage/budget-alerts/).

## Configuração inicial

Instale Node.js e Wrangler em um computador confiável. Não cole tokens, chaves da conta ou senhas no código-fonte, issues, Pages públicas ou conversa.

1. Crie um bucket privado chamado `games-tvbox-private`. Mantenha o acesso público desativado.
2. Crie quatro namespaces KV: `CATALOG_KV`, `DEVICE_KV`, `PAIRING_KV` e `RESERVATION_KV`.
3. Edite somente os identificadores KV em `cloudflare/wrangler.toml`. Coloque `ACCESS_TEAM_DOMAIN`, `ACCESS_AUD`, `ADMIN_EMAIL`, `CLOUDFLARE_ACCOUNT_ID`, `CLOUDFLARE_API_TOKEN`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY` e `EXPECTED_SIGNER_SHA256` em GitHub Actions Secrets; esses valores não devem ser gravados no repositório. O último deve ser o certificado SHA-256 do APK instalado. O link de pareamento usa automaticamente a origem do Worker.
4. Configure o CORS do bucket usando `cloudflare/r2-cors.example.json`, trocando o domínio de exemplo pela origem exata do painel. Permita somente `PUT` e os cabeçalhos indicados; não habilite `GET` público.
5. Crie uma aplicação Cloudflare Access para o domínio do Worker. Restrinja `/admin*` e `/api/admin*` a uma única política que permita apenas o e-mail administrador e autenticação por código temporário enviado por e-mail. Não crie regra `Everyone`.
6. Gere uma chave R2 com permissão de leitura e gravação somente nesse bucket. O workflow instala os valores de conta e chaves R2, equipe/audiência Access e e-mail administrador como segredos do Worker antes da publicação.
7. Publique o Worker e assets a partir da pasta `cloudflare`. Teste `/api/health`, confirme que `/admin/` pede autenticação e que `/api/admin/status` retorna 401 sem identidade autorizada.
8. Faça um upload de um arquivo pequeno de teste. Confirme no painel tamanho e hash, publique-o, baixe-o pela API de dispositivo e confirme que o domínio R2 não permite acesso sem a URL temporária.

O script `cloudflare/scripts/Initialize-Cloudflare.ps1` faz a validação local e, com `-CreateResources`, cria o bucket e os namespaces. Revise os IDs retornados antes de atualizar `wrangler.toml`; `-Deploy` é uma ação separada e deve ser executada somente após os segredos e a política Access estarem prontos.

O R2 deve ficar sem domínio público e o painel deve ser protegido também no Cloudflare Access. A verificação de JWT dentro do Worker é uma segunda barreira para as rotas administrativas. Uma publicação do launcher só será aceita se o certificado e o código de versão forem compatíveis com os valores já configurados.

O workflow [`android-release.yml`](../.github/workflows/android-release.yml) compila a APK em Windows, restaura a keystore apenas durante o job, injeta a origem HTTPS do Worker e rejeita qualquer certificado diferente de `FIRERETRO_SIGNER_SHA256`. O artefato fica disponível para publicação pelo painel; a chave nunca é armazenada no GitHub.

## Parear uma TV

Na API, a TV cria um pedido de pareamento temporário. O administrador o vê em **Aparelhos** e escolhe **Aprovar**. A TV recebe uma credencial própria e a usa como Bearer token; o serviço armazena apenas o hash do token para validação. Revogar uma TV invalida a credencial individual. O launcher agora contém o cliente desse fluxo, mantém a credencial no Android Keystore e baixa ROMs uma por vez para arquivos temporários, validando tamanho e SHA-256 antes de disponibilizá-las.

O Worker já foi publicado em `https://jogos-retro-cloud.kivervinicius.workers.dev`; a origem fixa foi gravada no cliente Android. A rota de saúde responde normalmente, enquanto a administração continua exigindo Access. O catálogo e os downloads só ficam ativos depois de configurar as chaves R2 e a política Access. O SDK Android (API 28, Platform Tools e Build Tools 35) está instalado fora do repositório em `%USERPROFILE%\.codex\android-sdk-games-tvbox`, e a compilação completa foi verificada com uma keystore temporária. Essa APK de verificação não deve ser instalada sobre a TV: o certificado estável da instalação existente tem SHA-256 `6fdb783135552eb93771d5c8228a3daff309053fadecf78b86de71be7b6f721f` e precisa ser fornecido ao build de publicação.

Estado provisionado em 16/09/2026: bucket `games-tvbox-private` criado e namespaces KV `CATALOG_KV`, `DEVICE_KV`, `PAIRING_KV` e `RESERVATION_KV` vinculados no `wrangler.toml`. A rota `/api/health` retornou `200`. Ainda faltam as chaves S3 do R2 e a aplicação/política Cloudflare Access para liberar o painel e as operações privadas.

Para conferir o certificado de uma instalação sem deixar uma cópia da APK no computador, use `scripts/Read-InstalledSigner.ps1` com o `adb` e `apksigner` do SDK. O script apenas lê o pacote e remove o arquivo temporário ao terminar; ele não altera o aparelho.

## Publicar conteúdo

O painel calcula SHA-256 em fluxo, solicita uma reserva de tamanho, envia o arquivo diretamente para o bucket privado, pede confirmação de tamanho e hash e só então troca o manifesto ativo. Se o envio falhar, o manifesto vigente permanece sem alteração. A tela envia um arquivo por vez.

O painel permite cadastrar apps da Google Play e Amazon Appstore, editar um tema com validação de contraste e atribuir temas publicados a cada aparelho. O launcher consome essas entradas, baixa fundos de tema e APKs privados por URL temporária, confere hash, pacote e certificado, e abre a confirmação oficial de instalação. A tela Biblioteca também reconhece atualizações com versão maior, confere o certificado já instalado e pede confirmação antes do instalador do Android. As ROMs publicadas carregam caminho local, core e capa opcional. Esses fluxos ainda precisam ser compilados e validados no Android real após publicar o Worker e configurar a origem fixa.

## Limites e recuperação

- Nunca coloque credenciais R2, token de TV, ROMs privadas, APKs privados ou chave de assinatura no GitHub público.
- O painel não limpa arquivos antigos. Para recuperar espaço, remova itens somente pela interface de administração da Cloudflare depois de confirmar que não estão referenciados; a remoção automática ainda não foi implementada.
- A reserva de tamanho funciona para o fluxo normal de um único administrador. Não faça uploads paralelos em várias abas até adicionarmos uma trava transacional de quota.
- Os links assinados duram poucos minutos e não devem ser salvos em catálogo.
- A instalação de atualização ainda deve abrir o instalador oficial Android/Fire OS e exigir confirmação local.
- Mantenha backup independente da chave de assinatura original. O projeto deve continuar usando exatamente o mesmo certificado para atualizar instalações existentes.

## Validação local

Os testes sem conta Cloudflare rodam com Node.js:

```powershell
node --test cloudflare/tests/*.test.mjs
```

Eles cobrem assinatura Access, limite de 8 GB, validação R2, publicação, pareamento, revogação, omissão de chaves internas e hash progressivo no painel. A execução local não substitui publicação, configuração Access nem teste na TV.
