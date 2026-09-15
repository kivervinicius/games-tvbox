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

Write-Output 'PASS: Catalog inventory, merge, and non-destructive sync source contract'
exit 0
