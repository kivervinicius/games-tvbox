$root = Split-Path $PSScriptRoot -Parent
$sourcePath = Join-Path $root 'app\src\main\java\com\kiver\fireretro\CatalogStore.java'
if (-not (Test-Path $sourcePath)) { throw 'CatalogStore.java is missing' }
$source = Get-Content $sourcePath -Raw
foreach ($required in @('files/catalog/games.json','assets/games.json','publicDownload','description','tags')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "CatalogStore contract missing: $required" }
}
if ($source.IndexOf('files/catalog/games.json') -gt $source.IndexOf('assets/games.json')) {
    throw 'CatalogStore must try external catalog before embedded assets'
}
Write-Output 'PASS: CatalogStore external-first fallback contract'
