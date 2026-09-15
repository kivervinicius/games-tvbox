$ErrorActionPreference='Stop'
$source=Get-Content (Join-Path (Split-Path $PSScriptRoot -Parent) 'app/src/main/java/com/kiver/fireretro/RemoteLibrarySettings.java') -Raw
foreach($required in @('AndroidKeyStore','AES/GCM/NoPadding','manifest_url','read_token','enabled')){if($source -notmatch [regex]::Escape($required)){throw "Remote settings contract missing: $required"}}
if($source -match 'Log\.[devi]'){throw 'Remote settings must not log credentials.'}
Write-Output 'PASS: per-device encrypted remote settings contract'
