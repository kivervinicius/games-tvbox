$root = Split-Path $PSScriptRoot -Parent
$app = Join-Path $root 'FireRetroManager.ps1'
if (-not (Test-Path $app)) { throw 'FireRetroManager.ps1 is missing' }

$source = Get-Content $app -Raw
foreach ($required in @('Get-RemoteGameInventory','Merge-GameCatalog','Sync-CatalogToFireStick','Get-RemoteThemeInventory','shell','find','files/catalog/games.json')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "Catalog sync contract missing: $required" }
}

Write-Output 'PASS: Catalog inventory and sync source contract'
exit 0
