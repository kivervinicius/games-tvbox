[CmdletBinding()]
param(
    [string]$Adb = 'adb',
    [string]$ApkSigner = 'apksigner',
    [string]$Serial = ''
)

$ErrorActionPreference = 'Stop'
$adbArgs = @()
if (-not [string]::IsNullOrWhiteSpace($Serial)) { $adbArgs += @('-s', $Serial) }
$remote = (& $Adb @adbArgs shell pm path com.kiver.fireretro | Select-Object -First 1).ToString().Trim()
if (-not $remote.StartsWith('package:')) { throw 'com.kiver.fireretro não está instalado no aparelho selecionado.' }
$remote = $remote.Substring(8)
$tempApk = Join-Path ([IO.Path]::GetTempPath()) ('fireretro-signer-' + [guid]::NewGuid().ToString('N') + '.apk')
try {
    & $Adb @adbArgs pull $remote $tempApk | Out-Null
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $tempApk)) { throw 'Não foi possível copiar a APK instalada.' }
    & $ApkSigner verify --print-certs $tempApk
    if ($LASTEXITCODE -ne 0) { throw 'Não foi possível ler o certificado da APK.' }
} finally {
    if (Test-Path $tempApk) { Remove-Item -LiteralPath $tempApk -Force -ErrorAction SilentlyContinue }
}
