$root = Split-Path $PSScriptRoot -Parent
$manifest = Join-Path $root 'app\src\main\AndroidManifest.xml'
$activity = Join-Path $root 'app\src\main\java\com\kiver\fireretro\MainActivity.java'
$asset = Join-Path $root 'app\src\main\res\drawable-nodpi\jogos_retro_banner.png'
$banner = Join-Path $root 'app\src\main\res\drawable-nodpi\jogos_retro_banner.png'
$homeIcon = Join-Path $root 'app\src\main\res\drawable-nodpi\fireretro_home_icon_v2.png'
$gamesAsset = Join-Path $root 'app\src\main\assets\games.json'
$thumbDir = Join-Path $root 'app\src\main\res\drawable-nodpi'
$stateSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\LauncherState.java'
$stateTest = Join-Path $PSScriptRoot 'LauncherStateTest.java'
$themeSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\ThemeState.java'
$themeCustomizationSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\ThemeCustomization.java'
$themeTest = Join-Path $PSScriptRoot 'ThemeStateTest.java'
$themeCatalogSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\ThemeCatalog.java'
$controllerSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\ControllerState.java'
$controllerProfileSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\ControllerProfile.java'
$themeCatalogTest = Join-Path $PSScriptRoot 'ThemeCatalogTest.java'
$controllerTest = Join-Path $PSScriptRoot 'ControllerStateTest.java'
$controllerProfileTest = Join-Path $PSScriptRoot 'ControllerProfileTest.java'
$endpointSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\RemoteLibraryEndpoint.java'
$endpointTest = Join-Path $PSScriptRoot 'RemoteLibraryEndpointTest.java'
$cloudEndpointSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\CloudApiEndpoint.java'
$cloudEndpointTest = Join-Path $PSScriptRoot 'CloudApiEndpointTest.java'
$cloudClientSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\CloudDeviceClient.java'
$cloudSyncSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\CloudLibrarySync.java'
$appEntrySource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\AndroidAppEntry.java'
$appEntryTest = Join-Path $PSScriptRoot 'AndroidAppEntryTest.java'
$appSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\AndroidAppSource.java'
$appSourceTest = Join-Path $PSScriptRoot 'AndroidAppSourceTest.java'
$settingsNavigationSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\SettingsNavigation.java'
$settingsNavigationTest = Join-Path $PSScriptRoot 'SettingsNavigationTest.java'
$navigationSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\TvNavigationState.java'
$safeAreaSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\SafeAreaProfile.java'
$libraryUiSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\LibraryUiState.java'
$themeProfileSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\ThemeProfile.java'
$navigationTest = Join-Path $PSScriptRoot 'TvNavigationStateTest.java'
$inputRouterSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\ControllerInputRouter.java'
$inputRouterTest = Join-Path $PSScriptRoot 'ControllerInputRouterTest.java'
$gameActionSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\GameAction.java'
$inputDeviceTypeSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\InputDeviceType.java'
$inputManagerSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\InputManager.java'
$inputManagerTest = Join-Path $PSScriptRoot 'InputManagerTest.java'
$focusCoordinatorSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\TvFocusCoordinator.java'
$focusCoordinatorTest = Join-Path $PSScriptRoot 'TvFocusCoordinatorTest.java'
$stateThrottleSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\DeviceStateThrottle.java'
$stateThrottleTest = Join-Path $PSScriptRoot 'DeviceStateThrottleTest.java'
$safeAreaTest = Join-Path $PSScriptRoot 'SafeAreaProfileTest.java'
$libraryUiTest = Join-Path $PSScriptRoot 'LibraryUiStateTest.java'
$themeProfileTest = Join-Path $PSScriptRoot 'ThemeProfileTest.java'
$storageTypeSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\StorageType.java'
$romStorageStrategySource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\RomStorageStrategy.java'
$baseRomStorageStrategySource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\BaseRomStorageStrategy.java'
$appStorageStrategySource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\AppStorageStrategy.java'
$legacyExternalStorageStrategySource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\LegacyExternalStorageStrategy.java'
$removableStorageStrategySource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\RemovableStorageStrategy.java'
$usbStorageStrategySource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\UsbStorageStrategy.java'
$storagePathsSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\StoragePaths.java'
$romStorageResolverSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\RomStorageResolver.java'
$romStorageTest = Join-Path $PSScriptRoot 'RomStorageTest.java'
$deviceTypeSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\DeviceType.java'
$deviceProfileSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\DeviceProfile.java'
$deviceProfileTest = Join-Path $PSScriptRoot 'DeviceProfileTest.java'
$emulatorProviderSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\EmulatorProvider.java'
$retroArchProviderSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\RetroArchProvider.java'
$retroArchProviderTest = Join-Path $PSScriptRoot 'RetroArchProviderTest.java'
$gamerDashboardStateSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\GamerDashboardState.java'
$gamerDashboardStateTest = Join-Path $PSScriptRoot 'GamerDashboardStateTest.java'
$catalogTest = Join-Path $PSScriptRoot 'catalog-store.tests.ps1'
$buildScript = Join-Path $root 'scripts\Build-FireRetro.ps1'
if (-not (Test-Path $manifest)) { throw 'Manifest is missing' }
if (-not (Test-Path $activity)) { throw 'MainActivity is missing' }
if (-not (Test-Path $asset)) { throw 'Default launcher artwork is missing' }
if (-not (Test-Path $banner)) { throw 'The widescreen Fire TV banner is missing' }
if (-not (Test-Path $homeIcon)) { throw 'The dedicated Fire TV home icon is missing' }
if (-not (Test-Path $gamesAsset)) { throw 'Embedded games list is missing' }
if (-not (Test-Path $buildScript)) { throw 'Repeatable Android build script is missing' }
if (-not (Test-Path $themeCatalogSource) -or -not (Test-Path $controllerSource) -or -not (Test-Path $controllerProfileSource) -or -not (Test-Path $endpointSource) -or -not (Test-Path $cloudEndpointSource) -or -not (Test-Path $cloudEndpointTest) -or -not (Test-Path $cloudClientSource) -or -not (Test-Path $cloudSyncSource) -or -not (Test-Path $appEntrySource) -or -not (Test-Path $appSource) -or -not (Test-Path $settingsNavigationSource)) { throw 'Theme, controller, endpoint, Android app and settings navigation sources are missing' }
& pwsh -NoProfile -File $catalogTest
if ($LASTEXITCODE -ne 0) { throw 'Catalog store contract failed' }
$buildScriptText = Get-Content $buildScript -Raw
if ($buildScriptText -notmatch 'CloudDeviceClient\.java') { throw 'APK build is missing the per-device Cloudflare API client' }
if ($buildScriptText -notmatch 'CloudOrigin' -or $buildScriptText -notmatch 'FIRERETRO_CLOUD_ORIGIN') { throw 'APK build must inject a fixed Cloudflare Worker origin explicitly' }
if ($buildScriptText -notmatch 'CloudLibrarySync\.java') { throw 'APK build is missing the private Cloudflare library synchronizer' }
$cloudSyncText = Get-Content $cloudSyncSource -Raw
foreach ($required in @('sha256', 'renameTo', 'reserveBytes', 'downloadTicket', 'writeApps', '.part', 'r2.cloudflarestorage.com')) { if ($cloudSyncText -notmatch [regex]::Escape($required)) { throw "Cloud library sync safety check is missing $required" } }
foreach ($required in @('writeThemes','themeProfile','assignedThemeId')) { if ($cloudSyncText -notmatch [regex]::Escape($required)) { throw "Cloud theme sync is missing $required" } }
foreach ($required in @('publishRemoteCard','installItem','remoteAvailable')) { if ($cloudSyncText -notmatch [regex]::Escape($required)) { throw "On-demand cloud library is missing $required" } }
if ($cloudSyncText -match 'for \(int i = 0; i < pending\.size\(\)') { throw 'A catalog sync must not automatically download every remote ROM' }
$installerSource = Get-Content (Join-Path (Split-Path $activity -Parent) 'AndroidAppInstaller.java') -Raw
foreach ($required in @('GET_SIGNING_CERTIFICATES','expectedPackage','SHA-256','installedSignerMatches','archiveVersionCode')) { if ($installerSource -notmatch [regex]::Escape($required)) { throw "Private APK verification is missing $required" } }
foreach ($required in @('aapt2.exe', 'd8.bat', 'zipalign.exe', 'apksigner.bat')) {
    if ($buildScriptText -notmatch [regex]::Escape($required)) { throw "Build script is missing $required" }
}
if ($buildScriptText -notmatch 'classes\.args' -or $buildScriptText -notmatch '@\$classList') { throw 'Build script must use a D8 argument file on Windows' }
if ($buildScriptText -notmatch 'FIRERETRO_KEYSTORE' -or $buildScriptText -match 'keystore\\fireretro\.keystore') { throw 'Build script must use an external signing keystore' }
if ($buildScriptText -notmatch [regex]::Escape('ThemeState.java')) { throw 'Build script is missing the carousel state source' }
$javaRoot = 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2024.1.4\jbr'
$javacCmd = 'javac'
$javaCmd = 'java'
if ($IsWindows -and (Test-Path (Join-Path $javaRoot 'bin\javac.exe'))) {
    $javacCmd = (Join-Path $javaRoot 'bin\javac.exe')
    $javaCmd = (Join-Path $javaRoot 'bin\java.exe')
}
$testBuild = Join-Path $PSScriptRoot '.build'
New-Item -ItemType Directory -Force -Path $testBuild | Out-Null
& $javacCmd -encoding UTF-8 --release 8 -proc:none -d $testBuild $stateSource $stateTest $themeSource $themeTest $themeCustomizationSource $themeCatalogSource $themeCatalogTest $controllerSource $controllerProfileSource $controllerTest $controllerProfileTest $endpointSource $endpointTest $cloudEndpointSource $cloudEndpointTest $appEntrySource $appEntryTest $appSource $appSourceTest $settingsNavigationSource $settingsNavigationTest $navigationSource $navigationTest $inputRouterSource $inputRouterTest $gameActionSource $inputDeviceTypeSource $inputManagerSource $inputManagerTest $focusCoordinatorSource $focusCoordinatorTest $stateThrottleSource $stateThrottleTest $safeAreaSource $safeAreaTest $libraryUiSource $libraryUiTest $themeProfileSource $themeProfileTest $storageTypeSource $romStorageStrategySource $baseRomStorageStrategySource $appStorageStrategySource $legacyExternalStorageStrategySource $removableStorageStrategySource $usbStorageStrategySource $storagePathsSource $romStorageResolverSource $romStorageTest $deviceTypeSource $deviceProfileSource $deviceProfileTest $emulatorProviderSource $retroArchProviderSource $retroArchProviderTest $gamerDashboardStateSource $gamerDashboardStateTest
if ($LASTEXITCODE -ne 0) { throw 'Launcher state behavior did not compile' }
& $javaCmd -cp $testBuild com.kiver.fireretro.LauncherStateTest
if ($LASTEXITCODE -ne 0) { throw 'Launcher state behavior failed' }
& $javaCmd -cp $testBuild com.kiver.fireretro.ThemeStateTest
if ($LASTEXITCODE -ne 0) { throw 'Theme state behavior failed' }
& $javaCmd -cp $testBuild com.kiver.fireretro.ThemeCatalogTest
if ($LASTEXITCODE -ne 0) { throw 'Theme catalog behavior failed' }
& $javaCmd -cp $testBuild com.kiver.fireretro.ControllerStateTest
if ($LASTEXITCODE -ne 0) { throw 'Controller state behavior failed' }
& $javaCmd -cp $testBuild com.kiver.fireretro.ControllerProfileTest
if ($LASTEXITCODE -ne 0) { throw 'Controller profile behavior failed' }
& $javaCmd -cp $testBuild com.kiver.fireretro.RemoteLibraryEndpointTest
if ($LASTEXITCODE -ne 0) { throw 'Remote library endpoint behavior failed' }
& $javaCmd -cp $testBuild com.kiver.fireretro.CloudApiEndpointTest
if ($LASTEXITCODE -ne 0) { throw 'Cloud API endpoint validation failed' }
& $javaCmd -cp $testBuild com.kiver.fireretro.AndroidAppEntryTest
if ($LASTEXITCODE -ne 0) { throw 'Android app entry behavior failed' }
& $javaCmd -cp $testBuild com.kiver.fireretro.AndroidAppSourceTest
if ($LASTEXITCODE -ne 0) { throw 'Android app source behavior failed' }
& $javaCmd -cp $testBuild com.kiver.fireretro.SettingsNavigationTest
if ($LASTEXITCODE -ne 0) { throw 'Settings navigation behavior failed' }
& $javaCmd -cp $testBuild com.kiver.fireretro.TvNavigationStateTest
if ($LASTEXITCODE -ne 0) { throw 'TV navigation state behavior failed' }
& $javaCmd -cp $testBuild com.kiver.fireretro.ControllerInputRouterTest
if ($LASTEXITCODE -ne 0) { throw 'Controller input routing behavior failed' }
& $javaCmd -cp $testBuild com.kiver.fireretro.InputManagerTest
if ($LASTEXITCODE -ne 0) { throw 'Input manager behavior failed' }
& $javaCmd -cp $testBuild com.kiver.fireretro.TvFocusCoordinatorTest
if ($LASTEXITCODE -ne 0) { throw 'TV focus coordination behavior failed' }
& $javaCmd -cp $testBuild com.kiver.fireretro.DeviceStateThrottleTest
if ($LASTEXITCODE -ne 0) { throw 'Device state throttling behavior failed' }
& $javaCmd -cp $testBuild com.kiver.fireretro.SafeAreaProfileTest
if ($LASTEXITCODE -ne 0) { throw 'Safe-area behavior failed' }
& $javaCmd -cp $testBuild com.kiver.fireretro.LibraryUiStateTest
if ($LASTEXITCODE -ne 0) { throw 'Library UI state behavior failed' }
& $javaCmd -cp $testBuild com.kiver.fireretro.ThemeProfileTest
if ($LASTEXITCODE -ne 0) { throw 'Theme profile behavior failed' }
& $javaCmd -cp $testBuild com.kiver.fireretro.RomStorageTest
if ($LASTEXITCODE -ne 0) { throw 'RomStorage behavior failed' }
& $javaCmd -cp $testBuild com.kiver.fireretro.DeviceProfileTest
if ($LASTEXITCODE -ne 0) { throw 'DeviceProfile behavior failed' }
& $javaCmd -cp $testBuild com.kiver.fireretro.RetroArchProviderTest
if ($LASTEXITCODE -ne 0) { throw 'RetroArchProvider behavior failed' }
& $javaCmd -cp $testBuild com.kiver.fireretro.GamerDashboardStateTest
if ($LASTEXITCODE -ne 0) { throw 'GamerDashboardState behavior failed' }
$manifestText = Get-Content $manifest -Raw
if ($manifestText -notmatch 'android\.intent\.category\.LEANBACK_LAUNCHER') { throw 'TV launcher category is missing' }
if ($manifestText -notmatch 'android:label="Jogos Retro"') { throw 'The Fire TV app name must be Jogos Retro' }
if ($manifestText -notmatch 'android:banner="@drawable/jogos_retro_banner"') { throw 'The Fire TV banner must use the widescreen artwork' }
if ($manifestText -notmatch 'android:icon="@drawable/fireretro_home_icon_v2"') { throw 'The Fire TV home icon must use the dedicated square artwork' }
if ($manifestText -notmatch 'android:versionCode="20260936"') { throw 'The Fire TV package must bump versionCode for on-demand cloud downloads' }
if ($manifestText -notmatch 'android.permission.REQUEST_INSTALL_PACKAGES') { throw 'APK installation permission is missing' }
if ($manifestText -notmatch 'android:name="android.software.leanback" android:required="true"') { throw 'The Fire TV app must request the Leanback launcher tile' }
if ($manifestText -notmatch '<activity[^>]*android:banner="@drawable/jogos_retro_banner"') { throw 'The Leanback activity must expose the widescreen artwork' }
if ($manifestText -notmatch '<activity[^>]*android:icon="@drawable/fireretro_home_icon_v2"') { throw 'The Leanback activity must expose the dedicated home icon' }
if ($manifestText -notmatch '<activity-alias[^>]*android:name="\.FireTvHome"') { throw 'The Fire TV launcher alias is missing' }
if ($manifestText -notmatch '<activity-alias[^>]*android:banner="@drawable/jogos_retro_banner"') { throw 'The Fire TV launcher alias must expose the widescreen banner' }
$source = Get-Content $activity -Raw
foreach ($required in @('showSettingsScreen', 'populateControlsScreen', 'populateAppearanceScreen', 'populateLibraryScreen')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "Launcher source is missing full settings screen: $required" }
}
foreach ($required in @('com.retroarch.ra32','content_favorites.lpl','RetroActivityFuture','LIBRETRO')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "Launcher source is missing $required" }
}
foreach ($required in @('CONFIGFILE','/sdcard/Android/data/com.retroarch.ra32/files/retroarch.cfg')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "Launcher source is missing RetroArch configuration handoff: $required" }
}
foreach ($required in @('GradientDrawable','createGalleryNavigation','showAppearanceTextEditor')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "Visual layout is missing $required" }
}
foreach ($required in @('getAssets','LinearLayout.LayoutParams(0')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "Launcher source is missing $required" }
}
foreach ($required in @('platform','image','setAlpha','ImageView','FrameLayout','Section','FIT_CENTER','0xEE')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "Launcher source is missing $required" }
}
if ($source -notmatch 'panel\.setPadding\(scaled\(18\), scaled\(16\), scaled\(18\), scaled\(16\)\)') { throw 'Game sections need enough inner padding to keep borders and labels visible' }
if ($source -notmatch 'card\.setPadding\(scaled\(12\), scaled\(10\), scaled\(12\), scaled\(10\)\)') { throw 'Game cards need safe padding around cover and information' }
if ($source -notmatch 'createMissingCover') { throw 'Games without cover artwork need a readable fallback card' }
if ($source -notmatch 'cover\.setScaleType\(ImageView\.ScaleType\.FIT_CENTER\)') { throw 'Game cards must show full cover art without cropping' }
if ($source -match 'cover\.setScaleType\(ImageView\.ScaleType\.CENTER_CROP\)') { throw 'Game cards may not crop cover art' }
$gamesText = Get-Content $gamesAsset -Raw
foreach ($required in @('NES','SNES','Mega Drive','GBA','PlayStation')) {
    if ($gamesText -notmatch [regex]::Escape($required)) { throw "Games asset is missing platform $required" }
}
if ((Get-ChildItem $thumbDir -File | Where-Object { $_.Name -match '^cover_.*\.(png|jpg)$' }).Count -lt 1) { throw 'Game cover assets are missing' }
$games = Get-Content $gamesAsset -Raw | ConvertFrom-Json
if ($games.items.Count -ne 99) { throw 'The launcher must include all 99 installed games' }
$knownCoverMappings = @{
    'Super Mario Kart' = 'cover_mario_kart_snes'
    'Mario Kart: Super Circuit' = 'cover_mario_kart_gba'
    'Crash Team Racing' = 'cover_crash_team'
    'Need for Speed III' = 'cover_need_speed'
    'Sonic the Hedgehog' = 'cover_sonic'
    'Super Mario World' = 'cover_mario_world'
    'Donkey Kong Country' = 'cover_donkey_kong'
    'Crash Bandicoot' = 'cover_crash'
    'Hercules' = 'cover_hercules'
    'Bomberman' = 'cover_bomberman'
    'Teenage Mutant Ninja Turtles IV: Turtles in Time' = 'cover_turtles_time'
    'Metal Slug X' = 'cover_metal_slug'
    'Alex Kidd in Miracle World (USA, Europe)' = 'cover_custom_alex_kidd'
    'anguna' = 'cover_custom_anguna'
    'cavestory' = 'cover_custom_cave_story'
    'cheril-the-goddess' = 'cover_custom_cheril'
    'furryrpg' = 'cover_custom_furry_rpg'
    'Great Circus Mystery Starring Mickey & Minnie, The (USA)' = 'cover_custom_great_circus'
    'Magical Quest Starring Mickey Mouse, The (USA) (Rev 1)' = 'cover_custom_magical_quest'
    'Mario & Luigi - Superstar Saga (USA)' = 'cover_custom_mario_luigi'
    'Metal Slug Advance (China) (En) (Aftermarket) (Pirate)' = 'cover_custom_metal_slug_advance'
    'Shinobi III - Return of the Ninja Master (USA) (Beta) (1993-06-29)' = 'cover_custom_shinobi'
    'Sonic & Knuckles (World)' = 'cover_custom_sonic_knuckles'
    'Streets of Rage (World) (En,Ja)' = 'cover_custom_streets_rage'
    "Super Ghouls 'n Ghosts (USA)" = 'cover_custom_super_ghouls'
    'Vigilante (USA, Europe, Brazil) (En)' = 'cover_custom_vigilante'
}
foreach ($label in $knownCoverMappings.Keys) {
    $game = $games.items | Where-Object { $_.label -eq $label } | Select-Object -First 1
    if ($null -eq $game -or $game.image -ne $knownCoverMappings[$label]) { throw "Known game cover is not linked correctly: $label" }
}
foreach ($game in $games.items) {
    if ([string]::IsNullOrWhiteSpace($game.image)) { throw "Missing card artwork for $($game.label)" }
    $cover = Join-Path $thumbDir ($game.image + '.png')
    if (-not (Test-Path $cover)) { $cover = Join-Path $thumbDir ($game.image + '.jpg') }
    if (-not (Test-Path $cover)) { throw "Missing bundled artwork for $($game.label)" }
    $signature = [System.IO.File]::ReadAllBytes($cover)[0..2] -join ','
    if ($signature -notin @('137,80,78','255,216,255')) { throw "Invalid PNG or JPG artwork for $($game.label)" }
}
foreach ($required in @('DisplayMetrics','responsiveColumns','headerHeight','screenWidth')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "Responsive layout is missing $required" }
}
if ($source -notmatch 'panel\.setOrientation\(LinearLayout\.VERTICAL\)') { throw 'Section titles must sit above their cards' }
if ($source -match 'Jogos do usuário') { throw 'Header title text should be hidden' }
foreach ($required in @('setOnKeyListener','KEYCODE_BACK','scrollTo')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "Home navigation is missing $required" }
}
foreach ($required in @('ThemeState','createGalleryNavigation','showAppearanceTextEditor','editorKey','BUSCAR JOGO','APARÊNCIA','applySelectedTheme','ensureControllerProfile','Atalhos do RetroArch corrigidos','copyFile')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "TV navigation is missing $required" }
}
if ($source -match 'searchView\s*=\s*new\s+EditText') { throw 'Game search must not invoke the Fire OS keyboard' }
foreach ($required in @('showSearchEditor','Aplicar busca','showControllerTestScreen','onGenericMotion','showManualMappingGuide','showControllerProfilesDashboard','showRemoteSettings','CloudDeviceClient','CloudLibrarySync','beginCloudPairing','PAREAR ESTA TV','saveCloudDeviceToken','applyCloudThemeAssignment','renderAndroidGames','verifyPrivateApk','installLauncherUpdate','launcher-update.json','createAppearanceControls','themeArtwork','generatedThemeArtwork','CONTRASTE','CAPAS','COR','FAVORITOS','toggleFavorite','platformFilterView','fireretro-backup-','backFromEditor','searchEditorOpen','remoteSettingsOpen','appearanceTextEditorOpen','controllerTestOpen','setNextFocusDownId(searchView.getId())','appearanceButtonView.setNextFocusDownId(searchView.getId())','KEYCODE_BUTTON_A','KEYCODE_BUTTON_R1','KEYCODE_BUTTON_R2','R1  Próxima aba','R2  Aba anterior')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "Missing verified controller/search flow: $required" }
}
if ($source -match 'remoteStatusView\.getText\(\)\.toString\(\)\.startsWith\("BIBLIOTECA PRIVADA: verificando"\)') { throw 'A successful library sync must clear a previous transient error' }
foreach ($required in @('TvNavigationState','SafeAreaProfile','LibraryUiState','ThemeProfile','StoragePaths','createSidebar','createCompactTopBar','safeAreaProfile.horizontalInset','KEYCODE_BUTTON_L1','KEYCODE_BUTTON_L2','KEYCODE_BUTTON_START','showQuickActions','setNextFocusLeftId')) { if ($source -notmatch [regex]::Escape($required)) { throw "TV shell redesign is missing $required" } }
if ((Get-Content (Join-Path (Split-Path $activity -Parent) 'ControllerProfile.java') -Raw) -notmatch 'mergeSafeSettings') { throw 'RetroArch safe profile merge is missing' }
foreach ($required in @('AndroidAppInstaller','verifyPrivateApk','private-apk','externalAppsChanged','APPS','BAIXANDO APP')) { if ($source -notmatch [regex]::Escape($required)) { throw "Android app integration is missing $required" } }
if ($manifestText -notmatch 'InstallStatusReceiver') { throw 'APK install status receiver is missing' }
if ($manifestText -notmatch 'android.permission.ACCESS_NETWORK_STATE') { throw 'Connectivity status must be permission-aware' }
foreach ($required in @('A  Selecionar','B  Voltar','createArcadeFooter','screenHeight')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "Controller shortcut strip is missing $required" }
}
$buildScreenMatch = [regex]::Match($source, 'private void buildScreen\(\)\s*\{(?<body>[\s\S]*?)\n\s*private LinearLayout createCompactTopBar')
if (-not $buildScreenMatch.Success) { throw 'Could not inspect buildScreen' }
if ($buildScreenMatch.Groups['body'].Value -match 'startRemoteSync\(\)') { throw 'Rebuilding the TV screen must not start another cloud synchronization' }
if ($source -notmatch 'postDelayed\(periodicCloudSync, 30L \* 60L \* 1000L\)') { throw 'Periodic cloud synchronization must start after the configured 30 minute interval' }
foreach ($required in @('ControllerInputRouter','TvFocusCoordinator')) { if ($source -notmatch [regex]::Escape($required)) { throw "Controller navigation is missing $required" } }
$profile = Join-Path (Split-Path $root -Parent) 'examples\controller-profile.example.cfg'
if (-not (Test-Path $profile)) { throw 'Xbox controller profile is missing' }
if ((Get-Content $profile -Raw) -notmatch 'input_analog_dpad_mode\s*=\s*"3"') { throw 'Xbox analog-to-dpad mapping is missing' }
if ((Get-Content $profile -Raw) -notmatch 'input_a_btn\s*=\s*"97"') { throw 'Xbox A button mapping is missing' }
if ((Get-Content $profile -Raw) -notmatch 'input_b_btn\s*=\s*"96"') { throw 'Xbox B button mapping is missing' }
$retroarchConfig = Join-Path (Split-Path $root -Parent) 'examples\retroarch.cfg.example'
if (-not (Test-Path $retroarchConfig)) { throw 'RetroArch active configuration is missing' }
$retroarchConfigText = Get-Content $retroarchConfig -Raw
if ($retroarchConfigText -notmatch 'input_quit_gamepad_combo\s*=\s*"4"') { throw 'Start + Select quit shortcut is missing' }
if ($retroarchConfigText -notmatch 'quit_press_twice\s*=\s*"false"') { throw 'Start + Select still requires two presses' }
Write-Output 'PASS: FireRetro project manifest, artwork, and RetroArch launch contract'
