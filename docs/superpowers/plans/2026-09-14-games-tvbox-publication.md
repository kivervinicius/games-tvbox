# Games TV Box Publicação Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publicar uma distribuição reutilizável do Games TV Box com todo o código-fonte, documentação ilustrada, exemplos neutros e fluxo reproduzível de instalação.

**Architecture:** O repositório será dividido em `launcher-android`, `manager-windows`, `scripts`, `examples` e `docs`. Os arquivos serão copiados do workspace local preservando a separação entre código público e dados do dispositivo; o README será a porta de entrada e apontará para o manual e os guias técnicos.

**Tech Stack:** Kotlin/Java Android Views, PowerShell, Android Platform Tools/ADB, JSON, Markdown, PNG/JPG e testes PowerShell/Java existentes.

**Spec:** `docs/superpowers/specs/2026-09-14-games-tvbox-publication-design.md`

## Global Constraints

- Não incluir ROMs comerciais, saves, dumps do Fire Stick, APKs de terceiros, chaves privadas ou configurações pessoais.
- Manter caminhos configuráveis e exemplos neutros; nenhum caminho `C:\Users\\kiver.teixeira` pode aparecer no produto.
- Preservar o baseline Android 9/API 28 e o alvo Fire TV Stick Lite/ARM32 documentado.
- Executar testes, build/verificação do APK e auditoria de arquivos antes do push.

### Task 1: Importar o código-fonte público

**Files:**
- Create: `launcher-android/` a partir de `tools/fireretro/app`, `tools/fireretro/tests` e `tools/fireretro/scripts` selecionados.
- Create: `manager-windows/` a partir de `fireretro-public/manager` sem `user-data` pessoal.
- Create: `scripts/` com scripts de build, preparação e validação que usam caminhos relativos.
- Test: `scripts/Test-PublicLayout.ps1`

**Interfaces:**
- Produces: fontes Android, Manager e scripts que podem ser executados a partir da raiz clonada.

- [ ] **Step 1: Write the failing layout audit** — faça o teste exigir os diretórios e arquivos públicos, além de rejeitar `sources`, `device-backups`, ROMs, APKs e caminhos pessoais.
- [ ] **Step 2: Run it to verify it fails** — `pwsh -File scripts/Test-PublicLayout.ps1`; esperado: falha porque os componentes ainda não foram importados.
- [ ] **Step 3: Copy and normalize sources** — copie somente fontes, testes, assets criados e scripts; substitua referências absolutas por resolução baseada em `$PSScriptRoot`.
- [ ] **Step 4: Run the audit again** — esperado: PASS e nenhum arquivo proibido.
- [ ] **Step 5: Commit** — `git add launcher-android manager-windows scripts && git commit -m "feat: publish launcher manager and scripts"`.

### Task 2: Criar exemplos configuráveis

**Files:**
- Create: `examples/catalog.example.json`
- Create: `examples/theme/slides.example.json`
- Create: `examples/controller-profile.example.cfg`
- Create: `examples/README.md`

- [ ] **Step 1: Write the failing example checks** — valide JSON, campos de caminho relativos e ausência de IP/endereço pessoal.
- [ ] **Step 2: Run the checks and confirm failure** — os exemplos ainda não existem.
- [ ] **Step 3: Add neutral examples** — use placeholders como `<PASTA_DE_ROMS>`, plataformas suportadas e um tema genérico sem nomes pessoais.
- [ ] **Step 4: Run JSON and content checks** — esperado: PASS.
- [ ] **Step 5: Commit** — `git add examples && git commit -m "docs: add neutral configuration examples"`.

### Task 3: Manual ilustrado do usuário

**Files:**
- Create: `docs/manual/README.md`
- Create: `docs/manual/01-manager.png`, `docs/manual/02-launcher.png`, `docs/manual/03-controller.png`, `docs/manual/04-customization.png`
- Create: `docs/manual/VIDEO.md`
- Create: `docs/manual/troubleshooting.md`

- [ ] **Step 1: Select public-safe captures** — use as telas do launcher e Manager sem ROMs distribuídas, credenciais ou dados do computador; redija legendas em português.
- [ ] **Step 2: Write the installation walkthrough** — documente Platform Tools, ADB, primeira autorização na TV, instalação do APK, catálogo do usuário, controle, saída do jogo e atualização.
- [ ] **Step 3: Add visual navigation guide** — explique setas, A/B, Menu, busca, abas, favoritos e o retorno ao launcher.
- [ ] **Step 4: Add video guide** — documente o roteiro de uma gravação demonstrativa e inclua links somente quando forem públicos e estáveis; não inclua ROMs comerciais.
- [ ] **Step 5: Review links and images** — valide todos os caminhos Markdown e a renderização das imagens.
- [ ] **Step 6: Commit** — `git add docs/manual && git commit -m "docs: add illustrated user manual"`.

### Task 4: README, desenvolvimento e licença

**Files:**
- Create: `README.md`
- Create: `CONTRIBUTING.md`
- Create: `SECURITY.md`
- Modify: `LICENSE` only if the existing license needs the project name attribution.

- [ ] **Step 1: Write README checks** — valide links para manual, exemplos, build e solução de problemas.
- [ ] **Step 2: Write the product README** — explique o que é, requisitos, instalação rápida, estrutura, personalização, compatibilidade e limites legais de conteúdo.
- [ ] **Step 3: Add contributor workflow** — descreva build, testes, revisão de assets e como abrir issues sem anexar ROMs.
- [ ] **Step 4: Add security policy** — peça para não publicar credenciais, IPs, dumps ou arquivos proprietários.
- [ ] **Step 5: Run link/content checks** — esperado: PASS.
- [ ] **Step 6: Commit** — `git add README.md CONTRIBUTING.md SECURITY.md LICENSE && git commit -m "docs: add project documentation and contribution guide"`.

### Task 5: Verificação completa e empacotamento

**Files:**
- Create: `scripts/Verify-PublicRelease.ps1`
- Create: `docs/release-checklist.md`

- [ ] **Step 1: Write release checks** — audite extensões, nomes proibidos, caminhos pessoais, manifesto, versão e links.
- [ ] **Step 2: Run checks before cleanup** — registre as falhas encontradas.
- [ ] **Step 3: Fix only public-release issues** — remova cópias privadas e corrija referências; não modifique ROMs ou dados fora do clone.
- [ ] **Step 4: Run Android and PowerShell tests** — execute os testes do launcher, Manager e scripts; compile/verifique o APK.
- [ ] **Step 5: Run the release audit** — esperado: PASS com inventário de arquivos.
- [ ] **Step 6: Commit** — `git add scripts docs/release-checklist.md && git commit -m "chore: add reproducible public release checks"`.

### Task 6: Push e validação remota

**Files:**
- Modify: GitHub repository history only after local review.

- [ ] **Step 1: Review diff and file inventory** — `git status --short`, `git diff --stat HEAD~5..HEAD` e auditoria de segredos.
- [ ] **Step 2: Run the complete verification command** — `pwsh -File scripts/Verify-PublicRelease.ps1`.
- [ ] **Step 3: Push main** — `git push origin main`.
- [ ] **Step 4: Validate remote tree** — confirme o commit no GitHub e os arquivos principais via `git ls-tree`/página remota.
- [ ] **Step 5: Record release handoff** — atualize `docs/release-checklist.md` com o commit publicado e o caminho para o APK gerado localmente, sem publicar o binário se ele não for redistribuível.
