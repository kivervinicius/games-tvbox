$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$workflow = Get-Content -LiteralPath (Join-Path $root '.github/workflows/pages.yml') -Raw
$events = [regex]::Match($workflow, '(?ms)^on:\s*\r?\n(?<events>.*?)(?=^\S|\z)').Groups['events'].Value
if ($events -notmatch '(?m)^  push:\s*$' -or $events -notmatch '(?m)^    branches: *\[main\][ \t]*\r?$') { throw 'Pages must publish automatically on pushes to main.' }
if ($events -notmatch '(?m)^  workflow_dispatch:\s*$') { throw 'Pages must retain manual publication.' }
if ($workflow -notmatch '(?m)^          path: catalog-site\s*$') { throw 'Pages must publish only the static catalog folder.' }
Write-Output 'PASS: Pages automatic main push, manual trigger and catalog-only artifact'
