using SharpCompress.Archives;

namespace JogosRetroImporter.Core;

public static class ArchiveExtractor
{
    public const long MaxArchiveBytes = 4L * 1024 * 1024 * 1024; // 4 GB limit

    public static async Task<IReadOnlyList<string>> ExtractAsync(string archivePath, string destination, IProgress<double>? progress = null, CancellationToken cancellationToken = default)
    {
        Directory.CreateDirectory(destination);
        var root = Path.GetFullPath(destination).TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
        using var archive = ArchiveFactory.OpenArchive(archivePath);
        var entries = archive.Entries.Where(entry => !entry.IsDirectory).ToList();

        long total = Math.Max(1, entries.Sum(entry => (long)entry.Size));
        if (total > MaxArchiveBytes)
        {
            throw new InvalidDataException($"O arquivo compactado excede o limite de tamanho descompactado permitido ({total} > {MaxArchiveBytes} bytes).");
        }

        var output = new List<string>();
        long done = 0;
        foreach (var entry in entries)
        {
            cancellationToken.ThrowIfCancellationRequested();
            var key = entry.Key ?? throw new InvalidDataException("O arquivo compactado contém uma entrada sem nome.");

            // Protection against Zip Slip / Path Traversal
            if (key.Contains("..") || Path.IsPathRooted(key))
            {
                throw new InvalidDataException($"O arquivo compactado contém um caminho inseguro: {key}");
            }

            var target = Path.GetFullPath(Path.Combine(destination, key.Replace('/', Path.DirectorySeparatorChar)));
            if (!target.StartsWith(root, StringComparison.OrdinalIgnoreCase))
            {
                throw new InvalidDataException($"O arquivo compactado tenta gravar fora da pasta de destino: {key}");
            }

            Directory.CreateDirectory(Path.GetDirectoryName(target)!);
            await using var input = entry.OpenEntryStream();
            await using var file = new FileStream(target, FileMode.Create, FileAccess.Write, FileShare.None, 1024 * 1024, true);
            await input.CopyToAsync(file, cancellationToken);
            done += (long)entry.Size;
            progress?.Report((double)done / total);
            output.Add(target);
        }
        return output;
    }
}
