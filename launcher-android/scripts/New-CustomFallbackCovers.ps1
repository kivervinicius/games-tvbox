param(
    [string]$Sheet = (Join-Path $PSScriptRoot '..\artwork\custom-cover-sheet.png'),
    [string]$Destination = (Join-Path $PSScriptRoot '..\app\src\main\res\drawable-nodpi')
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path $Sheet)) { throw "Cover artwork sheet is missing: $Sheet" }
New-Item -ItemType Directory -Force -Path $Destination | Out-Null
Add-Type -AssemblyName System.Drawing

$coverNames = @(
    'cover_custom_alex_kidd', 'cover_custom_anguna', 'cover_custom_cave_story', 'cover_custom_cheril',
    'cover_custom_furry_rpg', 'cover_custom_great_circus', 'cover_custom_magical_quest', 'cover_custom_mario_luigi',
    'cover_custom_metal_slug_advance', 'cover_custom_shinobi', 'cover_custom_sonic_knuckles', 'cover_custom_streets_rage',
    'cover_custom_super_ghouls', 'cover_custom_vigilante'
)

$source = [System.Drawing.Image]::FromFile((Resolve-Path $Sheet))
try {
    $tileWidth = [int]($source.Width / 4)
    $tileHeight = [int]($source.Height / 4)
    for ($index = 0; $index -lt $coverNames.Count; $index++) {
        $tile = New-Object System.Drawing.Bitmap $tileWidth, $tileHeight
        $graphics = [System.Drawing.Graphics]::FromImage($tile)
        try {
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $sourceX = ($index % 4) * $tileWidth
            $sourceY = [math]::Floor($index / 4) * $tileHeight
            $graphics.DrawImage($source, (New-Object System.Drawing.Rectangle 0, 0, $tileWidth, $tileHeight), (New-Object System.Drawing.Rectangle $sourceX, $sourceY, $tileWidth, $tileHeight), [System.Drawing.GraphicsUnit]::Pixel)
            $tile.Save((Join-Path $Destination ($coverNames[$index] + '.png')), [System.Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $graphics.Dispose()
            $tile.Dispose()
        }
    }
} finally {
    $source.Dispose()
}

Write-Output "Created $($coverNames.Count) custom cover cards."
