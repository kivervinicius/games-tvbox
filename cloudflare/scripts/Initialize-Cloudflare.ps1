[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$Wrangler = 'wrangler',
    [string]$Config = (Join-Path $PSScriptRoot '..\wrangler.toml'),
    [switch]$CreateResources,
    [switch]$Deploy
)

$ErrorActionPreference = 'Stop'
$configText = Get-Content -LiteralPath $Config -Raw
$hasPlaceholders = $configText -match 'REPLACE_WITH'
if ($hasPlaceholders -and -not $CreateResources) { throw 'Preencha os IDs KV no wrangler.toml antes de publicar, ou use -CreateResources para criá-los.' }
$required = @('CATALOG_KV','DEVICE_KV','PAIRING_KV','RESERVATION_KV','PRIVATE_ASSETS')
foreach ($name in $required) { if ($configText -notmatch [regex]::Escape($name)) { throw "Binding ausente: $name" } }

if (-not $CreateResources -and -not $Deploy) {
    Write-Output 'PASS: configuração reconhecida; use -CreateResources para criar namespaces/bucket e -Deploy depois de revisar a conta.'
    exit 0
}

function Invoke-Wrangler([string[]]$Arguments) {
    if ($PSCmdlet.ShouldProcess("Cloudflare", "wrangler $($Arguments -join ' ' )")) {
        & $Wrangler @Arguments
        if ($LASTEXITCODE -ne 0) { throw "Wrangler falhou: $($Arguments -join ' ')" }
    }
}

if ($CreateResources) {
    Invoke-Wrangler @('r2','bucket','create','games-tvbox-private')
    foreach ($binding in @('CATALOG_KV','DEVICE_KV','PAIRING_KV','RESERVATION_KV')) {
        Invoke-Wrangler @('kv','namespace','create',$binding,'--config',$Config)
    }
    Write-Output 'Namespaces criados. Copie os IDs retornados para cloudflare/wrangler.toml e execute novamente sem -CreateResources para validar.'
    exit 0
}

if ($Deploy) { Invoke-Wrangler @('deploy','--config',$Config); Write-Output 'Worker publicado; valide /api/health, Access e o painel antes de parear TVs.' }
