using System.Text.Json;

namespace JogosRetro.Downloads;

public sealed class JsonDownloadRepository : IDownloadRepository
{
    private readonly string filePath;
    private readonly SemaphoreSlim fileLock = new(1, 1);
    private static readonly JsonSerializerOptions JsonOptions = new() { WriteIndented = true };

    public JsonDownloadRepository(string? filePath = null)
    {
        if (string.IsNullOrWhiteSpace(filePath))
        {
            var dataDir = JogosRetroImporter.Core.AppPaths.Default.DataDirectory;
            Directory.CreateDirectory(dataDir);
            this.filePath = Path.Combine(dataDir, "downloads.json");
        }
        else
        {
            this.filePath = filePath;
            var dir = Path.GetDirectoryName(filePath);
            if (!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);
        }
    }

    public async Task<IReadOnlyList<DownloadJob>> LoadAllAsync(CancellationToken cancellationToken = default)
    {
        await fileLock.WaitAsync(cancellationToken);
        try
        {
            if (!File.Exists(filePath)) return [];
            var text = await File.ReadAllTextAsync(filePath, cancellationToken);
            if (string.IsNullOrWhiteSpace(text)) return [];
            return JsonSerializer.Deserialize<List<DownloadJob>>(text, JsonOptions) ?? [];
        }
        catch
        {
            return [];
        }
        finally
        {
            fileLock.Release();
        }
    }

    public async Task SaveAsync(DownloadJob job, CancellationToken cancellationToken = default)
    {
        await fileLock.WaitAsync(cancellationToken);
        try
        {
            var jobs = new List<DownloadJob>();
            if (File.Exists(filePath))
            {
                var text = await File.ReadAllTextAsync(filePath, cancellationToken);
                if (!string.IsNullOrWhiteSpace(text))
                {
                    jobs = JsonSerializer.Deserialize<List<DownloadJob>>(text, JsonOptions) ?? [];
                }
            }

            var index = jobs.FindIndex(j => j.Id == job.Id);
            if (index >= 0) jobs[index] = job;
            else jobs.Add(job);

            var tempFile = $"{filePath}.{Guid.NewGuid():N}.tmp";
            await File.WriteAllTextAsync(tempFile, JsonSerializer.Serialize(jobs, JsonOptions), cancellationToken);
            File.Move(tempFile, filePath, true);
        }
        finally
        {
            fileLock.Release();
        }
    }

    public async Task DeleteAsync(string jobId, CancellationToken cancellationToken = default)
    {
        await fileLock.WaitAsync(cancellationToken);
        try
        {
            if (!File.Exists(filePath)) return;
            var text = await File.ReadAllTextAsync(filePath, cancellationToken);
            if (string.IsNullOrWhiteSpace(text)) return;
            var jobs = JsonSerializer.Deserialize<List<DownloadJob>>(text, JsonOptions) ?? [];
            if (jobs.RemoveAll(j => j.Id == jobId) > 0)
            {
                var tempFile = $"{filePath}.{Guid.NewGuid():N}.tmp";
                await File.WriteAllTextAsync(tempFile, JsonSerializer.Serialize(jobs, JsonOptions), cancellationToken);
                File.Move(tempFile, filePath, true);
            }
        }
        finally
        {
            fileLock.Release();
        }
    }
}
