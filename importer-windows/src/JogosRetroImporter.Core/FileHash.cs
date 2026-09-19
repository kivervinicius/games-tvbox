using System.Security.Cryptography;

namespace JogosRetroImporter.Core;

public static class FileHash
{
    public static async Task<string> Sha256Async(string path, CancellationToken cancellationToken = default)
    {
        await using var input = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read, 1024 * 1024, true);
        using var hash = SHA256.Create();
        var bytes = await hash.ComputeHashAsync(input, cancellationToken);
        return Convert.ToHexString(bytes).ToLowerInvariant();
    }
}
