# Jogos Retro Cloud local

Esta pasta contém o painel administrativo e a API que sincronizam as TVs. O modo local permite entender o fluxo sem enviar ROMs, APKs ou capas para a conta Cloudflare.

## Testar no computador

O painel local precisa do Node.js 20 ou mais recente e do Wrangler 4. No Windows, instale o Node.js LTS uma vez; depois o comando `npx` baixa o Wrangler automaticamente. Não é necessário criar conta ou enviar arquivos para testar localmente.

Abra o PowerShell na raiz do projeto e execute:

```powershell
pwsh -File cloudflare/scripts/Start-Local.ps1
```

Depois abra [http://127.0.0.1:8787/admin/](http://127.0.0.1:8787/admin/). A rota [http://127.0.0.1:8787/api/health](http://127.0.0.1:8787/api/health) deve responder `ok: true`.

O Wrangler cria simulações locais de KV e R2 em `cloudflare/.wrangler/`. Esse diretório é temporário, está ignorado pelo Git e pode ser removido quando você quiser reiniciar o teste local. Nenhum arquivo local é enviado à nuvem nesse modo.

Se o PowerShell informar que `npx` ou `wrangler` não foi encontrado, instale o Node.js LTS e abra um novo PowerShell. Confira com `node --version` e `npx --version`, e execute o comando novamente.

Para encerrar o painel, use `Ctrl+C` na janela que o iniciou. Para abrir somente a interface visual sem a API, use um servidor de arquivos na pasta `cloudflare/public`; esse modo é apenas uma prévia.

## O fluxo completo

1. **Arquivos**: escolha uma ROM, capa, fundo, APK ou atualização no painel.
2. **Metadados**: informe nome, plataforma, caminho seguro e, quando necessário, pacote, ABI e certificado.
3. **Verificação**: o painel calcula tamanho e SHA-256 antes de publicar.
4. **Catálogo**: a entrada só fica visível depois que o arquivo inteiro foi confirmado.
5. **TV**: cada aparelho pareado consulta o catálogo com ETag e mostra o card remoto sem baixar a ROM.
6. **Instalação**: ao selecionar **Instalar**, a ROM é gravada em arquivo temporário, verificada e promovida atomicamente; APKs passam pela confirmação oficial do Android.

O [Jogos Retro Importer](../importer-windows/README.md) usa um pareamento próprio e revogável. Ele recebe apenas permissão para reservar, enviar, verificar e publicar arquivos; nenhuma chave de R2 é enviada ao Windows.

## Publicar na conta Cloudflare

O Worker de produção já está em `https://jogos-retro-cloud.kivervinicius.workers.dev`. Para publicar alterações do painel:

```powershell
npx wrangler@4 deploy --config cloudflare/wrangler.toml
```

Antes de usar arquivos privados, configure no Worker os segredos `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `ACCESS_TEAM_DOMAIN`, `ACCESS_AUD`, `ADMIN_EMAIL` e `EXPECTED_SIGNER_SHA256`. Eles nunca devem ser gravados neste diretório.

O painel de produção deve ficar protegido pelo Cloudflare Access. A rota `/api/health` é pública apenas para diagnóstico; `/api/admin/*` exige identidade autorizada e `/api/device/*` exige a credencial individual da TV.

## Atualizar o launcher

Use o workflow `.github/workflows/android-release.yml`. Ele compila no Windows, injeta a origem fixa do Worker e rejeita APKs assinadas com certificado diferente. Publique o artefato pelo painel depois de conferir o hash e o certificado.

## Limites e segurança

- O limite interno é 8 GB, abaixo da franquia gratuita do R2.
- Leituras de catálogo e pedidos de download nunca atualizam presença no KV. O estado do aparelho só é gravado quando muda ou após seis horas.
- O painel atualiza automaticamente a cada dez minutos enquanto estiver visível e também oferece **Atualizar agora**.
- O painel estima o orçamento diário; 600 gravações é o alerta interno, 800 suspende telemetria não essencial e as 200 finais ficam reservadas a pareamento, publicação e revogação.
- Uma resposta 429 não bloqueia jogos locais nem o catálogo em cache; a mensagem informa que o limite diário é temporário.
- ROMs, saves, capas pessoais, tokens e keystores não pertencem ao repositório público.
- Não apague `cloudflare/.wrangler` enquanto uma execução local estiver ativa.
- A ativação do R2 pode exigir cartão e autorização de cobranças excedentes; isso é feito somente no painel da Cloudflare.

Para o procedimento detalhado, consulte [`docs/cloudflare-admin.md`](../docs/cloudflare-admin.md) e [`docs/quick-start.md`](../docs/quick-start.md).
