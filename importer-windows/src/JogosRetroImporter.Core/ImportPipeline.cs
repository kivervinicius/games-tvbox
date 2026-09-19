namespace JogosRetroImporter.Core;

public sealed record PreparedGame(string OriginalPath, string OriginalSha256, string WorkDirectory, string DiscSource, string FinalFile, GameMetadata Metadata);

public sealed class ImportPipeline
{
    private readonly string cacheRoot;
    private readonly string chdman;
    public ImportPipeline(string chdman, string? cacheRoot = null)
    {
        this.chdman = chdman;
        this.cacheRoot = cacheRoot ?? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "JogosRetro", "Importer");
    }

    public async Task<PreparedGame> PreparePlayStationAsync(string inputPath, IProgress<string>? status = null, CancellationToken cancellationToken = default)
    {
        var original = new FileInfo(inputPath); if (!original.Exists) throw new FileNotFoundException("Arquivo de entrada não encontrado.", inputPath);
        var originalHash = await FileHash.Sha256Async(original.FullName, cancellationToken);
        var work = Path.Combine(cacheRoot, "sessions", Guid.NewGuid().ToString("N")); Directory.CreateDirectory(work);
        var sourceRoot = Path.Combine(work, "source"); Directory.CreateDirectory(sourceRoot);
        IReadOnlyList<string> inputs;
        if (new[] { ".7z", ".zip" }.Contains(original.Extension.ToLowerInvariant())) { status?.Report("Extraindo arquivo sem alterar o original…"); inputs = await ArchiveExtractor.ExtractAsync(original.FullName, sourceRoot, null, cancellationToken); }
        else { var copy = Path.Combine(sourceRoot, original.Name); File.Copy(original.FullName, copy); inputs = [copy]; }
        var cue = inputs.FirstOrDefault(path => Path.GetExtension(path).Equals(".cue", StringComparison.OrdinalIgnoreCase));
        var chd = inputs.FirstOrDefault(path => Path.GetExtension(path).Equals(".chd", StringComparison.OrdinalIgnoreCase));
        if (cue is null && chd is null) throw new InvalidDataException("Não foi encontrado CUE/BIN ou CHD de PlayStation.");
        var metadata = await new LibretroMetadataClient().FetchAsync(original.Name, Path.Combine(work, "metadata"), cancellationToken);
        var final = Path.Combine(work, Sanitize(metadata.Title) + ".chd");
        if (chd is not null) File.Copy(chd, final);
        else { status?.Report("Convertendo para CHD e verificando integridade…"); await new ChdmanRunner(chdman).ConvertAndVerifyAsync(cue!, final, status, cancellationToken); }
        if (originalHash != await FileHash.Sha256Async(original.FullName, cancellationToken)) throw new IOException("O arquivo original foi alterado durante a importação.");
        return new PreparedGame(original.FullName, originalHash, work, cue ?? chd!, final, metadata);
    }

    private static string Sanitize(string value) => string.Concat(value.Select(ch => Path.GetInvalidFileNameChars().Contains(ch) ? '_' : ch)).Trim();
}
