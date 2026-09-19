# Pipeline determinístico de ROMs com Cloudflare opcional — Design

## Objetivo

Criar um único processo reproduzível para ROMs novas e atualizadas, evitando
que um caminho local por ADB e um caminho remoto pela Cloudflare produzam
catálogos diferentes. O processo deve preservar o arquivo original, capturar
metadados verificáveis, instalar localmente quando solicitado e publicar na
Cloudflare quando houver conexão e credencial disponíveis.

O pipeline não depende de LLM. Identificação, validação, categorização,
seleção de core, hashes e transições de estado são determinadas por regras,
fontes configuradas e respostas registradas.

## Escopo e não objetivos

Incluído:

- arquivos ROM isolados e arquivos compactados que contenham uma ROM válida;
- conversão necessária por plataforma, sem modificar o original;
- nome, plataforma, categoria, capa e sinopse com origem registrada;
- instalação por ADB no Fire TV/Android TV;
- publicação posterior na Cloudflare/R2 sem duplicação;
- atualização de uma ROM existente identificada por conteúdo e caminho;
- manifesto local, fila pendente e relatórios de cada execução;
- testes unitários, de contrato e de integração simulada.

Fora deste ciclo:

- inventar sinopses ou aceitar metadados sem fonte;
- publicar ROMs no site público;
- apagar ou mover ROMs, saves, configurações ou coleções existentes;
- instalação automática de cores ausentes no aparelho;
- resolver legalidade/licença de ROM comercial;
- tornar a Cloudflare um bloqueio para instalação local offline.

## Estado validado antes da implementação

- O Importer .NET já possui pareamento, armazenamento seguro do token,
  reserva de upload, envio para R2, finalização por tamanho/SHA-256 e
  publicação em `api/importer/*`.
- O Worker já valida itens `rom`, caminho local, categoria, tamanho, SHA-256
  e caminho de core, mas o Manager pode enviar ROMs diretamente por ADB sem
  passar pelo mesmo contrato.
- O Importer atual é específico de PlayStation e o detector não contempla
  N64; o pipeline novo deve separar o registro de plataformas do fluxo de
  conversão de disco.
- O Manager PowerShell já possui inventário, catálogo, sincronização ADB,
  cache de metadados e capas, porém a consulta ao provedor pode cair
  silenciosamente para o nome local.

## Decisões de design

### 1. Um manifesto canônico por conteúdo

Cada item processado terá um manifesto JSON local, fora do repositório quando
contiver caminhos ou credenciais. O identificador primário será o SHA-256 do
arquivo final enviado ao aparelho; o hash do arquivo original e o hash de cada
artefato derivado também serão registrados.

Campos mínimos:

```json
{
  "schemaVersion": 1,
  "contentId": "sha256:...",
  "source": { "path": "...", "sha256": "...", "size": 0 },
  "artifact": { "path": "...", "sha256": "...", "size": 0, "format": "z64" },
  "identity": { "title": "...", "region": "U", "platform": "N64", "category": "game" },
  "runtime": { "corePath": "...", "remotePath": "/sdcard/roms/n64/..." },
  "metadata": {
    "cover": { "path": "...", "sha256": "...", "source": "..." },
    "synopsis": { "text": "...", "source": "..." }
  },
  "delivery": { "adb": "pending", "cloud": "pending" },
  "history": []
}
```

O formato será documentado e validado por um schema/validador local. Nenhuma
etapa posterior poderá substituir hash, plataforma ou core sem registrar uma
nova revisão do manifesto.

### 2. Pipeline único, dois destinos

O fluxo lógico será sempre o mesmo:

1. **Ingestão:** ler o arquivo sem alterar o original, detectar arquivo
   compactado, aplicar limites de extração e criar staging seguro.
2. **Conversão:** produzir o formato final exigido pela plataforma, validar o
   resultado e verificar novamente o hash do original.
3. **Identidade:** usar regras determinísticas de extensão, cabeçalho, tamanho
   e região. Ambiguidade ou plataforma não suportada interrompe o pipeline.
4. **Metadados:** buscar nome, capa e sinopse por provedores configurados,
   registrar a fonte e os hashes dos downloads. Falta de fonte nunca gera
   conteúdo inventado; o campo fica ausente e o item recebe estado pendente.
5. **Manifesto:** persistir a decisão antes de qualquer envio.
6. **ADB local:** criar diretórios, enviar ROM/capa por cópia incremental,
   gerar/atualizar catálogo e confirmar tamanho e SHA-256 no aparelho.
7. **Cloudflare:** se houver configuração e conectividade, reservar, enviar,
   finalizar, publicar e reler o catálogo canônico. A resposta relida precisa
   coincidir com o manifesto antes de marcar `cloud=verified`.
8. **Reconciliação:** falha Cloudflare após ADB não desfaz a instalação; marca
   `cloud=pending` e permite retry idempotente pelo mesmo `contentId`.

ADB e Cloudflare são destinos independentes depois do manifesto. Assim o modo
offline continua funcional, mas não cria um segundo método de categorização.

### 3. Fontes e política de confiança

As fontes serão adaptadores explícitos, com prioridade configurável:

- parser local: nome bruto, extensão, região e regras de plataforma;
- Libretro Thumbnails: capa, quando houver correspondência válida;
- provedor de sinopse configurado: somente resposta estruturada e registrada;
- Cloudflare: publicação e leitura do catálogo canônico, não uma fonte para
  preencher campos que não foram fornecidos;
- revisão manual: permitida para corrigir título/capa, sempre registrada no
  manifesto e sem alterar o arquivo ROM.

Cada campo terá `source`, `retrievedAt` e, quando aplicável, `confidence` ou
`reviewed`. O pipeline bloqueará publicação/instalação de um item marcado
`ambiguous`; poderá instalar localmente um item `metadataPending` apenas se a
ação tiver sido explicitamente solicitada, exibindo esse estado no relatório.

### 4. Registro determinístico de plataformas e cores

Um registro versionado será a única fonte para extensão, plataforma, diretório,
core e regras de validação. Ele incluirá N64 (`.z64`, `.n64`, `.v64`) e as
plataformas já suportadas, sem inferência livre por nome de arquivo.

O registro poderá declarar que um core é obrigatório e que deve existir no
aparelho. A ausência do core não será corrigida silenciosamente: a ROM pode ser
preparada e publicada, mas o relatório indicará `runtimeUnavailable` e a
execução não será declarada como testada.

### 5. Cloudflare opcional, mas consistente

O cliente reutilizará o contrato atual de `api/importer/uploads`, finalize e
publication. Após publicar, consultará o catálogo de importador e selecionará
o item por `contentId`/SHA-256. O item relido deverá confirmar:

- tamanho e SHA-256;
- `category=game` e `kind=rom`;
- plataforma e caminho local;
- caminho do core;
- nome e campos de metadados aprovados.

O upload temporário continuará usando o UUID da reserva, mas o item durável
do catálogo terá um `contentId` estável derivado do SHA-256 do artefato final.
O Worker deverá tratar uma publicação repetida com o mesmo `contentId` como
idempotente: se tamanho, hash, caminho e metadados compatíveis já existirem,
retornará o item/revisão existente; se o mesmo identificador vier com bytes ou
campos de execução conflitantes, rejeitará a operação. Assim, uma falha depois
do upload ou um retry offline não cria cópias do mesmo jogo.

Se a API estiver indisponível, expirada ou sem token, o pipeline salvará a fila
pendente com erro estruturado. Não haverá fallback para “publicado” baseado em
uma tentativa parcial.

### 6. ADB seguro e idempotente

O instalador ADB usará somente `mkdir`, `push --sync`, leitura de inventário e
hash/size de confirmação. Não usará `rm`, `mv` ou limpeza automática. O caminho
remoto será derivado do registro de plataforma e sanitizado; nomes com espaços,
parênteses e colchetes serão tratados como argumentos, nunca concatenados em
comandos sem escape.

Uma ROM já presente só será considerada válida se tamanho e SHA-256 coincidirem.
Se divergirem, será feita atualização controlada por arquivo temporário ou
`push --sync`, seguida de nova confirmação; o arquivo original remoto não será
apagado pela automação.

### 7. Interfaces e artefatos

- `importer-windows`: serviço .NET compartilhado para manifesto, provedores,
  publicação Cloudflare e estados de pipeline;
- `manager-windows`: adaptador ADB e integração da UI, consumindo o manifesto
  em vez de recriar a categorização;
- `cloudflare/worker`: endpoint de leitura do catálogo do importador e
  validações necessárias para manter o contrato canônico;
- `scripts`: comandos de diagnóstico, retry da fila, validação de manifestos e
  testes sem dispositivo real;
- `docs`: quick start, configuração de provedores, política offline e relatório
  de estados.

O token Cloudflare continuará protegido pelo armazenamento existente. Nenhum
token, senha ou URL assinada será persistido em manifesto, catálogo público,
logs ou argumentos do RetroArch.

## Tratamento de erros

Cada falha terá código estável e mensagem humana, por exemplo:

- `source_missing`, `archive_ambiguous`, `unsupported_platform`;
- `conversion_failed`, `metadata_unavailable`, `metadata_ambiguous`;
- `adb_unavailable`, `adb_hash_mismatch`, `core_unavailable`;
- `cloud_auth_required`, `cloud_upload_failed`, `cloud_readback_mismatch`.

Uma execução só será `complete` quando o destino solicitado estiver verificado.
`localComplete/cloudPending` será um estado válido; `cloudComplete/localPending`
também poderá existir para publicação prévia, mas não será apresentado como
instalado na TV.

## Testes e critérios de aceitação

Testes obrigatórios:

- schema: manifesto mínimo, estados, campos obrigatórios e rejeição de hash
  inválido;
- identidade: N64, extensões ambíguas, arquivos compactados, nomes regionais e
  plataformas não suportadas;
- metadados: resposta válida, resposta incompleta, timeout e ausência de capa;
- Cloudflare: reserva/finalize/publicação/readback, mismatch e retry idempotente;
- ADB simulado: `push --sync`, caminhos especiais, hash remoto divergente e
  ausência de comandos destrutivos;
- integração: offline local, online completo, Cloudflare indisponível após ADB
  e atualização do mesmo conteúdo;
- regressão dos testes existentes do Worker, Importer e Manager.

Critérios de aceitação:

1. Não existe caminho novo de ROM que copie para ADB sem manifesto válido.
2. O mesmo registro de plataforma/core é usado no modo local e Cloudflare.
3. Nenhum metadado ausente é preenchido por invenção ou por LLM.
4. A instalação local pode terminar offline e fica claramente pendente na fila
   Cloudflare.
5. O retry posterior não duplica a ROM nem perde metadados revisados.
6. ROM, capa, catálogo e manifesto têm hashes verificáveis.
7. O processo preserva os arquivos existentes e não executa remoções.
8. O Mario 64 poderá ser reprocessado pelo pipeline e só será considerado
   plenamente concluído quando o core N64 e os metadados forem validados.

## Plano de implementação de alto nível

1. Extrair contrato de manifesto e registro de plataformas para o núcleo comum.
2. Corrigir o cliente de metadados para não mascarar falhas e adicionar nome,
   capa e sinopse como resultados separados.
3. Implementar o orquestrador Cloudflare opcional com readback canônico.
4. Implementar adaptador ADB idempotente e substituir cópias diretas no
   Manager.
5. Adicionar fila/retry e comandos de diagnóstico.
6. Adicionar testes e atualizar documentação/UI de estados.
