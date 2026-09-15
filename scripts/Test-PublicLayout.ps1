$root = Split-Path $PSScriptRoot -Parent
$required = @(
    'launcher-android/app/src/main/AndroidManifest.xml',
    'launcher-android/app/src/main/java/com/kiver/fireretro/MainActivity.java',
    'manager-windows/FireRetroManager.ps1',
    'examples/catalog.example.json',
    'docs/manual/README.md'
)
foreach ($relative in $required) {
    if (-not (Test-Path -LiteralPath (Join-Path $root $relative))) { throw "Missing public file: $relative" }
}
$forbiddenNames = @('sources','device-backups','backups','ps1-chd','crash-extracted','bomberman.chd','ctr.chd','msx.chd','keystore','FireRetroLauncher-debug.apk')
$files = Get-ChildItem -LiteralPath $root -Recurse -Force -File
foreach ($file in $files) {
    $relative = $file.FullName.Substring($root.Length + 1)
    foreach ($name in $forbiddenNames) {
        if ($relative -match [regex]::Escape($name)) { throw "Forbidden private or binary content: $relative" }
    }
    if ($relative -match 'C:\\Users|192\.168\.15|kiver\.teixeira') { throw "Personal path or address found: $relative" }
}
Write-Output 'PASS: public layout'
