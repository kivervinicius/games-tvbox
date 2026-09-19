param(
    [Parameter(Mandatory=$true)][string]$InputFolder,
    [Parameter(Mandatory=$true)][string]$OutputFolder,
    [string]$SevenZip = 'C:\Program Files\7-Zip\7z.exe',
    [string]$Chdman = 'chdman.exe',
    [string]$EcmTool = '',
    [string]$AudioTool = 'ffmpeg.exe'
)
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $OutputFolder | Out-Null
$workRoot = Join-Path $env:LOCALAPPDATA 'JogosRetro\Importer\ps1-work'
New-Item -ItemType Directory -Force -Path $workRoot | Out-Null
$report = [System.Collections.Generic.List[object]]::new()
foreach ($source in Get-ChildItem -LiteralPath $InputFolder -File | Where-Object { $_.Extension.ToLowerInvariant() -in @('.7z','.zip','.rar','.iso','.cue','.chd') }) {
    $job = Join-Path $workRoot ([guid]::NewGuid().ToString('N')); New-Item -ItemType Directory -Force $job | Out-Null
    try {
        if ($source.Extension.ToLowerInvariant() -in @('.7z','.zip','.rar')) { & $SevenZip x $source.FullName "-o$job" -y | Out-Null }
        else { Copy-Item -LiteralPath $source.FullName -Destination $job }
        foreach ($nested in @(Get-ChildItem -LiteralPath $job -Recurse -File -Filter '*.7z')) { & $SevenZip x $nested.FullName "-o$($nested.DirectoryName)\nested" -y | Out-Null }
        $ecm = Get-ChildItem -LiteralPath $job -Recurse -File -Filter '*.ecm' | Select-Object -First 1
        if ($ecm) {
            if ([string]::IsNullOrWhiteSpace($EcmTool) -or -not (Test-Path -LiteralPath $EcmTool)) { throw 'ECM_TOOL_REQUIRED: instale/repare unecm.exe para processar BIN.ECM.' }
            & $EcmTool $ecm.FullName | Out-Null
            if ($LASTEXITCODE -ne 0) { throw 'unecm.exe falhou.' }
            $decodedBin = Get-ChildItem -LiteralPath $job -Recurse -File -Filter '*.bin' | Select-Object -First 1
            if ($decodedBin) {
                $generatedCue = Join-Path $decodedBin.DirectoryName ($decodedBin.BaseName + '.cue')
                [IO.File]::WriteAllLines($generatedCue, @("FILE `"$($decodedBin.Name)`" BINARY", '  TRACK 01 MODE2/2352', '    INDEX 01 00:00:00'), [Text.Encoding]::ASCII)
            }
        }
        $cue = Get-ChildItem $job -Recurse -File -Filter '*.cue' | Select-Object -First 1
        $iso = Get-ChildItem $job -Recurse -File -Filter '*.iso' | Select-Object -First 1
        $chd = Get-ChildItem $job -Recurse -File -Filter '*.chd' | Select-Object -First 1
        $apes = @(Get-ChildItem $job -Recurse -File -Filter '*.ape' | Sort-Object Name)
        if ($apes.Count -gt 0 -and -not $cue) {
            if (-not (Get-Command $AudioTool -ErrorAction SilentlyContinue) -and -not (Test-Path -LiteralPath $AudioTool)) { throw 'AUDIO_TOOL_REQUIRED: instale/repare ffmpeg.exe para processar faixas APE.' }
            $wavs = foreach ($ape in $apes) { $wav = Join-Path $ape.DirectoryName ($ape.BaseName + '.wav'); & $AudioTool -y -i $ape.FullName -ar 44100 -ac 2 -c:a pcm_s16le $wav 2>$null; if ($LASTEXITCODE -ne 0) { throw "ffmpeg falhou em $($ape.Name)" }; $wav }
            $data = Get-ChildItem $job -Recurse -File -Include '*.bin','*.img' | Select-Object -First 1
            if (-not $data) { throw 'Faixas APE encontradas, mas a faixa de dados não foi localizada.' }
            $cue = Join-Path $job ($source.BaseName + '.cue')
            $cueLines = @("FILE `"$($data.Name)`" BINARY", '  TRACK 01 MODE2/2352', '    INDEX 01 00:00:00')
            $trackNumber = 2
            foreach ($wav in $wavs) { $cueLines += "FILE `"$([IO.Path]::GetFileName($wav))`" WAVE"; $cueLines += ('  TRACK {0:D2} AUDIO' -f $trackNumber); $cueLines += '    INDEX 01 00:00:00'; $trackNumber++ }
            [IO.File]::WriteAllLines($cue, $cueLines, [Text.Encoding]::ASCII)
        }
        if (-not ($cue -or $iso -or $chd)) { throw 'Nenhum CUE, ISO ou CHD encontrado.' }
        $target = Join-Path $OutputFolder ($source.BaseName + '.chd')
        if ($chd) { Copy-Item $chd.FullName $target -Force }
        else { $disc = if ($cue) { $cue } else { $iso }; & $Chdman createcd -i $disc.FullName -o $target -f | Out-Null; if ($LASTEXITCODE -ne 0) { throw 'chdman createcd falhou.' }; & $Chdman verify -i $target | Out-Null; if ($LASTEXITCODE -ne 0) { throw 'chdman verify falhou.' } }
        $report.Add([pscustomobject]@{ source=$source.Name; status='ok'; output=$target })
    } catch { $report.Add([pscustomobject]@{ source=$source.Name; status='pending'; error=$_.Exception.Message }) }
    finally { Remove-Item $job -Recurse -Force -ErrorAction SilentlyContinue }
}
$report | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $OutputFolder 'conversion-report.json') -Encoding UTF8
$report | Format-Table -AutoSize
