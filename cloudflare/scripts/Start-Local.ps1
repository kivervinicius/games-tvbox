[CmdletBinding()]
param(
    [string]$Wrangler = 'wrangler',
    [int]$Port = 8787
)

$ErrorActionPreference = 'Stop'
if ($Port -lt 1024 -or $Port -gt 65535) { throw 'Porta deve estar entre 1024 e 65535.' }
if ($Wrangler -eq 'wrangler') {
    $nodeRoot = Join-Path $env:TEMP 'node-v22.19.0-win-x64'
    $npx = Join-Path $nodeRoot 'npx.cmd'
    if (Test-Path $npx) { $env:PATH = $nodeRoot + ';' + (Join-Path $nodeRoot 'node_modules\npm\bin') + ';' + $env:PATH; $Wrangler = $npx; $arguments = @('--yes','wrangler@4','dev','--local','--port',$Port,'--ip','127.0.0.1','--config',(Join-Path $PSScriptRoot '..\wrangler.toml')) }
    elseif (Get-Command npx -ErrorAction SilentlyContinue) { $Wrangler = (Get-Command npx).Source; $arguments = @('--yes','wrangler@4','dev','--local','--port',$Port,'--ip','127.0.0.1','--config',(Join-Path $PSScriptRoot '..\wrangler.toml')) }
    elseif (Get-Command wrangler -ErrorAction SilentlyContinue) { $Wrangler = (Get-Command wrangler).Source; $arguments = @('dev','--local','--port',$Port,'--ip','127.0.0.1','--config',(Join-Path $PSScriptRoot '..\wrangler.toml')) }
    else { throw 'Node.js e npx são necessários. Instale o Node.js LTS e abra um novo PowerShell.' }
} else { $arguments = @('dev','--local','--port',$Port,'--ip','127.0.0.1','--config',(Join-Path $PSScriptRoot '..\wrangler.toml')) }
Write-Output "Painel local: http://127.0.0.1:$Port/admin/"
Write-Output 'Use Ctrl+C para encerrar. Os dados locais ficam em cloudflare/.wrangler e não são publicados.'
Push-Location (Join-Path $PSScriptRoot '..')
try { & $Wrangler @arguments; $code = $LASTEXITCODE } finally { Pop-Location }
exit $code
