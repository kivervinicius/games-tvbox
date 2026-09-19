using SharpCompress.Archives;

namespace JogosRetroImporter.Core;

public static class ArchiveExtractor
{
    public static async Task<IReadOnlyList<string>> ExtractAsync(string archivePath, string destination, IProgress<double>? progress = null, CancellationToken cancellationToken = default)
    {
        Directory.CreateDirectory(destination);
        var root = Path.GetFullPath(destination).TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
        using var archive = ArchiveFactory.OpenArchive(archivePath);
        var entries = archive.Entries.Where(entry => !entry.IsDirectory).ToList();
        var output = new List<string>(); long done = 0, total = Math.Max(1, entries.Sum(entry => (long)entry.Size));
        foreach (var entry in entries)
        {
            cancellationToken.ThrowIfCancellationRequested();
            var key = entry.Key ?? throw new InvalidDataException("O arquivo compactado contém uma entrada sem nome.");
            var target = Path.GetFullPath(Path.Combine(destination, key.Replace('/', Path.DirectorySeparatorChar)));
            if (!target.StartsWith(root, StringComparison.OrdinalIgnoreCase)) throw new InvalidDataException("O arquivo compactado contém um caminho inseguro.");
            Directory.CreateDirectory(Path.GetDirectoryName(target)!);
            await using var input = entry.OpenEntryStream();
            await using var file = new FileStream(target, FileMode.CreateNew, FileAccess.Write, FileShare.None, 1024 * 1024, true);
            await input.CopyToAsync(file, cancellationToken);
            done += (long)entry.Size; progress?.Report((double)done / total); output.Add(target);
        }
        return output;
    }
}
