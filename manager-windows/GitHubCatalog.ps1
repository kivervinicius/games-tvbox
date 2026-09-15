# Authentication stays in the Windows user profile; Git push uses the configured Git credential helper.
function Save-GitHubCredential {
    param([Parameter(Mandatory)][Security.SecureString]$Token)
    if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) { throw 'O armazenamento protegido exige Windows.' }
    if ($Token.Length -eq 0) { throw 'Informe uma credencial.' }
    $directory = Join-Path $env:LOCALAPPDATA 'FireRetroManager'
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $destination = Join-Path $directory 'github-token.xml'
    # Without -Key, ConvertFrom-SecureString uses DPAPI CurrentUser on Windows.
    $encrypted = $Token | ConvertFrom-SecureString -ErrorAction Stop
    $encrypted | Export-Clixml -LiteralPath $destination -Force -ErrorAction Stop
    return $destination
}

function Get-GitHubCredential {
    $path = Join-Path $env:LOCALAPPDATA 'FireRetroManager/github-token.xml'
    try { return (Import-Clixml -LiteralPath $path -ErrorAction Stop | ConvertTo-SecureString -ErrorAction Stop) }
    catch { throw 'Salve a credencial GitHub neste usuário do Windows antes de publicar.' }
}

function Get-PublicCatalogText {
    param($Value, [int]$MaxLength = 2000)
    $text = ([string]$Value).Trim()
    if ($text.Length -gt $MaxLength -or $text -match '(?i)(?:[a-z]:[\\/]|\\\\|/(?:sdcard|data|home|users|storage)/|https?://|\b(?:\d{1,3}\.){3}\d{1,3}\b|gh[pousr]_|github_pat_|(?:token|password|secret|authorization|api[_ -]?key)\s*[:=])') { return '' }
    return $text
}

function Test-PublicLandingUrl {
    param([string]$Url)
    $uri = $null
    if (-not [Uri]::TryCreate($Url, [UriKind]::Absolute, [ref]$uri)) { return $false }
    return ($uri.Scheme -eq 'https' -and $uri.IsDefaultPort -and -not $uri.UserInfo -and -not $uri.Query -and -not $uri.Fragment -and
        $uri.HostNameType -eq [UriHostNameType]::Dns -and $uri.Host -match '\.' -and $uri.Host -notmatch '(?i)(?:\.local|\.internal|\.localhost|\.lan|\.home|\.test|\.invalid)$' -and
        $uri.AbsolutePath -notmatch '(?i)(?:\.(?:nes|nez|sfc|smc|fig|md|gen|sms|gba|gb|gbc|iso|chd|cue|bin|zip|7z|sav|srm)$|/(?:users|home|sdcard|storage)/|gh[pousr]_|github_pat_)')
}

function Export-PublicCatalog {
    param([AllowEmptyCollection()][array]$Catalog = @(), [string]$DestinationPath)
    $items = @()
    foreach ($game in $Catalog) {
        if ($game.publicSelected -isnot [bool] -or $game.publicSelected -ne $true -or $game.redistributable -isnot [bool] -or $game.redistributable -ne $true) { continue }
        if ($game.category -notin @('homebrew','demo','public-domain')) { continue }
        $label = Get-PublicCatalogText $game.label 200
        $license = Get-PublicCatalogText $game.license 200
        if (-not $label -or -not $license) { continue }
        $platform = Get-PublicCatalogText $game.platform 100
        $hash = [Security.Cryptography.SHA256]::Create()
        try { $id = ([BitConverter]::ToString($hash.ComputeHash([Text.Encoding]::UTF8.GetBytes($platform + ':' + $label)))).Replace('-','').ToLowerInvariant().Substring(0,24) } finally { $hash.Dispose() }
        $item = [ordered]@{ id=$id; label=$label; platform=$platform; description=(Get-PublicCatalogText $game.description); license=$license; category=[string]$game.category; tags=@() }
        foreach ($tag in @($game.tags)) { $clean = Get-PublicCatalogText $tag 80; if ($clean) { $item.tags += $clean } }
        $year = 0
        if ([int]::TryParse([string]$game.year,[ref]$year) -and $year -ge 1950 -and $year -le 2200) { $item.year=$year }
        if ([string]$game.image -cmatch '^assets/[a-zA-Z0-9][a-zA-Z0-9_-]*\.(png|jpg|jpeg|webp)$') { $item.image=[string]$game.image }
        if ($game.downloadPublicConfirmed -is [bool] -and $game.downloadPublicConfirmed -eq $true -and (Test-PublicLandingUrl ([string]$game.publicDownload))) { $item.publicDownload=[string]$game.publicDownload }
        $items += [pscustomobject]$item
    }
    $payload = [pscustomobject]@{ version=1; items=@($items) }
    if ($DestinationPath) {
        if ([IO.Path]::GetFileName($DestinationPath) -ne 'catalog.public.json') { throw 'Use o nome catalog.public.json para a projeção pública.' }
        $payload | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $DestinationPath -Encoding utf8 -ErrorAction Stop
    }
    return $payload
}

function Invoke-CatalogGit {
    param([string]$RepoPath, [string[]]$Arguments)
    $output = & git -C $RepoPath @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'A operação Git falhou. Verifique o checkout e a autenticação no Git.' }
    return ($output -join "`n").Trim()
}

function Assert-PrivateGitHubRemote {
    param([string]$Remote)
    $repoName = ''
    if ($Remote -match '^https://github\.com/([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+?)(?:\.git)?$') { $repoName=$Matches[1] }
    elseif ($Remote -match '^git@github\.com:([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+?)(?:\.git)?$') { $repoName=$Matches[1] }
    else { throw 'Configure origin com um endereço GitHub sem credenciais na URL.' }
    $credential = Get-GitHubCredential
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($credential)
    try {
        $headers = @{ Authorization=('Bearer ' + [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)); Accept='application/vnd.github+json' }
        try { $repo = Invoke-RestMethod -Uri ('https://api.github.com/repos/' + $repoName) -Headers $headers -TimeoutSec 20 -ErrorAction Stop }
        catch { throw 'Não foi possível confirmar que o repositório GitHub é privado.' }
        if ($repo.private -ne $true -or $repo.full_name -ine $repoName) { throw 'A publicação exige um repositório GitHub privado verificado.' }
    } finally {
        if ($headers) { $headers.Clear() }
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
        $credential.Dispose()
    }
}

function Publish-PrivateCatalog {
    param([Parameter(Mandatory)][string]$RepoPath, [AllowEmptyCollection()][array]$Catalog = @())
    $resolved = (Resolve-Path -LiteralPath $RepoPath -ErrorAction Stop).Path.TrimEnd('\','/')
    $publicRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')).TrimEnd('\','/')
    if ($resolved.Equals($publicRoot,[StringComparison]::OrdinalIgnoreCase) -or $resolved.StartsWith($publicRoot + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Escolha um checkout privado fora do projeto público.' }
    $top = Invoke-CatalogGit $resolved @('rev-parse','--show-toplevel')
    if (-not [IO.Path]::GetFullPath($top).TrimEnd('\','/').Equals($resolved,[StringComparison]::OrdinalIgnoreCase)) { throw 'Escolha a raiz do checkout privado.' }
    if (Invoke-CatalogGit $resolved @('status','--porcelain')) { throw 'O checkout deve estar limpo antes da publicação.' }
    $branch = Invoke-CatalogGit $resolved @('symbolic-ref','--short','HEAD')
    $remote = Invoke-CatalogGit $resolved @('remote','get-url','--all','origin')
    $pushRemote = Invoke-CatalogGit $resolved @('remote','get-url','--push','--all','origin')
    if ($pushRemote -ne $remote) { throw 'Os endereços de leitura e publicação de origin devem coincidir.' }
    Assert-PrivateGitHubRemote $remote
    $destination = Join-Path $resolved 'catalog.private.json'
    if ((Test-Path $destination) -and ((Get-Item -LiteralPath $destination -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'O catálogo privado não pode ser um link.' }
    # An allowlist prevents credentials and arbitrary nested objects from entering even the private catalog.
    $privateItems = @($Catalog | ForEach-Object {
        $item=[ordered]@{}
        foreach ($name in @('id','label','platform','path','core_path','image','description','year','tags','publicSelected','redistributable','category','license','publicDownload','downloadPublicConfirmed')) {
            $value=$_.PSObject.Properties[$name]
            if ($null -eq $value) { continue }
            if ($name -eq 'tags') {
                $item[$name]=@($value.Value | Where-Object { $_ -is [string] -and $_ -notmatch '(?i)gh[pousr]_|github_pat_|(?:token|password|secret|authorization|api[_ -]?key)\s*[:=]' })
            } elseif ($value.Value -is [string] -or $value.Value -is [bool] -or $value.Value -is [int] -or $value.Value -is [long]) {
                if ([string]$value.Value -notmatch '(?i)gh[pousr]_|github_pat_|(?:token|password|secret|authorization|api[_ -]?key)\s*[:=]|https?://[^/\s]+@') { $item[$name]=$value.Value }
            }
        }
        [pscustomobject]$item
    })
    [pscustomobject]@{version=1;items=$privateItems} | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $destination -Encoding utf8 -ErrorAction Stop
    Invoke-CatalogGit $resolved @('add','--','catalog.private.json') | Out-Null
    $changed = Invoke-CatalogGit $resolved @('diff','--cached','--name-only')
    if ($changed -and $changed -ne 'catalog.private.json') { throw 'Há outros arquivos preparados para commit; publicação interrompida.' }
    if ($changed) { Invoke-CatalogGit $resolved @('commit','-m','Update private game catalog','--','catalog.private.json') | Out-Null }
    Invoke-CatalogGit $resolved @('push','origin',('HEAD:refs/heads/' + $branch)) | Out-Null
    return [pscustomobject]@{ Published=$true; Count=$privateItems.Count }
}

function Show-GitHubCatalogDialog {
    param([array]$Catalog = @(), [Windows.Forms.IWin32Window]$Owner)
    $dialog = [Windows.Forms.Form]::new()
    $dialog.Text = 'Catálogo GitHub — revisão e publicação'
    $dialog.Size = [Drawing.Size]::new(1060,620)
    $dialog.StartPosition = 'CenterParent'
    $hint = [Windows.Forms.Label]::new(); $hint.Dock='Top'; $hint.Height=62
    $hint.Text = 'Revise os itens e marque Publicar + Redistribuível apenas para homebrew, demo ou public-domain com licença preenchida. Capas: assets/nome.png. Links: página pública HTTPS confirmada. A exportação grava somente JSON; copie as capas autorizadas para assets.'
    $dialog.Controls.Add($hint)
    $table = [Data.DataTable]::new()
    foreach ($name in @('publicSelected','redistributable','downloadPublicConfirmed')) { [void]$table.Columns.Add($name,[bool]) }
    foreach ($name in @('label','platform','category','license','image','publicDownload')) { [void]$table.Columns.Add($name,[string]) }
    foreach ($game in $Catalog) {
        $row=$table.NewRow()
        foreach ($column in $table.Columns) {
            $value=$game.PSObject.Properties[$column.ColumnName]
            if ($column.DataType -eq [bool]) { $row[$column.ColumnName]=($null -ne $value -and $value.Value -is [bool] -and $value.Value -eq $true) }
            elseif ($value) { $row[$column.ColumnName]=[string]$value.Value }
        }
        [void]$table.Rows.Add($row)
    }
    $grid=[Windows.Forms.DataGridView]::new(); $grid.Dock='Fill'; $grid.DataSource=$table; $grid.AllowUserToAddRows=$false; $grid.AllowUserToDeleteRows=$false; $grid.AutoSizeColumnsMode='DisplayedCells'; $grid.AllowUserToOrderColumns=$false
    $dialog.Controls.Add($grid); $grid.BringToFront()
    $dialog.Add_Shown({
        $headers=@{ publicSelected='Publicar'; redistributable='Redistribuição autorizada'; downloadPublicConfirmed='Link público confirmado'; label='Jogo'; platform='Plataforma'; category='Categoria'; license='Licença'; image='Capa no site'; publicDownload='Página de download' }
        foreach ($column in $grid.Columns) { $column.SortMode='NotSortable'; $column.HeaderText=$headers[$column.Name] }
    })
    $footer=[Windows.Forms.FlowLayoutPanel]::new(); $footer.Dock='Bottom'; $footer.Height=112; $dialog.Controls.Add($footer)
    $tokenLabel=[Windows.Forms.Label]::new(); $tokenLabel.Text='Credencial GitHub'; $tokenLabel.AutoSize=$true; $footer.Controls.Add($tokenLabel)
    $tokenInput=[Windows.Forms.TextBox]::new(); $tokenInput.UseSystemPasswordChar=$true; $tokenInput.Width=220; $footer.Controls.Add($tokenInput)
    $saveToken=[Windows.Forms.Button]::new(); $saveToken.Text='Salvar credencial'; $saveToken.Width=140; $footer.Controls.Add($saveToken)
    $exportButton=[Windows.Forms.Button]::new(); $exportButton.Text='Exportar público'; $exportButton.Width=140; $footer.Controls.Add($exportButton)
    $publishButton=[Windows.Forms.Button]::new(); $publishButton.Text='Publicar privado'; $publishButton.Width=140; $footer.Controls.Add($publishButton)
    $message=[Windows.Forms.Label]::new(); $message.Width=990; $message.Height=50; $footer.Controls.Add($message)
    $collect = {
        $grid.EndEdit() | Out-Null
        $updated=@()
        for ($i=0; $i -lt $Catalog.Count; $i++) {
            $copy=[ordered]@{}
            foreach ($property in $Catalog[$i].PSObject.Properties) { $copy[$property.Name]=$property.Value }
            foreach ($column in $table.Columns) { $value=$table.Rows[$i][$column.ColumnName]; $copy[$column.ColumnName]=if ($value -is [DBNull]) { '' } else { $value } }
            $updated += [pscustomobject]$copy
        }
        return $updated
    }
    $saveToken.Add_Click({
        try { $secure=ConvertTo-SecureString $tokenInput.Text -AsPlainText -Force; Save-GitHubCredential $secure | Out-Null; $message.Text='Credencial protegida neste usuário do Windows. Configure também o acesso Git ao checkout.' }
        catch { $message.Text='Não foi possível salvar a credencial protegida.' }
        finally { $tokenInput.Clear(); if ($secure) { $secure.Dispose() } }
    })
    $exportButton.Add_Click({
        try {
            $save=[Windows.Forms.SaveFileDialog]::new(); $save.FileName='catalog.public.json'; $save.Filter='Catálogo público|catalog.public.json'
            if ($save.ShowDialog($dialog) -ne [Windows.Forms.DialogResult]::OK) { return }
            $result=Export-PublicCatalog -Catalog @(& $collect) -DestinationPath $save.FileName
            $message.Text="Catálogo público exportado: $($result.items.Count) itens. Revise o JSON e as capas antes de publicar o site."
        } catch { $message.Text='Falha ao exportar. Verifique o destino e os campos revisados.' }
    })
    $publishButton.Add_Click({
        try {
            $folder=[Windows.Forms.FolderBrowserDialog]::new(); $folder.Description='Escolha a raiz do checkout privado externo ao projeto público'
            if ($folder.ShowDialog($dialog) -ne [Windows.Forms.DialogResult]::OK) { return }
            if ([Windows.Forms.MessageBox]::Show($dialog,'Publicar catalog.private.json no origin do checkout selecionado? O Git criará um commit e enviará a branch atual.','Publicar catálogo privado','YesNo','Question') -ne [Windows.Forms.DialogResult]::Yes) { return }
            $result=Publish-PrivateCatalog -RepoPath $folder.SelectedPath -Catalog @(& $collect)
            $message.Text="Catálogo privado publicado: $($result.Count) itens."
        } catch { $message.Text='Não foi possível publicar. Confirme que o checkout está limpo, origin é privado e a credencial tem acesso. Uma falha de push pode deixar um commit local pendente.' }
    })
    try { [void]$dialog.ShowDialog($Owner) } finally { $tokenInput.Clear(); $dialog.Dispose() }
}
