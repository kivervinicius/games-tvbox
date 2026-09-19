namespace JogosRetroImporter.Core;

public enum PipelineStatus
{
    Supported,
    Planned,
    Deprecated
}

public sealed record PlatformDefinition(
    string Id,
    string DisplayName,
    string[] Extensions,
    PipelineStatus Status,
    string PreferredFormat,
    string RemoteDirectory,
    string Emulator,
    string[] CoreCandidates
);

public static class PlatformRegistry
{
    public static readonly IReadOnlyList<PlatformDefinition> Platforms = new List<PlatformDefinition>
    {
        new(
            "ps1",
            "PlayStation",
            [".cue", ".bin", ".iso", ".chd"],
            PipelineStatus.Supported,
            "chd",
            "/sdcard/roms/ps1",
            "retroarch",
            ["pcsx_rearmed_libretro_android.so"]
        ),
        new(
            "snes",
            "Super Nintendo (SNES)",
            [".smc", ".sfc"],
            PipelineStatus.Planned,
            "sfc",
            "/sdcard/roms/snes",
            "retroarch",
            ["snes9x_libretro_android.so"]
        ),
        new(
            "nes",
            "Nintendo Entertainment System (NES)",
            [".nes"],
            PipelineStatus.Planned,
            "nes",
            "/sdcard/roms/nes",
            "retroarch",
            ["fceumm_libretro_android.so", "nestopia_libretro_android.so"]
        ),
        new(
            "megadrive",
            "Sega Mega Drive / Genesis",
            [".bin", ".gen", ".smd", ".md"],
            PipelineStatus.Planned,
            "md",
            "/sdcard/roms/megadrive",
            "retroarch",
            ["genesis_plus_gx_libretro_android.so"]
        ),
        new(
            "gba",
            "Game Boy Advance",
            [".gba"],
            PipelineStatus.Planned,
            "gba",
            "/sdcard/roms/gba",
            "retroarch",
            ["mgba_libretro_android.so"]
        ),
        new(
            "n64",
            "Nintendo 64",
            [".z64", ".n64", ".v64"],
            PipelineStatus.Planned,
            "z64",
            "/sdcard/roms/n64",
            "retroarch",
            ["mupen64plus_next_libretro_android.so"]
        ),
        new(
            "android",
            "Android Native",
            [".apk"],
            PipelineStatus.Supported,
            "apk",
            "",
            "native",
            []
        )
    }.AsReadOnly();

    public static PlatformDefinition? FindById(string? id)
    {
        if (string.IsNullOrWhiteSpace(id)) return null;
        var normalized = id.Trim().ToLowerInvariant();
        return Platforms.FirstOrDefault(p => p.Id == normalized || p.DisplayName.Equals(id, StringComparison.OrdinalIgnoreCase));
    }

    public static PlatformDefinition? FindByExtension(string? filePath)
    {
        if (string.IsNullOrWhiteSpace(filePath)) return null;
        var ext = Path.GetExtension(filePath).ToLowerInvariant();
        return Platforms.FirstOrDefault(p => p.Extensions.Contains(ext));
    }

    public static bool IsPipelineSupported(string? id)
    {
        var platform = FindById(id);
        return platform != null && platform.Status == PipelineStatus.Supported;
    }
}
