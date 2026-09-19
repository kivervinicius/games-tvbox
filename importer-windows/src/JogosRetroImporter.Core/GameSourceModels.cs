namespace JogosRetroImporter.Core;

public sealed record PlatformInfo(
    string Id,
    string Slug,
    string Name,
    string Category,
    IReadOnlyList<string> SupportedExtensions,
    string CanonicalFormat,
    int GameCount
);

public sealed record GameSearchQuery(
    string Query,
    string? PlatformId = null,
    int Page = 1,
    int PageSize = 25
);

public sealed record GameSearchResult(
    string Id,
    string Slug,
    string PlatformId,
    string Title,
    string Region,
    int? ReleaseYear,
    string? ThumbnailUrl,
    long? ArchiveSizeBytes,
    string? SourceArchiveFormat
);

public sealed record SourceFile(
    string Filename,
    long SizeBytes,
    string? Sha256,
    string Format
);

public sealed record GameSourceDetails(
    string Id,
    string Slug,
    string PlatformId,
    string Title,
    string Region,
    int? ReleaseYear,
    string? Publisher,
    string? Developer,
    string? Description,
    string? CoverUrl,
    IReadOnlyList<string> Screenshots,
    SourceFile SourceFile
);

public sealed record DownloadDescriptor(
    string TicketId,
    string GameId,
    string DownloadUrl,
    string Filename,
    long SizeBytes,
    string? ExpectedSha256 = null,
    bool SupportsRange = true,
    DateTimeOffset? ExpiresAt = null,
    IReadOnlyDictionary<string, string>? Headers = null
);

public interface IGameSourceProvider
{
    string ProviderId { get; }
    string DisplayName { get; }

    Task<IReadOnlyList<PlatformInfo>> GetPlatformsAsync(CancellationToken cancellationToken = default);
    Task<IReadOnlyList<GameSearchResult>> SearchAsync(GameSearchQuery query, CancellationToken cancellationToken = default);
    Task<GameSourceDetails> GetDetailsAsync(string gameId, CancellationToken cancellationToken = default);
    Task<DownloadDescriptor> ResolveDownloadAsync(string gameId, CancellationToken cancellationToken = default);
}
