[CmdletBinding()]
param(
    [string]$OutputRoot = (Join-Path (Split-Path $PSScriptRoot -Parent) 'build'),
    [string]$JavaRoot = '',
    [string]$SdkRoot = ''
)

$ErrorActionPreference = 'Stop'
$buildScript = Join-Path (Split-Path $PSScriptRoot -Parent) 'launcher-android\scripts\Build-FireRetro.ps1'
if (-not (Test-Path -LiteralPath $buildScript)) { throw "Launcher build script is missing: $buildScript" }
$forward = @('-OutputRoot', $OutputRoot)
if (-not [string]::IsNullOrWhiteSpace($JavaRoot)) { $forward += @('-JavaRoot', $JavaRoot) }
if (-not [string]::IsNullOrWhiteSpace($SdkRoot)) { $forward += @('-SdkRoot', $SdkRoot) }
& pwsh -NoProfile -File $buildScript @forward
if ($LASTEXITCODE -ne 0) { throw 'Launcher build failed' }
