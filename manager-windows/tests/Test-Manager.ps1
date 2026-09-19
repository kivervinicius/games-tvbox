$root = Split-Path $PSScriptRoot -Parent
$app = Join-Path $root 'FireRetroManager.ps1'
$readme = Join-Path $root 'README.md'
if (-not (Test-Path $app)) { throw 'FireRetroManager.ps1 is missing' }
if (-not (Test-Path $readme)) { throw 'Manager README is missing' }
$source = Get-Content $app -Raw
foreach ($required in @('Connect-AdbDevice','Get-DeviceSummary','Open-FireRetroRemote','Restart-FireRetroRemote','Set-RemoteControllerProfile','Select-RomFolder','Get-LocalGameCatalog','Get-RemoteGameInventory','Merge-GameCatalog','Sync-CatalogToFireStick','Get-RemoteThemeInventory','Get-ConfiguredMetadataProvider','Add-CatalogMetadata','MetadataProviders.ps1','Select-CoverImage','Save-CoverImage','Load-ManagerSettings','Save-ManagerSettings','Get-ThemeConfiguration','Save-ThemeConfiguration','Push-ThemeToFireStick','Temas e aparência','Criar tema do fundo','Publicar tema GitHub','Apps Android','Publicar apps','Salvar no Fire Stick','Sincronizar catálogo','Carrossel automático','Opacidade do fundo','Apply-ButtonStyle','FlatStyle','LOCALAPPDATA','settings.json','catalog.json','ComboBox','PictureBox','Enviar configurações do controle','Reiniciar FireRetro','Start-Process','adb devices','ro.product.cpu.abi','df -h /sdcard')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "Manager is missing $required" }
}
Write-Output 'PASS: Windows Manager source contract'
exit 0
