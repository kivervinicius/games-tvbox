$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$app = Join-Path $root 'FireRetroManager.ps1'
if (-not (Test-Path -LiteralPath $app)) { throw 'FireRetroManager.ps1 is missing' }

function Assert([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

# Load declarations only: the Manager UI is never created in this isolated test process.
$source = Get-Content -LiteralPath $app -Raw
$formStart = $source.IndexOf('$form = [Windows.Forms.Form]::new()')
if ($formStart -lt 0) { throw 'Could not isolate Manager functions from the UI.' }
$functionSource = $source.Substring(0, $formStart)
$functionSource = $functionSource.Replace('$script:ManagerRoot = Split-Path $PSScriptRoot -Parent', "`$script:ManagerRoot = '$($root.Replace("'", "''"))'")
$tempRoot = Join-Path ([IO.Path]::GetTempPath()) ('FireRetroManager-DeviceImportTest-' + [Guid]::NewGuid().ToString('N'))
$priorLocalAppData = $env:LOCALAPPDATA
try {
    $env:LOCALAPPDATA = $tempRoot
    Invoke-Expression $functionSource

    $script:AdbCalls = [System.Collections.Generic.List[object]]::new()
    function Invoke-Adb {
        param([string[]]$Arguments)
        [void]$script:AdbCalls.Add(@($Arguments))
        if ($Arguments -contains 'pull') {
            $destination = $Arguments[-1]
            New-Item -ItemType Directory -Path $destination -Force | Out-Null
            Set-Content -LiteralPath (Join-Path $destination 'imported.txt') -Value ($Arguments[-2]) -Encoding utf8
        }
        return [pscustomobject]@{ ExitCode = 0; Output = 'ok'; Error = '' }
    }

    $result = Import-FireStickLibrary -Serial 'fake:5555'
    Assert ($result.ExitCode -eq 0) 'Import must return success when ADB pulls succeed.'
    Assert (Test-Path -LiteralPath $result.Destination) 'Import must create a local destination.'
    Assert ($result.Destination -like (Join-Path $tempRoot 'FireRetroManager\imports\*')) 'Default imports must be outside the public checkout under LOCALAPPDATA.'
    Assert (Test-Path -LiteralPath (Join-Path $result.Destination 'import-report.json')) 'Import must record a report in the private destination.'
    $pulls = @($script:AdbCalls | Where-Object { $_ -contains 'pull' })
    Assert ($pulls.Count -ge 5) 'Import must pull ROMs, saves, catalog, theme, covers and playlists.'
    foreach ($call in $pulls) {
        Assert (($call -join ' ') -match '^-s fake:5555 pull ') 'Every import transfer must target the selected device with adb pull.'
    }
    $allPullText = (($pulls | ForEach-Object { $_ -join ' ' }) -join "`n")
    Assert ($allPullText -match [regex]::Escape('/sdcard/roms')) 'ROMs must be imported by a read-only adb pull.'
    Assert ($allPullText -match [regex]::Escape('/sdcard/Android/data/com.retroarch.ra32/files/saves')) 'Saves must be imported by a read-only adb pull.'
    Assert (($script:AdbCalls | Where-Object { ($_) -join ' ' -match '\b(?:rm|mv|move)\b' }).Count -eq 0) 'Import must never delete or move remote files.'

    $blocked = $false
    try { Import-FireStickLibrary -Serial 'fake:5555' -Destination $root | Out-Null } catch { $blocked = $true }
    Assert $blocked 'Import must reject a destination inside the public checkout.'

    $privateImport = Join-Path $tempRoot 'private-import'
    New-Item -ItemType Directory -Path (Join-Path $privateImport 'catalog') -Force | Out-Null
    [pscustomobject]@{ version=1; items=@([pscustomobject]@{ label='Imported game'; path='/sdcard/roms/gb/Imported.gb'; platform='GBA'; core_path='core' }) } |
        ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $privateImport 'catalog/games.json') -Encoding utf8
    $script:PublishedCatalog = $null
    function Import-FireStickLibrary { param([string]$Serial) return [pscustomobject]@{ ExitCode=0; Destination=$privateImport; Error='' } }
    function Get-RemoteGameInventory { param([string]$Serial) return [pscustomobject]@{ ExitCode=0; Output=@([pscustomobject]@{ label='Remote game'; path='/sdcard/roms/nes/Remote.nes'; platform='NES'; core_path='core' }); Error='' } }
    function Add-CatalogMetadata { param([array]$Catalog) return $Catalog }
    function Publish-PrivateCatalog { param([string]$RepoPath, [array]$Catalog) $script:PublishedCatalog=@($Catalog); return [pscustomobject]@{ Published=$true; Count=$Catalog.Count } }
    $privateSync = Sync-FireStickToPrivateRepo -Serial 'fake:5555' -RepoPath (Join-Path $tempRoot 'private-checkout')
    Assert $privateSync.Published.Published 'Private sync must delegate publication to the private publisher.'
    Assert ($privateSync.CatalogCount -eq 2) 'Private sync must merge imported and remote catalog entries.'
    Assert ($script:PublishedCatalog.Count -eq 2) 'Private sync must publish the sanitized merged catalog only through Publish-PrivateCatalog.'
} finally {
    $env:LOCALAPPDATA = $priorLocalAppData
    if (Test-Path -LiteralPath $tempRoot) { Remove-Item -LiteralPath $tempRoot -Recurse -Force }
}

foreach ($required in @('Import-FireStickLibrary','Sync-FireStickToPrivateRepo','adb pull','ROMs','saves','Publish-PrivateCatalog','MessageBox')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "Device import contract missing: $required" }
}
if ($source -match 'adb shell rm|adb shell move|Remove-Item.*rom') { throw 'Device import must not delete ROMs or saves' }
Write-Output 'PASS: Fire Stick library import uses read-only pulls and rejects public destinations'
