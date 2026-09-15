param([string]$ProjectRoot = (Split-Path $PSScriptRoot -Parent))

$assetPath = Join-Path $ProjectRoot 'app\src\main\assets\games.json'
$coverPath = Join-Path $ProjectRoot 'app\src\main\res\drawable-nodpi'
$repos = @{
  'NES' = 'Nintendo_-_Nintendo_Entertainment_System'
  'SNES' = 'Nintendo_-_Super_Nintendo_Entertainment_System'
  'Mega Drive' = 'Sega_-_Mega_Drive_-_Genesis'
  'GBA' = 'Nintendo_-_Game_Boy_Advance'
  'PlayStation' = 'Sony_-_PlayStation'
}

function Normalize-Name([string]$Name) {
  return (($Name -replace '\.[^.]+$','' -replace '[^a-zA-Z0-9]','').ToLowerInvariant())
}
function Get-Candidate($files, [string]$label) {
  $target = Normalize-Name $label
  $matches = @($files | Where-Object { (Normalize-Name ([IO.Path]::GetFileNameWithoutExtension($_.path))) -eq $target })
  if ($matches.Count -gt 0) { return $matches[0] }
  $short = Normalize-Name ($label -replace '\s*\([^)]*\)','')
  $matches = @($files | Where-Object { (Normalize-Name ([IO.Path]::GetFileNameWithoutExtension($_.path))) -eq $short })
  if ($matches.Count -gt 0) { return $matches[0] }
  $matches = @($files | Where-Object { $short.Length -gt 5 -and (Normalize-Name ([IO.Path]::GetFileNameWithoutExtension($_.path))).Contains($short) })
  if ($matches.Count -gt 0) { return $matches[0] }
  return $null
}
function New-TitleCard([string]$file, [string]$label, [string]$platform, [int]$index) {
  Add-Type -AssemblyName System.Drawing
  $palette = @('#244a89','#6d3d8c','#9b3b47','#136b66','#7c5518')
  $color = [Drawing.ColorTranslator]::FromHtml($palette[$index % $palette.Count])
  $bitmap = New-Object Drawing.Bitmap 720,360
  $graphics = [Drawing.Graphics]::FromImage($bitmap)
  $graphics.Clear($color)
  $brush = New-Object Drawing.SolidBrush([Drawing.Color]::FromArgb(55,255,255,255))
  $graphics.FillEllipse($brush,210,-80,300,300)
  $fontBig = New-Object Drawing.Font('Arial',30,[Drawing.FontStyle]::Bold)
  $fontSmall = New-Object Drawing.Font('Arial',20,[Drawing.FontStyle]::Bold)
  $white = [Drawing.Brushes]::White
  $format = New-Object Drawing.StringFormat
  $format.Alignment = [Drawing.StringAlignment]::Center
  $format.LineAlignment = [Drawing.StringAlignment]::Center
  $graphics.DrawString($platform,$fontSmall,$white,(New-Object Drawing.RectangleF(30,32,660,48)),$format)
  $graphics.DrawString($label,$fontBig,$white,(New-Object Drawing.RectangleF(50,108,620,190)),$format)
  $graphics.Dispose(); $bitmap.Save($file,[Drawing.Imaging.ImageFormat]::Png); $bitmap.Dispose()
}

$data = Get-Content $assetPath -Raw | ConvertFrom-Json
$downloaded = 0; $generated = 0
for ($i = 0; $i -lt $data.items.Count; $i++) {
  $game = $data.items[$i]; $repo = $repos[$game.platform]
  $resource = ('cover_{0:d3}' -f ($i + 1)); $destination = Join-Path $coverPath ($resource + '.png')
  $remoteName = [uri]::EscapeDataString($game.label + '.png')
  $uri = 'https://raw.githubusercontent.com/libretro-thumbnails/' + $repo + '/master/Named_Boxarts/' + $remoteName
  try {
    Invoke-WebRequest -Uri $uri -OutFile $destination -Headers @{ 'User-Agent'='FireRetro-Setup' }
    if (([System.IO.File]::ReadAllBytes($destination)[0..7] -join ',') -ne '137,80,78,71,13,10,26,10') { throw 'The downloaded content is not a PNG image' }
    $downloaded++
  } catch {
    New-TitleCard $destination $game.label $game.platform $i
    $generated++
  }
  $game.image = $resource
}
$data | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $assetPath -Encoding utf8
Write-Output "Covers synced: official=$downloaded, personalized title cards=$generated, total=$($data.items.Count)"
