$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$module = Join-Path $root 'manager-windows/GitHubCatalog.ps1'
if (-not (Test-Path $module)) { throw 'GitHub catalog bridge is missing.' }
. $module
function Assert($Condition, $Message) { if (-not $Condition) { throw $Message } }
$legal = [pscustomobject]@{ label='Example demo'; platform='NES'; publicSelected=$true; redistributable=$true; category='demo'; license='CC0'; path='/sdcard/roms/private.nes'; token='secret'; image='assets/demo.png'; description='A free demo'; publicDownload='https://example.org/demo'; downloadPublicConfirmed=$true }
$strictLegal = [pscustomobject]@{ label='Minimal demo'; platform='NES'; publicSelected=$true; redistributable=$true; category='demo'; license='CC0' }
Set-StrictMode -Version Latest
$strictExport = Export-PublicCatalog -Catalog @($strictLegal)
Assert (@($strictExport.items).Count -eq 1) 'A legal public item without optional properties must export under StrictMode.'
Set-StrictMode -Off
$reviewRoot=Join-Path ([IO.Path]::GetTempPath()) ('github-catalog-review-'+[guid]::NewGuid().ToString('N'))
$reviewPrior=$env:LOCALAPPDATA
try {
    $env:LOCALAPPDATA=$reviewRoot
    Save-ManagerCatalogReview -Catalog @($strictLegal) | Out-Null
    $reopened=Get-ManagerCatalogReview
    Assert (@($reopened).Count -eq 1) 'A reviewed catalog must reopen from private cache.'
    Assert ($reopened[0].label -eq 'Minimal demo' -and $reopened[0].publicSelected) 'Catalog review flags must round-trip for the next sync.'
} finally {
    $env:LOCALAPPDATA=$reviewPrior
    if (Test-Path -LiteralPath $reviewRoot) { Remove-Item -LiteralPath $reviewRoot -Recurse -Force }
}
$commercial = [pscustomobject]@{ label='Commercial'; publicSelected=$true; redistributable=$false; category='commercial' }
$unselected = [pscustomobject]@{ label='Hidden'; publicSelected=$false; redistributable=$true; category='demo' }
$export = Export-PublicCatalog -Catalog @($legal,$commercial,$unselected)
Assert (@($export.items).Count -eq 1) 'Only explicitly selected legal entries may be exported.'
$json = $export | ConvertTo-Json -Depth 10
Assert ($json -notmatch 'sdcard|private.nes|secret|"token"|"path"') 'Public projection leaked private fields.'
Assert ($export.items[0].image -eq 'assets/demo.png') 'Safe asset image was lost.'
Assert ($export.items[0].publicDownload -eq 'https://example.org/demo') 'Confirmed legal landing page missing.'
$legal.publicSelected = 'true'
Assert (@((Export-PublicCatalog @($legal)).items).Count -eq 0) 'String flags must not imply consent.'
$legal.publicSelected=$true
foreach ($bad in @('file:///private/image.png','../private.png','assets/../private.png','https://example.org/private.png')) {
    $legal.image=$bad
    Assert (-not (Export-PublicCatalog @($legal)).items[0].image) 'Unsafe cover must be removed.'
}
foreach ($bad in @('https://user:pass@example.org/x','https://localhost/x','https://127.0.0.1/x','https://example.org/x?token=secret','https://example.org/game.nes','http://example.org/demo')) {
    $legal.publicDownload=$bad
    Assert (-not (Export-PublicCatalog @($legal)).items[0].publicDownload) 'Unsafe download must be removed.'
}
$legal.description='Private C:\Users\someone\games and 10.0.0.1'
Assert (-not (Export-PublicCatalog @($legal)).items[0].description) 'Private data in free text must be removed.'
foreach ($privateText in @('/var/lib/games/game','Stored under /opt/library/game','/mnt/archive','games/private/game','../library/game','~/.config/game','%2Fvar%2Flib%2Fgames','%252Fvar%252Flib','C%253A%255CUsers','%25252Fopt%25252Flibrary','Folder: \library\game')) {
    $legal.description=$privateText
    Assert (-not (Export-PublicCatalog @($legal)).items[0].description) 'Absolute, relative and encoded local paths must be removed from public free text.'
}
$deeplyEncoded='/var/lib/games'
for ($round=0; $round -lt 12; $round++) { $deeplyEncoded=[Uri]::EscapeDataString($deeplyEncoded) }
Assert (-not (Get-PublicCatalogText $deeplyEncoded)) 'Encoding deeper than the decoding limit must be rejected.'
Assert ((Get-PublicCatalogText 'A demo with 100% original artwork') -eq 'A demo with 100% original artwork') 'Ordinary percent signs must remain valid text.'
$tempRoot=Join-Path ([IO.Path]::GetTempPath()) ('github-catalog-test-'+[guid]::NewGuid().ToString('N'))
$prior=$env:LOCALAPPDATA
try {
    $env:LOCALAPPDATA=$tempRoot
    $token=ConvertTo-SecureString 'test-only-credential' -AsPlainText -Force
    $stored=Save-GitHubCredential -Token $token
    Assert ((Get-Content $stored -Raw) -notmatch 'test-only-credential') 'Credential must be encrypted.'
    $secure=Get-GitHubCredential
    Assert ($secure -is [Security.SecureString]) 'Reading the credential must return a SecureString.'
    $ptr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { Assert ([Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) -eq 'test-only-credential') 'DPAPI round trip failed.' } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    $token | Export-Clixml -LiteralPath $stored
    $nativeSecure=Get-GitHubCredential
    Assert ($nativeSecure -is [Security.SecureString]) 'Native DPAPI SecureString CLIXML must be returned directly.'
    $ptr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($nativeSecure)
    try { Assert ([Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) -eq 'test-only-credential') 'Native SecureString round trip failed.' } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    Save-GitHubCredential -Token $token | Out-Null
    $rejected=$false
    try { Publish-PrivateCatalog -RepoPath $root -Catalog @($legal) } catch { $rejected=$true }
    Assert $rejected 'Publishing into the public product checkout must be rejected.'
    $auditRoot=Join-Path $tempRoot 'audit-fixture'
    foreach ($relative in @('launcher-android/app/src/main/AndroidManifest.xml','launcher-android/app/src/main/java/com/kiver/fireretro/MainActivity.java','manager-windows/FireRetroManager.ps1','examples/catalog.example.json','docs/manual/README.md','catalog-site/index.html','catalog-site/assets/.gitkeep')) {
        $file=Join-Path $auditRoot $relative
        New-Item -ItemType Directory -Path (Split-Path $file -Parent) -Force | Out-Null
        Set-Content -LiteralPath $file -Value ''
    }
    New-Item -ItemType Directory -Path (Join-Path $auditRoot 'scripts') -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $root 'scripts/Test-PublicLayout.ps1') -Destination (Join-Path $auditRoot 'scripts/Test-PublicLayout.ps1')
    Copy-Item -LiteralPath $module -Destination (Join-Path $auditRoot 'manager-windows/GitHubCatalog.ps1')
    $fixtureCatalog=Join-Path $auditRoot 'catalog-site/catalog.public.json'
    Set-Content $fixtureCatalog '{"version":1,"items":[]}'
    & (Join-Path $auditRoot 'scripts/Test-PublicLayout.ps1') | Out-Null
    Set-Content (Join-Path $auditRoot 'catalog-site/game.nes') 'not-a-rom-test-fixture'
    $rejected=$false
    try { & (Join-Path $auditRoot 'scripts/Test-PublicLayout.ps1') | Out-Null } catch { $rejected=$true }
    Assert $rejected 'Pages audit must reject ROM files.'
    Remove-Item -LiteralPath (Join-Path $auditRoot 'catalog-site/game.nes')
    Set-Content $fixtureCatalog '{"version":1,"items":[{"id":"aaaaaaaaaaaaaaaaaaaaaaaa","label":"Demo","license":"CC0","category":"demo","path":"private"}]}'
    $rejected=$false
    try { & (Join-Path $auditRoot 'scripts/Test-PublicLayout.ps1') | Out-Null } catch { $rejected=$true }
    Assert $rejected 'Pages audit must reject private fields in the artifact.'
    # Execute publishing orchestration with only Git and the network boundary substituted.
    $checkout=Join-Path $tempRoot 'checkout'
    New-Item -ItemType Directory -Path $checkout -Force | Out-Null
    $script:Calls=[Collections.Generic.List[object]]::new()
    $script:Dirty=$false
    $script:RemotePrivate=$false
    function Invoke-CatalogGit {
        param([string]$RepoPath,[string[]]$Arguments)
        $script:Calls.Add(@($Arguments))
        switch ($Arguments[0]) {
            'rev-parse' { return $checkout }
            'status' { if ($script:Dirty) { return ' M unrelated.txt' }; return '' }
            'symbolic-ref' { return 'main' }
            'remote' { return 'https://github.com/example/private-catalog.git' }
            'diff' { return 'catalog.private.json' }
        }
        return ''
    }
    function Invoke-RestMethod {
        param($Uri,$Headers,$TimeoutSec,$ErrorAction)
        Assert ($Uri -eq 'https://api.github.com/repos/example/private-catalog') 'Repository verification targets the wrong API resource.'
        return [pscustomobject]@{private=$script:RemotePrivate;full_name='example/private-catalog'}
    }
    $rejected=$false
    try { Publish-PrivateCatalog $checkout @($legal) } catch { $rejected=$true }
    Assert $rejected 'Public repository must be rejected before writing.'
    Assert (-not (Test-Path (Join-Path $checkout 'catalog.private.json'))) 'Unverified checkout was changed.'
    $script:RemotePrivate=$true
    $script:Dirty=$true
    $rejected=$false
    try { Publish-PrivateCatalog $checkout @($legal) } catch { $rejected=$true }
    Assert $rejected 'Dirty checkout must be rejected.'
    $script:Dirty=$false
    $legal | Add-Member -NotePropertyName tags -NotePropertyValue @([pscustomobject]@{token='nested-secret'}) -Force
    $linked=Join-Path $tempRoot 'linked-checkout'
    New-Item -ItemType Junction -Path $linked -Target $checkout | Out-Null
    $rejected=$false
    try { Publish-PrivateCatalog $linked @($legal) } catch { $rejected=$true }
    Assert $rejected 'Linked checkout must be rejected before publishing.'
    [IO.Directory]::Delete($linked)
    $script:Calls.Clear()
    $result=Publish-PrivateCatalog $checkout @($legal)
    Assert $result.Published 'Verified private catalog must publish.'
    $privateJson=Get-Content (Join-Path $checkout 'catalog.private.json') -Raw
    Assert ($privateJson -notmatch 'nested-secret|"token"') 'Credentials in nested metadata must never be serialized.'
    $staged=@($script:Calls | Where-Object { $_[0] -eq 'add' })
    Assert ($staged.Count -eq 1 -and ($staged[0] -join ' ') -eq 'add -- catalog.private.json') 'Publisher must stage only catalog.private.json.'
    $pushes=@($script:Calls | Where-Object { $_[0] -eq 'push' })
    Assert ($pushes.Count -eq 1 -and ($pushes[0] -join ' ') -eq 'push origin HEAD:refs/heads/main') 'Publisher must push only the current branch.'
} finally {
    $env:LOCALAPPDATA=$prior
    if ([IO.Path]::GetFullPath($tempRoot).StartsWith([IO.Path]::GetFullPath([IO.Path]::GetTempPath()))) { Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue }
}
$manager=Get-Content (Join-Path $root 'manager-windows/FireRetroManager.ps1') -Raw
Assert ($manager -match 'Show-GitHubCatalogDialog') 'Manager must expose catalog actions.'
Assert ($manager -match 'Get-ManagerCatalogReview' -and $manager -match 'Get-CatalogForSync') 'The next Manager sync must consume the saved private review.'
$site=Get-Content (Join-Path $root 'catalog-site/index.html') -Raw
Assert ($site -match 'catalog.public.json' -and $site -match 'textContent' -and $site -notmatch 'innerHTML') 'Gallery must fetch safely and avoid HTML injection.'
$workflow=Get-Content (Join-Path $root '.github/workflows/pages.yml') -Raw
Assert ($workflow -match 'path: catalog-site' -and $workflow -match 'workflow_dispatch') 'Pages must deploy only the static folder explicitly.'
& (Join-Path $root 'scripts/Test-PagesWorkflow.ps1')
Write-Output 'PASS: GitHub catalog privacy, credential storage and Pages contract'
