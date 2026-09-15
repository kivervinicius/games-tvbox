$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$missing = @()
foreach ($file in Get-ChildItem -LiteralPath $root -Recurse -File -Filter *.md) {
    $text = Get-Content -LiteralPath $file.FullName -Raw
    foreach ($match in [regex]::Matches($text, '\]\(([^)]+)\)')) {
        $link = $match.Groups[1].Value.Trim().Trim('<','>')
        if ($link -match '^(https?|mailto):' -or $link.StartsWith('#')) { continue }
        $path = $link.Split('#')[0].Split('?')[0]
        if ([string]::IsNullOrWhiteSpace($path)) { continue }
        $target = Join-Path $file.DirectoryName $path
        if (-not (Test-Path -LiteralPath $target)) { $missing += "$($file.FullName) -> $link" }
    }
}
if ($missing.Count -gt 0) { $missing; throw 'Documentation links are broken' }
foreach ($required in @('docs/index.md','docs/quick-start.md','docs/internal/FEATURE_INVENTORY.md','docs/internal/DOCUMENTATION_EVIDENCE.md','DOCUMENTATION_COMPLETE.md')) {
    if (-not (Test-Path -LiteralPath (Join-Path $root $required))) { throw "Documentation file missing: $required" }
}
Write-Output 'PASS: documentation links and required pages'
