using JogosRetroImporter.Core;
using JogosRetro.Downloads;
using JogosRetroImporter.Tests;

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
    Assert(PlatformRegistry.IsPipelineSupported("snes"), "SNES pipeline is now supported");
    Assert(PlatformRegistry.IsPipelineSupported("nes"), "NES pipeline is now supported");
    Assert(PlatformRegistry.IsPipelineSupported("megadrive"), "Mega Drive pipeline is now supported");
    Assert(PlatformRegistry.IsPipelineSupported("gba"), "GBA pipeline is now supported");
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
    // ----------------------------------------------------
    // Fault Simulation Server & DownloadManager Tests
    // ----------------------------------------------------
    using var server = new HttpFaultServer();
    var sampleData = new byte[64 * 1024];
    new Random(42).NextBytes(sampleData);
    var sampleSha = Convert.ToHexString(System.Security.Cryptography.SHA256.HashData(sampleData)).ToLowerInvariant();

    // 1. Full 200 OK Download
    server.RequestHandler = async (req, res) =>
    {
        res.StatusCode = 200;
        res.ContentLength64 = sampleData.Length;
        await res.OutputStream.WriteAsync(sampleData);
        res.Close();
    };

    var downloadsDir = Path.Combine(root, "downloads");
    var repo = new JsonDownloadRepository(Path.Combine(downloadsDir, "downloads.json"));
    using var dlManager = new DownloadManager(repo, stagingDirectory: downloadsDir);
    await dlManager.InitializeAsync();

    var job1 = await dlManager.EnqueueAsync(new DownloadDescriptor("tkt1", "g1", $"{server.BaseUrl}game.7z", "game1.7z", sampleData.Length, sampleSha));
    while (job1.State is DownloadState.Queued or DownloadState.Downloading or DownloadState.Verifying)
    {
        await Task.Delay(50);
    }
    Assert(job1.State == DownloadState.Completed, $"Job1 expected Completed, got {job1.State}: {job1.ErrorMessage}");
    Assert(File.Exists(job1.DestinationPath), "Job1 destination file missing");
    Assert(await FileHash.Sha256Async(job1.DestinationPath) == sampleSha, "Job1 SHA256 mismatch");

    // 2. Resumed 206 Partial Content Download
    var half = sampleData.Length / 2;
    var job2Part = Path.Combine(downloadsDir, "game2.7z.part");
    await File.WriteAllBytesAsync(job2Part, sampleData[..half]);

    server.RequestHandler = async (req, res) =>
    {
        var range = req.Headers["Range"];
        if (range != null && range.StartsWith("bytes="))
        {
            var start = int.Parse(range.Replace("bytes=", "").Replace("-", ""));
            res.StatusCode = 206;
            res.Headers["Content-Range"] = $"bytes {start}-{sampleData.Length - 1}/{sampleData.Length}";
            res.ContentLength64 = sampleData.Length - start;
            await res.OutputStream.WriteAsync(sampleData[start..]);
            res.Close();
        }
        else
        {
            res.StatusCode = 200;
            res.ContentLength64 = sampleData.Length;
            await res.OutputStream.WriteAsync(sampleData);
            res.Close();
        }
    };

    var job2 = await dlManager.EnqueueAsync(new DownloadDescriptor("tkt2", "g2", $"{server.BaseUrl}game2.7z", "game2.7z", sampleData.Length, sampleSha, SupportsRange: true));
    while (job2.State is DownloadState.Queued or DownloadState.Downloading or DownloadState.Verifying)
    {
        await Task.Delay(50);
    }
    Assert(job2.State == DownloadState.Completed, $"Job2 expected Completed, got {job2.State}: {job2.ErrorMessage}");
    Assert(File.Exists(job2.DestinationPath), "Job2 destination file missing");
    Assert(await FileHash.Sha256Async(job2.DestinationPath) == sampleSha, "Job2 SHA256 mismatch");

    // 3. Server ignoring Range header (returns 200 OK) -> engine restarts cleanly
    var job3Part = Path.Combine(downloadsDir, "game3.7z.part");
    await File.WriteAllBytesAsync(job3Part, sampleData[..1000]);

    server.RequestHandler = async (req, res) =>
    {
        res.StatusCode = 200;
        res.ContentLength64 = sampleData.Length;
        await res.OutputStream.WriteAsync(sampleData);
        res.Close();
    };

    var job3 = await dlManager.EnqueueAsync(new DownloadDescriptor("tkt3", "g3", $"{server.BaseUrl}game3.7z", "game3.7z", sampleData.Length, sampleSha, SupportsRange: true));
    while (job3.State is DownloadState.Queued or DownloadState.Downloading or DownloadState.Verifying)
    {
        await Task.Delay(50);
    }
    Assert(job3.State == DownloadState.Completed, $"Job3 expected Completed, got {job3.State}: {job3.ErrorMessage}");
    Assert(new FileInfo(job3.DestinationPath).Length == sampleData.Length, "Job3 length mismatch");

    // 4. Expired ticket (403) with automatic URL refresh via provider
    var refreshedUrl = $"{server.BaseUrl}renewed_game4.7z";
    var mockProvider = new MockSourceProvider(refreshedUrl);
    using var dlManager4 = new DownloadManager(repo, sourceProvider: mockProvider, stagingDirectory: downloadsDir);
    await dlManager4.InitializeAsync();

    server.RequestHandler = async (req, res) =>
    {
        if (req.Url?.AbsolutePath.Contains("renewed") == true)
        {
            res.StatusCode = 200;
            res.ContentLength64 = sampleData.Length;
            await res.OutputStream.WriteAsync(sampleData);
            res.Close();
        }
        else
        {
            res.StatusCode = 403;
            res.Close();
        }
    };

    var job4 = await dlManager4.EnqueueAsync(new DownloadDescriptor("tkt4", "g4", $"{server.BaseUrl}expired_game4.7z", "game4.7z", sampleData.Length, sampleSha));
    while (job4.State is DownloadState.Queued or DownloadState.Downloading or DownloadState.Resolving or DownloadState.Verifying)
    {
        await Task.Delay(50);
    }
    Assert(job4.State == DownloadState.Completed, $"Job4 expected Completed, got {job4.State}: {job4.ErrorMessage}");

    // 5. Transient 500 error retry
    int attempts = 0;
    server.RequestHandler = async (req, res) =>
    {
        if (Interlocked.Increment(ref attempts) == 1)
        {
            res.StatusCode = 500;
            res.Close();
        }
        else
        {
            res.StatusCode = 200;
            res.ContentLength64 = sampleData.Length;
            await res.OutputStream.WriteAsync(sampleData);
            res.Close();
        }
    };

    var job5 = await dlManager.EnqueueAsync(new DownloadDescriptor("tkt5", "g5", $"{server.BaseUrl}transient_game5.7z", "game5.7z", sampleData.Length, sampleSha));
    while (job5.State is DownloadState.Queued or DownloadState.Downloading or DownloadState.Verifying)
    {
        await Task.Delay(50);
    }
    Assert(job5.State == DownloadState.Completed, $"Job5 expected Completed, got {job5.State}: {job5.ErrorMessage}");
    Assert(job5.RetryCount >= 1, "Job5 retry count should be >= 1");

    // 6. Checksum mismatch failure
    server.RequestHandler = async (req, res) =>
    {
        res.StatusCode = 200;
        res.ContentLength64 = sampleData.Length;
        await res.OutputStream.WriteAsync(sampleData);
        res.Close();
    };

    var wrongSha = new string('0', 64);
    var job6 = await dlManager.EnqueueAsync(new DownloadDescriptor("tkt6", "g6", $"{server.BaseUrl}bad_hash.7z", "bad_hash.7z", sampleData.Length, wrongSha));
    while (job6.State is DownloadState.Queued or DownloadState.Downloading or DownloadState.Verifying)
    {
        await Task.Delay(50);
    }
    Assert(job6.State == DownloadState.Failed, $"Job6 expected Failed, got {job6.State}");
    Assert(!File.Exists(job6.DestinationPath), "Job6 bad file should not be finalized");

    // 7. Pause and Resume lifecycle
    var slowData = new byte[128 * 1024];
    new Random(99).NextBytes(slowData);
    var slowSha = Convert.ToHexString(System.Security.Cryptography.SHA256.HashData(slowData)).ToLowerInvariant();

    server.RequestHandler = async (req, res) =>
    {
        var range = req.Headers["Range"];
        int offset = 0;
        if (range != null && range.StartsWith("bytes="))
        {
            offset = int.Parse(range.Replace("bytes=", "").Replace("-", ""));
            res.StatusCode = 206;
            res.Headers["Content-Range"] = $"bytes {offset}-{slowData.Length - 1}/{slowData.Length}";
            res.ContentLength64 = slowData.Length - offset;
        }
        else
        {
            res.StatusCode = 200;
            res.ContentLength64 = slowData.Length;
        }

        // Send slowly in chunks
        var chunk = 16 * 1024;
        for (int i = offset; i < slowData.Length; i += chunk)
        {
            var count = Math.Min(chunk, slowData.Length - i);
            await res.OutputStream.WriteAsync(slowData.AsMemory(i, count));
            await res.OutputStream.FlushAsync();
            await Task.Delay(40);
        }
        res.Close();
    };

    var job7 = await dlManager.EnqueueAsync(new DownloadDescriptor("tkt7", "g7", $"{server.BaseUrl}slow_game.7z", "slow_game.7z", slowData.Length, slowSha, SupportsRange: true));
    // Wait until it starts downloading
    while (job7.State != DownloadState.Downloading || job7.DownloadedBytes == 0)
    {
        await Task.Delay(20);
    }
    await dlManager.PauseAsync(job7.Id);
    Assert(job7.State == DownloadState.Paused, $"Job7 expected Paused, got {job7.State}");

    // Now resume
    await dlManager.ResumeAsync(job7.Id);
    while (job7.State is DownloadState.Queued or DownloadState.Downloading or DownloadState.Verifying)
    {
        await Task.Delay(50);
    }
    Assert(job7.State == DownloadState.Completed, $"Job7 expected Completed, got {job7.State}: {job7.ErrorMessage}");
    Assert(File.Exists(job7.DestinationPath), "Job7 destination file missing");
    Assert(await FileHash.Sha256Async(job7.DestinationPath) == slowSha, "Job7 SHA256 mismatch");

    // ----------------------------------------------------
    // Multi-Platform Intake Pipeline & Canonical Artifacts Tests
    // ----------------------------------------------------
    var pipeline = new ImportPipeline("chdman_dummy", root);

    // NES Cartridge Intake
    var nesFile = Path.Combine(root, "Super Mario Bros (USA).nes");
    await File.WriteAllBytesAsync(nesFile, new byte[4096]);
    var preparedNes = await pipeline.ProcessAsync(nesFile, "nes");
    Assert(preparedNes.PlatformId == "nes", "PlatformId should be nes");
    Assert(File.Exists(preparedNes.FinalFile), "NES final canonical ROM should exist");
    Assert(preparedNes.CanonicalSha256 != null, "CanonicalSha256 must be populated");
    Assert(preparedNes.ContentId == $"sha256:{preparedNes.CanonicalSha256!.ToLowerInvariant()}", "ContentId must be sha256:canonical");
    Assert(preparedNes.OriginalSha256 == await FileHash.Sha256Async(nesFile), "OriginalSha256 mismatch");

    // SNES Cartridge Intake with Auto-Detection
    var snesFile = Path.Combine(root, "Super Mario World (USA).sfc");
    await File.WriteAllBytesAsync(snesFile, new byte[8192]);
    var preparedSnes = await pipeline.ProcessAsync(snesFile);
    Assert(preparedSnes.PlatformId == "snes", "Auto-detected PlatformId should be snes");
    Assert(File.Exists(preparedSnes.FinalFile), "SNES final canonical ROM should exist");
    Assert(preparedSnes.ContentId.StartsWith("sha256:"), "SNES ContentId invalid");

    // Mega Drive Cartridge Intake
    var mdFile = Path.Combine(root, "Sonic The Hedgehog (USA).md");
    await File.WriteAllBytesAsync(mdFile, new byte[8192]);
    var preparedMd = await pipeline.ProcessAsync(mdFile, "megadrive");
    Assert(preparedMd.PlatformId == "megadrive", "Mega Drive platform mismatch");
    Assert(File.Exists(preparedMd.FinalFile), "Mega Drive final ROM should exist");

    // GBA Cartridge Intake
    var gbaFile = Path.Combine(root, "Pokemon Emerald (USA).gba");
    await File.WriteAllBytesAsync(gbaFile, new byte[16384]);
    var preparedGba = await pipeline.ProcessAsync(gbaFile, "gba");
    Assert(preparedGba.PlatformId == "gba", "GBA platform mismatch");
    Assert(File.Exists(preparedGba.FinalFile), "GBA final ROM should exist");

    // ----------------------------------------------------
    // Cloud Atomic Publication Test
    // ----------------------------------------------------
    var coverFile = Path.Combine(root, "cover.jpg");
    await File.WriteAllBytesAsync(coverFile, new byte[512]);

    var pubHandler = new TestHttpMessageHandler(req =>
    {
        var uri = req.RequestUri?.ToString() ?? "";
        if (req.Method == HttpMethod.Post && uri.EndsWith("/api/importer/uploads"))
        {
            return new HttpResponseMessage(System.Net.HttpStatusCode.OK)
            {
                Content = new StringContent("{\"uploadId\":\"up_test_123\",\"uploadUrl\":\"https://mock-r2.jogosretro.dev/put\"}", System.Text.Encoding.UTF8, "application/json")
            };
        }
        if (req.Method == HttpMethod.Put && uri.Contains("mock-r2"))
        {
            return new HttpResponseMessage(System.Net.HttpStatusCode.OK);
        }
        if (req.Method == HttpMethod.Post && uri.Contains("/finalize"))
        {
            return new HttpResponseMessage(System.Net.HttpStatusCode.OK)
            {
                Content = new StringContent("{\"status\":\"finalized\"}", System.Text.Encoding.UTF8, "application/json")
            };
        }
        if (req.Method == HttpMethod.Post && uri.EndsWith("/api/importer/publications"))
        {
            return new HttpResponseMessage(System.Net.HttpStatusCode.OK)
            {
                Content = new StringContent("{\"revision\":\"rev_abc987\",\"itemCount\":1,\"updatedAt\":\"2026-09-19T12:00:00Z\"}", System.Text.Encoding.UTF8, "application/json")
            };
        }
        return new HttpResponseMessage(System.Net.HttpStatusCode.NotFound);
    });

    var pubClient = new CloudPublisherClient("https://mock-cloud.jogosretro.dev/", new HttpClient(pubHandler));
    var pubResult = await pubClient.PublishGameAsync("test_token_123", preparedGba, coverFile);
    Assert(pubResult.Revision == "rev_abc987", "Publication revision mismatch");
    Assert(pubResult.ItemCount == 1, "Publication item count mismatch");

    Console.WriteLine("PASS: importer core preserves originals, validates PlayStation input, runs cross-platform abstractions, verifies Retrostic resolvers and provider, passes download fault simulation, verifies multi-platform canonical intake, and tests atomic cloud publication");
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

sealed class MockSourceProvider(string renewedUrl) : IGameSourceProvider
{
    public string ProviderId => "mock";
    public string DisplayName => "Mock Provider";

    public Task<IReadOnlyList<PlatformInfo>> GetPlatformsAsync(CancellationToken cancellationToken = default)
        => Task.FromResult<IReadOnlyList<PlatformInfo>>([]);

    public Task<IReadOnlyList<GameSearchResult>> SearchAsync(GameSearchQuery query, CancellationToken cancellationToken = default)
        => Task.FromResult<IReadOnlyList<GameSearchResult>>([]);

    public Task<GameSourceDetails> GetDetailsAsync(string gameId, CancellationToken cancellationToken = default)
        => Task.FromResult(new GameSourceDetails(gameId, gameId, "ps1", "Mock Game", "USA", 1999, null, null, null, null, [], new SourceFile("mock.7z", 1024, null, "7z")));

    public Task<DownloadDescriptor> ResolveDownloadAsync(string gameId, CancellationToken cancellationToken = default)
        => Task.FromResult(new DownloadDescriptor("tkt_renewed", gameId, renewedUrl, $"{gameId}.7z", 64 * 1024, SupportsRange: true));
}
