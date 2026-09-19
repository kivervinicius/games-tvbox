using System.Text.Json;

namespace JogosRetroImporter.Core;

public sealed class ToolchainManager
{
    private readonly IToolchainResolver toolchainResolver;
    public string CacheDirectory { get; }
    public string ChdmanPath => Path.Combine(CacheDirectory, toolchainResolver.ChdmanExecutableName);

    public ToolchainManager(string? cacheDirectory = null, IToolchainResolver? toolchainResolver = null)
    {
        this.toolchainResolver = toolchainResolver ?? new ToolchainResolver();
        CacheDirectory = cacheDirectory ?? AppPaths.Default.ToolsDirectory;
    }

    public async Task<bool> IsHealthyAsync(string lockPath, CancellationToken cancellationToken = default)
    {
        if (!File.Exists(ChdmanPath) || !File.Exists(lockPath)) return false;
        var manifest = JsonSerializer.Deserialize<ToolchainManifest>(await File.ReadAllTextAsync(lockPath, cancellationToken), new JsonSerializerOptions { PropertyNameCaseInsensitive = true });
        return manifest?.IsValid() == true && string.Equals(manifest.Sha256, await FileHash.Sha256Async(ChdmanPath, cancellationToken), StringComparison.OrdinalIgnoreCase);
    }

    public async Task RepairAsync(string bundledTool, string lockPath, CancellationToken cancellationToken = default)
    {
        var exeName = toolchainResolver.ChdmanExecutableName;
        if (!File.Exists(bundledTool)) throw new FileNotFoundException($"A distribuição não contém {exeName}. Baixe novamente o instalador ou o pacote portátil.", bundledTool);
        var manifest = JsonSerializer.Deserialize<ToolchainManifest>(await File.ReadAllTextAsync(lockPath, cancellationToken), new JsonSerializerOptions { PropertyNameCaseInsensitive = true }) ?? throw new InvalidDataException("toolchain.lock.json inválido.");
        if (!manifest.IsValid() || !string.Equals(manifest.Sha256, await FileHash.Sha256Async(bundledTool, cancellationToken), StringComparison.OrdinalIgnoreCase)) throw new InvalidDataException("A ferramenta incluída não corresponde ao SHA-256 fixado.");
        Directory.CreateDirectory(CacheDirectory);
        File.Copy(bundledTool, ChdmanPath, true);
        toolchainResolver.EnsureExecutable(ChdmanPath);
    }
}
