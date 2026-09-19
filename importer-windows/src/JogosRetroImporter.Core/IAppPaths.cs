namespace JogosRetroImporter.Core;

public interface IAppPaths
{
    string ConfigDirectory { get; }
    string DataDirectory { get; }
    string CacheDirectory { get; }
    string StagingDirectory { get; }
    string ToolsDirectory { get; }
    string TokenFile { get; }
}

public sealed class AppPaths : IAppPaths
{
    public static readonly AppPaths Default = new();

    public string ConfigDirectory { get; }
    public string DataDirectory { get; }
    public string CacheDirectory { get; }
    public string StagingDirectory { get; }
    public string ToolsDirectory { get; }
    public string TokenFile { get; }

    public AppPaths(IPlatformServices? platform = null, string? customRoot = null)
    {
        var services = platform ?? PlatformServices.Instance;

        if (!string.IsNullOrWhiteSpace(customRoot))
        {
            var fullRoot = Path.GetFullPath(customRoot);
            ConfigDirectory = fullRoot;
            DataDirectory = fullRoot;
            CacheDirectory = fullRoot;
            StagingDirectory = Path.Combine(fullRoot, "staging");
            ToolsDirectory = Path.Combine(fullRoot, "tools");
            TokenFile = Path.Combine(fullRoot, "publisher.token");
            return;
        }

        if (services.IsWindows)
        {
            var localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            var root = Path.Combine(localAppData, "JogosRetro", "Importer");
            ConfigDirectory = root;
            DataDirectory = root;
            CacheDirectory = root;
            StagingDirectory = Path.Combine(root, "staging");
            ToolsDirectory = Path.Combine(root, "tools");
            TokenFile = Path.Combine(root, "publisher.token");
        }
        else
        {
            var home = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
            if (string.IsNullOrWhiteSpace(home)) home = "/tmp";

            var xdgConfig = Environment.GetEnvironmentVariable("XDG_CONFIG_HOME");
            var xdgData = Environment.GetEnvironmentVariable("XDG_DATA_HOME");
            var xdgCache = Environment.GetEnvironmentVariable("XDG_CACHE_HOME");

            ConfigDirectory = !string.IsNullOrWhiteSpace(xdgConfig)
                ? Path.Combine(xdgConfig, "jogos-retro")
                : Path.Combine(home, ".config", "jogos-retro");

            DataDirectory = !string.IsNullOrWhiteSpace(xdgData)
                ? Path.Combine(xdgData, "jogos-retro")
                : Path.Combine(home, ".local", "share", "jogos-retro");

            CacheDirectory = !string.IsNullOrWhiteSpace(xdgCache)
                ? Path.Combine(xdgCache, "jogos-retro")
                : Path.Combine(home, ".cache", "jogos-retro");

            StagingDirectory = Path.Combine(CacheDirectory, "staging");
            ToolsDirectory = Path.Combine(DataDirectory, "tools");
            TokenFile = Path.Combine(ConfigDirectory, "publisher.token");
        }
    }
}
