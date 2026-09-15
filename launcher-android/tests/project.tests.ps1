$root = Split-Path $PSScriptRoot -Parent
$manifest = Join-Path $root 'app\src\main\AndroidManifest.xml'
$activity = Join-Path $root 'app\src\main\java\com\kiver\fireretro\MainActivity.java'
$asset = Join-Path $root 'app\src\main\res\drawable-nodpi\kalel_katherine_menu.png'
$banner = Join-Path $root 'app\src\main\res\drawable-nodpi\jogos_retro_banner.png'
$homeIcon = Join-Path $root 'app\src\main\res\drawable-nodpi\fireretro_home_icon_v2.png'
$gamesAsset = Join-Path $root 'app\src\main\assets\games.json'
$thumbDir = Join-Path $root 'app\src\main\res\drawable-nodpi'
$stateSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\LauncherState.java'
$stateTest = Join-Path $PSScriptRoot 'LauncherStateTest.java'
$themeSource = Join-Path $root 'app\src\main\java\com\kiver\fireretro\ThemeState.java'
$themeTest = Join-Path $PSScriptRoot 'ThemeStateTest.java'
$buildScript = Join-Path $root 'scripts\Build-FireRetro.ps1'
if (-not (Test-Path $manifest)) { throw 'Manifest is missing' }
if (-not (Test-Path $activity)) { throw 'MainActivity is missing' }
if (-not (Test-Path $asset)) { throw 'Personalized artwork is missing' }
if (-not (Test-Path $banner)) { throw 'The widescreen Fire TV banner is missing' }
if (-not (Test-Path $homeIcon)) { throw 'The dedicated Fire TV home icon is missing' }
if (-not (Test-Path $gamesAsset)) { throw 'Embedded games list is missing' }
if (-not (Test-Path $buildScript)) { throw 'Repeatable Android build script is missing' }
$buildScriptText = Get-Content $buildScript -Raw
foreach ($required in @('aapt2.exe', 'd8.bat', 'zipalign.exe', 'apksigner.bat')) {
    if ($buildScriptText -notmatch [regex]::Escape($required)) { throw "Build script is missing $required" }
}
if ($buildScriptText -notmatch 'FIRERETRO_KEYSTORE' -or $buildScriptText -match 'keystore\\fireretro\.keystore') { throw 'Build script must use an external signing keystore' }
if ($buildScriptText -notmatch [regex]::Escape('ThemeState.java')) { throw 'Build script is missing the carousel state source' }
$javaRoot = 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2024.1.4\jbr'
$testBuild = Join-Path $PSScriptRoot '.build'
New-Item -ItemType Directory -Force -Path $testBuild | Out-Null
& (Join-Path $javaRoot 'bin\javac.exe') -encoding UTF-8 --release 8 -proc:none -d $testBuild $stateSource $stateTest $themeSource $themeTest
if ($LASTEXITCODE -ne 0) { throw 'Launcher state behavior did not compile' }
& (Join-Path $javaRoot 'bin\java.exe') -cp $testBuild com.kiver.fireretro.LauncherStateTest
if ($LASTEXITCODE -ne 0) { throw 'Launcher state behavior failed' }
& (Join-Path $javaRoot 'bin\java.exe') -cp $testBuild com.kiver.fireretro.ThemeStateTest
if ($LASTEXITCODE -ne 0) { throw 'Theme state behavior failed' }
$manifestText = Get-Content $manifest -Raw
if ($manifestText -notmatch 'android\.intent\.category\.LEANBACK_LAUNCHER') { throw 'TV launcher category is missing' }
if ($manifestText -notmatch 'android:label="Jogos Retro"') { throw 'The Fire TV app name must be Jogos Retro' }
if ($manifestText -notmatch 'android:banner="@drawable/jogos_retro_banner"') { throw 'The Fire TV banner must use the widescreen artwork' }
if ($manifestText -notmatch 'android:icon="@drawable/fireretro_home_icon_v2"') { throw 'The Fire TV home icon must use the dedicated square artwork' }
if ($manifestText -notmatch 'android:versionCode="20260916"') { throw 'The Fire TV package must bump versionCode for the refreshed launcher component' }
if ($manifestText -notmatch 'android:name="android.software.leanback" android:required="true"') { throw 'The Fire TV app must request the Leanback launcher tile' }
if ($manifestText -notmatch '<activity[^>]*android:banner="@drawable/jogos_retro_banner"') { throw 'The Leanback activity must expose the widescreen artwork' }
if ($manifestText -notmatch '<activity[^>]*android:icon="@drawable/fireretro_home_icon_v2"') { throw 'The Leanback activity must expose the dedicated home icon' }
if ($manifestText -notmatch '<activity-alias[^>]*android:name="\.FireTvHome"') { throw 'The Fire TV launcher alias is missing' }
if ($manifestText -notmatch '<activity-alias[^>]*android:banner="@drawable/jogos_retro_banner"') { throw 'The Fire TV launcher alias must expose the widescreen banner' }
$source = Get-Content $activity -Raw
foreach ($required in @('com.retroarch.ra32','content_favorites.lpl','RetroActivityFuture','LIBRETRO')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "Launcher source is missing $required" }
}
foreach ($required in @('CONFIGFILE','/sdcard/Android/data/com.retroarch.ra32/files/retroarch.cfg')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "Launcher source is missing RetroArch configuration handoff: $required" }
}
foreach ($required in @('GradientDrawable','PERSONALIZAR')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "Visual layout is missing $required" }
}
foreach ($required in @('getAssets','LinearLayout.LayoutParams(0')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "Launcher source is missing $required" }
}
foreach ($required in @('platform','image','setAlpha','ImageView','FrameLayout','Section','FIT_CENTER','0xEE')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "Launcher source is missing $required" }
}
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
if ($source -match 'Jogos do Kalel e da Katherine') { throw 'Header title text should be hidden' }
foreach ($required in @('setOnKeyListener','KEYCODE_BACK','scrollTo')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "Home navigation is missing $required" }
}
foreach ($required in @('ThemeState','scheduleCarousel','KEYCODE_DPAD_LEFT','KEYCODE_DPAD_RIGHT','BUSCAR JOGO','SLIDE')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "Carousel navigation is missing $required" }
}
foreach ($required in @('A  ABRIR','B  VOLTAR','MENU  PERSONALIZAR','screenHeight')) {
    if ($source -notmatch [regex]::Escape($required)) { throw "Controller shortcut strip is missing $required" }
}
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
