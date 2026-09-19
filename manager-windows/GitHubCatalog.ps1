# Authentication stays in the Windows user profile; Git push uses the configured Git credential helper.
function Save-GitHubCredential {
    param([Parameter(Mandatory)][Security.SecureString]$Token)
    if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) { throw 'O armazenamento protegido exige Windows.' }
    if ($Token.Length -eq 0) { throw 'Informe uma credencial.' }
    $directory = Join-Path $env:LOCALAPPDATA 'FireRetroManager'
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $destination = Join-Path $directory 'github-token.xml'
    # Without -Key, ConvertFrom-SecureString uses DPAPI CurrentUser on Windows.
    $encrypted = $Token | ConvertFrom-SecureString -ErrorAction Stop
    $encrypted | Export-Clixml -LiteralPath $destination -Force -ErrorAction Stop
    return $destination
}

function Get-GitHubCredential {
    $path = Join-Path $env:LOCALAPPDATA 'FireRetroManager/github-token.xml'
    try {
        $stored = Import-Clixml -LiteralPath $path -ErrorAction Stop
        # Native CLIXML decrypts its SecureString through DPAPI during import.
        if ($stored -is [Security.SecureString]) { return $stored }
        # Retain support for the encrypted-string format written by this Manager.
        if ($stored -is [string]) { return (ConvertTo-SecureString -String $stored -ErrorAction Stop) }
        throw 'Formato de credencial inválido.'
    }
    catch { throw 'Salve a credencial GitHub neste usuário do Windows antes de publicar.' }
}

function Get-ManagerCatalogReviewPath {
    return (Join-Path $env:LOCALAPPDATA 'FireRetroManager/catalog-review.json')
}

function Save-ManagerCatalogReview {
    param([AllowEmptyCollection()][array]$Catalog = @())
    $path = Get-ManagerCatalogReviewPath
    New-Item -ItemType Directory -Path (Split-Path -Parent $path) -Force | Out-Null
    [pscustomobject]@{ version=1; updatedAt=(Get-Date).ToUniversalTime().ToString('o'); items=@($Catalog) } |
        ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $path -Encoding utf8
    return $path
}

function Get-ManagerCatalogReview {
    $path = Get-ManagerCatalogReviewPath
    if (-not (Test-Path -LiteralPath $path)) { return @() }
    try { return @((Get-Content -LiteralPath $path -Raw | ConvertFrom-Json -ErrorAction Stop).items) } catch { return @() }
}

function Get-PublicCatalogText {
    param($Value, [int]$MaxLength = 2000)
    $text = ([string]$Value).Trim()
    # Free-text fields never need file separators. Reject them conservatively,
    # including percent-encoded paths, instead of guessing private root names.
    if ($text.Length -gt $MaxLength) { return '' }
    $decoded = $text
    $stable = $false
    for ($round = 0; $round -lt 8; $round++) {
        $next = [Uri]::UnescapeDataString($decoded)
        if ($next -ceq $decoded) { $stable = $true; break }
        $decoded = $next
    }
    # Reject excessive nesting instead of accepting a still-encoded path.
    if (-not $stable) { return '' }
    if ($decoded.Contains('/') -or $decoded.Contains('\')) { return '' }
    if ($decoded -match '(?i)(?:[a-z]:[\\/]|\\\\|/(?:sdcard|data|home|users|storage)/|https?://|\b(?:\d{1,3}\.){3}\d{1,3}\b|gh[pousr]_|github_pat_|(?:token|password|secret|authorization|api[_ -]?key)\s*[:=])') { return '' }
    return $text
}

function Get-CatalogPropertyValue {
    param($Game, [Parameter(Mandatory)][string]$Name)
    if ($null -eq $Game) { return $null }
    $property = $Game.PSObject.Properties[$Name]
    if ($null -eq $property) { return $null }
    return $property.Value
}

function Test-PublicLandingUrl {
    param([string]$Url)
    $uri = $null
    if (-not [Uri]::TryCreate($Url, [UriKind]::Absolute, [ref]$uri)) { return $false }
    return ($uri.Scheme -eq 'https' -and $uri.IsDefaultPort -and -not $uri.UserInfo -and -not $uri.Query -and -not $uri.Fragment -and
        $uri.HostNameType -eq [UriHostNameType]::Dns -and $uri.Host -match '\.' -and $uri.Host -notmatch '(?i)(?:\.local|\.internal|\.localhost|\.lan|\.home|\.test|\.invalid)$' -and
        $uri.AbsolutePath -notmatch '(?i)(?:\.(?:nes|nez|sfc|smc|fig|md|gen|sms|gba|gb|gbc|iso|chd|cue|bin|zip|7z|sav|srm)$|/(?:users|home|sdcard|storage)/|gh[pousr]_|github_pat_)')
}

function Export-PublicCatalog {
    param([AllowEmptyCollection()][array]$Catalog = @(), [string]$DestinationPath)
    $items = @()
    foreach ($game in $Catalog) {
        $publicSelected = Get-CatalogPropertyValue $game 'publicSelected'
        $redistributable = Get-CatalogPropertyValue $game 'redistributable'
        $category = Get-CatalogPropertyValue $game 'category'
        if ($publicSelected -isnot [bool] -or $publicSelected -ne $true -or $redistributable -isnot [bool] -or $redistributable -ne $true) { continue }
        if ($category -notin @('homebrew','demo','public-domain')) { continue }
        $label = Get-PublicCatalogText (Get-CatalogPropertyValue $game 'label') 200
        $license = Get-PublicCatalogText (Get-CatalogPropertyValue $game 'license') 200
        if (-not $label -or -not $license) { continue }
        $platform = Get-PublicCatalogText (Get-CatalogPropertyValue $game 'platform') 100
        $hash = [Security.Cryptography.SHA256]::Create()
        try { $id = ([BitConverter]::ToString($hash.ComputeHash([Text.Encoding]::UTF8.GetBytes($platform + ':' + $label)))).Replace('-','').ToLowerInvariant().Substring(0,24) } finally { $hash.Dispose() }
        $item = [ordered]@{ id=$id; label=$label; platform=$platform; description=(Get-PublicCatalogText (Get-CatalogPropertyValue $game 'description')); license=$license; category=[string]$category; tags=@() }
        foreach ($tag in @(Get-CatalogPropertyValue $game 'tags')) { $clean = Get-PublicCatalogText $tag 80; if ($clean) { $item.tags += $clean } }
        $year = 0
        if ([int]::TryParse([string](Get-CatalogPropertyValue $game 'year'),[ref]$year) -and $year -ge 1950 -and $year -le 2200) { $item.year=$year }
        $image = Get-CatalogPropertyValue $game 'image'
        if ([string]$image -cmatch '^assets/[a-zA-Z0-9][a-zA-Z0-9_-]*\.(png|jpg|jpeg|webp)$') { $item.image=[string]$image }
        $downloadConfirmed = Get-CatalogPropertyValue $game 'downloadPublicConfirmed'
        $publicDownload = Get-CatalogPropertyValue $game 'publicDownload'
        if ($downloadConfirmed -is [bool] -and $downloadConfirmed -eq $true -and (Test-PublicLandingUrl ([string]$publicDownload))) { $item.publicDownload=[string]$publicDownload }
        $items += [pscustomobject]$item
    }
    $payload = [pscustomobject]@{ version=1; items=@($items) }
    if ($DestinationPath) {
        if ([IO.Path]::GetFileName($DestinationPath) -ne 'catalog.public.json') { throw 'Use o nome catalog.public.json para a projeção pública.' }
        $payload | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $DestinationPath -Encoding utf8 -ErrorAction Stop
    }
    return $payload
}

function Test-ThemePackage {
    param([Parameter(Mandatory)][string]$PackagePath)
    $root = (Resolve-Path -LiteralPath $PackagePath -ErrorAction Stop).Path
    $manifestPath = Join-Path $root 'theme.json'
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw 'O pacote de tema precisa de theme.json.' }
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json -ErrorAction Stop
    if ([string]$manifest.id -notmatch '^[a-z0-9][a-z0-9-]{1,48}$') { throw 'O identificador do tema deve usar letras minúsculas, números e hífens.' }
    if ([string]::IsNullOrWhiteSpace([string]$manifest.name)) { throw 'O tema precisa de um nome.' }
    $overlay = 0
    if (-not [int]::TryParse([string]$manifest.overlay,[ref]$overlay) -or $overlay -lt 0 -or $overlay -gt 90) { throw 'A opacidade deve ficar entre 0 e 90.' }
    if ([string]$manifest.cardLayout -notin @('compact','wide')) { throw 'A disposição deve ser compact ou wide.' }
    if ([string]$manifest.scale -notin @('contain','cover')) { throw 'O enquadramento deve ser contain ou cover.' }
    $background = [string]$manifest.background
    if ($background -notmatch '^[a-zA-Z0-9][a-zA-Z0-9_.-]*\.(png|jpg|jpeg|webp)$' -or -not (Test-Path -LiteralPath (Join-Path $root $background) -PathType Leaf)) { throw 'O fundo do tema não foi encontrado.' }
    return $manifest
}

function Export-AndroidAppCatalog {
    param([Parameter(Mandatory)][string]$DestinationPath, [AllowEmptyCollection()][array]$Apps = @())
    $items = @()
    foreach ($app in $Apps) {
        $title = [string](Get-CatalogPropertyValue $app 'title'); $package = [string](Get-CatalogPropertyValue $app 'package'); $source = [string](Get-CatalogPropertyValue $app 'source')
        if ([string]::IsNullOrWhiteSpace($title) -or $package -notmatch '^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+$' -or $source -notmatch '^(?:https://|/|[A-Za-z]:[\\/])') { continue }
        $sourceType = if ($source -match '^https://') { 'store' } else { 'apk' }
        $items += [pscustomobject][ordered]@{ title=$title.Trim(); package=$package; source=$source; sourceType=$sourceType; category=([string](Get-CatalogPropertyValue $app 'category')) }
    }
    $result = [pscustomobject][ordered]@{ version=1; generatedAt=(Get-Date).ToUniversalTime().ToString('o'); items=@($items | Sort-Object title) }
    $result | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $DestinationPath -Encoding utf8
    return $result
}

function Publish-AndroidAppCatalog {
    param([Parameter(Mandatory)][string]$RepoPath, [Parameter(Mandatory)][string]$CatalogPath)
    $catalog = Get-Content -LiteralPath $CatalogPath -Raw | ConvertFrom-Json -ErrorAction Stop
    $valid = Export-AndroidAppCatalog -DestinationPath $CatalogPath -Apps @($catalog.items)
    $resolved = (Resolve-Path -LiteralPath $RepoPath -ErrorAction Stop).Path.TrimEnd('\\','/')
    if (Invoke-CatalogGit $resolved @('status','--porcelain')) { throw 'O checkout deve estar limpo antes da publicação.' }
    Assert-PrivateGitHubRemote (Invoke-CatalogGit $resolved @('remote','get-url','--all','origin'))
    Copy-Item -LiteralPath $CatalogPath -Destination (Join-Path $resolved 'apps.json') -Force
    $manifestPath = Join-Path $resolved 'library.manifest.json'
    $manifest = if (Test-Path $manifestPath) { Get-Content $manifestPath -Raw | ConvertFrom-Json } else { [pscustomobject]@{version=1;items=@();themes=@()} }
    $manifest | Add-Member -NotePropertyName apps -NotePropertyValue @($valid.items) -Force
    $manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestPath -Encoding utf8
    Invoke-CatalogGit $resolved @('add','--','apps.json','library.manifest.json') | Out-Null
    $branch = Invoke-CatalogGit $resolved @('symbolic-ref','--short','HEAD')
    Invoke-CatalogGit $resolved @('commit','-m','Update Android app catalog') | Out-Null
    Invoke-CatalogGit $resolved @('push','origin',('HEAD:refs/heads/' + $branch)) | Out-Null
    return [pscustomobject]@{ Published=$true; Count=@($valid.items).Count }
}

function New-ThemePackage {
    param(
        [Parameter(Mandatory)][string]$Id,
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$BackgroundPath,
        [string]$AccentColor = '#38D9FF',
        [int]$Overlay = 68,
        [ValidateSet('compact','wide')][string]$CardLayout = 'compact',
        [ValidateSet('contain','cover')][string]$Scale = 'contain',
        [Parameter(Mandatory)][string]$OutputRoot
    )
    if ($Id -notmatch '^[a-z0-9][a-z0-9-]{1,48}$') { throw 'O identificador do tema deve usar letras minúsculas, números e hífens.' }
    if (-not (Test-Path -LiteralPath $BackgroundPath -PathType Leaf)) { throw 'Escolha um arquivo de fundo existente.' }
    if ($Overlay -lt 0 -or $Overlay -gt 90) { throw 'A opacidade deve ficar entre 0 e 90.' }
    if ($AccentColor -notmatch '^#[0-9a-fA-F]{6}$') { throw 'A cor de destaque deve estar no formato #RRGGBB.' }
    $extension = [IO.Path]::GetExtension($BackgroundPath).ToLowerInvariant()
    if ($extension -notin @('.png','.jpg','.jpeg','.webp')) { throw 'O fundo deve ser PNG, JPG, JPEG ou WEBP.' }
    $package = Join-Path ([IO.Path]::GetFullPath($OutputRoot)) $Id
    New-Item -ItemType Directory -Path $package -Force | Out-Null
    $background = 'background' + $extension
    Copy-Item -LiteralPath $BackgroundPath -Destination (Join-Path $package $background) -Force
    [ordered]@{ version=1; id=$Id; name=$Name.Trim(); background=$background; accent=$AccentColor.ToUpperInvariant(); overlay=$Overlay; cardLayout=$CardLayout; scale=$Scale } |
        ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $package 'theme.json') -Encoding utf8
    Test-ThemePackage -PackagePath $package | Out-Null
    return $package
}

function Publish-ThemePackage {
    param([Parameter(Mandatory)][string]$RepoPath, [Parameter(Mandatory)][string]$PackagePath)
    $manifest = Test-ThemePackage -PackagePath $PackagePath
    $resolved = (Resolve-Path -LiteralPath $RepoPath -ErrorAction Stop).Path.TrimEnd('\','/')
    if (Invoke-CatalogGit $resolved @('status','--porcelain')) { throw 'O checkout deve estar limpo antes da publicação.' }
    $remote = Invoke-CatalogGit $resolved @('remote','get-url','--all','origin')
    Assert-PrivateGitHubRemote $remote
    $destination = Join-Path $resolved ('themes/' + $manifest.id)
    New-Item -ItemType Directory -Path $destination -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $PackagePath '*') -Destination $destination -Recurse -Force
    $libraryManifestPath = Join-Path $resolved 'library.manifest.json'
    $library = if (Test-Path -LiteralPath $libraryManifestPath) { Get-Content -LiteralPath $libraryManifestPath -Raw | ConvertFrom-Json } else { [pscustomobject]@{ version=1; items=@(); themes=@() } }
    $themes = @($library.themes | Where-Object { $_.id -ne $manifest.id })
    $themes += [pscustomobject][ordered]@{ id=$manifest.id; name=$manifest.name; background=$manifest.background; accent=$manifest.accent; overlay=[int]$manifest.overlay; cardLayout=$manifest.cardLayout; scale=$manifest.scale; packagePath=('themes/' + $manifest.id) }
    $library | Add-Member -NotePropertyName themes -NotePropertyValue @($themes) -Force
    $library | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $libraryManifestPath -Encoding utf8
    Invoke-CatalogGit $resolved @('add','--',('themes/' + $manifest.id),'library.manifest.json') | Out-Null
    $branch = Invoke-CatalogGit $resolved @('symbolic-ref','--short','HEAD')
    Invoke-CatalogGit $resolved @('commit','-m',('Add theme ' + $manifest.id)) | Out-Null
    Invoke-CatalogGit $resolved @('push','origin',('HEAD:refs/heads/' + $branch)) | Out-Null
    return [pscustomobject]@{ Published=$true; Id=$manifest.id; Name=$manifest.name; Path=$destination }
}

function New-RemoteLibraryManifest {
    param(
        [AllowEmptyCollection()][array]$Catalog = @(),
        [Parameter(Mandatory)][string]$AssetRoot,
        [string]$ReleaseTag = 'library-latest',
        [string]$Repository = 'owner/private-library'
    )
    if ($Repository -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') { throw 'Repository must be owner/name without credentials.' }
    if ($ReleaseTag -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]*$') { throw 'ReleaseTag contains unsupported characters.' }
    $items = @()
    foreach ($game in @($Catalog)) {
        if ($null -eq $game) { continue }
        $source = [string](Get-CatalogPropertyValue $game 'FullPath')
        $path = [string](Get-CatalogPropertyValue $game 'path')
        if ([string]::IsNullOrWhiteSpace($source) -or -not (Test-Path -LiteralPath $source -PathType Leaf)) {
            $relative = $path -replace '^/sdcard/roms/',''
            $source = Join-Path $AssetRoot ($relative -replace '/','\')
        }
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { continue }
        $relativePath = if ($path -match '^/sdcard/roms/') { $path.Substring('/sdcard/roms/'.Length) } else { [IO.Path]::GetFileName($source) }
        $assetName = ($relativePath -replace '[\\/]+','-')
        $hash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
        $length = (Get-Item -LiteralPath $source).Length
        $id = [string](Get-CatalogPropertyValue $game 'id'); if ([string]::IsNullOrWhiteSpace($id)) { $id = ($relativePath -replace '[^A-Za-z0-9_.-]','_') }
        $items += [pscustomobject][ordered]@{
            id=$id; label=[string](Get-CatalogPropertyValue $game 'label'); platform=[string](Get-CatalogPropertyValue $game 'platform')
            path=(('/sdcard/roms/' + $relativePath).Replace('\\','/')); core_path=[string](Get-CatalogPropertyValue $game 'core_path')
            image=[string](Get-CatalogPropertyValue $game 'image'); size=[int64]$length; sha256=$hash; assetName=$assetName
            releaseTag=$ReleaseTag; downloadUrl=('https://github.com/' + $Repository + '/releases/download/' + $ReleaseTag + '/' + [Uri]::EscapeDataString($assetName))
        }
    }
    return [pscustomobject][ordered]@{ version=1; generatedAt=(Get-Date).ToUniversalTime().ToString('o'); repository=$Repository; items=@($items | Sort-Object platform,label); themes=@() }
}

function Export-RemoteLibraryManifest {
    param([Parameter(Mandatory)][string]$DestinationPath, [Parameter(Mandatory)][string]$AssetRoot, [AllowEmptyCollection()][array]$Catalog = @(), [string]$ReleaseTag = 'library-latest', [string]$Repository = 'owner/private-library')
    if ([IO.Path]::GetFileName($DestinationPath) -ne 'library.manifest.json') { throw 'Use o nome library.manifest.json para o manifesto remoto.' }
    $manifest=New-RemoteLibraryManifest -Catalog $Catalog -AssetRoot $AssetRoot -ReleaseTag $ReleaseTag -Repository $Repository
    $manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $DestinationPath -Encoding utf8
    return $manifest
}

function Invoke-CatalogGit {
    param([string]$RepoPath, [string[]]$Arguments)
    $output = & git -C $RepoPath @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'A operação Git falhou. Verifique o checkout e a autenticação no Git.' }
    return ($output -join "`n").Trim()
}

function Assert-PrivateGitHubRemote {
    param([string]$Remote)
    $repoName = ''
    if ($Remote -match '^https://github\.com/([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+?)(?:\.git)?$') { $repoName=$Matches[1] }
    elseif ($Remote -match '^git@github\.com:([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+?)(?:\.git)?$') { $repoName=$Matches[1] }
    else { throw 'Configure origin com um endereço GitHub sem credenciais na URL.' }
    $credential = Get-GitHubCredential
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($credential)
    try {
        $headers = @{ Authorization=('Bearer ' + [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)); Accept='application/vnd.github+json' }
        try { $repo = Invoke-RestMethod -Uri ('https://api.github.com/repos/' + $repoName) -Headers $headers -TimeoutSec 20 -ErrorAction Stop }
        catch { throw 'Não foi possível confirmar que o repositório GitHub é privado.' }
        if ($repo.private -ne $true -or $repo.full_name -ine $repoName) { throw 'A publicação exige um repositório GitHub privado verificado.' }
    } finally {
        if ($headers) { $headers.Clear() }
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
        $credential.Dispose()
    }
}

function Publish-PrivateCatalog {
    param([Parameter(Mandatory)][string]$RepoPath, [AllowEmptyCollection()][array]$Catalog = @())
    $resolved = (Resolve-Path -LiteralPath $RepoPath -ErrorAction Stop).Path.TrimEnd('\','/')
    $publicRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')).TrimEnd('\','/')
    if ($resolved.Equals($publicRoot,[StringComparison]::OrdinalIgnoreCase) -or $resolved.StartsWith($publicRoot + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Escolha um checkout privado fora do projeto público.' }
    $top = Invoke-CatalogGit $resolved @('rev-parse','--show-toplevel')
    if (-not [IO.Path]::GetFullPath($top).TrimEnd('\','/').Equals($resolved,[StringComparison]::OrdinalIgnoreCase)) { throw 'Escolha a raiz do checkout privado.' }
    if (Invoke-CatalogGit $resolved @('status','--porcelain')) { throw 'O checkout deve estar limpo antes da publicação.' }
    $branch = Invoke-CatalogGit $resolved @('symbolic-ref','--short','HEAD')
    $remote = Invoke-CatalogGit $resolved @('remote','get-url','--all','origin')
    $pushRemote = Invoke-CatalogGit $resolved @('remote','get-url','--push','--all','origin')
    if ($pushRemote -ne $remote) { throw 'Os endereços de leitura e publicação de origin devem coincidir.' }
    Assert-PrivateGitHubRemote $remote
    $destination = Join-Path $resolved 'catalog.private.json'
    if ((Test-Path $destination) -and ((Get-Item -LiteralPath $destination -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'O catálogo privado não pode ser um link.' }
    # An allowlist prevents credentials and arbitrary nested objects from entering even the private catalog.
    $privateItems = @($Catalog | ForEach-Object {
        $item=[ordered]@{}
        foreach ($name in @('id','label','platform','path','core_path','image','description','year','tags','publicSelected','redistributable','category','license','publicDownload','downloadPublicConfirmed')) {
            $value=$_.PSObject.Properties[$name]
            if ($null -eq $value) { continue }
            if ($name -eq 'tags') {
                $item[$name]=@($value.Value | Where-Object { $_ -is [string] -and $_ -notmatch '(?i)gh[pousr]_|github_pat_|(?:token|password|secret|authorization|api[_ -]?key)\s*[:=]' })
            } elseif ($value.Value -is [string] -or $value.Value -is [bool] -or $value.Value -is [int] -or $value.Value -is [long]) {
                if ([string]$value.Value -notmatch '(?i)gh[pousr]_|github_pat_|(?:token|password|secret|authorization|api[_ -]?key)\s*[:=]|https?://[^/\s]+@') { $item[$name]=$value.Value }
            }
        }
        [pscustomobject]$item
    })
    [pscustomobject]@{version=1;items=$privateItems} | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $destination -Encoding utf8 -ErrorAction Stop
    Invoke-CatalogGit $resolved @('add','--','catalog.private.json') | Out-Null
    $changed = Invoke-CatalogGit $resolved @('diff','--cached','--name-only')
    if ($changed -and $changed -ne 'catalog.private.json') { throw 'Há outros arquivos preparados para commit; publicação interrompida.' }
    if ($changed) { Invoke-CatalogGit $resolved @('commit','-m','Update private game catalog','--','catalog.private.json') | Out-Null }
    Invoke-CatalogGit $resolved @('push','origin',('HEAD:refs/heads/' + $branch)) | Out-Null
    return [pscustomobject]@{ Published=$true; Count=$privateItems.Count }
}

function Get-PrivateLibraryFiles {
    param([Parameter(Mandatory)][string]$ImportPath)
    $mapping = [ordered]@{ ROMs = 'roms'; saves = 'saves'; covers = 'covers'; theme = 'theme'; playlists = 'playlists' }
    $files = @()
    foreach ($sourceName in $mapping.Keys) {
        $sourceRoot = Join-Path $ImportPath $sourceName
        if (-not (Test-Path -LiteralPath $sourceRoot -PathType Container)) { continue }
        foreach ($file in Get-ChildItem -LiteralPath $sourceRoot -Recurse -File -ErrorAction SilentlyContinue) {
            $relative = $file.FullName.Substring($sourceRoot.Length).TrimStart('\','/').Replace('\','/')
            $files += [pscustomobject]@{ Source=$file.FullName; Relative=(Join-Path $mapping[$sourceName] $relative).Replace('\','/'); Length=$file.Length }
        }
    }
    return @($files)
}

function Publish-PrivateLibrary {
    param([Parameter(Mandatory)][string]$RepoPath, [Parameter(Mandatory)][string]$ImportPath, [AllowEmptyCollection()][array]$Catalog = @())
    $files = Get-PrivateLibraryFiles -ImportPath $ImportPath
    $oversized = @($files | Where-Object { $_.Length -ge 100MB })
    if ($oversized.Count -gt 0) { throw 'A biblioteca contém arquivo(s) com 100 MB ou mais; use Git LFS no checkout privado antes de publicar.' }
    $catalogResult = Publish-PrivateCatalog -RepoPath $RepoPath -Catalog $Catalog
    $resolved = (Resolve-Path -LiteralPath $RepoPath -ErrorAction Stop).Path.TrimEnd('\','/')
    $copied = @(); $conflicts = @()
    foreach ($file in $files) {
        $destination = Join-Path $resolved $file.Relative
        if (Test-Path -LiteralPath $destination -PathType Leaf) {
            $sourceHash = (Get-FileHash -LiteralPath $file.Source -Algorithm SHA256).Hash
            $destinationHash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash
            if ($sourceHash -ne $destinationHash) { $conflicts += $file.Relative }
            continue
        }
        New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
        Copy-Item -LiteralPath $file.Source -Destination $destination -ErrorAction Stop
        $copied += $file.Relative
    }
    $stagedPaths = @('roms','saves','covers','theme','playlists') | Where-Object { Test-Path -LiteralPath (Join-Path $resolved $_) }
    if ($stagedPaths.Count -gt 0) { Invoke-CatalogGit $resolved (@('add','--') + $stagedPaths) | Out-Null }
    $changed = @(Invoke-CatalogGit $resolved @('diff','--cached','--name-only') -split "`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    foreach ($path in $changed) { if ($path -ne 'catalog.private.json' -and $path -notmatch '^(?:roms|saves|covers|theme|playlists)/') { throw 'A publicação privada encontrou arquivo fora da lista permitida.' } }
    if ($changed.Count -gt 0) {
        Invoke-CatalogGit $resolved @('commit','-m','Update private game library') | Out-Null
        $branch = Invoke-CatalogGit $resolved @('symbolic-ref','--short','HEAD')
        Invoke-CatalogGit $resolved @('push','origin',('HEAD:refs/heads/' + $branch)) | Out-Null
    }
    $report = [pscustomobject]@{ copied=@($copied); conflicts=@($conflicts); files=@($files | ForEach-Object Relative) }
    $report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $ImportPath 'private-sync-report.json') -Encoding utf8
    return [pscustomobject]@{ Published=$true; Count=$Catalog.Count; Copied=$copied.Count; Conflicts=$conflicts.Count; ReportPath=(Join-Path $ImportPath 'private-sync-report.json') }
}

function Show-GitHubCatalogDialog {
    param([array]$Catalog = @(), [Windows.Forms.IWin32Window]$Owner)
    $savedReview = Get-ManagerCatalogReview
    if ($savedReview.Count -gt 0) { $Catalog = $savedReview }
    $dialog = [Windows.Forms.Form]::new()
    $dialog.Text = 'Catálogo GitHub — revisão e publicação'
    $dialog.Size = [Drawing.Size]::new(1060,620)
    $dialog.StartPosition = 'CenterParent'
    $hint = [Windows.Forms.Label]::new(); $hint.Dock='Top'; $hint.Height=62
    $hint.Text = 'Revise os itens e marque Publicar + Redistribuível apenas para homebrew, demo ou public-domain com licença preenchida. Capas: assets/nome.png. Links: página pública HTTPS confirmada. A exportação grava somente JSON; copie as capas autorizadas para assets.'
    $dialog.Controls.Add($hint)
    $table = [Data.DataTable]::new()
    foreach ($name in @('publicSelected','redistributable','downloadPublicConfirmed')) { [void]$table.Columns.Add($name,[bool]) }
    foreach ($name in @('label','platform','category','license','image','publicDownload')) { [void]$table.Columns.Add($name,[string]) }
    foreach ($game in $Catalog) {
        $row=$table.NewRow()
        foreach ($column in $table.Columns) {
            $value=$game.PSObject.Properties[$column.ColumnName]
            if ($column.DataType -eq [bool]) { $row[$column.ColumnName]=($null -ne $value -and $value.Value -is [bool] -and $value.Value -eq $true) }
            elseif ($value) { $row[$column.ColumnName]=[string]$value.Value }
        }
        [void]$table.Rows.Add($row)
    }
    $grid=[Windows.Forms.DataGridView]::new(); $grid.Dock='Fill'; $grid.DataSource=$table; $grid.AllowUserToAddRows=$false; $grid.AllowUserToDeleteRows=$false; $grid.AutoSizeColumnsMode='DisplayedCells'; $grid.AllowUserToOrderColumns=$false
    $dialog.Controls.Add($grid); $grid.BringToFront()
    $dialog.Add_Shown({
        $headers=@{ publicSelected='Publicar'; redistributable='Redistribuição autorizada'; downloadPublicConfirmed='Link público confirmado'; label='Jogo'; platform='Plataforma'; category='Categoria'; license='Licença'; image='Capa no site'; publicDownload='Página de download' }
        foreach ($column in $grid.Columns) { $column.SortMode='NotSortable'; $column.HeaderText=$headers[$column.Name] }
    })
    $footer=[Windows.Forms.FlowLayoutPanel]::new(); $footer.Dock='Bottom'; $footer.Height=112; $dialog.Controls.Add($footer)
    $tokenLabel=[Windows.Forms.Label]::new(); $tokenLabel.Text='Credencial GitHub'; $tokenLabel.AutoSize=$true; $footer.Controls.Add($tokenLabel)
    $tokenInput=[Windows.Forms.TextBox]::new(); $tokenInput.UseSystemPasswordChar=$true; $tokenInput.Width=220; $footer.Controls.Add($tokenInput)
    $saveToken=[Windows.Forms.Button]::new(); $saveToken.Text='Salvar credencial'; $saveToken.Width=140; $footer.Controls.Add($saveToken)
    $exportButton=[Windows.Forms.Button]::new(); $exportButton.Text='Exportar público'; $exportButton.Width=140; $footer.Controls.Add($exportButton)
    $publishButton=[Windows.Forms.Button]::new(); $publishButton.Text='Publicar privado'; $publishButton.Width=140; $footer.Controls.Add($publishButton)
    $message=[Windows.Forms.Label]::new(); $message.Width=990; $message.Height=50; $footer.Controls.Add($message)
    $collect = {
        $grid.EndEdit() | Out-Null
        $updated=@()
        for ($i=0; $i -lt $Catalog.Count; $i++) {
            $copy=[ordered]@{}
            foreach ($property in $Catalog[$i].PSObject.Properties) { $copy[$property.Name]=$property.Value }
            foreach ($column in $table.Columns) { $value=$table.Rows[$i][$column.ColumnName]; $copy[$column.ColumnName]=if ($value -is [DBNull]) { '' } else { $value } }
            $updated += [pscustomobject]$copy
        }
        return $updated
    }
    $saveToken.Add_Click({
        try { $secure=ConvertTo-SecureString $tokenInput.Text -AsPlainText -Force; Save-GitHubCredential $secure | Out-Null; $message.Text='Credencial protegida neste usuário do Windows. Configure também o acesso Git ao checkout.' }
        catch { $message.Text='Não foi possível salvar a credencial protegida.' }
        finally { $tokenInput.Clear(); if ($secure) { $secure.Dispose() } }
    })
    $exportButton.Add_Click({
        try {
            $save=[Windows.Forms.SaveFileDialog]::new(); $save.FileName='catalog.public.json'; $save.Filter='Catálogo público|catalog.public.json'
            if ($save.ShowDialog($dialog) -ne [Windows.Forms.DialogResult]::OK) { return }
            $result=Export-PublicCatalog -Catalog @(& $collect) -DestinationPath $save.FileName
            $message.Text="Catálogo público exportado: $($result.items.Count) itens. Revise o JSON e as capas antes de publicar o site."
        } catch { $message.Text='Falha ao exportar. Verifique o destino e os campos revisados.' }
    })
    $publishButton.Add_Click({
        try {
            $folder=[Windows.Forms.FolderBrowserDialog]::new(); $folder.Description='Escolha a raiz do checkout privado externo ao projeto público'
            if ($folder.ShowDialog($dialog) -ne [Windows.Forms.DialogResult]::OK) { return }
            if ([Windows.Forms.MessageBox]::Show($dialog,'Publicar catalog.private.json no origin do checkout selecionado? O Git criará um commit e enviará a branch atual.','Publicar catálogo privado','YesNo','Question') -ne [Windows.Forms.DialogResult]::Yes) { return }
            $result=Publish-PrivateCatalog -RepoPath $folder.SelectedPath -Catalog @(& $collect)
            $message.Text="Catálogo privado publicado: $($result.Count) itens."
        } catch { $message.Text='Não foi possível publicar. Confirme que o checkout está limpo, origin é privado e a credencial tem acesso. Uma falha de push pode deixar um commit local pendente.' }
    })
    try { [void]$dialog.ShowDialog($Owner) } finally { Save-ManagerCatalogReview -Catalog @(& $collect) | Out-Null; $tokenInput.Clear(); $dialog.Dispose() }
}
