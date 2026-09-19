using System.Text.RegularExpressions;

namespace JogosRetroImporter.Core;

public enum GamePlatform { Unknown, PlayStation, Nes, Snes, MegaDrive, GameBoyAdvance }

public sealed record ParsedGameTitle(string Title, string Region);

public static partial class GameTitleParser
{
    [GeneratedRegex(@"\s*\((?<region>USA|U|E|Europe|Japan|J|World|Brazil|B)[^)]*\)\s*", RegexOptions.IgnoreCase)]
    private static partial Regex RegionExpression();

    [GeneratedRegex(@"\s*\[[^\]]+\]\s*", RegexOptions.IgnoreCase)]
    private static partial Regex BracketTagExpression();

    [GeneratedRegex(@"\s*\((?:No EDC|Rev\s*\d+|v?\d+(?:\.\d+)+|Demo|Beta|Proto|Sample)[^)]*\)\s*", RegexOptions.IgnoreCase)]
    private static partial Regex ReleaseTagExpression();

    public static ParsedGameTitle Parse(string path)
    {
        var name = Path.GetFileNameWithoutExtension(path).Trim();
        var match = RegionExpression().Match(name);
        var region = match.Success ? NormalizeRegion(match.Groups["region"].Value) : "";
        var title = BracketTagExpression().Replace(ReleaseTagExpression().Replace(RegionExpression().Replace(name, " "), " "), " ");
        title = Regex.Replace(title, @"\s+", " ").Trim(' ', '-', '_');
        return new ParsedGameTitle(title, region);
    }

    private static string NormalizeRegion(string value) => value.ToUpperInvariant() switch
    {
        "U" or "USA" => "USA",
        "E" or "EUROPE" => "Europe",
        "J" or "JAPAN" => "Japan",
        "B" or "BRAZIL" => "Brazil",
        _ => "World"
    };
}

public static class PlatformDetector
{
    public static GamePlatform Detect(string path) => Path.GetExtension(path).ToLowerInvariant() switch
    {
        ".cue" or ".bin" or ".iso" or ".chd" => GamePlatform.PlayStation,
        ".nes" => GamePlatform.Nes,
        ".sfc" or ".smc" => GamePlatform.Snes,
        ".md" or ".gen" => GamePlatform.MegaDrive,
        ".gba" => GamePlatform.GameBoyAdvance,
        _ => GamePlatform.Unknown
    };
}

public sealed record ToolchainManifest(string Id, string Version, string License, string Source, string Sha256)
{
    public bool IsValid() => !string.IsNullOrWhiteSpace(Id) && !string.IsNullOrWhiteSpace(Version)
        && Uri.TryCreate(Source, UriKind.Absolute, out var uri) && uri.Scheme == Uri.UriSchemeHttps
        && Regex.IsMatch(Sha256 ?? "", "^[a-fA-F0-9]{64}$");
}
