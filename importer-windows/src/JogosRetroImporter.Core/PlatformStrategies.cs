namespace JogosRetroImporter.Core;

public sealed class PlayStationImportStrategy : IPlatformImportStrategy
{
    private readonly string chdmanPath;

    public string PlatformId => "ps1";

    public PlayStationImportStrategy(string chdmanPath)
    {
        this.chdmanPath = chdmanPath;
    }

    public bool CanHandle(string extension, string? detectedPlatform = null)
    {
        if (string.Equals(detectedPlatform, "ps1", StringComparison.OrdinalIgnoreCase) ||
            string.Equals(detectedPlatform, "playstation", StringComparison.OrdinalIgnoreCase))
        {
            return true;
        }

        return extension.ToLowerInvariant() switch
        {
            ".cue" or ".bin" or ".iso" or ".chd" => true,
            _ => false
        };
    }

    public async Task<PreparedGame> ImportAsync(
        string inputPath,
        string originalHash,
        string workDirectory,
        IProgress<string>? status = null,
        CancellationToken cancellationToken = default)
    {
        var original = new FileInfo(inputPath);
        var sourceRoot = Path.Combine(workDirectory, "source");
        Directory.CreateDirectory(sourceRoot);

        IReadOnlyList<string> inputs;
        var ext = original.Extension.ToLowerInvariant();
        if (ext is ".7z" or ".zip")
        {
            status?.Report("Extraindo arquivo compactado de PlayStation…");
            inputs = await ArchiveExtractor.ExtractAsync(original.FullName, sourceRoot, null, cancellationToken);
        }
        else if (ext == ".cue")
        {
            var sheet = CueSheet.Load(original.FullName);
            var copy = Path.Combine(sourceRoot, original.Name);
            File.Copy(original.FullName, copy, true);
            foreach (var track in sheet.ReferencedFiles)
            {
                File.Copy(track, Path.Combine(sourceRoot, Path.GetFileName(track)), true);
            }
            inputs = Directory.GetFiles(sourceRoot);
        }
        else if (ext == ".bin")
        {
            var siblingCue = Path.ChangeExtension(original.FullName, ".cue");
            if (!File.Exists(siblingCue))
            {
                throw new InvalidDataException("O arquivo BIN precisa de um CUE acompanhante com o mesmo nome para preservar as faixas.");
            }
            var sheet = CueSheet.Load(siblingCue);
            File.Copy(siblingCue, Path.Combine(sourceRoot, Path.GetFileName(siblingCue)), true);
            foreach (var track in sheet.ReferencedFiles)
            {
                File.Copy(track, Path.Combine(sourceRoot, Path.GetFileName(track)), true);
            }
            inputs = Directory.GetFiles(sourceRoot);
        }
        else
        {
            var copy = Path.Combine(sourceRoot, original.Name);
            File.Copy(original.FullName, copy, true);
            inputs = [copy];
        }

        var cue = inputs.FirstOrDefault(path => Path.GetExtension(path).Equals(".cue", StringComparison.OrdinalIgnoreCase));
        var chd = inputs.FirstOrDefault(path => Path.GetExtension(path).Equals(".chd", StringComparison.OrdinalIgnoreCase));
        var iso = inputs.FirstOrDefault(path => Path.GetExtension(path).Equals(".iso", StringComparison.OrdinalIgnoreCase));

        if (cue is null && chd is null && iso is null)
        {
            throw new InvalidDataException("Não foi encontrado CUE/BIN, ISO ou CHD válido de PlayStation.");
        }

        var metadata = await new LibretroMetadataClient().FetchAsync(original.Name, Path.Combine(workDirectory, "metadata"), cancellationToken);
        var final = Path.Combine(workDirectory, Sanitize(metadata.Title) + ".chd");

        if (chd is not null)
        {
            File.Copy(chd, final, true);
        }
        else
        {
            status?.Report("Convertendo para CHD e verificando integridade…");
            await new ChdmanRunner(chdmanPath).ConvertAndVerifyAsync(cue ?? iso!, final, status, cancellationToken);
        }

        var canonicalSha = await FileHash.Sha256Async(final, cancellationToken);
        return new PreparedGame(original.FullName, originalHash, workDirectory, cue ?? iso ?? chd!, final, metadata, canonicalSha, "ps1");
    }

    private static string Sanitize(string value) =>
        string.Concat(value.Select(ch => Path.GetInvalidFileNameChars().Contains(ch) ? '_' : ch)).Trim();
}

public sealed class CartridgeImportStrategy : IPlatformImportStrategy
{
    public string PlatformId { get; }
    private readonly HashSet<string> supportedExtensions;
    private readonly string preferredExtension;

    public CartridgeImportStrategy(string platformId, IEnumerable<string> extensions, string preferredExtension)
    {
        PlatformId = platformId;
        supportedExtensions = new HashSet<string>(extensions.Select(e => e.ToLowerInvariant()));
        this.preferredExtension = preferredExtension.StartsWith('.') ? preferredExtension.ToLowerInvariant() : "." + preferredExtension.ToLowerInvariant();
    }

    public bool CanHandle(string extension, string? detectedPlatform = null)
    {
        if (string.Equals(detectedPlatform, PlatformId, StringComparison.OrdinalIgnoreCase))
        {
            return true;
        }

        return supportedExtensions.Contains(extension.ToLowerInvariant());
    }

    public async Task<PreparedGame> ImportAsync(
        string inputPath,
        string originalHash,
        string workDirectory,
        IProgress<string>? status = null,
        CancellationToken cancellationToken = default)
    {
        var original = new FileInfo(inputPath);
        var sourceRoot = Path.Combine(workDirectory, "source");
        Directory.CreateDirectory(sourceRoot);

        IReadOnlyList<string> inputs;
        var ext = original.Extension.ToLowerInvariant();
        if (ext is ".7z" or ".zip")
        {
            status?.Report($"Extraindo compactado de {PlatformId.ToUpperInvariant()}…");
            inputs = await ArchiveExtractor.ExtractAsync(original.FullName, sourceRoot, null, cancellationToken);
        }
        else
        {
            var copy = Path.Combine(sourceRoot, original.Name);
            File.Copy(original.FullName, copy, true);
            inputs = [copy];
        }

        var romFile = inputs.FirstOrDefault(p => supportedExtensions.Contains(Path.GetExtension(p).ToLowerInvariant()))
            ?? throw new InvalidDataException($"Nenhum arquivo de ROM compatível com {PlatformId} foi encontrado no pacote.");

        var romInfo = new FileInfo(romFile);
        if (romInfo.Length < 64)
        {
            throw new InvalidDataException($"Arquivo de ROM corrompido ou excessivamente pequeno ({romInfo.Length} bytes).");
        }

        var parsedTitle = GameTitleParser.Parse(original.Name);
        var metadata = await new LibretroMetadataClient().FetchAsync(original.Name, Path.Combine(workDirectory, "metadata"), cancellationToken);

        var finalExt = Path.GetExtension(romFile).ToLowerInvariant();
        var final = Path.Combine(workDirectory, Sanitize(metadata.Title) + finalExt);
        File.Copy(romFile, final, true);

        var canonicalSha = await FileHash.Sha256Async(final, cancellationToken);
        return new PreparedGame(original.FullName, originalHash, workDirectory, romFile, final, metadata, canonicalSha, PlatformId);
    }

    private static string Sanitize(string value) =>
        string.Concat(value.Select(ch => Path.GetInvalidFileNameChars().Contains(ch) ? '_' : ch)).Trim();
}
