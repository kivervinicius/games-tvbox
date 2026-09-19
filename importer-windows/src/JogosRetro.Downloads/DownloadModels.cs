using System.Text.Json.Serialization;

namespace JogosRetro.Downloads;

public enum DownloadState
{
    Queued,
    Resolving,
    Downloading,
    Paused,
    Verifying,
    Completed,
    Failed,
    Cancelled
}

public sealed class DownloadJob
{
    public string Id { get; set; } = Guid.NewGuid().ToString("N");
    public string GameId { get; set; } = "";
    public string SourceUrl { get; set; } = "";
    public string ResolvedUrl { get; set; } = "";
    public string Filename { get; set; } = "";
    public string DestinationPath { get; set; } = "";
    public string TempPartPath { get; set; } = "";
    public long TotalBytes { get; set; }
    public long DownloadedBytes { get; set; }
    public DownloadState State { get; set; } = DownloadState.Queued;
    public string? ErrorMessage { get; set; }
    public int RetryCount { get; set; }
    public int MaxRetries { get; set; } = 5;
    public string? ETag { get; set; }
    public string? ExpectedSha256 { get; set; }
    public string? ComputedSha256 { get; set; }
    public bool SupportsRange { get; set; } = true;
    public DateTimeOffset? ExpiresAt { get; set; }
    public Dictionary<string, string> Headers { get; set; } = new(StringComparer.OrdinalIgnoreCase);
    public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;
    public DateTimeOffset UpdatedAt { get; set; } = DateTimeOffset.UtcNow;

    [JsonIgnore]
    public CancellationTokenSource? Cts { get; set; }
}

public sealed record DownloadProgressSnapshot(
    string JobId,
    DownloadState State,
    long DownloadedBytes,
    long TotalBytes,
    double ProgressPercentage,
    double SpeedBytesPerSecond,
    TimeSpan? EstimatedRemaining,
    string? ErrorMessage = null
);

public interface IDownloadRepository
{
    Task<IReadOnlyList<DownloadJob>> LoadAllAsync(CancellationToken cancellationToken = default);
    Task SaveAsync(DownloadJob job, CancellationToken cancellationToken = default);
    Task DeleteAsync(string jobId, CancellationToken cancellationToken = default);
}
