namespace JogosRetroImporter.Core;

public interface IToolchainResolver
{
    string ChdmanExecutableName { get; }
    string ResolveChdmanPath(string? customToolsDir = null);
    bool IsExecutableAvailable(string executablePath);
    void EnsureExecutable(string executablePath);
}

public sealed class ToolchainResolver : IToolchainResolver
{
    private readonly IPlatformServices platform;

    public ToolchainResolver(IPlatformServices? platform = null)
    {
        this.platform = platform ?? PlatformServices.Instance;
    }

    public string ChdmanExecutableName => platform.IsWindows ? "chdman.exe" : "chdman";

    public string ResolveChdmanPath(string? customToolsDir = null)
    {
        var exeName = ChdmanExecutableName;

        // 1. Check custom tools dir
        if (!string.IsNullOrWhiteSpace(customToolsDir))
        {
            var candidate = Path.Combine(customToolsDir, exeName);
            if (File.Exists(candidate)) return candidate;
        }

        // 2. Check standard app tools directory
        var standardDir = AppPaths.Default.ToolsDirectory;
        var standardPath = Path.Combine(standardDir, exeName);
        if (File.Exists(standardPath)) return standardPath;

        // 3. Check application base directory / relative tools
        var baseDir = AppContext.BaseDirectory;
        var bundledPath = Path.Combine(baseDir, "tools", exeName);
        if (File.Exists(bundledPath)) return bundledPath;
        var directPath = Path.Combine(baseDir, exeName);
        if (File.Exists(directPath)) return directPath;

        // 4. Check system PATH
        var pathEnv = Environment.GetEnvironmentVariable("PATH");
        if (!string.IsNullOrWhiteSpace(pathEnv))
        {
            var separator = platform.IsWindows ? ';' : ':';
            foreach (var dir in pathEnv.Split(separator, StringSplitOptions.RemoveEmptyEntries))
            {
                try
                {
                    var found = Path.Combine(dir.Trim(), exeName);
                    if (File.Exists(found)) return found;
                }
                catch { }
            }
        }

        return standardPath;
    }

    public bool IsExecutableAvailable(string executablePath)
    {
        return File.Exists(executablePath);
    }

    public void EnsureExecutable(string executablePath)
    {
        if ((OperatingSystem.IsLinux() || OperatingSystem.IsMacOS()) && File.Exists(executablePath))
        {
            try
            {
                File.SetUnixFileMode(executablePath,
                    UnixFileMode.UserRead | UnixFileMode.UserWrite | UnixFileMode.UserExecute |
                    UnixFileMode.GroupRead | UnixFileMode.GroupExecute |
                    UnixFileMode.OtherRead | UnixFileMode.OtherExecute);
            }
            catch { }
        }
    }
}
