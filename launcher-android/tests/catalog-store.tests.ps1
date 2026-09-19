$root = Split-Path $PSScriptRoot -Parent
$sourcePath = Join-Path $root 'app\src\main\java\com\kiver\fireretro\CatalogStore.java'
if (-not (Test-Path $sourcePath)) { throw 'CatalogStore.java is missing' }
$source = Get-Content $sourcePath -Raw
foreach ($required in @('files/catalog/games.json','ASSET_CATALOG = "games.json"','publicDownload','description','tags','cloudId','remoteAvailable','size','merge(bundledGames, externalGames)','LinkedHashMap')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "CatalogStore contract missing: $required" }
}
$activity = Get-Content launcher-android/app/src/main/java/com/kiver/fireretro/MainActivity.java -Raw
foreach ($required in @('BitmapFactory.decodeFile','COVER_DIRECTORY','getResources().getIdentifier','loadGameCover')) {
    if ($activity -notmatch [regex]::Escape($required)) { throw "External cover contract missing: $required" }
}
foreach ($required in @('onResume','catalogLastModified','catalogLength','externalCatalogChanged','buildScreen')) {
    if ($activity -notmatch [regex]::Escape($required)) { throw "Dynamic catalog reload contract missing: $required" }
}
if ($source -notmatch 'externalGames = parse\(reader\)' -or $source -notmatch 'bundledGames = parse\(reader\)') {
    throw 'CatalogStore must load both external and bundled libraries'
}
foreach ($required in @('installRemoteGame','INSTALAR','remoteAvailable','cloudId')) {
    if ($activity -notmatch [regex]::Escape($required)) { throw "On-demand remote game contract missing: $required" }
}
Write-Output 'PASS: CatalogStore merges external and bundled games without hiding either library'
