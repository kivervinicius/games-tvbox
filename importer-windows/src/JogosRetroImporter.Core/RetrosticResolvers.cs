using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;

namespace JogosRetroImporter.Core;

public interface IRetrosticBrowserBridge
{
    Task<DownloadDescriptor> ResolveWithBrowserAsync(string gameUrl, CancellationToken cancellationToken = default);
}

public sealed class RetrosticApiResolver
{
    private readonly HttpClient http;
    private readonly Uri baseUri;

    public RetrosticApiResolver(Uri? baseUri = null, HttpClient? http = null)
    {
        this.baseUri = baseUri ?? new Uri("https://api.retrostic.com/v1/");
        this.http = http ?? new HttpClient();
    }

    public async Task<IReadOnlyList<PlatformInfo>> GetPlatformsAsync(CancellationToken cancellationToken = default)
    {
        var response = await http.GetAsync(new Uri(baseUri, "platforms"), cancellationToken);
        response.EnsureSuccessStatusCode();
        var json = await response.Content.ReadFromJsonAsync<JsonObject>(cancellationToken: cancellationToken);
        var list = new List<PlatformInfo>();
        if (json?["platforms"] is JsonArray arr)
        {
            foreach (var node in arr)
            {
                if (node is JsonObject obj)
                {
                    var exts = new List<string>();
                    if (obj["supportedExtensions"] is JsonArray extArr)
                    {
                        foreach (var e in extArr) if (e != null) exts.Add(e.GetValue<string>());
                    }
                    list.Add(new PlatformInfo(
                        obj["id"]?.GetValue<string>() ?? "",
                        obj["slug"]?.GetValue<string>() ?? "",
                        obj["name"]?.GetValue<string>() ?? "",
                        obj["category"]?.GetValue<string>() ?? "Console",
                        exts,
                        obj["canonicalFormat"]?.GetValue<string>() ?? "",
                        obj["gameCount"]?.GetValue<int>() ?? 0
                    ));
                }
            }
        }
        return list;
    }

    public async Task<IReadOnlyList<GameSearchResult>> SearchAsync(GameSearchQuery query, CancellationToken cancellationToken = default)
    {
        var uriBuilder = new UriBuilder(new Uri(baseUri, "search"))
        {
            Query = $"q={Uri.EscapeDataString(query.Query)}&page={query.Page}&pageSize={query.PageSize}" +
                    (!string.IsNullOrEmpty(query.PlatformId) ? $"&platform={Uri.EscapeDataString(query.PlatformId)}" : "")
        };

        var response = await http.GetAsync(uriBuilder.Uri, cancellationToken);
        response.EnsureSuccessStatusCode();
        var json = await response.Content.ReadFromJsonAsync<JsonObject>(cancellationToken: cancellationToken);
        var list = new List<GameSearchResult>();
        if (json?["results"] is JsonArray arr)
        {
            foreach (var node in arr)
            {
                if (node is JsonObject obj)
                {
                    list.Add(new GameSearchResult(
                        obj["id"]?.GetValue<string>() ?? "",
                        obj["slug"]?.GetValue<string>() ?? "",
                        obj["platformId"]?.GetValue<string>() ?? "",
                        obj["title"]?.GetValue<string>() ?? "",
                        obj["region"]?.GetValue<string>() ?? "World",
                        obj["releaseYear"]?.GetValue<int>(),
                        obj["thumbnailUrl"]?.GetValue<string>(),
                        obj["archiveSizeBytes"]?.GetValue<long>(),
                        obj["sourceArchiveFormat"]?.GetValue<string>()
                    ));
                }
            }
        }
        return list;
    }

    public async Task<GameSourceDetails> GetDetailsAsync(string gameId, CancellationToken cancellationToken = default)
    {
        var response = await http.GetAsync(new Uri(baseUri, $"games/{Uri.EscapeDataString(gameId)}"), cancellationToken);
        response.EnsureSuccessStatusCode();
        var obj = await response.Content.ReadFromJsonAsync<JsonObject>(cancellationToken: cancellationToken)
            ?? throw new InvalidDataException("Dados do jogo inválidos ou vazios.");

        var screens = new List<string>();
        if (obj["screenshots"] is JsonArray screenArr)
        {
            foreach (var s in screenArr) if (s != null) screens.Add(s.GetValue<string>());
        }

        var sourceObj = obj["sourceArchive"] as JsonObject;
        var sourceFile = new SourceFile(
            sourceObj?["filename"]?.GetValue<string>() ?? $"{gameId}.7z",
            sourceObj?["sizeBytes"]?.GetValue<long>() ?? 0L,
            sourceObj?["sha256"]?.GetValue<string>(),
            sourceObj?["format"]?.GetValue<string>() ?? "7z"
        );

        return new GameSourceDetails(
            obj["id"]?.GetValue<string>() ?? gameId,
            obj["slug"]?.GetValue<string>() ?? gameId,
            obj["platformId"]?.GetValue<string>() ?? "",
            obj["title"]?.GetValue<string>() ?? "",
            obj["region"]?.GetValue<string>() ?? "World",
            obj["releaseYear"]?.GetValue<int>(),
            obj["publisher"]?.GetValue<string>(),
            obj["developer"]?.GetValue<string>(),
            obj["description"]?.GetValue<string>(),
            obj["coverUrl"]?.GetValue<string>(),
            screens,
            sourceFile
        );
    }

    public async Task<DownloadDescriptor> ResolveDownloadAsync(string gameId, CancellationToken cancellationToken = default)
    {
        var response = await http.PostAsJsonAsync(new Uri(baseUri, $"games/{Uri.EscapeDataString(gameId)}/download-ticket"),
            new { clientType = "desktop-importer", clientVersion = "2.0.0" }, cancellationToken);
        response.EnsureSuccessStatusCode();
        var obj = await response.Content.ReadFromJsonAsync<JsonObject>(cancellationToken: cancellationToken)
            ?? throw new InvalidDataException("Ticket de download inválido.");

        var headersDict = new Dictionary<string, string>();
        if (obj["headers"] is JsonObject hObj)
        {
            foreach (var prop in hObj) if (prop.Value != null) headersDict[prop.Key] = prop.Value.GetValue<string>();
        }

        DateTimeOffset? expires = null;
        if (obj["expiresAt"] != null && obj["expiresAt"]!.GetValue<long>() > 0)
        {
            expires = DateTimeOffset.FromUnixTimeSeconds(obj["expiresAt"]!.GetValue<long>());
        }

        return new DownloadDescriptor(
            obj["ticketId"]?.GetValue<string>() ?? Guid.NewGuid().ToString("N"),
            obj["gameId"]?.GetValue<string>() ?? gameId,
            obj["downloadUrl"]?.GetValue<string>() ?? throw new InvalidDataException("URL de download ausente no ticket."),
            obj["filename"]?.GetValue<string>() ?? $"{gameId}.7z",
            obj["sizeBytes"]?.GetValue<long>() ?? 0L,
            obj["sha256"]?.GetValue<string>(),
            obj["supportsRange"]?.GetValue<bool>() ?? true,
            expires,
            headersDict
        );
    }
}

public sealed class RetrosticHtmlResolver
{
    private readonly HttpClient http;
    private readonly Uri baseUri;

    public RetrosticHtmlResolver(Uri? baseUri = null, HttpClient? http = null)
    {
        this.baseUri = baseUri ?? new Uri("https://www.retrostic.com/");
        this.http = http ?? CreateDefaultHttpClient();
    }

    private static HttpClient CreateDefaultHttpClient()
    {
        var client = new HttpClient();
        client.DefaultRequestHeaders.UserAgent.ParseAdd("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36");
        client.DefaultRequestHeaders.Accept.ParseAdd("text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        return client;
    }

    public Task<IReadOnlyList<PlatformInfo>> GetPlatformsAsync(CancellationToken cancellationToken = default)
    {
        IReadOnlyList<PlatformInfo> platforms =
        [
            new PlatformInfo("ps1", "playstation", "Sony PlayStation", "Console", [".cue", ".bin", ".iso", ".chd"], "chd", 1640),
            new PlatformInfo("snes", "super-nintendo", "Super Nintendo", "Console", [".smc", ".sfc"], "sfc", 1757),
            new PlatformInfo("nes", "nintendo", "Nintendo Entertainment System", "Console", [".nes"], "nes", 1820),
            new PlatformInfo("megadrive", "genesis", "Sega Genesis / Mega Drive", "Console", [".md", ".gen", ".bin"], "md", 915),
            new PlatformInfo("gba", "gameboy-advance", "Game Boy Advance", "Handheld", [".gba"], "gba", 1498)
        ];
        return Task.FromResult(platforms);
    }

    public async Task<IReadOnlyList<GameSearchResult>> SearchAsync(GameSearchQuery query, CancellationToken cancellationToken = default)
    {
        var searchUrl = new Uri(baseUri, $"search?q={Uri.EscapeDataString(query.Query)}");
        var html = await http.GetStringAsync(searchUrl, cancellationToken);
        return ParseSearchResults(html, query.PlatformId);
    }

    public async Task<GameSourceDetails> GetDetailsAsync(string gameUrlOrId, CancellationToken cancellationToken = default)
    {
        var uri = gameUrlOrId.StartsWith("http", StringComparison.OrdinalIgnoreCase)
            ? new Uri(gameUrlOrId)
            : new Uri(baseUri, gameUrlOrId.TrimStart('/'));

        var html = await http.GetStringAsync(uri, cancellationToken);
        return ParseGameDetails(html, uri.ToString());
    }

    public async Task<DownloadDescriptor> ResolveDownloadAsync(string gameUrlOrId, CancellationToken cancellationToken = default)
    {
        var details = await GetDetailsAsync(gameUrlOrId, cancellationToken);
        var uri = gameUrlOrId.StartsWith("http", StringComparison.OrdinalIgnoreCase)
            ? new Uri(gameUrlOrId)
            : new Uri(baseUri, gameUrlOrId.TrimStart('/'));

        var html = await http.GetStringAsync(uri, cancellationToken);
        var downloadUrl = ParseDownloadUrl(html);

        if (string.IsNullOrEmpty(downloadUrl))
        {
            // Try appending /download or countdown page
            var downloadPageUri = new Uri(uri.ToString().TrimEnd('/') + "/download");
            try
            {
                var downloadPageHtml = await http.GetStringAsync(downloadPageUri, cancellationToken);
                downloadUrl = ParseDownloadUrl(downloadPageHtml);
            }
            catch { }
        }

        if (string.IsNullOrEmpty(downloadUrl))
        {
            throw new InvalidDataException($"Não foi possível resolver o link direto de download para {gameUrlOrId}");
        }

        return new DownloadDescriptor(
            TicketId: Guid.NewGuid().ToString("N"),
            GameId: details.Id,
            DownloadUrl: downloadUrl,
            Filename: details.SourceFile.Filename,
            SizeBytes: details.SourceFile.SizeBytes,
            ExpectedSha256: details.SourceFile.Sha256,
            SupportsRange: true,
            ExpiresAt: DateTimeOffset.UtcNow.AddHours(2),
            Headers: new Dictionary<string, string>
            {
                ["Referer"] = uri.ToString(),
                ["User-Agent"] = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
            }
        );
    }

    public static IReadOnlyList<GameSearchResult> ParseSearchResults(string html, string? platformFilter = null)
    {
        var results = new List<GameSearchResult>();
        // Regex patterns to parse Retrostic search markup
        // Anchor pattern: <a href="/roms/{system}/{slug}">Title</a>
        var regex = new Regex(@"<a\s+[^>]*href=[""'](?<link>/roms/(?<platform>[^/]+)/(?<slug>[^""'/]+))[""'][^>]*>(?<content>.*?)</a>", RegexOptions.Singleline | RegexOptions.IgnoreCase);
        var matches = regex.Matches(html);

        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach (Match match in matches)
        {
            var link = match.Groups["link"].Value;
            var platformSlug = match.Groups["platform"].Value;
            var slug = match.Groups["slug"].Value;
            var content = match.Groups["content"].Value;

            if (seen.Contains(link)) continue;
            seen.Add(link);

            var headingMatch = Regex.Match(content, @"<h\d[^>]*>(?<title>[^<]+)</h\d>", RegexOptions.Singleline | RegexOptions.IgnoreCase);
            var rawTitle = headingMatch.Success
                ? headingMatch.Groups["title"].Value.Trim()
                : Regex.Replace(content, @"<[^>]+>", " ").Trim();
            if (string.IsNullOrWhiteSpace(rawTitle)) rawTitle = slug.Replace('-', ' ');

            var imgMatch = Regex.Match(content, @"<img\s+[^>]*src=[""'](?<src>[^""']+)[""']", RegexOptions.IgnoreCase);
            var thumb = imgMatch.Success ? imgMatch.Groups["src"].Value : null;

            var normalizedPlatform = NormalizePlatformSlug(platformSlug);
            if (!string.IsNullOrEmpty(platformFilter) && !string.Equals(platformFilter, normalizedPlatform, StringComparison.OrdinalIgnoreCase))
            {
                continue;
            }

            var parsedTitle = GameTitleParser.Parse(rawTitle);

            results.Add(new GameSearchResult(
                Id: link.TrimStart('/'),
                Slug: slug,
                PlatformId: normalizedPlatform,
                Title: parsedTitle.Title,
                Region: string.IsNullOrEmpty(parsedTitle.Region) ? "World" : parsedTitle.Region,
                ReleaseYear: null,
                ThumbnailUrl: thumb,
                ArchiveSizeBytes: null,
                SourceArchiveFormat: "7z"
            ));
        }

        return results;
    }

    public static GameSourceDetails ParseGameDetails(string html, string sourceUrl)
    {
        var titleMatch = Regex.Match(html, @"<h1[^>]*>(?<title>[^<]+)</h1>", RegexOptions.IgnoreCase);
        var rawTitle = titleMatch.Success ? titleMatch.Groups["title"].Value.Trim() : "Unknown Game";
        var parsed = GameTitleParser.Parse(rawTitle);

        var descMatch = Regex.Match(html, @"<meta\s+name=[""']description[""']\s+content=[""'](?<desc>[^""']+)[""']", RegexOptions.IgnoreCase);
        var description = descMatch.Success ? descMatch.Groups["desc"].Value.Trim() : "";

        var imgMatch = Regex.Match(html, @"<img\s+[^>]*class=[""'][^""']*cover[^""']*[""'][^>]*src=[""'](?<src>[^""']+)[""']", RegexOptions.IgnoreCase);
        var cover = imgMatch.Success ? imgMatch.Groups["src"].Value : null;

        var sizeMatch = Regex.Match(html, @"(?<size>\d+(?:\.\d+)?)\s*(?<unit>MB|GB|KB)", RegexOptions.IgnoreCase);
        long sizeBytes = 0;
        if (sizeMatch.Success)
        {
            if (double.TryParse(sizeMatch.Groups["size"].Value, System.Globalization.CultureInfo.InvariantCulture, out var num))
            {
                var unit = sizeMatch.Groups["unit"].Value.ToUpperInvariant();
                sizeBytes = unit switch
                {
                    "GB" => (long)(num * 1024 * 1024 * 1024),
                    "MB" => (long)(num * 1024 * 1024),
                    "KB" => (long)(num * 1024),
                    _ => (long)num
                };
            }
        }

        var slug = Path.GetFileName(new Uri(sourceUrl).AbsolutePath.TrimEnd('/'));
        var filename = $"{rawTitle}.7z";

        return new GameSourceDetails(
            Id: sourceUrl,
            Slug: slug,
            PlatformId: "ps1",
            Title: parsed.Title,
            Region: string.IsNullOrEmpty(parsed.Region) ? "World" : parsed.Region,
            ReleaseYear: null,
            Publisher: null,
            Developer: null,
            Description: description,
            CoverUrl: cover,
            Screenshots: [],
            SourceFile: new SourceFile(filename, sizeBytes, null, "7z")
        );
    }

    public static string? ParseDownloadUrl(string html)
    {
        // Check for direct downloads.retrostic.com link
        var directMatch = Regex.Match(html, @"href=[""'](?<url>https?://downloads\.retrostic\.com/[^""']+)[""']", RegexOptions.IgnoreCase);
        if (directMatch.Success) return directMatch.Groups["url"].Value;

        // Check for countdown / data-download-url attribute
        var attrMatch = Regex.Match(html, @"data-download-url=[""'](?<url>[^""']+)[""']", RegexOptions.IgnoreCase);
        if (attrMatch.Success) return attrMatch.Groups["url"].Value;

        // Check for "If your download didn't start" link
        var fallbackMatch = Regex.Match(html, @"<a\s+[^>]*href=[""'](?<url>[^""']+)[""'][^>]*>(?:click here|download now|baixar)", RegexOptions.IgnoreCase);
        if (fallbackMatch.Success && fallbackMatch.Groups["url"].Value.StartsWith("http", StringComparison.OrdinalIgnoreCase))
        {
            return fallbackMatch.Groups["url"].Value;
        }

        return null;
    }

    private static string NormalizePlatformSlug(string slug) => slug.ToLowerInvariant() switch
    {
        "playstation" or "psx" or "ps1" => "ps1",
        "super-nintendo" or "snes" => "snes",
        "nintendo" or "nes" => "nes",
        "genesis" or "mega-drive" or "megadrive" => "megadrive",
        "gameboy-advance" or "gba" => "gba",
        _ => slug
    };
}

public sealed class RetrosticSourceProvider : IGameSourceProvider
{
    private readonly RetrosticApiResolver? apiResolver;
    private readonly RetrosticHtmlResolver htmlResolver;
    private readonly IRetrosticBrowserBridge? browserBridge;

    public string ProviderId => "retrostic";
    public string DisplayName => "Retrostic ROMs";

    public RetrosticSourceProvider(
        RetrosticApiResolver? apiResolver = null,
        RetrosticHtmlResolver? htmlResolver = null,
        IRetrosticBrowserBridge? browserBridge = null)
    {
        this.apiResolver = apiResolver;
        this.htmlResolver = htmlResolver ?? new RetrosticHtmlResolver();
        this.browserBridge = browserBridge;
    }

    public async Task<IReadOnlyList<PlatformInfo>> GetPlatformsAsync(CancellationToken cancellationToken = default)
    {
        if (apiResolver != null)
        {
            try { return await apiResolver.GetPlatformsAsync(cancellationToken); }
            catch { /* fallback to HTML */ }
        }
        return await htmlResolver.GetPlatformsAsync(cancellationToken);
    }

    public async Task<IReadOnlyList<GameSearchResult>> SearchAsync(GameSearchQuery query, CancellationToken cancellationToken = default)
    {
        if (apiResolver != null)
        {
            try { return await apiResolver.SearchAsync(query, cancellationToken); }
            catch { /* fallback to HTML */ }
        }
        return await htmlResolver.SearchAsync(query, cancellationToken);
    }

    public async Task<GameSourceDetails> GetDetailsAsync(string gameId, CancellationToken cancellationToken = default)
    {
        if (apiResolver != null)
        {
            try { return await apiResolver.GetDetailsAsync(gameId, cancellationToken); }
            catch { /* fallback to HTML */ }
        }
        return await htmlResolver.GetDetailsAsync(gameId, cancellationToken);
    }

    public async Task<DownloadDescriptor> ResolveDownloadAsync(string gameId, CancellationToken cancellationToken = default)
    {
        // Tier 1: API
        if (apiResolver != null)
        {
            try { return await apiResolver.ResolveDownloadAsync(gameId, cancellationToken); }
            catch { /* fallback to Tier 2 */ }
        }

        // Tier 2: HTML
        try
        {
            return await htmlResolver.ResolveDownloadAsync(gameId, cancellationToken);
        }
        catch (Exception) when (browserBridge != null)
        {
            // Tier 3: Browser Bridge fallback
            return await browserBridge.ResolveWithBrowserAsync(gameId, cancellationToken);
        }
    }
}
