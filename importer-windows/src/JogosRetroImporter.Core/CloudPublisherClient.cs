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
