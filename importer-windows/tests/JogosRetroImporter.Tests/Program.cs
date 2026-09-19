using JogosRetroImporter.Core;

var root = Path.Combine(Path.GetTempPath(), "JogosRetroImporterTests", Guid.NewGuid().ToString("N"));
Directory.CreateDirectory(root);
try
{
    var bin = Path.Combine(root, "Mega Man X4 (USA).bin");
    await File.WriteAllBytesAsync(bin, new byte[2352 * 4]);
    var cue = Path.Combine(root, "Mega Man X4 (USA).cue");
    await File.WriteAllTextAsync(cue, "FILE \"Mega Man X4 (USA).bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\n");

    var sheet = CueSheet.Load(cue);
    Assert(sheet.Tracks.Count == 1, "CUE track was not detected");
    Assert(sheet.ReferencedFiles.Single() == bin, "CUE file was not resolved safely");
    Assert(sheet.TotalBytes == new FileInfo(bin).Length, "CUE byte count is wrong");

    var title = GameTitleParser.Parse("Mega Man X4 (USA).7z");
    Assert(title.Title == "Mega Man X4" && title.Region == "USA", "Title and region were not normalized");
    var noIntro = GameTitleParser.Parse("CTR - Crash Team Racing (E) (No EDC) [SCES-02105].7z");
    Assert(noIntro.Title == "CTR - Crash Team Racing" && noIntro.Region == "Europe", "No-Intro title was not normalized");
    Assert(PlatformDetector.Detect("game.cue") == GamePlatform.PlayStation, "PlayStation detection failed");
    Assert(PlatformDetector.Detect("game.gba") == GamePlatform.GameBoyAdvance, "GBA detection failed");

    Assert(PlatformRegistry.IsPipelineSupported("ps1"), "PS1 pipeline must be supported");
    Assert(!PlatformRegistry.IsPipelineSupported("snes"), "SNES pipeline is currently planned, not supported");
    Assert(PlatformRegistry.FindByExtension("game.cue")?.Id == "ps1", "CUE resolves to ps1");
    Assert(PlatformRegistry.FindByExtension("mario.smc")?.Id == "snes", "SMC resolves to snes");
    Assert(PlatformRegistry.FindById("playstation")?.Id == "ps1", "FindById supports display name or id");

    var originalHash = await FileHash.Sha256Async(cue);
    var copy = Path.Combine(root, "copy.cue"); File.Copy(cue, copy);
    Assert(originalHash == await FileHash.Sha256Async(copy), "SHA-256 is not stable");

    var unsafeCue = Path.Combine(root, "unsafe.cue");
    await File.WriteAllTextAsync(unsafeCue, "FILE \"..\\outside.bin\" BINARY\n TRACK 01 MODE2/2352\n");
    AssertThrows(() => CueSheet.Load(unsafeCue), "unsafe parent path must be rejected");

    var tools = new ToolchainManifest("mame-chdman", "0.289", "BSD-3-Clause", "https://github.com/mamedev/mame/tree/mame0289", new string('a', 64));
    Assert(tools.IsValid(), "valid locked tool was rejected");
    Assert(!(tools with { Sha256 = "bad" }).IsValid(), "invalid tool hash was accepted");

    // Test cross-platform abstractions
    var platform = PlatformServices.Instance;
    Assert(!string.IsNullOrEmpty(platform.OperatingSystemName), "OS name must not be empty");
    Assert(!string.IsNullOrEmpty(platform.RuntimeIdentifier), "RID must not be empty");

    var paths = new AppPaths(customRoot: root);
    Assert(paths.CacheDirectory == root, "Custom root not respected");
    Assert(paths.StagingDirectory == Path.Combine(root, "staging"), "Staging path wrong");

    var credStore = new CrossPlatformCredentialStore(root);
    credStore.Save("test_key", "secret_value_12345");
    var loaded = credStore.Load("test_key");
    Assert(loaded == "secret_value_12345", "Credential store roundtrip failed");
    credStore.Clear("test_key");
    Assert(string.IsNullOrEmpty(credStore.Load("test_key")), "Credential clear failed");

    var secureTokenStore = new SecureTokenStore(credStore);
    secureTokenStore.Save("tok_xyz987");
    Assert(secureTokenStore.Load() == "tok_xyz987", "SecureTokenStore delegation failed");
    secureTokenStore.Clear();
    Assert(string.IsNullOrEmpty(secureTokenStore.Load()), "SecureTokenStore clear failed");

    var resolver = new ToolchainResolver(platform);
    Assert(platform.IsWindows ? resolver.ChdmanExecutableName == "chdman.exe" : resolver.ChdmanExecutableName == "chdman", "Executable name wrong for platform");

    // Retrostic HTML fixtures tests
    var fixturesDir = Path.Combine(AppContext.BaseDirectory, "..", "..", "..", "fixtures", "retrostic");
    if (!Directory.Exists(fixturesDir)) fixturesDir = Path.Combine(Directory.GetCurrentDirectory(), "importer-windows", "tests", "fixtures", "retrostic");

    var searchHtml = await File.ReadAllTextAsync(Path.Combine(fixturesDir, "search_page.html"));
    var parsedSearch = RetrosticHtmlResolver.ParseSearchResults(searchHtml);
    Assert(parsedSearch.Count == 2, $"Expected 2 search results, got {parsedSearch.Count}");
    Assert(parsedSearch[0].Title == "Crash Bandicoot" && parsedSearch[0].PlatformId == "ps1", "PS1 game parse mismatch");
    Assert(parsedSearch[1].Title == "Super Mario World" && parsedSearch[1].PlatformId == "snes", "SNES game parse mismatch");

    // Filter by platform
    var filteredSearch = RetrosticHtmlResolver.ParseSearchResults(searchHtml, "ps1");
    Assert(filteredSearch.Count == 1 && filteredSearch[0].PlatformId == "ps1", "Platform filter failed");

    var gameHtml = await File.ReadAllTextAsync(Path.Combine(fixturesDir, "game_page.html"));
    var parsedDetails = RetrosticHtmlResolver.ParseGameDetails(gameHtml, "https://www.retrostic.com/roms/ps1/crash-bandicoot-usa");
    Assert(parsedDetails.Title == "Crash Bandicoot", "Game details title parse mismatch");
    Assert(parsedDetails.Region == "USA", "Game details region parse mismatch");
    Assert(parsedDetails.SourceFile.SizeBytes > 0, "Game details size parse mismatch");

    var downloadHtml = await File.ReadAllTextAsync(Path.Combine(fixturesDir, "download_page.html"));
    var parsedDownloadUrl = RetrosticHtmlResolver.ParseDownloadUrl(downloadHtml);
    Assert(parsedDownloadUrl != null && parsedDownloadUrl.Contains("downloads.retrostic.com"), "Download URL parse failed");

    // Retrostic API Resolver tests with mock HTTP handler
    var platformsJson = await File.ReadAllTextAsync(Path.Combine(fixturesDir, "platforms.json"));
    var searchJson = await File.ReadAllTextAsync(Path.Combine(fixturesDir, "search.json"));
    var gameJson = await File.ReadAllTextAsync(Path.Combine(fixturesDir, "game.json"));
    var ticketJson = await File.ReadAllTextAsync(Path.Combine(fixturesDir, "download_ticket.json"));

    var mockHandler = new TestHttpMessageHandler(req =>
    {
        var path = req.RequestUri?.AbsolutePath ?? "";
        if (path.EndsWith("/platforms")) return new HttpResponseMessage(System.Net.HttpStatusCode.OK) { Content = new StringContent(platformsJson, System.Text.Encoding.UTF8, "application/json") };
        if (path.EndsWith("/search")) return new HttpResponseMessage(System.Net.HttpStatusCode.OK) { Content = new StringContent(searchJson, System.Text.Encoding.UTF8, "application/json") };
        if (path.EndsWith("/download-ticket")) return new HttpResponseMessage(System.Net.HttpStatusCode.OK) { Content = new StringContent(ticketJson, System.Text.Encoding.UTF8, "application/json") };
        if (path.Contains("/games/")) return new HttpResponseMessage(System.Net.HttpStatusCode.OK) { Content = new StringContent(gameJson, System.Text.Encoding.UTF8, "application/json") };
        return new HttpResponseMessage(System.Net.HttpStatusCode.NotFound);
    });

    var mockClient = new HttpClient(mockHandler);
    var apiResolver = new RetrosticApiResolver(new Uri("http://localhost:5000/v1/"), mockClient);

    var apiPlatforms = await apiResolver.GetPlatformsAsync();
    Assert(apiPlatforms.Count == 5, $"Expected 5 platforms from API, got {apiPlatforms.Count}");
    Assert(apiPlatforms.Any(p => p.Id == "ps1" && p.CanonicalFormat == "chd"), "PS1 platform missing in API platforms");

    var apiSearch = await apiResolver.SearchAsync(new GameSearchQuery("crash"));
    Assert(apiSearch.Count == 1 && apiSearch[0].Slug == "crash-bandicoot", "API search failed");

    var apiGame = await apiResolver.GetDetailsAsync("ps1-crash-bandicoot-usa");
    Assert(apiGame.Title == "Crash Bandicoot" && apiGame.SourceFile.SizeBytes == 45612346, "API details failed");

    var apiTicket = await apiResolver.ResolveDownloadAsync("ps1-crash-bandicoot-usa");
    Assert(apiTicket.TicketId == "tkt_test_12345" && apiTicket.SupportsRange, "API ticket failed");

    // RetrosticSourceProvider Tier Fallback test
    // 1. API Primary
    var providerWithApi = new RetrosticSourceProvider(apiResolver);
    var providerSearch = await providerWithApi.SearchAsync(new GameSearchQuery("crash"));
    Assert(providerSearch.Count == 1, "Provider API tier failed");

    // 2. HTML Fallback when API throws
    var failingApiHandler = new TestHttpMessageHandler(_ => new HttpResponseMessage(System.Net.HttpStatusCode.InternalServerError));
    var failingApiResolver = new RetrosticApiResolver(new Uri("http://localhost:5000/v1/"), new HttpClient(failingApiHandler));
    var mockHtmlHandler = new TestHttpMessageHandler(req =>
    {
        var path = req.RequestUri?.AbsolutePath ?? "";
        if (path.Contains("search")) return new HttpResponseMessage(System.Net.HttpStatusCode.OK) { Content = new StringContent(searchHtml, System.Text.Encoding.UTF8, "text/html") };
        if (path.Contains("download")) return new HttpResponseMessage(System.Net.HttpStatusCode.OK) { Content = new StringContent(downloadHtml, System.Text.Encoding.UTF8, "text/html") };
        return new HttpResponseMessage(System.Net.HttpStatusCode.OK) { Content = new StringContent(gameHtml, System.Text.Encoding.UTF8, "text/html") };
    });
    var mockHtmlResolver = new RetrosticHtmlResolver(new Uri("http://localhost:5000/"), new HttpClient(mockHtmlHandler));

    var providerWithFallback = new RetrosticSourceProvider(failingApiResolver, mockHtmlResolver);
    var fallbackSearch = await providerWithFallback.SearchAsync(new GameSearchQuery("crash"));
    Assert(fallbackSearch.Count == 2, "Provider HTML tier fallback failed");

    // 3. Browser Bridge Fallback when HTML resolver fails download resolution
    var failingHtmlHandler = new TestHttpMessageHandler(_ => new HttpResponseMessage(System.Net.HttpStatusCode.Forbidden));
    var failingHtmlResolver = new RetrosticHtmlResolver(new Uri("http://localhost:5000/"), new HttpClient(failingHtmlHandler));
    var mockBrowser = new MockBrowserBridge();
    var providerWithBrowser = new RetrosticSourceProvider(failingApiResolver, failingHtmlResolver, mockBrowser);
    var browserDownload = await providerWithBrowser.ResolveDownloadAsync("blocked-game");
    Assert(browserDownload.TicketId == "mock-browser-ticket", "Browser bridge tier fallback failed");

    Console.WriteLine("PASS: importer core preserves originals, validates PlayStation input, runs cross-platform abstractions, and verifies Retrostic resolvers and provider");
}
finally { try { Directory.Delete(root, true); } catch { } }

if (args.Length >= 2)
{
    var cache = args.Length >= 3 ? args[2] : Path.Combine(Path.GetTempPath(), "JogosRetroImporterReal");
    var live = await new ImportPipeline(args[1], cache).PreparePlayStationAsync(args[0], new Progress<string>(Console.WriteLine));
    Console.WriteLine($"PASS: real import {live.Metadata.Title} | {new FileInfo(live.FinalFile).Length} bytes | {live.FinalFile}");
}

static void Assert(bool condition, string message) { if (!condition) throw new Exception(message); }
static void AssertThrows(Action action, string message) { try { action(); } catch { return; } throw new Exception(message); }

sealed class TestHttpMessageHandler(Func<HttpRequestMessage, HttpResponseMessage> handler) : HttpMessageHandler
{
    protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        => Task.FromResult(handler(request));
}

sealed class MockBrowserBridge : IRetrosticBrowserBridge
{
    public Task<DownloadDescriptor> ResolveWithBrowserAsync(string gameUrl, CancellationToken cancellationToken = default)
    {
        return Task.FromResult(new DownloadDescriptor(
            TicketId: "mock-browser-ticket",
            GameId: gameUrl,
            DownloadUrl: "https://downloads.retrostic.com/mock/game.7z",
            Filename: "mock-game.7z",
            SizeBytes: 1024 * 1024,
            SupportsRange: true
        ));
    }
}
