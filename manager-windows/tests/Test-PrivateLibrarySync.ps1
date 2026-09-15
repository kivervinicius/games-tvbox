$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$bridge = Join-Path $root 'GitHubCatalog.ps1'
$manager = Join-Path $root 'FireRetroManager.ps1'
if (-not (Test-Path $bridge) -or -not (Test-Path $manager)) { throw 'Private library bridge is missing.' }
$source = Get-Content $bridge -Raw
foreach ($required in @('Publish-PrivateLibrary','Get-PrivateLibraryFiles','ROMs','saves','covers','theme','playlists','100 MB','catalog.private.json')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "Private library contract missing: $required" }
}
if ($source -match 'ImportPath.*import-report|Copy-Item.*import-report') { throw 'Import reports must never be published with the private library.' }
$managerSource = Get-Content $manager -Raw
if ($managerSource -notmatch 'Publish-PrivateLibrary') { throw 'Fire Stick private sync must publish the allowlisted library.' }
$temp = Join-Path ([IO.Path]::GetTempPath()) ('private-library-test-' + [Guid]::NewGuid().ToString('N'))
try {
    New-Item -ItemType Directory -Path (Join-Path $temp 'ROMs\nes'),(Join-Path $temp 'saves'),(Join-Path $temp 'ignored') -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $temp 'ROMs\nes\Demo.nes') -Value 'rom' -Encoding utf8
    Set-Content -LiteralPath (Join-Path $temp 'saves\Demo.srm') -Value 'save' -Encoding utf8
    Set-Content -LiteralPath (Join-Path $temp 'ignored\import-report.json') -Value '{}' -Encoding utf8
    . $bridge
    $files = @(Get-PrivateLibraryFiles -ImportPath $temp)
    if ($files.Relative -notcontains 'roms/nes/Demo.nes') { throw 'ROMs must map into the private roms directory.' }
    if ($files.Relative -notcontains 'saves/Demo.srm') { throw 'Saves must map into the private saves directory.' }
    if ($files.Relative -contains 'ignored/import-report.json') { throw 'Only allowlisted import subfolders may be published.' }
} finally {
    if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Recurse -Force }
}
Write-Output 'PASS: private library allowlist, preservation and publication contract'
