Set-StrictMode -Version 2.0

function Get-MetadataCacheRoot {
    $localAppData = $env:LOCALAPPDATA
    if ([string]::IsNullOrWhiteSpace($localAppData)) { $localAppData = [IO.Path]::GetTempPath() }
    return Join-Path $localAppData 'FireRetroManager'
}

function Get-MetadataValue {
    param($Object, [string[]]$Names)
    if ($null -eq $Object) { return $null }
    foreach ($name in $Names) {
        $property = $Object.PSObject.Properties[$name]
        if ($null -ne $property -and $null -ne $property.Value -and -not [string]::IsNullOrWhiteSpace([string]$property.Value)) {
            return $property.Value
        }
    }
    return $null
}

function ConvertTo-MetadataTags {
    param($Tags)
    if ($null -eq $Tags) { return @() }
    if ($Tags -is [string]) { return @($Tags -split ',' | ForEach-Object { $_.Trim() } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) }
    return @($Tags | ForEach-Object { ([string]$_).Trim() } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function Get-GameMetadataFallback {
    param([Parameter(Mandatory)]$Game)
    $path = [string](Get-MetadataValue -Object $Game -Names @('path','Path','RelativePath'))
    $title = [string](Get-MetadataValue -Object $Game -Names @('label','title','DisplayName','Name'))
    if ([string]::IsNullOrWhiteSpace($title) -and -not [string]::IsNullOrWhiteSpace($path)) { $title = [IO.Path]::GetFileNameWithoutExtension($path) }
    return [pscustomobject]@{
        label = $title
        title = $title
        description = [string](Get-MetadataValue -Object $Game -Names @('description','Description'))
        year = Get-MetadataValue -Object $Game -Names @('year','Year','releaseYear')
        tags = @(ConvertTo-MetadataTags (Get-MetadataValue -Object $Game -Names @('tags','Tags','genres')))
        image = [string](Get-MetadataValue -Object $Game -Names @('image','Image','cover','coverUrl','cover_url'))
        coverUrl = [string](Get-MetadataValue -Object $Game -Names @('coverUrl','cover_url','image','Image'))
    }
}

function ConvertTo-NormalizedGameMetadata {
    param([Parameter(Mandatory)]$Response, [Parameter(Mandatory)]$Fallback)
    $data = Get-MetadataValue -Object $Response -Names @('data','metadata','game','result')
    if ($null -eq $data) { $data = $Response }
    $title = Get-MetadataValue -Object $data -Names @('title','name','label')
    if ([string]::IsNullOrWhiteSpace([string]$title)) { $title = $Fallback.label }
    $coverUrl = Get-MetadataValue -Object $data -Names @('coverUrl','cover_url','cover','image','imageUrl','image_url')
    if ([string]::IsNullOrWhiteSpace([string]$coverUrl)) { $coverUrl = $Fallback.coverUrl }
    $description = Get-MetadataValue -Object $data -Names @('description','summary','overview')
    if ($null -eq $description) { $description = $Fallback.description }
    $year = Get-MetadataValue -Object $data -Names @('year','releaseYear','release_year')
    if ($null -eq $year) { $year = $Fallback.year }
    # Wrap function output so an empty tag collection remains an empty array under StrictMode.
    $tags = @(ConvertTo-MetadataTags (Get-MetadataValue -Object $data -Names @('tags','genres','genre')))
    if ($tags.Count -eq 0) { $tags = $Fallback.tags }
    return [pscustomobject]@{
        label = [string]$title
        title = [string]$title
        description = [string]$description
        year = $year
        tags = @($tags)
        image = [string]$coverUrl
        coverUrl = [string]$coverUrl
    }
}

function Get-GameMetadata {
    param([Parameter(Mandatory)]$Game, $ProviderConfig)
    $fallback = Get-GameMetadataFallback -Game $Game
    if ($null -eq $ProviderConfig) { return $fallback }
    $baseUrl = Get-MetadataValue -Object $ProviderConfig -Names @('baseUrl','BaseUrl')
    if ([string]::IsNullOrWhiteSpace([string]$baseUrl)) { return $fallback }
    try {
        $timeout = Get-MetadataValue -Object $ProviderConfig -Names @('timeoutSeconds','TimeoutSeconds','timeout')
        if ($null -eq $timeout) { $timeout = 8 }
        $timeout = [Math]::Max(1, [Math]::Min(30, [int]$timeout))
        $separator = if ([string]$baseUrl -match '\?') { '&' } else { '?' }
        $requestUri = ([string]$baseUrl).TrimEnd('/') + $separator + 'q=' + [Uri]::EscapeDataString($fallback.title)
        $headers = @{}
        $apiKey = Get-MetadataValue -Object $ProviderConfig -Names @('apiKey','ApiKey','key')
        if (-not [string]::IsNullOrWhiteSpace([string]$apiKey)) { $headers['Authorization'] = 'Bearer ' + [string]$apiKey }
        $response = Invoke-WebRequest -Uri $requestUri -Method Get -Headers $headers -TimeoutSec $timeout -UseBasicParsing -ErrorAction Stop
        $body = $response.Content | ConvertFrom-Json -ErrorAction Stop
        return ConvertTo-NormalizedGameMetadata -Response $body -Fallback $fallback
    } catch {
        return $fallback
    }
}

function Save-GameMetadataCache {
    param([Parameter(Mandatory)][array]$Items)
    $cacheRoot = Get-MetadataCacheRoot
    New-Item -ItemType Directory -Path $cacheRoot -Force | Out-Null
    $cachePath = Join-Path $cacheRoot 'metadata.json'
    [ordered]@{ version = 1; updatedAt = (Get-Date).ToUniversalTime().ToString('o'); items = @($Items) } |
        ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $cachePath -Encoding utf8
    return $cachePath
}

function Download-CoverToCache {
    param([Parameter(Mandatory)][string]$Url, [Parameter(Mandatory)][string]$GameId)
    try {
        $uri = [Uri]$Url
        if ($uri.Scheme -notin @('http','https')) { return $null }
        $safeId = ($GameId -replace '[^\p{L}\p{Nd}._-]', '_').Trim('_')
        if ([string]::IsNullOrWhiteSpace($safeId)) { return $null }
        $extension = [IO.Path]::GetExtension($uri.AbsolutePath).ToLowerInvariant()
        if ($extension -notin @('.png','.jpg','.jpeg','.webp')) { $extension = '.jpg' }
        $coverRoot = Join-Path (Get-MetadataCacheRoot) 'covers'
        New-Item -ItemType Directory -Path $coverRoot -Force | Out-Null
        $destination = Join-Path $coverRoot ($safeId + $extension)
        Invoke-WebRequest -Uri $uri.AbsoluteUri -OutFile $destination -TimeoutSec 15 -UseBasicParsing -ErrorAction Stop
        return $destination
    } catch {
        return $null
    }
}
