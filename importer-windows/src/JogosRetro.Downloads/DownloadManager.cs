using System.Collections.Concurrent;
using System.Diagnostics;
using System.Net;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using JogosRetroImporter.Core;

namespace JogosRetro.Downloads;

public sealed class DownloadManager : IDisposable
{
    private readonly HttpClient http;
    private readonly IDownloadRepository repository;
    private readonly IGameSourceProvider? sourceProvider;
    private readonly string stagingDirectory;
    private readonly int maxConcurrent;

    private readonly ConcurrentDictionary<string, DownloadJob> jobs = new();
    private readonly SemaphoreSlim concurrencySemaphore;
    private readonly CancellationTokenSource managerCts = new();
    private readonly Task processingLoopTask;
    private readonly ChannelQueue<DownloadJob> queue = new();

    public event EventHandler<DownloadProgressSnapshot>? ProgressChanged;
    public event EventHandler<DownloadJob>? StateChanged;

    public DownloadManager(
        IDownloadRepository? repository = null,
        IGameSourceProvider? sourceProvider = null,
        string? stagingDirectory = null,
        HttpClient? http = null,
        int maxConcurrent = 2)
    {
        this.repository = repository ?? new JsonDownloadRepository();
        this.sourceProvider = sourceProvider;
        this.stagingDirectory = stagingDirectory ?? AppPaths.Default.StagingDirectory;
        this.http = http ?? new HttpClient { Timeout = TimeSpan.FromMinutes(30) };
        this.maxConcurrent = Math.Max(1, maxConcurrent);

        Directory.CreateDirectory(this.stagingDirectory);
        concurrencySemaphore = new SemaphoreSlim(this.maxConcurrent, this.maxConcurrent);
        processingLoopTask = Task.Run(ProcessQueueLoopAsync);
    }

    public async Task InitializeAsync(CancellationToken cancellationToken = default)
    {
        var existing = await repository.LoadAllAsync(cancellationToken);
        foreach (var job in existing)
        {
            jobs[job.Id] = job;
            if (job.State is DownloadState.Downloading or DownloadState.Resolving or DownloadState.Queued)
            {
                job.State = DownloadState.Paused; // Safe state on re-opening
                await repository.SaveAsync(job, cancellationToken);
            }
        }
    }

    public async Task<DownloadJob> EnqueueAsync(DownloadDescriptor descriptor, string? destinationPath = null, CancellationToken cancellationToken = default)
    {
        var dest = destinationPath ?? Path.Combine(stagingDirectory, descriptor.Filename);
        var part = dest + ".part";

        var job = new DownloadJob
        {
            GameId = descriptor.GameId,
            SourceUrl = descriptor.DownloadUrl,
            ResolvedUrl = descriptor.DownloadUrl,
            Filename = descriptor.Filename,
            DestinationPath = dest,
            TempPartPath = part,
            TotalBytes = descriptor.SizeBytes,
            ExpectedSha256 = descriptor.ExpectedSha256,
            SupportsRange = descriptor.SupportsRange,
            ExpiresAt = descriptor.ExpiresAt,
            State = DownloadState.Queued
        };

        if (descriptor.Headers != null)
        {
            foreach (var (k, v) in descriptor.Headers) job.Headers[k] = v;
        }

        jobs[job.Id] = job;
        await repository.SaveAsync(job, cancellationToken);
        NotifyState(job);
        queue.Enqueue(job);
        return job;
    }

    public async Task PauseAsync(string jobId)
    {
        if (jobs.TryGetValue(jobId, out var job))
        {
            job.State = DownloadState.Paused;
            job.Cts?.Cancel();
            job.UpdatedAt = DateTimeOffset.UtcNow;
            await repository.SaveAsync(job);
            NotifyState(job);
        }
    }

    public async Task ResumeAsync(string jobId)
    {
        if (jobs.TryGetValue(jobId, out var job) && (job.State == DownloadState.Paused || job.State == DownloadState.Failed))
        {
            job.State = DownloadState.Queued;
            job.ErrorMessage = null;
            job.RetryCount = 0;
            job.UpdatedAt = DateTimeOffset.UtcNow;
            await repository.SaveAsync(job);
            NotifyState(job);
            queue.Enqueue(job);
        }
    }

    public async Task CancelAsync(string jobId)
    {
        if (jobs.TryGetValue(jobId, out var job))
        {
            job.Cts?.Cancel();
            job.State = DownloadState.Cancelled;
            job.UpdatedAt = DateTimeOffset.UtcNow;
            await repository.SaveAsync(job);
            NotifyState(job);

            if (File.Exists(job.TempPartPath))
            {
                try { File.Delete(job.TempPartPath); } catch { }
            }
        }
    }

    public DownloadJob? GetJob(string jobId) => jobs.GetValueOrDefault(jobId);
    public IReadOnlyList<DownloadJob> GetAllJobs() => jobs.Values.OrderByDescending(j => j.CreatedAt).ToList();

    private async Task ProcessQueueLoopAsync()
    {
        while (!managerCts.Token.IsCancellationRequested)
        {
            DownloadJob job;
            try
            {
                job = await queue.DequeueAsync(managerCts.Token);
            }
            catch (OperationCanceledException)
            {
                break;
            }

            if (job.State != DownloadState.Queued) continue;

            await concurrencySemaphore.WaitAsync(managerCts.Token);
            _ = Task.Run(async () =>
            {
                try
                {
                    await ExecuteJobAsync(job, managerCts.Token);
                }
                finally
                {
                    concurrencySemaphore.Release();
                }
            });
        }
    }

    private async Task ExecuteJobAsync(DownloadJob job, CancellationToken managerToken)
    {
        using var jobCts = CancellationTokenSource.CreateLinkedTokenSource(managerToken);
        job.Cts = jobCts;
        var token = jobCts.Token;

        try
        {
            job.State = DownloadState.Downloading;
            job.UpdatedAt = DateTimeOffset.UtcNow;
            NotifyState(job);

            while (!token.IsCancellationRequested)
            {
                try
                {
                    await DownloadStreamAsync(job, token);

                    if (job.State == DownloadState.Downloading)
                    {
                        // Download completed successfully, proceed to verification
                        await VerifyAndFinalizeAsync(job, token);
                        break;
                    }
                }
                catch (OperationCanceledException)
                {
                    if (job.State is DownloadState.Paused or DownloadState.Cancelled || token.IsCancellationRequested)
                    {
                        break;
                    }
                }
                catch (Exception ex)
                {
                    if (job.State is DownloadState.Paused or DownloadState.Cancelled || token.IsCancellationRequested)
                    {
                        break;
                    }

                    if (IsExpiredUrlException(ex) && sourceProvider != null)
                    {
                        job.State = DownloadState.Resolving;
                        NotifyState(job);
                        try
                        {
                            var refreshed = await sourceProvider.ResolveDownloadAsync(job.GameId, token);
                            job.ResolvedUrl = refreshed.DownloadUrl;
                            job.ExpiresAt = refreshed.ExpiresAt;
                            job.State = DownloadState.Downloading;
                            NotifyState(job);
                            continue;
                        }
                        catch (Exception refreshEx)
                        {
                            job.ErrorMessage = $"Falha ao renovar URL expirada: {refreshEx.Message}";
                        }
                    }

                    if (job.RetryCount < job.MaxRetries && !token.IsCancellationRequested)
                    {
                        job.RetryCount++;
                        var backoffSeconds = Math.Min(30, Math.Pow(2, job.RetryCount) * 0.5);
                        await Task.Delay(TimeSpan.FromSeconds(backoffSeconds), token);
                        continue;
                    }

                    job.State = DownloadState.Failed;
                    job.ErrorMessage = ex.Message;
                    job.UpdatedAt = DateTimeOffset.UtcNow;
                    await repository.SaveAsync(job);
                    NotifyState(job);
                    break;
                }
            }
        }
        finally
        {
            job.Cts = null;
        }
    }

    private async Task DownloadStreamAsync(DownloadJob job, CancellationToken cancellationToken)
    {
        long existingBytes = 0;
        if (File.Exists(job.TempPartPath))
        {
            existingBytes = new FileInfo(job.TempPartPath).Length;
            job.DownloadedBytes = existingBytes;
        }

        using var request = new HttpRequestMessage(HttpMethod.Get, job.ResolvedUrl);
        foreach (var (k, v) in job.Headers) request.Headers.TryAddWithoutValidation(k, v);

        var resume = false;
        if (existingBytes > 0 && job.SupportsRange)
        {
            request.Headers.Range = new RangeHeaderValue(existingBytes, null);
            resume = true;
        }

        using var response = await http.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, cancellationToken);

        if (response.StatusCode is HttpStatusCode.Forbidden or HttpStatusCode.Gone)
        {
            throw new HttpRequestException("HTTP 403/410: Ticket ou URL de download expirado.", null, response.StatusCode);
        }

        if (!response.IsSuccessStatusCode)
        {
            throw new HttpRequestException($"Servidor retornou status de erro: {(int)response.StatusCode} {response.ReasonPhrase}", null, response.StatusCode);
        }

        var isPartial = response.StatusCode == HttpStatusCode.PartialContent;
        if (resume && !isPartial)
        {
            // Server ignored Range header and sent full 200 OK
            existingBytes = 0;
            job.DownloadedBytes = 0;
        }

        if (response.Content.Headers.ContentRange?.Length is long totalFromRange)
        {
            job.TotalBytes = totalFromRange;
            job.SupportsRange = true;
        }
        else if (response.Content.Headers.ContentLength is long contentLen)
        {
            job.TotalBytes = isPartial ? existingBytes + contentLen : contentLen;
        }

        if (response.Headers.ETag != null) job.ETag = response.Headers.ETag.ToString();

        await using var networkStream = await response.Content.ReadAsStreamAsync(cancellationToken);
        var fileMode = (resume && isPartial) ? FileMode.Append : FileMode.Create;
        await using var fileStream = new FileStream(job.TempPartPath, fileMode, FileAccess.Write, FileShare.None, 64 * 1024, true);

        var buffer = new byte[64 * 1024];
        var sw = Stopwatch.StartNew();
        var speedWindowStart = sw.ElapsedMilliseconds;
        var bytesSinceWindow = 0L;
        var speedBytesPerSec = 0.0;

        int bytesRead;
        while ((bytesRead = await networkStream.ReadAsync(buffer, cancellationToken)) > 0)
        {
            await fileStream.WriteAsync(buffer.AsMemory(0, bytesRead), cancellationToken);
            job.DownloadedBytes += bytesRead;
            bytesSinceWindow += bytesRead;

            var elapsed = sw.ElapsedMilliseconds;
            if (elapsed - speedWindowStart >= 500)
            {
                var seconds = (elapsed - speedWindowStart) / 1000.0;
                speedBytesPerSec = bytesSinceWindow / seconds;
                speedWindowStart = elapsed;
                bytesSinceWindow = 0;

                ReportProgress(job, speedBytesPerSec);
                _ = repository.SaveAsync(job);
            }
        }

        ReportProgress(job, 0);
        await repository.SaveAsync(job);
    }

    private async Task VerifyAndFinalizeAsync(DownloadJob job, CancellationToken cancellationToken)
    {
        job.State = DownloadState.Verifying;
        NotifyState(job);

        var hash = await FileHash.Sha256Async(job.TempPartPath, cancellationToken);
        job.ComputedSha256 = hash;

        if (!string.IsNullOrEmpty(job.ExpectedSha256) &&
            !string.Equals(hash, job.ExpectedSha256, StringComparison.OrdinalIgnoreCase))
        {
            job.State = DownloadState.Failed;
            job.ErrorMessage = $"Checksum incorreto: esperado {job.ExpectedSha256}, obtido {hash}";
            job.UpdatedAt = DateTimeOffset.UtcNow;
            await repository.SaveAsync(job, cancellationToken);
            NotifyState(job);
            return;
        }

        Directory.CreateDirectory(Path.GetDirectoryName(job.DestinationPath)!);
        File.Move(job.TempPartPath, job.DestinationPath, true);

        job.State = DownloadState.Completed;
        job.DownloadedBytes = job.TotalBytes > 0 ? job.TotalBytes : new FileInfo(job.DestinationPath).Length;
        job.UpdatedAt = DateTimeOffset.UtcNow;
        await repository.SaveAsync(job, cancellationToken);
        NotifyState(job);
    }

    private void ReportProgress(DownloadJob job, double speedBytesPerSec)
    {
        var percent = job.TotalBytes > 0 ? Math.Min(100.0, (double)job.DownloadedBytes / job.TotalBytes * 100.0) : 0.0;
        TimeSpan? remaining = null;
        if (speedBytesPerSec > 0 && job.TotalBytes > job.DownloadedBytes)
        {
            var seconds = (job.TotalBytes - job.DownloadedBytes) / speedBytesPerSec;
            remaining = TimeSpan.FromSeconds(seconds);
        }

        var snapshot = new DownloadProgressSnapshot(
            job.Id,
            job.State,
            job.DownloadedBytes,
            job.TotalBytes,
            percent,
            speedBytesPerSec,
            remaining,
            job.ErrorMessage
        );

        ProgressChanged?.Invoke(this, snapshot);
    }

    private void NotifyState(DownloadJob job)
    {
        StateChanged?.Invoke(this, job);
        ReportProgress(job, 0);
    }

    private static bool IsExpiredUrlException(Exception ex)
    {
        if (ex is HttpRequestException { StatusCode: HttpStatusCode.Forbidden or HttpStatusCode.Gone }) return true;
        return ex.Message.Contains("403") || ex.Message.Contains("410");
    }

    public void Dispose()
    {
        managerCts.Cancel();
        try { processingLoopTask.Wait(TimeSpan.FromSeconds(2)); } catch { }
        managerCts.Dispose();
        concurrencySemaphore.Dispose();
    }

    private sealed class ChannelQueue<T>
    {
        private readonly ConcurrentQueue<T> items = new();
        private readonly SemaphoreSlim signal = new(0);

        public void Enqueue(T item)
        {
            items.Enqueue(item);
            signal.Release();
        }

        public async Task<T> DequeueAsync(CancellationToken cancellationToken)
        {
            while (true)
            {
                await signal.WaitAsync(cancellationToken);
                if (items.TryDequeue(out var item)) return item;
            }
        }
    }
}
