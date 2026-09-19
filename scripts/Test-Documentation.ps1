$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$missing = @()
$tracked = & git -C $root ls-files '*.md'
if ($LASTEXITCODE -ne 0) { throw 'Unable to enumerate tracked documentation.' }
foreach ($relativePath in $tracked) {
    $file = Get-Item -LiteralPath (Join-Path $root $relativePath)
    # Internal implementation/review packages can contain diff snippets, not documentation links.
    if ($file.FullName.Substring($root.Length + 1) -match '^\.superpowers[\\/]') { continue }
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
