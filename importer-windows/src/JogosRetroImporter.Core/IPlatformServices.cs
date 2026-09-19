using System.Runtime.InteropServices;

namespace JogosRetroImporter.Core;

public interface IPlatformServices
{
    bool IsWindows { get; }
    bool IsLinux { get; }
    bool IsMacOS { get; }
    bool IsArm64 { get; }
    bool IsX64 { get; }
    string OperatingSystemName { get; }
    string ArchitectureName { get; }
    string RuntimeIdentifier { get; }
}

public sealed class PlatformServices : IPlatformServices
{
    public static readonly PlatformServices Instance = new();

    public bool IsWindows => RuntimeInformation.IsOSPlatform(OSPlatform.Windows);
    public bool IsLinux => RuntimeInformation.IsOSPlatform(OSPlatform.Linux);
    public bool IsMacOS => RuntimeInformation.IsOSPlatform(OSPlatform.OSX);

    public bool IsArm64 => RuntimeInformation.ProcessArchitecture == Architecture.Arm64;
    public bool IsX64 => RuntimeInformation.ProcessArchitecture == Architecture.X64;

    public string OperatingSystemName => IsWindows ? "Windows" : IsLinux ? "Linux" : IsMacOS ? "macOS" : "Unknown";
    public string ArchitectureName => RuntimeInformation.ProcessArchitecture.ToString().ToLowerInvariant();

    public string RuntimeIdentifier
    {
        get
        {
            var os = IsWindows ? "win" : IsLinux ? "linux" : IsMacOS ? "osx" : "unknown";
            return $"{os}-{ArchitectureName}";
        }
    }
}
