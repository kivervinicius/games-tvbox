$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$source=Get-Content (Join-Path $root 'app/src/main/java/com/kiver/fireretro/RemoteLibrarySync.java') -Raw
foreach($required in @('SHA-256','.part','onProgress','downloadUrl','downloadedAt','freeSpace','HttpURLConnection','Authorization')) {
    if($source -notmatch $required){ throw "Remote sync contract missing: $required" }
}
if($source -match 'Log\.[devi].*token'){ throw 'Remote sync must not log tokens.' }
Write-Output 'PASS: remote sync download, integrity and progress contract'
