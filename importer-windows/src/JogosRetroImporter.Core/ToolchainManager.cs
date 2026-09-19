using System.Text.Json;

namespace JogosRetroImporter.Core;

public sealed class ToolchainManager
{
    public string CacheDirectory { get; }
    public string ChdmanPath => Path.Combine(CacheDirectory, "chdman.exe");
    public ToolchainManager(string? cacheDirectory = null) => CacheDirectory = cacheDirectory ?? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "JogosRetro", "Importer", "tools");

    public async Task<bool> IsHealthyAsync(string lockPath, CancellationToken cancellationToken = default)
    {
        if (!File.Exists(ChdmanPath) || !File.Exists(lockPath)) return false;
        var manifest = JsonSerializer.Deserialize<ToolchainManifest>(await File.ReadAllTextAsync(lockPath, cancellationToken), new JsonSerializerOptions { PropertyNameCaseInsensitive = true });
        return manifest?.IsValid() == true && string.Equals(manifest.Sha256, await FileHash.Sha256Async(ChdmanPath, cancellationToken), StringComparison.OrdinalIgnoreCase);
    }

    public async Task RepairAsync(string bundledTool, string lockPath, CancellationToken cancellationToken = default)
    {
        if (!File.Exists(bundledTool)) throw new FileNotFoundException("A distribuição não contém chdman.exe. Baixe novamente o instalador ou o pacote portátil.", bundledTool);
        var manifest = JsonSerializer.Deserialize<ToolchainManifest>(await File.ReadAllTextAsync(lockPath, cancellationToken), new JsonSerializerOptions { PropertyNameCaseInsensitive = true }) ?? throw new InvalidDataException("toolchain.lock.json inválido.");
        if (!manifest.IsValid() || !string.Equals(manifest.Sha256, await FileHash.Sha256Async(bundledTool, cancellationToken), StringComparison.OrdinalIgnoreCase)) throw new InvalidDataException("A ferramenta incluída não corresponde ao SHA-256 fixado.");
        Directory.CreateDirectory(CacheDirectory); File.Copy(bundledTool, ChdmanPath, true);
    }
}
