$ErrorActionPreference='Stop'
$module=Join-Path (Split-Path $PSScriptRoot -Parent) 'GitHubCatalog.ps1'
. $module
$root=Join-Path ([IO.Path]::GetTempPath()) ('remote-manifest-'+[guid]::NewGuid().ToString('N')); New-Item -ItemType Directory -Path (Join-Path $root 'roms/nes') -Force | Out-Null
$rom=Join-Path $root 'roms/nes/Test.nes'; Set-Content -LiteralPath $rom -Value 'fixture' -NoNewline
$catalog=@([pscustomobject]@{ id='test'; label='Test'; platform='NES'; path=$rom; core_path='core'; image='cover.png'; FullPath=$rom; RelativePath='nes/Test.nes' })
$manifest=New-RemoteLibraryManifest -Catalog $catalog -AssetRoot $root -ReleaseTag 'v1'
if($manifest.version -ne 1 -or $manifest.items.Count -ne 1){throw 'Manifest envelope is invalid.'}
$item=$manifest.items[0]
foreach($name in @('id','label','platform','path','size','sha256','assetName','releaseTag','downloadUrl')){if(-not $item.PSObject.Properties[$name]){throw "Missing manifest field: $name"}}
if($item.sha256 -notmatch '^[a-f0-9]{64}$'){throw 'Manifest hash is not SHA-256.'}
if($item.downloadUrl -notmatch '^https://github\.com/[^/]+/[^/]+/releases/download/v1/') {throw 'Manifest download URL is not a release URL.'}
$out=Join-Path $root 'library.manifest.json'; Export-RemoteLibraryManifest -DestinationPath $out -AssetRoot $root -Catalog $catalog -ReleaseTag 'v1' | Out-Null
if(-not (Test-Path $out)){throw 'Manifest export did not create the requested file.'}
Remove-Item -LiteralPath $root -Recurse -Force
Write-Output 'PASS: private remote library manifest contract'
