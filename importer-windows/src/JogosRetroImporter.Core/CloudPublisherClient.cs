using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace JogosRetroImporter.Core;

public sealed record PairingInfo(string PairId, string Code, string PairingUrl, long ExpiresAt);
public sealed record PublicationResult(string PublishedId, string Revision, int ItemCount, DateTimeOffset UpdatedAt);

public sealed class CloudPublisherClient
{
    private readonly HttpClient http;
    private readonly Uri origin;
    public CloudPublisherClient(string origin, HttpClient? http = null)
    {
        if (!Uri.TryCreate(origin.TrimEnd('/') + "/", UriKind.Absolute, out var parsed) || parsed.Scheme != Uri.UriSchemeHttps) throw new ArgumentException("Use uma URL HTTPS válida do Jogos Retro Cloud.", nameof(origin));
        this.origin = parsed; this.http = http ?? new HttpClient { Timeout = TimeSpan.FromMinutes(20) };
    }

    public async Task<PairingInfo> StartPairingAsync(string deviceId, CancellationToken cancellationToken = default)
    {
        var response = await http.PostAsJsonAsync(new Uri(origin, "api/device/pair/start"), new { deviceId, model = $"Windows {Environment.OSVersion.Version}", clientType = "importer" }, cancellationToken);
        var json = await ReadJsonAsync(response, cancellationToken);
        return new PairingInfo(json["pairId"]!.GetValue<string>(), json["code"]!.GetValue<string>(), json["pairingUrl"]!.GetValue<string>(), json["expiresAt"]!.GetValue<long>());
    }

    public async Task<string?> CompletePairingAsync(PairingInfo pairing, CancellationToken cancellationToken = default)
    {
        var response = await http.PostAsJsonAsync(new Uri(origin, "api/device/pair/complete"), new { pairId = pairing.PairId, code = pairing.Code, model = $"Windows {Environment.OSVersion.Version}" }, cancellationToken);
        if ((int)response.StatusCode == 202) return null;
        var json = await ReadJsonAsync(response, cancellationToken);
        if (json["clientType"]?.GetValue<string>() != "importer") throw new InvalidDataException("O servidor não aprovou uma credencial de importador.");
        return json["deviceToken"]!.GetValue<string>();
    }

    public async Task<JsonObject> StatusAsync(string token, CancellationToken cancellationToken = default)
        => await AuthorizedJsonAsync(HttpMethod.Get, "api/importer/status", token, null, cancellationToken);

    public async Task<PublicationResult> PublishFileAsync(string token, string file, string kind, JsonObject item, IProgress<double>? progress = null, CancellationToken cancellationToken = default)
    {
        var info = new FileInfo(file); if (!info.Exists || info.Length == 0) throw new FileNotFoundException("Arquivo final não encontrado.", file);
        var hash = await FileHash.Sha256Async(file, cancellationToken); progress?.Report(.10);
        var reservation = await AuthorizedJsonAsync(HttpMethod.Post, "api/importer/uploads", token,
            new JsonObject { ["filename"] = info.Name, ["size"] = info.Length, ["sha256"] = hash, ["kind"] = kind, ["contentType"] = ContentType(info.Extension) }, cancellationToken);
        var upload = new HttpRequestMessage(HttpMethod.Put, reservation["uploadUrl"]!.GetValue<string>());
        await using var stream = new FileStream(file, FileMode.Open, FileAccess.Read, FileShare.Read, 1024 * 1024, true);
        upload.Content = new ProgressStreamContent(stream, 1024 * 1024, value => progress?.Report(.10 + value * .75), cancellationToken);
        if (reservation["uploadHeaders"] is JsonObject headers) foreach (var header in headers) upload.Headers.TryAddWithoutValidation(header.Key, header.Value?.GetValue<string>() ?? "");
        var uploadResponse = await http.SendAsync(upload, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
        if (!uploadResponse.IsSuccessStatusCode) throw new HttpRequestException($"O R2 recusou o arquivo ({(int)uploadResponse.StatusCode}).");
        var id = reservation["uploadId"]!.GetValue<string>();
        await AuthorizedJsonAsync(HttpMethod.Post, $"api/importer/uploads/{id}/finalize", token, new JsonObject(), cancellationToken); progress?.Report(.90);
        var publication = await AuthorizedJsonAsync(HttpMethod.Post, "api/importer/publications", token,
            new JsonObject { ["uploads"] = new JsonArray(new JsonObject { ["uploadId"] = id, ["item"] = item }) }, cancellationToken);
        progress?.Report(1);
        return new PublicationResult(id, publication["revision"]!.GetValue<string>(), publication["itemCount"]!.GetValue<int>(), publication["updatedAt"]!.GetValue<DateTimeOffset>());
    }

    public async Task<PublicationResult> PublishGameAsync(
        string token,
        PreparedGame game,
        string? coverImageFile = null,
        IProgress<double>? progress = null,
        CancellationToken cancellationToken = default)
    {
        var uploads = new JsonArray();

        // 1. Upload Cover if present
        if (!string.IsNullOrEmpty(coverImageFile) && File.Exists(coverImageFile))
        {
            var coverInfo = new FileInfo(coverImageFile);
            var coverHash = await FileHash.Sha256Async(coverImageFile, cancellationToken);
            var coverRes = await AuthorizedJsonAsync(HttpMethod.Post, "api/importer/uploads", token,
                new JsonObject
                {
                    ["filename"] = coverInfo.Name,
                    ["size"] = coverInfo.Length,
                    ["sha256"] = coverHash,
                    ["kind"] = "cover",
                    ["contentType"] = ContentType(coverInfo.Extension)
                }, cancellationToken);

            var coverUpload = new HttpRequestMessage(HttpMethod.Put, coverRes["uploadUrl"]!.GetValue<string>());
            await using var coverStream = new FileStream(coverImageFile, FileMode.Open, FileAccess.Read, FileShare.Read, 64 * 1024, true);
            coverUpload.Content = new ProgressStreamContent(coverStream, 64 * 1024, v => progress?.Report(v * 0.2), cancellationToken);
            if (coverRes["uploadHeaders"] is JsonObject headers)
            {
                foreach (var h in headers) coverUpload.Headers.TryAddWithoutValidation(h.Key, h.Value?.GetValue<string>() ?? "");
            }
            var res = await http.SendAsync(coverUpload, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
            if (!res.IsSuccessStatusCode) throw new HttpRequestException($"Falha no upload da capa para R2 ({(int)res.StatusCode})");

            var coverUploadId = coverRes["uploadId"]!.GetValue<string>();
            await AuthorizedJsonAsync(HttpMethod.Post, $"api/importer/uploads/{coverUploadId}/finalize", token, new JsonObject(), cancellationToken);
            uploads.Add(new JsonObject { ["uploadId"] = coverUploadId, ["kind"] = "cover" });
        }

        // 2. Upload Game ROM / CHD
        var romInfo = new FileInfo(game.FinalFile);
        if (!romInfo.Exists || romInfo.Length == 0) throw new FileNotFoundException("Arquivo canônico do jogo não encontrado.", game.FinalFile);

        var canonicalHash = game.CanonicalSha256 ?? await FileHash.Sha256Async(game.FinalFile, cancellationToken);
        var romRes = await AuthorizedJsonAsync(HttpMethod.Post, "api/importer/uploads", token,
            new JsonObject
            {
                ["filename"] = romInfo.Name,
                ["size"] = romInfo.Length,
                ["sha256"] = canonicalHash,
                ["kind"] = "rom",
                ["contentType"] = ContentType(romInfo.Extension)
            }, cancellationToken);

        var romUpload = new HttpRequestMessage(HttpMethod.Put, romRes["uploadUrl"]!.GetValue<string>());
        await using var romStream = new FileStream(game.FinalFile, FileMode.Open, FileAccess.Read, FileShare.Read, 1024 * 1024, true);
        romUpload.Content = new ProgressStreamContent(romStream, 1024 * 1024, v => progress?.Report(0.2 + v * 0.7), cancellationToken);
        if (romRes["uploadHeaders"] is JsonObject romHeaders)
        {
            foreach (var h in romHeaders) romUpload.Headers.TryAddWithoutValidation(h.Key, h.Value?.GetValue<string>() ?? "");
        }
        var romResponse = await http.SendAsync(romUpload, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
        if (!romResponse.IsSuccessStatusCode) throw new HttpRequestException($"Falha no upload da ROM para R2 ({(int)romResponse.StatusCode})");

        var romUploadId = romRes["uploadId"]!.GetValue<string>();
        await AuthorizedJsonAsync(HttpMethod.Post, $"api/importer/uploads/{romUploadId}/finalize", token, new JsonObject(), cancellationToken);

        var gameItem = new JsonObject
        {
            ["contentId"] = game.ContentId,
            ["title"] = game.Metadata.Title,
            ["platform"] = game.PlatformId ?? "ps1",
            ["region"] = game.Metadata.Region,
            ["filename"] = romInfo.Name,
            ["sizeBytes"] = romInfo.Length,
            ["canonicalSha256"] = canonicalHash,
            ["sourceSha256"] = game.OriginalSha256,
            ["publisher"] = game.Metadata.Publisher,
            ["developer"] = game.Metadata.Developer,
            ["year"] = game.Metadata.Year,
            ["description"] = game.Metadata.Description
        };

        uploads.Add(new JsonObject { ["uploadId"] = romUploadId, ["kind"] = "rom", ["item"] = gameItem });

        // 3. Atomic publication commit
        var publication = await AuthorizedJsonAsync(HttpMethod.Post, "api/importer/publications", token,
            new JsonObject { ["uploads"] = uploads }, cancellationToken);

        progress?.Report(1.0);
        return new PublicationResult(
            romUploadId,
            publication["revision"]!.GetValue<string>(),
            publication["itemCount"]!.GetValue<int>(),
            publication["updatedAt"]!.GetValue<DateTimeOffset>()
        );
    }

    private async Task<JsonObject> AuthorizedJsonAsync(HttpMethod method, string route, string token, JsonObject? body, CancellationToken cancellationToken)
    {
        using var request = new HttpRequestMessage(method, new Uri(origin, route));
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
        if (body is not null) request.Content = new StringContent(body.ToJsonString(), Encoding.UTF8, "application/json");
        return await ReadJsonAsync(await http.SendAsync(request, cancellationToken), cancellationToken);
    }

    private static async Task<JsonObject> ReadJsonAsync(HttpResponseMessage response, CancellationToken cancellationToken)
    {
        var text = await response.Content.ReadAsStringAsync(cancellationToken);
        var json = string.IsNullOrWhiteSpace(text) ? new JsonObject() : JsonNode.Parse(text)?.AsObject() ?? new JsonObject();
        if (!response.IsSuccessStatusCode) throw new HttpRequestException(json["message"]?.GetValue<string>() ?? json["error"]?["message"]?.GetValue<string>() ?? $"Serviço indisponível ({(int)response.StatusCode}).");
        return json;
    }

    private static string ContentType(string extension) => extension.ToLowerInvariant() switch { ".png" => "image/png", ".jpg" or ".jpeg" => "image/jpeg", ".chd" => "application/octet-stream", _ => "application/octet-stream" };

    private sealed class ProgressStreamContent(Stream stream, int bufferSize, Action<double> report, CancellationToken cancellationToken) : HttpContent
    {
        protected override async Task SerializeToStreamAsync(Stream output, System.Net.TransportContext? context)
        {
            var buffer = new byte[bufferSize]; long sent = 0; int read;
            while ((read = await stream.ReadAsync(buffer, cancellationToken)) > 0) { await output.WriteAsync(buffer.AsMemory(0, read), cancellationToken); sent += read; report(stream.Length == 0 ? 1 : (double)sent / stream.Length); }
        }
        protected override bool TryComputeLength(out long length) { length = stream.Length; return true; }
    }
}
