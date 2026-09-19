$root = Split-Path $PSScriptRoot -Parent
$required = @(
    'launcher-android/app/src/main/AndroidManifest.xml',
    'launcher-android/app/src/main/java/com/kiver/fireretro/MainActivity.java',
    'manager-windows/FireRetroManager.ps1',
    'examples/catalog.example.json',
    'docs/manual/README.md'
)
foreach ($relative in $required) {
    if (-not (Test-Path -LiteralPath (Join-Path $root $relative))) { throw "Missing public file: $relative" }
}
$forbiddenNames = @('sources','device-backups','backups','ps1-chd','crash-extracted','bomberman.chd','ctr.chd','msx.chd','keystore','FireRetroLauncher-debug.apk')
$tracked = & git -C $root ls-files
if ($LASTEXITCODE -ne 0) { throw 'Unable to enumerate tracked public files.' }
$files = foreach ($relativePath in $tracked) {
    $candidate = Join-Path $root $relativePath
    if (Test-Path -LiteralPath $candidate -PathType Leaf) { Get-Item -LiteralPath $candidate }
}
foreach ($file in $files) {
    $relative = $file.FullName.Substring($root.Length + 1)
    foreach ($name in $forbiddenNames) {
        if ($relative -match [regex]::Escape($name)) { throw "Forbidden private or binary content: $relative" }
    }
    if ($relative -match 'C:\\Users|192\.168\.15|kiver\.teixeira') { throw "Personal path or address found: $relative" }
}
Write-Output 'PASS: public layout'

# Pages artifacts are audited by content as well as file name, before deployment.
$siteRoot = Join-Path $root 'catalog-site'
foreach ($relative in @('index.html','catalog.public.json','assets/.gitkeep')) {
    if (-not (Test-Path -LiteralPath (Join-Path $siteRoot $relative))) { throw 'Missing catalog-site file.' }
}
. (Join-Path $root 'manager-windows/GitHubCatalog.ps1')
foreach ($file in Get-ChildItem -LiteralPath $siteRoot -Recurse -Force -File) {
    if ($file.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Pages cannot contain file links.' }
    $relative=$file.FullName.Substring($siteRoot.Length+1).Replace('\','/')
    if ($relative -notin @('index.html','catalog.public.json','README.md','assets/.gitkeep') -and $relative -cnotmatch '^assets/[a-zA-Z0-9][a-zA-Z0-9_-]*\.(png|jpg|jpeg|webp)$') { throw 'Pages contains an unapproved file.' }
    if ($file.Extension -in @('.png','.jpg','.jpeg','.webp')) {
        $bytes=[IO.File]::ReadAllBytes($file.FullName)
        $signature=[BitConverter]::ToString($bytes[0..([Math]::Min(11,$bytes.Length-1))])
        if (($file.Extension -eq '.png' -and $signature -notlike '89-50-4E-47-0D-0A-1A-0A*') -or
            ($file.Extension -in @('.jpg','.jpeg') -and $signature -notlike 'FF-D8-FF*') -or
            ($file.Extension -eq '.webp' -and $signature -notmatch '^52-49-46-46-.{11}-57-45-42-50$')) { throw 'Pages image does not match its file type.' }
    }
}
$public=Get-Content -LiteralPath (Join-Path $siteRoot 'catalog.public.json') -Raw | ConvertFrom-Json
if ($public.version -ne 1 -or $null -eq $public.items -or $public.items -isnot [array]) { throw 'Invalid public catalog envelope.' }
foreach ($property in $public.PSObject.Properties.Name) { if ($property -notin @('version','items')) { throw 'Unapproved public catalog field.' } }
foreach ($item in $public.items) {
    foreach ($property in $item.PSObject.Properties.Name) {
        if ($property -notin @('id','label','platform','description','license','category','tags','year','image','publicDownload')) { throw 'Private or unapproved field in Pages catalog.' }
    }
    if ($item.id -cnotmatch '^[a-f0-9]{24}$' -or -not $item.label -or -not $item.license -or $item.category -notin @('homebrew','demo','public-domain')) { throw 'Pages item lacks reviewed legal metadata.' }
    foreach ($field in @('label','platform','description','license','category')) {
        if ([string]$item.$field -ne (Get-PublicCatalogText $item.$field)) { throw 'Private data in public catalog text.' }
    }
    foreach ($tag in @($item.tags)) { if ([string]$tag -ne (Get-PublicCatalogText $tag 80)) { throw 'Private data in public tags.' } }
    if ($item.image) {
        if ($item.image -cnotmatch '^assets/[a-zA-Z0-9][a-zA-Z0-9_-]*\.(png|jpg|jpeg|webp)$' -or -not (Test-Path -LiteralPath (Join-Path $siteRoot $item.image))) { throw 'Public cover must be an existing approved Pages asset.' }
    }
    if ($item.publicDownload -and -not (Test-PublicLandingUrl $item.publicDownload)) { throw 'Unsafe public download page.' }
}
Write-Output 'PASS: catalog-site content and legal projection'
