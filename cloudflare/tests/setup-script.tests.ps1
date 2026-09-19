$script = Join-Path (Split-Path $PSScriptRoot -Parent) 'scripts\Initialize-Cloudflare.ps1'
if (-not (Test-Path $script)) { throw 'Cloudflare setup script is missing' }
$text = Get-Content $script -Raw
foreach ($required in @('REPLACE_WITH','games-tvbox-private','CATALOG_KV','DEVICE_KV','PAIRING_KV','RESERVATION_KV','ShouldProcess')) {
    if ($text -notmatch [regex]::Escape($required)) { throw "Setup script is missing $required" }
}
Write-Output 'PASS: Cloudflare setup script validates bindings and requires explicit actions'
