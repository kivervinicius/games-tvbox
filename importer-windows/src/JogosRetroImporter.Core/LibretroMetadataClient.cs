namespace JogosRetroImporter.Core;

public sealed record GameMetadata(string Title, string Region, GamePlatform Platform, string CoverPath, int Confidence);

public sealed class LibretroMetadataClient(HttpClient? http = null)
{
    private readonly HttpClient http = http ?? new HttpClient();

    public async Task<GameMetadata> FetchAsync(string sourceName, string destination, CancellationToken cancellationToken = default)
    {
        var parsed = GameTitleParser.Parse(sourceName);
        Directory.CreateDirectory(destination);
        var candidates = new[] { Path.GetFileNameWithoutExtension(sourceName), parsed.Title + (string.IsNullOrEmpty(parsed.Region) ? "" : $" ({parsed.Region})") }.Distinct();
        foreach (var candidate in candidates)
        {
            try
            {
                var encoded = Uri.EscapeDataString(candidate + ".png").Replace("%2F", "/");
                var url = $"https://raw.githubusercontent.com/libretro-thumbnails/Sony_-_PlayStation/master/Named_Boxarts/{encoded}";
                using var response = await http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
                if (!response.IsSuccessStatusCode) continue;
                var cover = Path.Combine(destination, "cover.png");
                await using var output = new FileStream(cover, FileMode.Create, FileAccess.Write, FileShare.None, 81920, true);
                await response.Content.CopyToAsync(output, cancellationToken);
                return new GameMetadata(parsed.Title, parsed.Region, GamePlatform.PlayStation, cover, candidate == Path.GetFileNameWithoutExtension(sourceName) ? 95 : 80);
            }
            catch (HttpRequestException)
            {
                break;
            }
        }
        return new GameMetadata(parsed.Title, parsed.Region, GamePlatform.PlayStation, "", 60);
    }
}
