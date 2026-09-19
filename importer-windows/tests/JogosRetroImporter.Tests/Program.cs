using JogosRetroImporter.Core;

var root = Path.Combine(Path.GetTempPath(), "JogosRetroImporterTests", Guid.NewGuid().ToString("N"));
Directory.CreateDirectory(root);
try
{
    var bin = Path.Combine(root, "Mega Man X4 (USA).bin");
    await File.WriteAllBytesAsync(bin, new byte[2352 * 4]);
    var cue = Path.Combine(root, "Mega Man X4 (USA).cue");
    await File.WriteAllTextAsync(cue, "FILE \"Mega Man X4 (USA).bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\n");

    var sheet = CueSheet.Load(cue);
    Assert(sheet.Tracks.Count == 1, "CUE track was not detected");
    Assert(sheet.ReferencedFiles.Single() == bin, "CUE file was not resolved safely");
    Assert(sheet.TotalBytes == new FileInfo(bin).Length, "CUE byte count is wrong");

    var title = GameTitleParser.Parse("Mega Man X4 (USA).7z");
    Assert(title.Title == "Mega Man X4" && title.Region == "USA", "Title and region were not normalized");
    var noIntro = GameTitleParser.Parse("CTR - Crash Team Racing (E) (No EDC) [SCES-02105].7z");
    Assert(noIntro.Title == "CTR - Crash Team Racing" && noIntro.Region == "Europe", "No-Intro title was not normalized");
    Assert(PlatformDetector.Detect("game.cue") == GamePlatform.PlayStation, "PlayStation detection failed");
    Assert(PlatformDetector.Detect("game.gba") == GamePlatform.GameBoyAdvance, "GBA detection failed");

    var originalHash = await FileHash.Sha256Async(cue);
    var copy = Path.Combine(root, "copy.cue"); File.Copy(cue, copy);
    Assert(originalHash == await FileHash.Sha256Async(copy), "SHA-256 is not stable");

    var unsafeCue = Path.Combine(root, "unsafe.cue");
    await File.WriteAllTextAsync(unsafeCue, "FILE \"..\\outside.bin\" BINARY\n TRACK 01 MODE2/2352\n");
    AssertThrows(() => CueSheet.Load(unsafeCue), "unsafe parent path must be rejected");

    var tools = new ToolchainManifest("mame-chdman", "0.289", "BSD-3-Clause", "https://github.com/mamedev/mame/tree/mame0289", new string('a', 64));
    Assert(tools.IsValid(), "valid locked tool was rejected");
    Assert(!(tools with { Sha256 = "bad" }).IsValid(), "invalid tool hash was accepted");

    Console.WriteLine("PASS: importer core preserves originals and validates PlayStation input");
}
finally { try { Directory.Delete(root, true); } catch { } }

if (args.Length >= 2)
{
    var cache = args.Length >= 3 ? args[2] : Path.Combine(Path.GetTempPath(), "JogosRetroImporterReal");
    var live = await new ImportPipeline(args[1], cache).PreparePlayStationAsync(args[0], new Progress<string>(Console.WriteLine));
    Console.WriteLine($"PASS: real import {live.Metadata.Title} | {new FileInfo(live.FinalFile).Length} bytes | {live.FinalFile}");
}

static void Assert(bool condition, string message) { if (!condition) throw new Exception(message); }
static void AssertThrows(Action action, string message) { try { action(); } catch { return; } throw new Exception(message); }
