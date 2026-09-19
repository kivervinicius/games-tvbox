using System.Text.RegularExpressions;

namespace JogosRetroImporter.Core;

public sealed record CueTrack(int Number, string Mode);

public sealed class CueSheet
{
    public string Path { get; }
    public IReadOnlyList<string> ReferencedFiles { get; }
    public IReadOnlyList<CueTrack> Tracks { get; }
    public long TotalBytes => ReferencedFiles.Sum(file => new FileInfo(file).Length);

    private CueSheet(string path, IReadOnlyList<string> files, IReadOnlyList<CueTrack> tracks)
        => (Path, ReferencedFiles, Tracks) = (path, files, tracks);

    public static CueSheet Load(string path)
    {
        var cue = new FileInfo(path);
        if (!cue.Exists) throw new FileNotFoundException("Arquivo CUE não encontrado.", path);
        var root = cue.Directory!.FullName.TrimEnd(PathSeparator()) + System.IO.Path.DirectorySeparatorChar;
        var files = new List<string>(); var tracks = new List<CueTrack>();
        foreach (var raw in File.ReadLines(cue.FullName))
        {
            var line = raw.Trim();
            var file = Regex.Match(line, "^FILE\\s+(?:\"(?<quoted>[^\"]+)\"|(?<plain>\\S+))\\s+", RegexOptions.IgnoreCase);
            if (file.Success)
            {
                var relative = file.Groups["quoted"].Success ? file.Groups["quoted"].Value : file.Groups["plain"].Value;
                var resolved = System.IO.Path.GetFullPath(System.IO.Path.Combine(cue.DirectoryName!, relative));
                if (!resolved.StartsWith(root, StringComparison.OrdinalIgnoreCase)) throw new InvalidDataException("O CUE tenta acessar um arquivo fora da pasta extraída.");
                if (!File.Exists(resolved)) throw new InvalidDataException($"Faixa referenciada não encontrada: {relative}");
                if (!files.Contains(resolved, StringComparer.OrdinalIgnoreCase)) files.Add(resolved);
            }
            var track = Regex.Match(line, "^TRACK\\s+(?<number>\\d+)\\s+(?<mode>[A-Z0-9/]+)", RegexOptions.IgnoreCase);
            if (track.Success) tracks.Add(new CueTrack(int.Parse(track.Groups["number"].Value), track.Groups["mode"].Value.ToUpperInvariant()));
        }
        if (files.Count == 0 || tracks.Count == 0) throw new InvalidDataException("O CUE não contém faixas válidas.");
        return new CueSheet(cue.FullName, files, tracks);
    }

    private static char PathSeparator() => System.IO.Path.DirectorySeparatorChar;
}
