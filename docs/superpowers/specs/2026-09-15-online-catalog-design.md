# Catálogo online e biblioteca privada

## Objetivo

Permitir que o Games TV Box tenha uma galeria online no GitHub Pages, com capas e informações selecionadas, enquanto a biblioteca pessoal permanece em um repositório privado e o Fire Stick funciona sem autenticação.

## Limites

- O repositório público do produto continua contendo apenas código, exemplos, documentação e mídia pública.
- O catálogo público contém somente itens explicitamente selecionados pelo usuário.
- ROMs comerciais pessoais não são publicadas no catálogo público nem no GitHub Pages.
- Downloads públicos ficam limitados a homebrews, demos e domínio público.
- ROMs e saves existentes no Fire Stick não são removidos ou substituídos automaticamente.

## Componentes

```text
games-tvbox                  código do Launcher e Manager
games-tvbox-catalog          catálogo público + capas + GitHub Pages
repo privado do usuário     ROMs e catálogo pessoal
Manager Windows              varredura, metadados, publicação e sync por ADB
Fire Stick                   catálogo local, capas locais e ROMs locais
```

O catálogo público será um `catalog.public.json` versionado junto da galeria Pages. O catálogo privado poderá ser lido pelo Manager usando um token de escopo mínimo guardado somente no Windows. O token nunca será enviado ao APK ou ao Fire Stick.

## Fluxo de dados

1. O usuário escolhe uma pasta local ou um repositório privado no Manager.
2. O Manager identifica arquivos, plataforma, nome, extensão e core sugerido.
3. O Manager busca metadados e capas por um provedor configurável, mantendo fallback local quando a rede ou a API falhar.
4. O usuário revisa os resultados e marca quais jogos podem entrar no catálogo público.
5. O Manager gera o catálogo público sem caminhos pessoais, credenciais ou ROMs comerciais.
6. O catálogo público é publicado no repositório Pages.
7. O Manager sincroniza o catálogo privado e os arquivos escolhidos com o Fire Stick por ADB.
8. O Launcher carrega o catálogo sincronizado, usando o catálogo embutido como fallback offline.

## Modelo de item

Cada jogo terá um identificador estável, nome, plataforma, caminho local/remoto, core, capa, descrição, ano, tags, origem e licença. O caminho público nunca deve revelar a pasta do usuário. Os campos de download público só serão gerados para itens marcados como legais para redistribuição.

## Atualização e offline

O Manager terá uma ação explícita de sincronização. A atualização não será destrutiva: itens existentes, favoritos, saves e configurações permanecem. O Launcher usará a última cópia válida quando o GitHub ou a rede estiverem indisponíveis.

## Segurança

- token do GitHub armazenado fora do repositório e fora dos arquivos enviados ao Fire Stick;
- validação que rejeita extensões de ROM no catálogo público;
- lista pública gerada somente por seleção explícita;
- separação entre `catalog.public.json` e catálogo privado;
- logs sem token, senha, caminho pessoal ou endereço IP.

## Critérios de aceite

- um jogo novo adicionado à pasta local aparece no catálogo após sincronização;
- capa e metadados são preenchidos quando o provedor responde;
- falha de rede não apaga nem impede a biblioteca local;
- o Fire Stick não solicita login do GitHub;
- o catálogo público contém somente itens marcados para publicação;
- o Launcher abre jogos do catálogo sincronizado e mantém o fallback embutido;
- testes verificam merge, deduplicação, sanitização e preservação de dados.

## Fases

1. catálogo remoto local no Launcher e sincronização ADB;
2. varredura, deduplicação e seleção pública no Manager;
3. capas/metadados por provedor configurável;
4. publicação GitHub Pages e fluxo autenticado no Windows;
5. downloads legais de homebrew/domínio público e testes de regressão.
