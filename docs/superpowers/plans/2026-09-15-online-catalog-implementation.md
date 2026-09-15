# Catálogo online e biblioteca privada Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sincronizar o inventário do Fire Stick e da pasta local com um catálogo público selecionado e uma biblioteca privada no GitHub, mantendo o Fire Stick sem autenticação.

**Architecture:** O Manager Windows será a única ponte autenticada. Ele lê o Fire Stick por ADB, mescla catálogos, consulta um provedor configurável de metadados/capas, publica somente itens marcados como públicos e envia um catálogo local ao Launcher. O Launcher lê o catálogo sincronizado com fallback para o catálogo embutido; uma galeria estática em `catalog-site/` serve como modelo para GitHub Pages.

**Tech Stack:** PowerShell 7, Windows DPAPI, ADB, Java Android API 28, JSON, GitHub REST/Pages e GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-15-online-catalog-design.md`

## Global Constraints

- O repositório público não conterá ROMs comerciais, saves, chaves, tokens ou caminhos pessoais.
- O token do GitHub ficará somente no Windows, protegido por DPAPI, e nunca será enviado ao APK ou ao Fire Stick.
- A sincronização será não destrutiva: não apagar ROMs, saves, favoritos ou configurações existentes.
- O catálogo público conterá somente itens explicitamente marcados pelo usuário e arquivos legais para redistribuição.
- O Launcher manterá fallback offline para o catálogo embutido.
- Downloads públicos serão limitados a homebrew, demos e domínio público.

---

### Task 1: Contrato de catálogo remoto no Launcher

**Files:**
- Create: `launcher-android/app/src/main/java/com/kiver/fireretro/CatalogStore.java`
- Modify: `launcher-android/app/src/main/java/com/kiver/fireretro/MainActivity.java:486-503`
- Create: `launcher-android/tests/catalog-store.tests.ps1`
- Modify: `launcher-android/tests/project.tests.ps1`

**Interfaces:**
- `CatalogStore.load(Context, File)` retorna `List<CatalogGame>` e tenta primeiro o arquivo externo em `/sdcard/Android/data/com.kiver.fireretro/files/catalog/games.json`, depois `assets/games.json`.
- `CatalogStore.CatalogGame` expõe `label`, `path`, `corePath`, `platform`, `image`, `description`, `year`, `tags` e `publicDownload`.
- `MainActivity.readGames()` converte `CatalogGame` para o modelo atual sem alterar a abertura RetroArch.

- [ ] **Step 1: Write the failing test**

Adicionar ao teste PowerShell asserções literais para o diretório remoto, a ordem externo-embutido e os campos de metadados:

```powershell
$source = Get-Content launcher-android/app/src/main/java/com/kiver/fireretro/CatalogStore.java -Raw
foreach ($required in @('files/catalog/games.json','assets/games.json','publicDownload','description','tags')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "CatalogStore contract missing: $required" }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pwsh -NoProfile -File launcher-android/tests/catalog-store.tests.ps1`

Expected: FAIL because `CatalogStore.java` does not exist.

- [ ] **Step 3: Write minimal implementation**

Implement `CatalogStore.load` with `FileReader` for the external file, `AssetManager.open("games.json")` as fallback, JSON parsing tolerante a campos ausentes e ignorando itens sem `path`. Atualizar `MainActivity.readGames()` para delegar ao store.

- [ ] **Step 4: Run test to verify it passes**

Run: `pwsh -NoProfile -File launcher-android/tests/catalog-store.tests.ps1; pwsh -NoProfile -File launcher-android/tests/project.tests.ps1`

Expected: PASS para o contrato externo, fallback e handoff RetroArch.

- [ ] **Step 5: Commit**

```powershell
git add launcher-android/app/src/main/java/com/kiver/fireretro/CatalogStore.java launcher-android/app/src/main/java/com/kiver/fireretro/MainActivity.java launcher-android/tests
git commit -m "feat: load synchronized catalog with offline fallback"
```

### Task 2: Varredura, merge e inventário do Fire Stick no Manager

**Files:**
- Modify: `manager-windows/FireRetroManager.ps1:1-220`
- Modify: `manager-windows/FireRetroManager.ps1:300-370`
- Modify: `manager-windows/tests/Test-Manager.ps1`
- Create: `manager-windows/tests/Test-CatalogSync.ps1`

**Interfaces:**
- `Get-RemoteGameInventory -Serial` retorna itens de ROM encontrados em `/sdcard/roms` sem remover arquivos.
- `Merge-GameCatalog -Existing -Incoming` deduplica por caminho normalizado e preserva campos editados manualmente.
- `Sync-CatalogToFireStick -Serial -Catalog` grava somente `files/catalog/games.json` por ADB.
- `Get-RemoteThemeInventory -Serial` lista capas/slides existentes para inclusão no inventário privado.

- [ ] **Step 1: Write the failing test**

Criar um teste textual que exija as funções, `adb shell find`, `adb pull` somente em modo leitura e o destino externo do catálogo:

```powershell
$source = Get-Content manager-windows/FireRetroManager.ps1 -Raw
foreach ($required in @('Get-RemoteGameInventory','Merge-GameCatalog','Sync-CatalogToFireStick','Get-RemoteThemeInventory','shell','find','files/catalog/games.json')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "Catalog sync contract missing: $required" }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pwsh -NoProfile -File manager-windows/tests/Test-CatalogSync.ps1`

Expected: FAIL because the inventory and merge functions do not exist.

- [ ] **Step 3: Write minimal implementation**

Adicionar funções que usem o wrapper ADB existente, classifiquem extensões conhecidas, gerem `label`, `platform`, `path` e `core_path`, façam merge sem duplicatas e usem `adb push` para o JSON externo. O botão de sincronização deve mostrar quantidade local, remota e enviada.

- [ ] **Step 4: Run test to verify it passes**

Run: `pwsh -NoProfile -File manager-windows/tests/Test-CatalogSync.ps1; pwsh -NoProfile -File manager-windows/tests/Test-Manager.ps1`

Expected: PASS sem comandos destrutivos e com catálogo enviado ao diretório externo.

- [ ] **Step 5: Commit**

```powershell
git add manager-windows/FireRetroManager.ps1 manager-windows/tests
git commit -m "feat: scan and sync Fire Stick catalog"
```

### Task 3: Metadados e capas automáticos no Windows

**Files:**
- Create: `manager-windows/MetadataProviders.ps1`
- Modify: `manager-windows/FireRetroManager.ps1:1-220`
- Create: `examples/metadata-provider.example.json`
- Modify: `manager-windows/tests/Test-CatalogSync.ps1`
- Modify: `docs/reference/configuration.md`

**Interfaces:**
- `Get-GameMetadata -Game -ProviderConfig` retorna título, descrição, ano, tags e URL de capa.
- `Save-GameMetadataCache -Items` grava dados em `%LOCALAPPDATA%\FireRetroManager\metadata.json`.
- `Download-CoverToCache -Url -GameId` grava capas somente no cache local.
- `ProviderConfig` permite base URL e chave fora do repositório; sem configuração, o fluxo usa nome de arquivo e capa existente.

- [ ] **Step 1: Write the failing test**

Adicionar testes para cache fora do projeto, fallback offline e ausência de chaves no catálogo público:

```powershell
$source = Get-Content manager-windows/MetadataProviders.ps1 -Raw
foreach ($required in @('Get-GameMetadata','Save-GameMetadataCache','Download-CoverToCache','LOCALAPPDATA','ProviderConfig')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "Metadata contract missing: $required" }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pwsh -NoProfile -File manager-windows/tests/Test-CatalogSync.ps1`

Expected: FAIL because the provider module does not exist.

- [ ] **Step 3: Write minimal implementation**

Criar uma interface de provedor HTTP configurável, normalizar respostas para o modelo do catálogo, armazenar a resposta e a capa em cache, aplicar timeout e continuar sem rede. Nunca colocar a chave ou a URL privada no `catalog.public.json`.

- [ ] **Step 4: Run test to verify it passes**

Run: `pwsh -NoProfile -File manager-windows/tests/Test-CatalogSync.ps1; pwsh -NoProfile -File scripts/Test-PublicLayout.ps1`

Expected: PASS com cache externo e auditoria pública sem segredos.

- [ ] **Step 5: Commit**

```powershell
git add manager-windows/MetadataProviders.ps1 manager-windows/FireRetroManager.ps1 manager-windows/tests examples docs/reference/configuration.md
git commit -m "feat: add cached metadata and cover providers"
```

### Task 4: Biblioteca privada e publicação GitHub Pages

**Files:**
- Create: `manager-windows/GitHubCatalog.ps1`
- Modify: `manager-windows/FireRetroManager.ps1:220-370`
- Create: `catalog-site/index.html`
- Create: `catalog-site/catalog.public.json`
- Create: `catalog-site/assets/.gitkeep`
- Create: `.github/workflows/pages.yml`
- Create: `catalog-site/README.md`
- Modify: `scripts/Test-PublicLayout.ps1`
- Create: `manager-windows/tests/Test-GitHubCatalog.ps1`

**Interfaces:**
- `Save-GitHubCredential -Token` stores a DPAPI-protected token in `%LOCALAPPDATA%\FireRetroManager\github-token.xml`.
- `Publish-PrivateCatalog -RepoPath -Catalog` updates a local private checkout and pushes through the configured Git credential.
- `Export-PublicCatalog -Catalog` removes private paths, tokens and non-redistributable download fields.
- `catalog-site/index.html` loads `catalog.public.json` and renders filters, covers and legal download links.

- [ ] **Step 1: Write the failing test**

Criar teste que exija DPAPI, separação público/privado, workflow Pages e rejeição de ROMs comerciais no site:

```powershell
$source = Get-Content manager-windows/GitHubCatalog.ps1 -Raw
foreach ($required in @('ConvertFrom-SecureString','Save-GitHubCredential','Publish-PrivateCatalog','Export-PublicCatalog','catalog.public.json')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "GitHub catalog contract missing: $required" }
}
if ((Get-Content scripts/Test-PublicLayout.ps1 -Raw) -notmatch 'catalog-site') { throw 'Public layout audit must cover catalog-site' }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pwsh -NoProfile -File manager-windows/tests/Test-GitHubCatalog.ps1`

Expected: FAIL because the private publisher and Pages site do not exist.

- [ ] **Step 3: Write minimal implementation**

Implement token storage with user-scoped DPAPI, exportação pública sanitizada, publicação de catálogo em checkout configurado e galeria estática sem código de servidor. O workflow publica somente `catalog-site/` e não executa comandos com ROMs.

- [ ] **Step 4: Run test to verify it passes**

Run: `pwsh -NoProfile -File manager-windows/tests/Test-GitHubCatalog.ps1; pwsh -NoProfile -File scripts/Verify-PublicRelease.ps1`

Expected: PASS e nenhuma credencial ou ROM comercial detectada no conteúdo público.

- [ ] **Step 5: Commit**

```powershell
git add manager-windows/GitHubCatalog.ps1 manager-windows/FireRetroManager.ps1 manager-windows/tests catalog-site .github/workflows/pages.yml scripts/Test-PublicLayout.ps1
git commit -m "feat: add private catalog bridge and Pages gallery"
```

### Task 5: Pull do Fire Stick, preservação e documentação operacional

**Files:**
- Modify: `manager-windows/FireRetroManager.ps1:300-370`
- Create: `manager-windows/tests/Test-DeviceImport.ps1`
- Create: `docs/operations/private-library.md`
- Modify: `docs/quick-start.md`
- Modify: `docs/reference/feature-matrix.md`
- Modify: `docs/faq.md`
- Modify: `docs/internal/FEATURE_INVENTORY.md`
- Modify: `docs/internal/DOCUMENTATION_EVIDENCE.md`

**Interfaces:**
- `Import-FireStickLibrary -Serial -Destination` usa `adb pull` para copiar ROMs, capas, catálogo e favoritos para uma área privada local, sem `rm`, `move` ou sobrescrita destrutiva.
- `Sync-FireStickToPrivateRepo -Serial -RepoPath` executa importação, merge, sanitização e publicação somente após a confirmação do usuário no Manager.

- [ ] **Step 1: Write the failing test**

Criar teste que exija comandos de leitura e rejeite ações destrutivas:

```powershell
$source = Get-Content manager-windows/FireRetroManager.ps1 -Raw
foreach ($required in @('Import-FireStickLibrary','Sync-FireStickToPrivateRepo','adb pull','ROMs','saves')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "Device import contract missing: $required" }
}
if ($source -match 'adb shell rm|adb shell move|Remove-Item.*rom') { throw 'Device import must not delete ROMs or saves' }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pwsh -NoProfile -File manager-windows/tests/Test-DeviceImport.ps1`

Expected: FAIL because the Fire Stick import action does not exist.

- [ ] **Step 3: Write minimal implementation**

Adicionar uma ação explícita no Manager para importar a biblioteca, copiar `games.json`, tema, capas e playlists relevantes, criar relatório de conflitos e encaminhar o resultado ao publicador privado. O destino padrão ficará no cache local, fora do repositório público.

- [ ] **Step 4: Run test to verify it passes**

Run: `pwsh -NoProfile -File manager-windows/tests/Test-DeviceImport.ps1; pwsh -NoProfile -File scripts/Verify-PublicRelease.ps1`

Expected: PASS com documentação atualizada e auditoria preservacionista.

- [ ] **Step 5: Commit**

```powershell
git add manager-windows/FireRetroManager.ps1 manager-windows/tests docs
git commit -m "docs: document private Fire Stick library sync"
```

## Final verification

- Executar `pwsh -NoProfile -File scripts/Verify-PublicRelease.ps1`.
- Executar `pwsh -NoProfile -File launcher-android/tests/project.tests.ps1`.
- Executar `pwsh -NoProfile -File manager-windows/tests/Test-Manager.ps1`.
- Executar todos os testes novos de catálogo, GitHub e importação.
- Confirmar que o APK não contém token, URL privada ou ROM comercial.
- Confirmar que o catálogo Pages contém apenas itens selecionados e legais.
- Confirmar que o Fire Stick abre o catálogo sincronizado sem login e funciona offline.
