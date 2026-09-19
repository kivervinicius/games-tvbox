[CmdletBinding()]
param(
    [string]$OutputRoot = (Join-Path (Split-Path $PSScriptRoot -Parent) 'build'),
    [string]$JavaRoot = 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2024.1.4\jbr',
    [string]$SdkRoot = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif (Test-Path (Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) 'android-sdk')) { Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) 'android-sdk' } else { Join-Path $env:USERPROFILE '.codex\android-sdk-games-tvbox' }),
    [string]$KeystorePath = $env:FIRERETRO_KEYSTORE,
    [string]$KeystoreAlias = $env:FIRERETRO_KEY_ALIAS,
    [string]$KeystorePassword = $env:FIRERETRO_KEY_PASSWORD,
    [string]$CloudOrigin = $env:FIRERETRO_CLOUD_ORIGIN
)

$ErrorActionPreference = 'Stop'
$appRoot = Split-Path $PSScriptRoot -Parent
$buildTools = Join-Path $sdkRoot 'build-tools\35.0.0'
$androidJar = Join-Path $sdkRoot 'platforms\android-28\android.jar'
$release = Join-Path $OutputRoot ('release-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))

foreach ($required in @($androidJar, (Join-Path $buildTools 'aapt2.exe'), (Join-Path $buildTools 'd8.bat'), (Join-Path $buildTools 'zipalign.exe'), (Join-Path $buildTools 'apksigner.bat'), (Join-Path $JavaRoot 'bin\javac.exe'))) {
    if (-not (Test-Path $required)) { throw "Build dependency is missing: $required" }
}
if ([string]::IsNullOrWhiteSpace($KeystorePath) -or -not (Test-Path -LiteralPath $KeystorePath)) { throw 'Provide an external signing keystore with -KeystorePath or FIRERETRO_KEYSTORE.' }
if ([string]::IsNullOrWhiteSpace($KeystoreAlias)) { throw 'Provide -KeystoreAlias or FIRERETRO_KEY_ALIAS.' }
if ([string]::IsNullOrWhiteSpace($KeystorePassword)) { throw 'Provide -KeystorePassword or FIRERETRO_KEY_PASSWORD.' }
if (-not [string]::IsNullOrWhiteSpace($CloudOrigin)) {
    try { $uri = [Uri]$CloudOrigin; if ($uri.Scheme -ne 'https' -or [string]::IsNullOrWhiteSpace($uri.Host) -or $uri.AbsolutePath -notin @('', '/')) { throw 'invalid' }; $CloudOrigin = 'https://' + $uri.Host.ToLowerInvariant() + ($(if ($uri.Port -gt 0 -and $uri.Port -ne 443) { ':' + $uri.Port } else { '' })) }
    catch { throw 'Provide -CloudOrigin as an HTTPS Worker origin without a path.' }
} else { Write-Warning 'CloudOrigin is empty; this APK will run offline until rebuilt with -CloudOrigin.' }

New-Item -ItemType Directory -Force -Path $release | Out-Null
$compiledResources = Join-Path $release 'compiled-resources.zip'
$baseApk = Join-Path $release 'base.apk'
$generated = Join-Path $release 'gen'
$cloudEndpoint = Join-Path $generated 'com\kiver\fireretro\CloudApiEndpoint.java'
$classes = Join-Path $release 'classes'
$dex = Join-Path $release 'dex'
$unsigned = Join-Path $release 'unsigned.apk'
$aligned = Join-Path $release 'aligned.apk'
$signed = Join-Path $release 'Jogos-Retro.apk'

& (Join-Path $buildTools 'aapt2.exe') compile --dir (Join-Path $appRoot 'app\src\main\res') -o $compiledResources
if ($LASTEXITCODE -ne 0) { throw 'Android resources did not compile' }

& (Join-Path $buildTools 'aapt2.exe') link -I $androidJar --manifest (Join-Path $appRoot 'app\src\main\AndroidManifest.xml') --auto-add-overlay --min-sdk-version 28 --target-sdk-version 28 -A (Join-Path $appRoot 'app\src\main\assets') --java $generated -o $baseApk $compiledResources
if ($LASTEXITCODE -ne 0) { throw 'Android package resources did not link' }

New-Item -ItemType Directory -Force -Path $classes, $dex | Out-Null
$sourceCloud = Get-Content (Join-Path $appRoot 'app\src\main\java\com\kiver\fireretro\CloudApiEndpoint.java') -Raw
if (-not [string]::IsNullOrWhiteSpace($CloudOrigin)) { $sourceCloud = [regex]::Replace($sourceCloud, 'DEFAULT_ORIGIN\s*=\s*"[^"]*"', 'DEFAULT_ORIGIN = "' + $CloudOrigin + '"') }
New-Item -ItemType Directory -Force -Path (Split-Path $cloudEndpoint -Parent) | Out-Null
[System.IO.File]::WriteAllText($cloudEndpoint, $sourceCloud, [System.Text.UTF8Encoding]::new($false))
$sourceFiles = @(
    (Join-Path $generated 'com\kiver\fireretro\R.java'),
    (Join-Path $appRoot 'app\src\main\java\com\kiver\fireretro\LauncherState.java'),
    (Join-Path $appRoot 'app\src\main\java\com\kiver\fireretro\ThemeState.java'),
    (Join-Path $appRoot 'app\src\main\java\com\kiver\fireretro\ThemeCatalog.java'),
    (Join-Path $appRoot 'app\src\main\java\com\kiver\fireretro\ThemeCustomization.java'),
    (Join-Path $appRoot 'app\src\main\java\com\kiver\fireretro\TvNavigationState.java'),
    (Join-Path $appRoot 'app\src\main\java\com\kiver\fireretro\ControllerInputRouter.java'),
    (Join-Path $appRoot 'app\src\main\java\com\kiver\fireretro\TvFocusCoordinator.java'),
    (Join-Path $appRoot 'app\src\main\java\com\kiver\fireretro\DeviceStateThrottle.java'),
    (Join-Path $appRoot 'app\src\main\java\com\kiver\fireretro\SafeAreaProfile.java'),
    (Join-Path $appRoot 'app\src\main\java\com\kiver\fireretro\LibraryUiState.java'),
    (Join-Path $appRoot 'app\src\main\java\com\kiver\fireretro\ThemeProfile.java'),
    (Join-Path $appRoot 'app\src\main\java\com\kiver\fireretro\StoragePaths.java'),
    (Join-Path $appRoot 'app\src\main\java\com\kiver\fireretro\ControllerProfile.java'),
    (Join-Path $appRoot 'app\src\main\java\com\kiver\fireretro\ControllerRegistry.java'),
    (Join-Path $appRoot 'app\src\main\java\com\kiver\fireretro\SettingsNavigation.java'),
    (Join-Path $appRoot 'app\src\main\java\com\kiver\fireretro\ControllerState.java'),
    (Join-Path $appRoot 'app\src\main\java\com\kiver\fireretro\RemoteLibraryEndpoint.java'),
    (Join-Path $appRoot 'app\src\main\java\com\kiver\fireretro\AndroidAppEntry.java'),
    (Join-Path $appRoot 'app\src\main\java\com\kiver\fireretro\AndroidAppSource.java'),
    (Join-Path $appRoot 'app\src\main\java\com\kiver\fireretro\AndroidAppInstaller.java'),
    (Join-Path $appRoot 'app\src\main\java\com\kiver\fireretro\InstallStatusReceiver.java'),
    (Join-Path $appRoot 'app\src\main\java\com\kiver\fireretro\RemoteLibrarySettings.java'),
    $cloudEndpoint,
    (Join-Path $appRoot 'app\src\main\java\com\kiver\fireretro\CloudDeviceClient.java'),
    (Join-Path $appRoot 'app\src\main\java\com\kiver\fireretro\CloudLibrarySync.java'),
    (Join-Path $appRoot 'app\src\main\java\com\kiver\fireretro\RemoteLibrarySync.java'),
    (Join-Path $appRoot 'app\src\main\java\com\kiver\fireretro\CatalogStore.java'),
    (Join-Path $appRoot 'app\src\main\java\com\kiver\fireretro\MainActivity.java')
)
& (Join-Path $JavaRoot 'bin\javac.exe') -encoding UTF-8 --release 8 -cp $androidJar -d $classes $sourceFiles
if ($LASTEXITCODE -ne 0) { throw 'Launcher Java source did not compile' }

$env:JAVA_HOME = $JavaRoot
$env:PATH = (Join-Path $JavaRoot 'bin') + ';' + $env:PATH
$classFiles = Get-ChildItem $classes -Recurse -Filter '*.class' | ForEach-Object FullName
$classList = Join-Path $release 'classes.args'
($classFiles | ForEach-Object { $_ }) | Set-Content -LiteralPath $classList -Encoding ascii
& (Join-Path $buildTools 'd8.bat') --min-api 28 --output $dex "@$classList"
if ($LASTEXITCODE -ne 0) { throw 'Launcher dex generation failed' }

Copy-Item -LiteralPath $baseApk -Destination $unsigned
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::Open($unsigned, [System.IO.Compression.ZipArchiveMode]::Update)
try {
    $oldDex = $archive.GetEntry('classes.dex')
    if ($null -ne $oldDex) { $oldDex.Delete() }
    $entry = $archive.CreateEntry('classes.dex', [System.IO.Compression.CompressionLevel]::NoCompression)
    $input = [System.IO.File]::OpenRead((Join-Path $dex 'classes.dex'))
    $output = $entry.Open()
    try { $input.CopyTo($output) } finally { $output.Dispose(); $input.Dispose() }
} finally {
    $archive.Dispose()
}

& (Join-Path $buildTools 'zipalign.exe') -f 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw 'APK alignment failed' }
& (Join-Path $buildTools 'apksigner.bat') sign --verbose --ks $KeystorePath --ks-key-alias $KeystoreAlias --ks-pass "pass:$KeystorePassword" --out $signed $aligned
if ($LASTEXITCODE -ne 0) { throw 'APK signing failed' }
& (Join-Path $buildTools 'apksigner.bat') verify --verbose $signed
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }

& (Join-Path $buildTools 'aapt2.exe') dump badging $signed
Get-Item $signed
