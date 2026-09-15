param([string]$InitialIp = '')

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()

$script:ManagerRoot = Split-Path $PSScriptRoot -Parent
$script:AdbPath = Join-Path $ManagerRoot 'tools\platform-tools\adb.exe'
if (-not (Test-Path $script:AdbPath)) { $script:AdbPath = 'adb' }
$script:CacheRoot = Join-Path $env:LOCALAPPDATA 'FireRetroManager'
$script:SettingsPath = Join-Path $script:CacheRoot 'settings.json'
$script:ThemeCacheRoot = Join-Path $script:CacheRoot 'theme'
$script:ThemeConfigPath = Join-Path $script:ThemeCacheRoot 'slides.json'
$script:RemoteThemeRoot = '/sdcard/Android/data/com.kiver.fireretro/files/theme'

function Load-ManagerSettings {
    if (Test-Path -LiteralPath $script:SettingsPath) {
        try { return Get-Content -LiteralPath $script:SettingsPath -Raw | ConvertFrom-Json } catch { }
    }
    return [pscustomobject]@{ LastIp = ''; RomFolder = ''; LastGameName = ''; LastCoverPath = '' }
}

function Save-ManagerSettings {
    param([string]$LastIp, [string]$RomFolder, [string]$LastGameName = '', [string]$LastCoverPath = '')
    New-Item -ItemType Directory -Path $script:CacheRoot -Force | Out-Null
    [pscustomobject]@{ LastIp = $LastIp; RomFolder = $RomFolder; LastGameName = $LastGameName; LastCoverPath = $LastCoverPath; UpdatedAt = (Get-Date).ToUniversalTime().ToString('o') } |
        ConvertTo-Json | Set-Content -LiteralPath $script:SettingsPath -Encoding utf8
}

$script:Settings = Load-ManagerSettings

function Invoke-Adb {
    param([string[]]$Arguments)
    $psi = [Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $script:AdbPath
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    # Windows PowerShell 5.1 does not expose ProcessStartInfo.ArgumentList.
    $psi.Arguments = (($Arguments | ForEach-Object { '"' + ([string]$_).Replace('"', '\"') + '"' }) -join ' ')
    $process = [Diagnostics.Process]::Start($psi)
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    [pscustomobject]@{ ExitCode = $process.ExitCode; Output = $stdout.Trim(); Error = $stderr.Trim() }
}

function Connect-AdbDevice {
    param([Parameter(Mandatory)][string]$Ip)
    if ($Ip -notmatch '^(?:\d{1,3}\.){3}\d{1,3}$') { return [pscustomobject]@{ ExitCode = 1; Output = ''; Error = 'Informe um endereço IP válido.' } }
    $connect = Invoke-Adb @('connect', "$Ip`:5555")
    if ($connect.ExitCode -ne 0) { return $connect }
    Invoke-Adb @('-s', "$Ip`:5555", 'get-state')
}

function Get-AdbDevices {
    # Equivalent of: adb devices
    Invoke-Adb @('devices')
}

function Get-DeviceSummary {
    param([Parameter(Mandatory)][string]$Serial)
    # Storage check uses: df -h /sdcard
    $model = Invoke-Adb @('-s', $Serial, 'shell', 'getprop', 'ro.product.model')
    $abi = Invoke-Adb @('-s', $Serial, 'shell', 'getprop', 'ro.product.cpu.abi')
    $space = Invoke-Adb @('-s', $Serial, 'shell', 'df', '-h', '/sdcard')
    [pscustomobject]@{ Model = $model.Output; Abi = $abi.Output; Storage = $space.Output }
}

function Select-RomFolder {
    $dialog = [Windows.Forms.FolderBrowserDialog]::new()
    $dialog.Description = 'Escolha a pasta local que contém suas ROMs'
    if ($dialog.ShowDialog() -eq [Windows.Forms.DialogResult]::OK) { return $dialog.SelectedPath }
    return $null
}

function Get-LocalGameCatalog {
    param([Parameter(Mandatory)][string]$RootPath)
    if (-not (Test-Path -LiteralPath $RootPath -PathType Container)) { return @() }
    $games = @()
    $extensions = @('.nes','.nez','.sfc','.smc','.fig','.md','.gen','.sms','.gba','.gb','.gbc','.iso','.chd','.cue','.bin','.zip','.7z')
    foreach ($file in Get-ChildItem -LiteralPath $RootPath -File -Recurse -ErrorAction SilentlyContinue) {
        if ($file.Extension.ToLowerInvariant() -notin $extensions) { continue }
        $rootPrefix = $RootPath.TrimEnd('\','/') + [IO.Path]::DirectorySeparatorChar
        $relative = $file.FullName.Substring($rootPrefix.Length)
        $pathText = $relative.ToLowerInvariant()
        $platform = if ($pathText -match 'ps1|playstation|psx' -or $file.Extension -in @('.iso','.chd','.cue','.bin')) { 'PlayStation' }
            elseif ($pathText -match 'gba|game boy advance' -or $file.Extension -eq '.gba') { 'GBA' }
            elseif ($pathText -match 'mega|genesis|megadrive' -or $file.Extension -in @('.md','.gen','.sms')) { 'Mega Drive' }
            elseif ($pathText -match 'snes|super nintendo' -or $file.Extension -in @('.sfc','.smc','.fig')) { 'SNES' }
            elseif ($pathText -match 'nes|famicom' -or $file.Extension -in @('.nes','.nez')) { 'NES' }
            else { 'Outros' }
        $games += [pscustomobject]@{ Id = ($relative -replace '[^\p{L}\p{Nd}]','_'); DisplayName = [IO.Path]::GetFileNameWithoutExtension($file.Name); Platform = $platform; RelativePath = $relative; FullPath = $file.FullName }
    }
    return @($games | Sort-Object Platform, DisplayName)
}

function Select-CoverImage {
    $dialog = [Windows.Forms.OpenFileDialog]::new()
    $dialog.Title = 'Escolha a capa do jogo'
    $dialog.Filter = 'Imagens (*.png;*.jpg;*.jpeg)|*.png;*.jpg;*.jpeg'
    if ($dialog.ShowDialog() -eq [Windows.Forms.DialogResult]::OK) { return $dialog.FileName }
    return $null
}

function Save-CoverImage {
    param([Parameter(Mandatory)][string]$GameName, [Parameter(Mandatory)][string]$SourcePath)
    if (-not (Test-Path -LiteralPath $SourcePath)) { throw 'A imagem escolhida não existe.' }
    $coverRoot = Join-Path $script:CacheRoot 'covers'
    New-Item -ItemType Directory -Path $coverRoot -Force | Out-Null
    $safeName = ($GameName -replace '[^\p{L}\p{Nd}._-]', '_').Trim('_')
    if ([string]::IsNullOrWhiteSpace($safeName)) { throw 'Informe o nome do jogo antes de salvar.' }
    $destination = Join-Path $coverRoot ($safeName + [IO.Path]::GetExtension($SourcePath).ToLowerInvariant())
    Copy-Item -LiteralPath $SourcePath -Destination $destination -Force
    return $destination
}

function Get-DefaultThemeSlides {
    $assetRoot = Join-Path $script:ManagerRoot 'launcher-android\app\src\main\assets\slides'
    $defaults = @(
        @{ Image = 'kalel-kath.png'; Title = 'KALEL E KATH'; Caption = 'Jogos nos unem sempre' }
        @{ Image = 'corrida.png'; Title = 'HORA DA CORRIDA'; Caption = 'Escolha um jogo e divirta-se' }
        @{ Image = 'aventura.png'; Title = 'MUNDOS PARA EXPLORAR'; Caption = 'Aventura para toda a família' }
        @{ Image = 'retro.png'; Title = 'JOGOS RETRO'; Caption = 'Clássicos prontos para jogar' }
    )
    return @($defaults | ForEach-Object {
        [pscustomobject]@{ Image = $_.Image; Title = $_.Title; Caption = $_.Caption; SourcePath = (Join-Path $assetRoot $_.Image) }
    })
}

function Get-ThemeConfiguration {
    if (Test-Path -LiteralPath $script:ThemeConfigPath) {
        try {
            $theme = Get-Content -LiteralPath $script:ThemeConfigPath -Raw | ConvertFrom-Json
            $slides = @($theme.slides | ForEach-Object {
                $image = [IO.Path]::GetFileName([string]$_.image)
                [pscustomobject]@{ Image = $image; Title = [string]$_.title; Caption = [string]$_.caption; SourcePath = (Join-Path $script:ThemeCacheRoot $image) }
            })
            if ($slides.Count -gt 0) {
                return [pscustomobject]@{ AutoAdvanceSeconds = [int]$theme.autoAdvanceSeconds; OverlayOpacity = [int]$theme.overlayOpacity; Slides = $slides }
            }
        } catch { }
    }
    return [pscustomobject]@{ AutoAdvanceSeconds = 5; OverlayOpacity = 70; Slides = @(Get-DefaultThemeSlides) }
}

function Save-ThemeConfiguration {
    param(
        [Parameter(Mandatory)][array]$Slides,
        [ValidateRange(3,20)][int]$AutoAdvanceSeconds = 5,
        [ValidateRange(0,90)][int]$OverlayOpacity = 70
    )
    if ($Slides.Count -lt 1) { throw 'Adicione ao menos um slide antes de salvar.' }
    New-Item -ItemType Directory -Path $script:ThemeCacheRoot -Force | Out-Null
    $payloadSlides = @($Slides | ForEach-Object {
        [ordered]@{ id = ([IO.Path]::GetFileNameWithoutExtension($_.Image)); image = [IO.Path]::GetFileName($_.Image); title = [string]$_.Title; caption = [string]$_.Caption }
    })
    [ordered]@{ version = 1; autoAdvanceSeconds = $AutoAdvanceSeconds; overlayOpacity = $OverlayOpacity; slides = $payloadSlides } |
        ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $script:ThemeConfigPath -Encoding utf8
    return $script:ThemeConfigPath
}

function Add-ThemeSlideImage {
    param([Parameter(Mandatory)][string]$SourcePath, [Parameter(Mandatory)][string]$Title, [string]$Caption = '')
    if (-not (Test-Path -LiteralPath $SourcePath)) { throw 'A imagem escolhida não existe.' }
    New-Item -ItemType Directory -Path $script:ThemeCacheRoot -Force | Out-Null
    $extension = [IO.Path]::GetExtension($SourcePath).ToLowerInvariant()
    if ($extension -notin @('.png','.jpg','.jpeg')) { throw 'Escolha uma imagem PNG ou JPG.' }
    $imageName = 'slide-' + [Guid]::NewGuid().ToString('N') + $extension
    $destination = Join-Path $script:ThemeCacheRoot $imageName
    Copy-Item -LiteralPath $SourcePath -Destination $destination -Force
    return [pscustomobject]@{ Image = $imageName; Title = $Title; Caption = $Caption; SourcePath = $destination }
}

function Push-ThemeToFireStick {
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][array]$Slides, [int]$AutoAdvanceSeconds = 5, [int]$OverlayOpacity = 70)
    $config = Save-ThemeConfiguration -Slides $Slides -AutoAdvanceSeconds $AutoAdvanceSeconds -OverlayOpacity $OverlayOpacity
    $mkdir = Invoke-Adb @('-s', $Serial, 'shell', 'mkdir', '-p', $script:RemoteThemeRoot)
    if ($mkdir.ExitCode -ne 0) { return $mkdir }
    $configPush = Invoke-Adb @('-s', $Serial, 'push', $config, "$script:RemoteThemeRoot/slides.json")
    if ($configPush.ExitCode -ne 0) { return $configPush }
    foreach ($slide in $Slides) {
        if (-not (Test-Path -LiteralPath $slide.SourcePath)) { return [pscustomobject]@{ ExitCode = 1; Output = ''; Error = "A imagem do slide não foi encontrada: $($slide.Title)" } }
        $push = Invoke-Adb @('-s', $Serial, 'push', $slide.SourcePath, "$script:RemoteThemeRoot/$([IO.Path]::GetFileName($slide.Image))")
        if ($push.ExitCode -ne 0) { return $push }
    }
    return [pscustomobject]@{ ExitCode = 0; Output = 'Tema enviado'; Error = '' }
}

function Open-FireRetroRemote {
    param([Parameter(Mandatory)][string]$Serial)
    Invoke-Adb @('-s', $Serial, 'shell', 'am', 'start', '-n', 'com.kiver.fireretro/.MainActivity')
}

function Restart-FireRetroRemote {
    param([Parameter(Mandatory)][string]$Serial)
    Invoke-Adb @('-s', $Serial, 'shell', 'am', 'force-stop', 'com.kiver.fireretro') | Out-Null
    Start-Sleep -Milliseconds 400
    Open-FireRetroRemote -Serial $Serial
}

function Set-RemoteControllerProfile {
    param([Parameter(Mandatory)][string]$Serial)
    $remoteConfig = '/sdcard/Android/data/com.retroarch.ra32/files/retroarch.cfg'
    $backupConfig = "$remoteConfig.before-manager-$(Get-Date -Format yyyyMMdd-HHmmss)"
    $backup = Invoke-Adb @('-s', $Serial, 'shell', 'cp', $remoteConfig, $backupConfig)
    if ($backup.ExitCode -ne 0) { return $backup }
    $localConfig = Join-Path $script:CacheRoot 'retroarch.cfg.manager-working'
    $pull = Invoke-Adb @('-s', $Serial, 'pull', $remoteConfig, $localConfig)
    if ($pull.ExitCode -ne 0) { return $pull }
    $text = Get-Content -LiteralPath $localConfig -Raw
    foreach ($pair in @{
        'input_player1_analog_dpad_mode' = '3'
        'input_menu_toggle_gamepad_combo' = '7'
        'input_menu_toggle_btn' = '106'
        'input_quit_gamepad_combo' = '4'
        'quit_press_twice' = 'false'
    }.GetEnumerator()) {
        $pattern = '(?m)^' + [regex]::Escape($pair.Key) + '\s*=\s*"[^"]*"\s*$'
        $replacement = $pair.Key + ' = "' + $pair.Value + '"'
        $text = [regex]::Replace($text, $pattern, $replacement)
    }
    Set-Content -LiteralPath $localConfig -Value $text -Encoding utf8
    $push = Invoke-Adb @('-s', $Serial, 'push', $localConfig, $remoteConfig)
    Remove-Item -LiteralPath $localConfig -Force -ErrorAction SilentlyContinue
    return $push
}

function Open-ManagerFolder {
    Start-Process explorer.exe -ArgumentList "`"$ManagerRoot`""
}

function Apply-ButtonStyle {
    param([Parameter(Mandatory)][Windows.Forms.Button]$Button, [switch]$Primary)
    $Button.FlatStyle = [Windows.Forms.FlatStyle]::Flat
    $Button.FlatAppearance.BorderSize = 1
    $Button.FlatAppearance.BorderColor = [Drawing.Color]::FromArgb(0, 213, 200)
    $Button.BackColor = if ($Primary) { [Drawing.Color]::FromArgb(18, 104, 145) } else { [Drawing.Color]::FromArgb(21, 55, 109) }
    $Button.ForeColor = [Drawing.Color]::White
    $Button.Font = [Drawing.Font]::new('Segoe UI', 10, [Drawing.FontStyle]::Bold)
    $Button.Cursor = [Windows.Forms.Cursors]::Hand
}

$form = [Windows.Forms.Form]::new()
$form.Text = 'FireRetro Manager'
$form.StartPosition = 'CenterScreen'
$form.Size = [Drawing.Size]::new(1180, 720)
$form.MinimumSize = [Drawing.Size]::new(1020, 620)
$form.BackColor = [Drawing.Color]::FromArgb(7, 19, 61)
$form.ForeColor = [Drawing.Color]::White
$form.Font = [Drawing.Font]::new('Segoe UI', 10)

$title = [Windows.Forms.Label]::new()
$title.Text = 'FireRetro Manager'
$title.Font = [Drawing.Font]::new('Segoe UI', 22, [Drawing.FontStyle]::Bold)
$title.AutoSize = $true
$title.Location = [Drawing.Point]::new(220, 24)
$title.ForeColor = [Drawing.Color]::FromArgb(116, 239, 255)
$form.Controls.Add($title)

$subtitle = [Windows.Forms.Label]::new()
$subtitle.Text = 'Conecte e prepare seu Fire TV com segurança'
$subtitle.AutoSize = $true
$subtitle.Location = [Drawing.Point]::new(224, 66)
$form.Controls.Add($subtitle)

$sidebar = [Windows.Forms.Panel]::new(); $sidebar.Location = [Drawing.Point]::new(0, 0); $sidebar.Size = [Drawing.Size]::new(190, 720); $sidebar.BackColor = [Drawing.Color]::FromArgb(10, 29, 73); $form.Controls.Add($sidebar)
$sideBrand = [Windows.Forms.Label]::new(); $sideBrand.Text = "FIRERETRO`nMANAGER"; $sideBrand.AutoSize = $true; $sideBrand.Location = [Drawing.Point]::new(22, 28); $sideBrand.Font = [Drawing.Font]::new('Segoe UI', 14, [Drawing.FontStyle]::Bold); $sideBrand.ForeColor = [Drawing.Color]::FromArgb(116, 239, 255); $sidebar.Controls.Add($sideBrand)
function New-NavigationButton { param([string]$Text, [int]$Top) $button = [Windows.Forms.Button]::new(); $button.Text = $Text; $button.Size = [Drawing.Size]::new(154, 42); $button.Location = [Drawing.Point]::new(18, $Top); Apply-ButtonStyle $button; $sidebar.Controls.Add($button); return $button }
$navHome = New-NavigationButton 'Início' 120
$navTheme = New-NavigationButton 'Slides e aparência' 170
$navLibrary = New-NavigationButton 'Jogos e pastas' 220
$navRemote = New-NavigationButton 'Controle e Fire TV' 270
$sideHint = [Windows.Forms.Label]::new(); $sideHint.Text = "As preferências são guardadas`nno seu computador."; $sideHint.AutoSize = $true; $sideHint.Location = [Drawing.Point]::new(20, 585); $sideHint.ForeColor = [Drawing.Color]::FromArgb(186, 207, 245); $sidebar.Controls.Add($sideHint)

$ipLabel = [Windows.Forms.Label]::new(); $ipLabel.Text = 'IP do Fire Stick'; $ipLabel.AutoSize = $true; $ipLabel.Location = [Drawing.Point]::new(220, 112); $form.Controls.Add($ipLabel)
$ip = [Windows.Forms.TextBox]::new(); $ip.Text = $(if ($InitialIp) { $InitialIp } else { $script:Settings.LastIp }); $ip.Width = 180; $ip.Location = [Drawing.Point]::new(220, 136); $form.Controls.Add($ip)
$connect = [Windows.Forms.Button]::new(); $connect.Text = 'Conectar via ADB'; $connect.Width = 155; $connect.Location = [Drawing.Point]::new(410, 134); $form.Controls.Add($connect); Apply-ButtonStyle $connect -Primary
$choose = [Windows.Forms.Button]::new(); $choose.Text = 'Escolher pasta de ROMs'; $choose.Width = 180; $choose.Location = [Drawing.Point]::new(575, 134); $form.Controls.Add($choose)
$remoteOpen = [Windows.Forms.Button]::new(); $remoteOpen.Text = 'Abrir FireRetro'; $remoteOpen.Width = 150; $remoteOpen.Location = [Drawing.Point]::new(765, 134); $form.Controls.Add($remoteOpen)
$remoteRestart = [Windows.Forms.Button]::new(); $remoteRestart.Text = 'Reiniciar FireRetro'; $remoteRestart.Width = 150; $remoteRestart.Location = [Drawing.Point]::new(925, 134); $form.Controls.Add($remoteRestart)
Apply-ButtonStyle $choose; Apply-ButtonStyle $remoteOpen; Apply-ButtonStyle $remoteRestart

$coverGroup = [Windows.Forms.GroupBox]::new(); $coverGroup.Text = 'Slides e aparência'; $coverGroup.ForeColor = [Drawing.Color]::FromArgb(116, 239, 255); $coverGroup.Font = [Drawing.Font]::new('Segoe UI', 11, [Drawing.FontStyle]::Bold); $coverGroup.Location = [Drawing.Point]::new(220, 244); $coverGroup.Size = [Drawing.Size]::new(855, 238); $form.Controls.Add($coverGroup)
$gameNameLabel = [Windows.Forms.Label]::new(); $gameNameLabel.Text = 'Título do slide'; $gameNameLabel.AutoSize = $true; $gameNameLabel.Location = [Drawing.Point]::new(18, 30); $coverGroup.Controls.Add($gameNameLabel)
$gameName = [Windows.Forms.TextBox]::new(); $gameName.Width = 300; $gameName.Location = [Drawing.Point]::new(18, 54); $coverGroup.Controls.Add($gameName)
$captionLabel = [Windows.Forms.Label]::new(); $captionLabel.Text = 'Legenda'; $captionLabel.AutoSize = $true; $captionLabel.Location = [Drawing.Point]::new(18, 88); $coverGroup.Controls.Add($captionLabel)
$script:ThemeCaption = [Windows.Forms.TextBox]::new(); $script:ThemeCaption.Width = 300; $script:ThemeCaption.Location = [Drawing.Point]::new(18, 112); $coverGroup.Controls.Add($script:ThemeCaption)
$gameSelectLabel = [Windows.Forms.Label]::new(); $gameSelectLabel.Text = 'Seus slides'; $gameSelectLabel.AutoSize = $true; $gameSelectLabel.Location = [Drawing.Point]::new(18, 150); $coverGroup.Controls.Add($gameSelectLabel)
$gameSelect = [Windows.Forms.ComboBox]::new(); $gameSelect.Width = 300; $gameSelect.DropDownStyle = 'DropDownList'; $gameSelect.Location = [Drawing.Point]::new(18, 174); $coverGroup.Controls.Add($gameSelect)
$coverChoose = [Windows.Forms.Button]::new(); $coverChoose.Text = 'Escolher imagem'; $coverChoose.Width = 145; $coverChoose.Location = [Drawing.Point]::new(335, 52); $coverGroup.Controls.Add($coverChoose); Apply-ButtonStyle $coverChoose
$coverSave = [Windows.Forms.Button]::new(); $coverSave.Text = 'Adicionar slide'; $coverSave.Width = 145; $coverSave.Location = [Drawing.Point]::new(490, 52); $coverGroup.Controls.Add($coverSave); Apply-ButtonStyle $coverSave -Primary
$script:ThemeUpdate = [Windows.Forms.Button]::new(); $script:ThemeUpdate.Text = 'Atualizar texto'; $script:ThemeUpdate.Width = 145; $script:ThemeUpdate.Location = [Drawing.Point]::new(335, 100); $coverGroup.Controls.Add($script:ThemeUpdate); Apply-ButtonStyle $script:ThemeUpdate
$script:ThemeSend = [Windows.Forms.Button]::new(); $script:ThemeSend.Text = 'Salvar no Fire Stick'; $script:ThemeSend.Width = 200; $script:ThemeSend.Location = [Drawing.Point]::new(335, 164); $coverGroup.Controls.Add($script:ThemeSend); Apply-ButtonStyle $script:ThemeSend -Primary
$autoLabel = [Windows.Forms.Label]::new(); $autoLabel.Text = 'Carrossel automático (segundos)'; $autoLabel.AutoSize = $true; $autoLabel.Location = [Drawing.Point]::new(540, 112); $coverGroup.Controls.Add($autoLabel)
$script:ThemeInterval = [Windows.Forms.NumericUpDown]::new(); $script:ThemeInterval.Minimum = 3; $script:ThemeInterval.Maximum = 20; $script:ThemeInterval.Location = [Drawing.Point]::new(540, 136); $script:ThemeInterval.Width = 70; $coverGroup.Controls.Add($script:ThemeInterval)
$opacityLabel = [Windows.Forms.Label]::new(); $opacityLabel.Text = 'Opacidade do fundo'; $opacityLabel.AutoSize = $true; $opacityLabel.Location = [Drawing.Point]::new(630, 112); $coverGroup.Controls.Add($opacityLabel)
$script:ThemeOpacity = [Windows.Forms.TrackBar]::new(); $script:ThemeOpacity.Minimum = 0; $script:ThemeOpacity.Maximum = 90; $script:ThemeOpacity.TickFrequency = 10; $script:ThemeOpacity.Location = [Drawing.Point]::new(625, 132); $script:ThemeOpacity.Width = 140; $coverGroup.Controls.Add($script:ThemeOpacity)
$coverPreview = [Windows.Forms.PictureBox]::new(); $coverPreview.SizeMode = 'Zoom'; $coverPreview.BorderStyle = 'FixedSingle'; $coverPreview.BackColor = [Drawing.Color]::FromArgb(21,55,109); $coverPreview.Location = [Drawing.Point]::new(640, 20); $coverPreview.Size = [Drawing.Size]::new(195, 80); $coverGroup.Controls.Add($coverPreview)
$script:SelectedCover = $null
$script:LocalCatalog = @()
$theme = Get-ThemeConfiguration
$script:ThemeSlides = [System.Collections.ArrayList]::new()
foreach ($slide in @($theme.Slides)) { [void]$script:ThemeSlides.Add($slide) }
$script:ThemeInterval.Value = [Math]::Max($script:ThemeInterval.Minimum, [Math]::Min($script:ThemeInterval.Maximum, $theme.AutoAdvanceSeconds))
$script:ThemeOpacity.Value = [Math]::Max($script:ThemeOpacity.Minimum, [Math]::Min($script:ThemeOpacity.Maximum, $theme.OverlayOpacity))

function Refresh-ThemeEditor {
    $selected = $gameSelect.SelectedIndex
    $gameSelect.Items.Clear()
    for ($i = 0; $i -lt $script:ThemeSlides.Count; $i++) { [void]$gameSelect.Items.Add("$($i + 1). $($script:ThemeSlides[$i].Title)") }
    if ($script:ThemeSlides.Count -gt 0) { $gameSelect.SelectedIndex = [Math]::Max(0, [Math]::Min($selected, $script:ThemeSlides.Count - 1)) }
}

function Show-ThemeSlide {
    param([int]$Index)
    if ($Index -lt 0 -or $Index -ge $script:ThemeSlides.Count) { return }
    $slide = $script:ThemeSlides[$Index]
    $gameName.Text = $slide.Title
    $script:ThemeCaption.Text = $slide.Caption
    if (Test-Path -LiteralPath $slide.SourcePath) {
        if ($coverPreview.Image) { $coverPreview.Image.Dispose(); $coverPreview.Image = $null }
        $coverPreview.Image = [Drawing.Image]::FromFile($slide.SourcePath)
    }
}

Refresh-ThemeEditor

$remoteControl = [Windows.Forms.Button]::new(); $remoteControl.Text = 'Enviar configurações do controle'; $remoteControl.Width = 250; $remoteControl.Location = [Drawing.Point]::new(220, 194); $form.Controls.Add($remoteControl); Apply-ButtonStyle $remoteControl -Primary
$status = [Windows.Forms.Label]::new(); $status.Text = '●  Desconectado'; $status.AutoSize = $true; $status.Location = [Drawing.Point]::new(490, 202); $status.Font = [Drawing.Font]::new('Segoe UI', 11, [Drawing.FontStyle]::Bold); $status.ForeColor = [Drawing.Color]::FromArgb(255, 195, 90); $form.Controls.Add($status)
$details = [Windows.Forms.TextBox]::new(); $details.Multiline = $true; $details.ReadOnly = $true; $details.ScrollBars = 'Vertical'; $details.Dock = 'Bottom'; $details.Height = 190; $details.BackColor = [Drawing.Color]::FromArgb(15, 40, 84); $details.ForeColor = [Drawing.Color]::FromArgb(225, 235, 255); $details.Font = [Drawing.Font]::new('Consolas', 10); $details.BorderStyle = 'FixedSingle'; $form.Controls.Add($details)

$connect.Add_Click({
    $serial = "$($ip.Text):5555"
    Save-ManagerSettings -LastIp $ip.Text -RomFolder $script:Settings.RomFolder -LastGameName $gameName.Text -LastCoverPath $script:SelectedCover
    $result = Connect-AdbDevice -Ip $ip.Text
    if ($result.ExitCode -ne 0) { $status.Text = 'Falha na conexão'; $status.ForeColor = [Drawing.Color]::OrangeRed; $details.Text = $result.Error; return }
    $devices = Get-AdbDevices
    $summary = Get-DeviceSummary -Serial $serial
    $status.Text = '●  Fire Stick conectado'; $status.ForeColor = [Drawing.Color]::LightGreen
    $details.Text = "Modelo: $($summary.Model)`r`nABI: $($summary.Abi)`r`n`r`nDispositivos ADB:`r`n$($devices.Output)`r`n`r`nArmazenamento:`r`n$($summary.Storage)`r`n`r`nNenhum dado foi alterado."
})
$choose.Add_Click({ $selected = Select-RomFolder; if ($selected) { $script:Settings.RomFolder = $selected; Save-ManagerSettings -LastIp $ip.Text -RomFolder $selected; $script:LocalCatalog = Get-LocalGameCatalog -RootPath $selected; $catalogPath = Join-Path $script:CacheRoot 'catalog.json'; New-Item -ItemType Directory -Path (Split-Path $catalogPath) -Force | Out-Null; $script:LocalCatalog | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $catalogPath -Encoding utf8; $details.Text = "Pasta de ROMs selecionada:`r`n$selected`r`n`r`nJogos encontrados: $($script:LocalCatalog.Count)`r`nA biblioteca original não foi alterada." } })
$gameSelect.Add_SelectedIndexChanged({ if ($gameSelect.SelectedIndex -ge 0) { Show-ThemeSlide -Index $gameSelect.SelectedIndex } })
$remoteOpen.Add_Click({ $result = Open-FireRetroRemote -Serial "$($ip.Text):5555"; $details.Text = if ($result.ExitCode -eq 0) { 'FireRetro aberto no Fire Stick.' } else { $result.Error } })
$remoteRestart.Add_Click({ $result = Restart-FireRetroRemote -Serial "$($ip.Text):5555"; $details.Text = if ($result.ExitCode -eq 0) { 'FireRetro reiniciado no Fire Stick.' } else { $result.Error } })
$remoteControl.Add_Click({ $result = Set-RemoteControllerProfile -Serial "$($ip.Text):5555"; $details.Text = if ($result.ExitCode -eq 0) { 'Configurações do controle enviadas. Um backup foi criado no Fire Stick.' } else { $result.Error } })
$coverChoose.Add_Click({ $script:SelectedCover = Select-CoverImage; if ($script:SelectedCover) { if ($coverPreview.Image) { $coverPreview.Image.Dispose(); $coverPreview.Image = $null }; $coverPreview.Image = [Drawing.Image]::FromFile($script:SelectedCover); $details.Text = "Imagem carregada. Escreva um título e selecione Adicionar slide." } })
$coverSave.Add_Click({ try { if (-not $script:SelectedCover) { throw 'Primeiro escolha uma imagem para o novo slide.' }; $titleText = $gameName.Text.Trim(); if ([string]::IsNullOrWhiteSpace($titleText)) { throw 'Informe o título do slide.' }; $newSlide = Add-ThemeSlideImage -SourcePath $script:SelectedCover -Title $titleText -Caption $script:ThemeCaption.Text.Trim(); [void]$script:ThemeSlides.Add($newSlide); $script:SelectedCover = $null; Refresh-ThemeEditor; $gameSelect.SelectedIndex = $script:ThemeSlides.Count - 1; $details.Text = "Slide adicionado ao tema. Clique em Salvar no Fire Stick quando terminar." } catch { $details.Text = $_.Exception.Message } })
$script:ThemeUpdate.Add_Click({ try { if ($gameSelect.SelectedIndex -lt 0) { throw 'Escolha um slide para atualizar.' }; $slide = $script:ThemeSlides[$gameSelect.SelectedIndex]; $slide.Title = $gameName.Text.Trim(); $slide.Caption = $script:ThemeCaption.Text.Trim(); if ([string]::IsNullOrWhiteSpace($slide.Title)) { throw 'Informe o título do slide.' }; $script:ThemeSlides[$gameSelect.SelectedIndex] = $slide; Refresh-ThemeEditor; $gameSelect.SelectedIndex = [Math]::Max(0, $gameSelect.SelectedIndex); $details.Text = 'Texto do slide atualizado no cache local.' } catch { $details.Text = $_.Exception.Message } })
$script:ThemeSend.Add_Click({ try { $serial = "$($ip.Text):5555"; $result = Push-ThemeToFireStick -Serial $serial -Slides @($script:ThemeSlides) -AutoAdvanceSeconds ([int]$script:ThemeInterval.Value) -OverlayOpacity ([int]$script:ThemeOpacity.Value); if ($result.ExitCode -ne 0) { throw $result.Error }; Restart-FireRetroRemote -Serial $serial | Out-Null; $details.Text = "Tema salvo e enviado ao Fire Stick.`r`nO FireRetro foi reiniciado para mostrar o novo carrossel." } catch { $details.Text = $_.Exception.Message } })
$navHome.Add_Click({ $ip.Focus() })
$navTheme.Add_Click({ $gameName.Focus() })
$navLibrary.Add_Click({ $choose.Focus() })
$navRemote.Add_Click({ $remoteControl.Focus() })
$form.Add_Shown({ $ip.Focus() })
$form.Add_FormClosing({ Save-ManagerSettings -LastIp $ip.Text -RomFolder $script:Settings.RomFolder -LastGameName $gameName.Text -LastCoverPath $script:SelectedCover })
[void]$form.ShowDialog()
