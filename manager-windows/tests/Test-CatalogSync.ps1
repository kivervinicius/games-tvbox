$root = Split-Path $PSScriptRoot -Parent
$app = Join-Path $root 'FireRetroManager.ps1'
$metadataProvider = Join-Path $root 'MetadataProviders.ps1'
if (-not (Test-Path $app)) { throw 'FireRetroManager.ps1 is missing' }
if (-not (Test-Path $metadataProvider)) { throw 'MetadataProviders.ps1 is missing' }

$metadataSource = Get-Content $metadataProvider -Raw
foreach ($required in @('Get-GameMetadata','Save-GameMetadataCache','Download-CoverToCache','LOCALAPPDATA','ProviderConfig')) {
    if ($metadataSource -notmatch [regex]::Escape($required)) { throw "Metadata contract missing: $required" }
}

$metadataTestCache = Join-Path ([IO.Path]::GetTempPath()) ('FireRetroManager-MetadataTest-' + [Guid]::NewGuid().ToString('N'))
$originalLocalAppData = $env:LOCALAPPDATA
try {
    $env:LOCALAPPDATA = $metadataTestCache
    . $metadataProvider
    $offlineGame = [pscustomobject]@{ label = 'Sonic The Hedgehog'; path = '/sdcard/roms/megadrive/Sonic The Hedgehog.bin'; image = 'existing-cover'; tags = @('favorito') }
    $fallback = Get-GameMetadata -Game $offlineGame -ProviderConfig $null
    if ($fallback.label -ne 'Sonic The Hedgehog') { throw 'Offline metadata must preserve the existing game label.' }
    if ($fallback.image -ne 'existing-cover') { throw 'Offline metadata must preserve the existing cover.' }
    if (@($fallback.tags).Count -ne 1) { throw 'Offline metadata must preserve existing tags.' }

    $cachePath = Save-GameMetadataCache -Items @($fallback)
    if (-not (Test-Path -LiteralPath $cachePath)) { throw 'Metadata cache must be written outside the project.' }
    if ($cachePath -notlike "$metadataTestCache*") { throw 'Metadata cache must use LOCALAPPDATA rather than the repository.' }
    $cached = Get-Content -LiteralPath $cachePath -Raw | ConvertFrom-Json
    if (@($cached.items).Count -ne 1) { throw 'Metadata cache must retain normalized entries.' }

    $invalidCover = Download-CoverToCache -Url 'file:///not-a-network-cover.png' -GameId 'sonic'
    if ($null -ne $invalidCover) { throw 'Cover downloads must reject non-HTTP URLs.' }

    Set-StrictMode -Version Latest
    $fallbackMetadata = Get-GameMetadataFallback -Game ([pscustomobject]@{ label='Fallback'; path='/sdcard/roms/nes/Fallback.nes' })
    foreach ($tags in @(@(), @('ação'), @('ação','arcade'))) {
        $normalized = ConvertTo-NormalizedGameMetadata -Response ([pscustomobject]@{ title='HTTP title'; tags=$tags }) -Fallback $fallbackMetadata
        if (@($normalized.tags).Count -ne @($tags).Count) { throw 'Metadata normalization must retain zero, one and many tags under StrictMode.' }
    }
    $sparse = ConvertTo-NormalizedGameMetadata -Response ([pscustomobject]@{ title='Sparse response' }) -Fallback $fallbackMetadata
    if ($sparse.label -ne 'Sparse response' -or $null -eq $sparse.tags) { throw 'Sparse metadata responses must use safe fallback values under StrictMode.' }
    $httpResponse = '{ "title":"Online title", "tags":["demo"] }' | ConvertFrom-Json
    $online = ConvertTo-NormalizedGameMetadata -Response $httpResponse -Fallback $fallbackMetadata
    if ($online.label -ne 'Online title' -or @($online.tags).Count -ne 1) { throw 'A valid HTTP metadata response body must not silently fall back.' }
} finally {
    $env:LOCALAPPDATA = $originalLocalAppData
    if (Test-Path -LiteralPath $metadataTestCache) { Remove-Item -LiteralPath $metadataTestCache -Recurse -Force }
}

$source = Get-Content $app -Raw
foreach ($required in @('MetadataProviders.ps1','Get-ConfiguredMetadataProvider','Add-CatalogMetadata','Get-GameMetadata','Save-GameMetadataCache','Download-CoverToCache')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "Manager metadata integration missing: $required" }
}
if ($source -match 'catalog\.public\.json.*(?:apiKey|ApiKey|Authorization)|(?:apiKey|ApiKey|Authorization).*catalog\.public\.json') {
    throw 'Metadata credentials must never be written to the public catalog.'
}
foreach ($required in @('Get-RemoteGameInventory','Get-LocalGameCatalog','Convert-LocalCatalogGame','Merge-GameCatalog','Sync-CatalogToFireStick','Get-RemoteThemeInventory','shell','find','adb pull','pull','adb push','push','mkdir','files/catalog/games.json','core_path','Mega Drive','PlayStation','.gb','.gbc')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "Catalog sync contract missing: $required" }
}
if ($source -notmatch '(?s)\$localCatalog.*\$incomingCatalog.*Merge-GameCatalog.*\$incomingCatalog') {
    throw 'Catalog sync must merge local and Fire Stick inventory before sending.'
}
if ($source -notmatch [regex]::Escape("'mkdir', '-p', '/sdcard/Android/data/com.kiver.fireretro/files/catalog'")) {
    throw 'Catalog sync must create the external catalog directory before the first push.'
}
if ($source -match "'shell', 'rm'|\bRemove-Item\b.*RemoteCatalog|\bmv\b.*RemoteCatalog") {
    throw 'Catalog sync must not delete or move Fire Stick catalog, ROM, save, or configuration files.'
}

# Load declarations only: the Manager UI is never created in this isolated test process.
$formStart = $source.IndexOf('$form = [Windows.Forms.Form]::new()')
if ($formStart -lt 0) { throw 'Could not isolate Manager functions from the UI.' }
$env:LOCALAPPDATA = [IO.Path]::GetTempPath()
$functionSource = $source.Substring(0, $formStart)
$functionSource = $functionSource.Replace('$script:ManagerRoot = Split-Path $PSScriptRoot -Parent', "`$script:ManagerRoot = '$($root.Replace("'", "''"))'")
Invoke-Expression $functionSource

function Assert-Equal {
    param($Actual, $Expected, [string]$Message)
    if ($Actual -ne $Expected) { throw "$Message Expected '$Expected'; got '$Actual'." }
}

$script:AdbCalls = [System.Collections.Generic.List[object]]::new()
function Invoke-Adb {
    param([string[]]$Arguments)
    [void]$script:AdbCalls.Add(@($Arguments))
    $joined = $Arguments -join ' '
    if ($joined -match ' find /sdcard/roms ') {
        return [pscustomobject]@{ ExitCode = 0; Output = "/sdcard/roms/megadrive/Sonic.bin`n/sdcard/roms/gb/Tetris.gb`n/sdcard/roms/readme.txt"; Error = '' }
    }
    return [pscustomobject]@{ ExitCode = 0; Output = 'ok'; Error = '' }
}

$remote = Get-RemoteGameInventory -Serial 'fake:5555'
Assert-Equal $remote.Output.Count 2 'Remote inventory must keep only known ROM extensions.'
$megaBin = @($remote.Output | Where-Object { $_.path -like '*Sonic.bin' })[0]
Assert-Equal $megaBin.platform 'Mega Drive' 'Mega Drive .bin must not use the PlayStation platform.'
if ($megaBin.core_path -notmatch 'genesis_plus_gx') { throw 'Mega Drive .bin must use the Genesis Plus GX core.' }

$psBin = New-RemoteCatalogGame -Path '/sdcard/roms/ps/Final Fantasy VII.bin'
Assert-Equal $psBin.platform 'PlayStation' 'PlayStation .bin must retain the PlayStation platform.'
if ($psBin.core_path -notmatch 'pcsx_rearmed') { throw 'PlayStation .bin must use the PCSX ReARmed core.' }
foreach ($path in @('/sdcard/roms/gb/Tetris.gb', '/sdcard/roms/gbc/Zelda.gbc')) {
    $game = New-RemoteCatalogGame -Path $path
    Assert-Equal $game.platform 'GBA' 'Game Boy files must use the configured handheld platform.'
    if ($game.core_path -notmatch 'mgba') { throw 'Game Boy files must use the configured mGBA core.' }
}

$local = Convert-LocalCatalogGame -Game ([pscustomobject]@{ RelativePath = 'gba\Local Adventure.gba' })
Assert-Equal $local.path '/sdcard/roms/gba/Local Adventure.gba' 'Local games must be converted to Fire Stick catalog paths.'
foreach ($field in @('label','platform','path','core_path')) {
    if ($null -eq $local.PSObject.Properties[$field]) { throw "Converted local game is missing catalog field: $field" }
}

$manual = [pscustomobject]@{ label = 'Título editado'; path = '/sdcard/roms/gba/Local Adventure.gba'; platform = 'GBA'; core_path = 'manual-core'; description = 'Preservar edição manual'; tags = @('favorito') }
$merged = Merge-GameCatalog -Existing @($manual) -Incoming @($local, $megaBin)
Assert-Equal $merged.Count 2 'Merge must deduplicate local and existing catalog paths.'
$preserved = @($merged | Where-Object { $_.path -eq $manual.path })[0]
Assert-Equal $preserved.label 'Título editado' 'Merge must preserve the manually edited label.'
Assert-Equal $preserved.description 'Preservar edição manual' 'Merge must preserve manual catalog fields.'

$managerMetadataCache = Join-Path ([IO.Path]::GetTempPath()) ('FireRetroManager-IntegrationTest-' + [Guid]::NewGuid().ToString('N'))
$managerOriginalLocalAppData = $env:LOCALAPPDATA
try {
    $env:LOCALAPPDATA = $managerMetadataCache
    $script:MetadataProviderConfigPath = Join-Path $managerMetadataCache 'FireRetroManager\metadata-provider.json'
    $enriched = Add-CatalogMetadata -Catalog @($manual)
    Assert-Equal $enriched.Count 1 'Metadata enrichment must retain every catalog item offline.'
    Assert-Equal $enriched[0].label 'Título editado' 'Metadata enrichment must preserve manual labels.'
    Assert-Equal $enriched[0].description 'Preservar edição manual' 'Metadata enrichment must preserve manual descriptions.'
    if (-not (Test-Path -LiteralPath (Join-Path $managerMetadataCache 'FireRetroManager\metadata.json'))) { throw 'Manager metadata enrichment must save an external cache.' }
} finally {
    $env:LOCALAPPDATA = $managerOriginalLocalAppData
    if (Test-Path -LiteralPath $managerMetadataCache) { Remove-Item -LiteralPath $managerMetadataCache -Recurse -Force }
}

$script:CacheRoot = Join-Path ([IO.Path]::GetTempPath()) ('FireRetroManager-Test-' + [Guid]::NewGuid().ToString('N'))
try {
    $script:AdbCalls.Clear()
    $sync = Sync-CatalogToFireStick -Serial 'fake:5555' -Catalog $merged
    Assert-Equal $sync.ExitCode 0 'Catalog sync must return the simulated ADB success result.'
    Assert-Equal $script:AdbCalls.Count 2 'Catalog sync must make only mkdir and push ADB calls.'
    Assert-Equal (($script:AdbCalls[0]) -join ' ') '-s fake:5555 shell mkdir -p /sdcard/Android/data/com.kiver.fireretro/files/catalog' 'Catalog sync must create its destination directory first.'
    $pushCall = @($script:AdbCalls[1])
    Assert-Equal $pushCall[0] '-s' 'Catalog sync must target the selected device.'
    Assert-Equal $pushCall[2] 'push' 'Catalog sync must push the generated catalog.'
    Assert-Equal $pushCall[4] '/sdcard/Android/data/com.kiver.fireretro/files/catalog/games.json' 'Catalog sync must push only games.json after mkdir.'
    if ([IO.Path]::GetFileName($pushCall[3]) -ne 'games.json') { throw 'Catalog sync must use the generated games.json file.' }
} finally {
    if (Test-Path -LiteralPath $script:CacheRoot) { Remove-Item -LiteralPath $script:CacheRoot -Recurse -Force }
}

$localSyncRoot = Join-Path ([IO.Path]::GetTempPath()) ('FireRetroManager-LocalSyncTest-' + [Guid]::NewGuid().ToString('N'))
try {
    New-Item -ItemType Directory -Path (Join-Path $localSyncRoot 'nes') -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $localSyncRoot 'nes/New Game.nes') -Value 'new game' -Encoding utf8
    $script:Settings.RomFolder = $localSyncRoot
    $script:CacheRoot = Join-Path $localSyncRoot 'cache'
    New-Item -ItemType Directory -Path (Join-Path $script:CacheRoot 'covers') -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $script:CacheRoot 'covers/new-cover.png') -Value 'cover' -Encoding utf8
    $script:AdbCalls.Clear()
    function Invoke-Adb {
        param([string[]]$Arguments)
        [void]$script:AdbCalls.Add(@($Arguments))
        if ($Arguments -contains 'test') { return [pscustomobject]@{ ExitCode=1; Output=''; Error='missing' } }
        return [pscustomobject]@{ ExitCode=0; Output='ok'; Error='' }
    }
    $sync = Sync-CatalogToFireStick -Serial 'fake:5555' -Catalog @()
    if ($sync.ExitCode -ne 0) { throw 'Dynamic local catalog sync must succeed with simulated ADB.' }
    $calls = (($script:AdbCalls | ForEach-Object { $_ -join ' ' }) -join "`n")
    if ($calls -notmatch [regex]::Escape('test -e /sdcard/roms/nes/New Game.nes')) { throw 'Sync must check remote ROM existence before copying.' }
    if ($calls -notmatch [regex]::Escape('push') -or $calls -notmatch [regex]::Escape('/sdcard/roms/nes/New Game.nes')) { throw 'Sync must copy newly scanned local ROMs to the Fire Stick.' }
    if ($calls -notmatch [regex]::Escape($script:RemoteCoverRoot)) { throw 'Sync must upload downloaded local covers to the external cover directory.' }
    $jsonCall = @($script:AdbCalls | Where-Object { ($_ -join ' ') -match 'catalog/games\.json' -and $_ -contains 'push' })[-1]
    $json = Get-Content -LiteralPath $jsonCall[3] -Raw
    if ($json -match [regex]::Escape($localSyncRoot) -or $json -match [regex]::Escape($script:CacheRoot)) { throw 'Synchronized JSON must not expose Windows or cache paths.' }
} finally {
    if (Test-Path -LiteralPath $localSyncRoot) { Remove-Item -LiteralPath $localSyncRoot -Recurse -Force }
}

Write-Output 'PASS: Catalog inventory, merge, simulated ADB sync, and non-destructive contract'
exit 0
