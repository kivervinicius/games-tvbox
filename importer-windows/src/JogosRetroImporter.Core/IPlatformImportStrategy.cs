namespace JogosRetroImporter.Core;

public interface IPlatformImportStrategy
{
    string PlatformId { get; }
    bool CanHandle(string extension, string? detectedPlatform = null);
    Task<PreparedGame> ImportAsync(
        string inputPath,
        string originalHash,
        string workDirectory,
        IProgress<string>? status = null,
        CancellationToken cancellationToken = default
    );
}
