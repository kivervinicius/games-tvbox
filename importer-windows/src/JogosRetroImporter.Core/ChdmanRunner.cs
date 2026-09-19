using System.Diagnostics;

namespace JogosRetroImporter.Core;

public sealed class ChdmanRunner
{
    private readonly string executable;
    public ChdmanRunner(string executable) => this.executable = executable;

    public async Task ConvertAndVerifyAsync(string cuePath, string outputPath, IProgress<string>? log = null, CancellationToken cancellationToken = default)
    {
        CueSheet.Load(cuePath);
        if (!File.Exists(executable)) throw new FileNotFoundException("chdman.exe não foi encontrado. Use Reparar ferramentas.", executable);
        Directory.CreateDirectory(Path.GetDirectoryName(outputPath)!);
        await RunAsync(["createcd", "-i", cuePath, "-o", outputPath], log, cancellationToken);
        await RunAsync(["verify", "-i", outputPath], log, cancellationToken);
        if (!File.Exists(outputPath) || new FileInfo(outputPath).Length == 0) throw new InvalidDataException("A conversão não gerou um CHD válido.");
    }

    private async Task RunAsync(IEnumerable<string> arguments, IProgress<string>? log, CancellationToken cancellationToken)
    {
        var start = new ProcessStartInfo(executable) { UseShellExecute = false, RedirectStandardOutput = true, RedirectStandardError = true, CreateNoWindow = true };
        foreach (var argument in arguments) start.ArgumentList.Add(argument);
        using var process = Process.Start(start) ?? throw new InvalidOperationException("Não foi possível iniciar chdman.");
        process.OutputDataReceived += (_, e) => { if (e.Data is not null) log?.Report(e.Data); };
        process.ErrorDataReceived += (_, e) => { if (e.Data is not null) log?.Report(e.Data); };
        process.BeginOutputReadLine(); process.BeginErrorReadLine();
        await process.WaitForExitAsync(cancellationToken);
        if (process.ExitCode != 0) throw new InvalidDataException($"chdman terminou com código {process.ExitCode}.");
    }
}
