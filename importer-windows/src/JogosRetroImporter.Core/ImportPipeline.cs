namespace JogosRetroImporter.Core;

public sealed record PreparedGame(
    string OriginalPath,
    string OriginalSha256,
    string WorkDirectory,
    string DiscSource,
    string FinalFile,
    GameMetadata Metadata,
    string? CanonicalSha256 = null,
    string? PlatformId = null
)
{
    public string ContentId => $"sha256:{(CanonicalSha256 ?? OriginalSha256).ToLowerInvariant()}";
}

public sealed class ImportPipeline
{
    private readonly string cacheRoot;
    private readonly string chdman;
    private readonly List<IPlatformImportStrategy> strategies = [];

    public ImportPipeline(string chdman, string? cacheRoot = null)
    {
        this.chdman = chdman;
        this.cacheRoot = cacheRoot ?? AppPaths.Default.CacheDirectory;

        RegisterStrategy(new PlayStationImportStrategy(this.chdman));
        RegisterStrategy(new CartridgeImportStrategy("snes", [".sfc", ".smc"], "sfc"));
        RegisterStrategy(new CartridgeImportStrategy("nes", [".nes"], "nes"));
        RegisterStrategy(new CartridgeImportStrategy("megadrive", [".md", ".gen", ".bin", ".smd"], "md"));
        RegisterStrategy(new CartridgeImportStrategy("gba", [".gba"], "gba"));
    }

    public void RegisterStrategy(IPlatformImportStrategy strategy) => strategies.Add(strategy);

    public async Task<PreparedGame> PreparePlayStationAsync(string inputPath, IProgress<string>? status = null, CancellationToken cancellationToken = default)
    {
        return await ProcessAsync(inputPath, "ps1", status, cancellationToken);
    }

    public async Task<PreparedGame> ProcessAsync(
        string inputPath,
        string? targetPlatformId = null,
        IProgress<string>? status = null,
        CancellationToken cancellationToken = default)
    {
        var original = new FileInfo(inputPath);
        if (!original.Exists) throw new FileNotFoundException("Arquivo de entrada não encontrado.", inputPath);

        var originalHash = await FileHash.Sha256Async(original.FullName, cancellationToken);
        var work = Path.Combine(cacheRoot, "sessions", Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(work);

        var ext = original.Extension.ToLowerInvariant();
        var platformId = targetPlatformId;
        if (string.IsNullOrEmpty(platformId))
        {
            var detected = PlatformDetector.Detect(inputPath);
            platformId = detected switch
            {
                GamePlatform.PlayStation => "ps1",
                GamePlatform.Nes => "nes",
                GamePlatform.Snes => "snes",
                GamePlatform.MegaDrive => "megadrive",
                GamePlatform.GameBoyAdvance => "gba",
                _ => null
            };
        }

        var strategy = strategies.FirstOrDefault(s => s.CanHandle(ext, platformId))
            ?? throw new NotSupportedException($"Não há estratégia de importação configurada para a plataforma '{platformId}' e extensão '{ext}'.");

        var prepared = await strategy.ImportAsync(inputPath, originalHash, work, status, cancellationToken);

        // Verify that original file was not mutated during import
        if (originalHash != await FileHash.Sha256Async(original.FullName, cancellationToken))
        {
            throw new IOException("O arquivo original foi alterado indevidamente durante o processo de importação.");
        }

        return prepared;
    }
}
