$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
& (Join-Path $PSScriptRoot 'Test-PublicLayout.ps1')
& (Join-Path $root 'launcher-android\tests\project.tests.ps1')
& (Join-Path $root 'manager-windows\tests\Test-Manager.ps1')
& (Join-Path $PSScriptRoot 'Test-Documentation.ps1')
$readme = Get-Content -LiteralPath (Join-Path $root 'README.md') -Raw
foreach ($link in @('docs/manual/README.md','examples/README.md','CONTRIBUTING.md')) {
    if ($readme -notmatch [regex]::Escape($link)) { throw "README link missing: $link" }
}
$forbiddenExtensions = @('.chd','.cue','.bin','.iso','.7z','.zip','.apk','.keystore','.idsig')
foreach ($file in Get-ChildItem -LiteralPath $root -Recurse -Force -File) {
    if ($forbiddenExtensions -contains $file.Extension.ToLowerInvariant()) { throw "Forbidden release file: $($file.FullName)" }
}
Write-Output 'PASS: public release verification'
