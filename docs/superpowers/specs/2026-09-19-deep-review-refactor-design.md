# Deep Review e Refatoração dos Processos — Design

## Objetivo

Alinhar o comportamento real do Games TV Box com sua documentação e reduzir os
fluxos duplicados ou inseguros, preservando ROMs, saves, configurações,
catálogo offline e o fluxo atual de publicação Cloudflare.

## Estado validado

- O Worker Cloudflare é o fluxo remoto ativo: pareamento por TV/importador,
  catálogo, tickets R2, publicação e revogação.
- O launcher usa `CloudLibrarySync`; `RemoteLibrarySync` permanece no build,
  mas não é chamado pelo fluxo atual.
- O importador Windows implementa somente preparação PlayStation: CUE/BIN,
  ISO, CHD e arquivos compactados que contenham uma imagem PlayStation válida.
- O Manager continua responsável por ADB, catálogo local, temas, importação
  preservacionista e recuperação.
- A suíte Cloudflare passa 30 testes Node. Os checks PowerShell e .NET têm
  limitações de execução nesta máquina: `pwsh` e o SDK .NET não estão
  instalados; esses limites não serão tratados como aprovação de build.

## Decisões

### 1. Uma única autoridade remota

Cloudflare será o único processo remoto documentado e incluído no caminho
principal do launcher. O sincronizador GitHub legado será removido do build e
de seus testes de contrato se não houver consumidor de produção. As classes de
configuração e endpoint legadas só serão mantidas se ainda servirem a uma
integração explícita; caso contrário, serão removidas junto com referências
documentais obsoletas.

O Manager local e o fallback embutido continuam válidos. A remoção é apenas do
fluxo remoto duplicado, não da biblioteca local nem dos dados do usuário.

### 2. Integridade e ciclo de vida no launcher

- Um item remoto já existente no aparelho não será considerado instalado apenas
  porque o caminho existe: tamanho e SHA-256 do arquivo serão conferidos antes
  de marcar `remoteAvailable=false`.
- Downloads continuarão usando arquivo `.part`, verificação de tamanho/hash e
  promoção atômica.
- O executor de sincronização será encerrado quando a Activity for destruída e
  a sincronização interrompida não poderá deixar callbacks ativos sem dono.
- Erros no formato `{error:{code,message}}` da API serão convertidos em
  mensagens úteis para a UI.
- O catálogo, temas, apps e estado de atualização continuarão sendo gravados
  em cache com fallback offline.

### 3. Escopo honesto do importador

Até existirem pipelines específicos para NES, SNES, Mega Drive e GBA, a UI do
importador exibirá somente PlayStation. Extensões e arquivos não suportados
serão rejeitados antes da publicação; não haverá seleção de plataforma que o
pipeline ignore.

Arquivos compactados deverão conter exatamente uma fonte de disco válida para
o pipeline atual. A extração terá limite de bytes descompactados e de número de
entradas, além da validação atual contra travessia de diretório. O arquivo
original continuará somente leitura e terá seu hash revalidado ao final.

### 4. Validação reproduzível

- Scripts resolverão arquivos relativos a `$PSScriptRoot` ou à raiz explícita
  do repositório, nunca ao diretório acidental do processo.
- Leituras de arquivos UTF-8 nos testes serão explícitas.
- O Java usado pelos testes será configurável por parâmetro/ambiente, com
  descoberta segura quando possível; caminhos de uma instalação local não
  serão requisitos implícitos.
- A verificação local manterá comandos separados para Node, PowerShell, Java e
  .NET, reportando claramente quando uma ferramenta obrigatória não está
  instalada.

### 5. Documentação e configuração pública

- A arquitetura, inventário, quick start, troubleshooting e matriz de
  funcionalidades refletirão Cloudflare, o fallback offline e o papel atual do
  Manager.
- Valores pessoais ou operacionais desnecessários não ficarão como defaults
  públicos em `wrangler.toml`; identificadores necessários para o deployment
  serão configuráveis, e segredos continuarão somente em secrets.
- A documentação não declarará um processo disponível quando ele existir
  apenas em código morto ou em uma etapa manual não implementada.

## Não objetivos

- Não implementar neste ciclo pipelines de conversão para NES, SNES, Mega Drive
  ou GBA.
- Não migrar ROMs, saves, playlists ou configurações existentes.
- Não alterar o contrato visual do launcher além do necessário para estados de
  erro, sincronização e disponibilidade do importador.
- Não publicar, revogar ou apagar recursos reais da conta Cloudflare durante os
  testes locais.

## Critérios de aceitação

1. O build do launcher não contém o sincronizador remoto legado sem consumidor
   de produção.
2. Um arquivo remoto localmente existente com tamanho/hash divergente continua
   disponível para instalação e não é tratado como válido.
3. A Activity encerra a sincronização e seu executor sem vazamento observável.
4. O importador não oferece plataformas que não consegue processar e rejeita
   arquivo compactado ambíguo ou que exceda o limite de extração.
5. Os testes de contrato do Worker continuam passando e novos testes cobrem os
   comportamentos acima.
6. Os checks executados a partir de qualquer diretório reportam caminhos
   corretamente e a documentação deixa de contradizer o código atual.

## Estratégia de testes

Cada comportamento alterado terá um teste de regressão escrito antes da
implementação. A validação final executará, quando disponível:

- `node --test cloudflare/tests/*.test.mjs`;
- os scripts PowerShell do launcher, Manager e release;
- os testes Java sem Android framework;
- `dotnet restore`, os testes do importador e o build/publish correspondente.

Quando uma ferramenta não existir no ambiente, o relatório registrará a
limitação separadamente dos resultados aprovados.
