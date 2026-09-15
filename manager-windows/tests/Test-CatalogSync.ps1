$root = Split-Path $PSScriptRoot -Parent
$app = Join-Path $root 'FireRetroManager.ps1'
if (-not (Test-Path $app)) { throw 'FireRetroManager.ps1 is missing' }

$source = Get-Content $app -Raw
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

Write-Output 'PASS: Catalog inventory, merge, simulated ADB sync, and non-destructive contract'
exit 0
