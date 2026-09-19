using System.Security.Cryptography;
using System.Text;

namespace JogosRetroImporter.Core;

public interface ICredentialStore
{
    void Save(string key, string secret);
    string Load(string key);
    void Clear(string key);
}

public sealed class CrossPlatformCredentialStore : ICredentialStore
{
    private readonly string storageDirectory;
    private static readonly byte[] Salt = "JogosRetro.CredentialStore.Salt.v1"u8.ToArray();

    public CrossPlatformCredentialStore(string? storageDirectory = null)
    {
        this.storageDirectory = storageDirectory ?? AppPaths.Default.ConfigDirectory;
    }

    public void Save(string key, string secret)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Directory.CreateDirectory(storageDirectory);
        var targetFile = GetFilePath(key);

        if (string.IsNullOrEmpty(secret))
        {
            Clear(key);
            return;
        }

        var plainBytes = Encoding.UTF8.GetBytes(secret);

        if (OperatingSystem.IsWindows())
        {
            var protectedBytes = ProtectedData.Protect(plainBytes, Salt, DataProtectionScope.CurrentUser);
            File.WriteAllBytes(targetFile, protectedBytes);
        }
        else
        {
            // Cross-platform authenticated AES-256-GCM encryption
            var keyBytes = DeriveMasterKey();
            var nonce = new byte[12];
            RandomNumberGenerator.Fill(nonce);

            var ciphertext = new byte[plainBytes.Length];
            var tag = new byte[16];

            using (var aesGcm = new AesGcm(keyBytes, 16))
            {
                aesGcm.Encrypt(nonce, plainBytes, ciphertext, tag);
            }

            // Layout: [1 byte version=1][12 bytes nonce][16 bytes tag][N bytes ciphertext]
            using var stream = new FileStream(targetFile, FileMode.Create, FileAccess.Write, FileShare.None);
            stream.WriteByte(1); // Version 1
            stream.Write(nonce);
            stream.Write(tag);
            stream.Write(ciphertext);

            // On Unix-like systems, restrict file permissions to user read/write only (0600)
            if (OperatingSystem.IsLinux() || OperatingSystem.IsMacOS())
            {
                try
                {
                    File.SetUnixFileMode(targetFile, UnixFileMode.UserRead | UnixFileMode.UserWrite);
                }
                catch
                {
                    // Fallback if filesystem does not support POSIX permissions
                }
            }
        }
    }

    public string Load(string key)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        var targetFile = GetFilePath(key);
        if (!File.Exists(targetFile)) return string.Empty;

        try
        {
            var fileBytes = File.ReadAllBytes(targetFile);
            if (fileBytes.Length == 0) return string.Empty;

            if (OperatingSystem.IsWindows())
            {
                var unprotected = ProtectedData.Unprotect(fileBytes, Salt, DataProtectionScope.CurrentUser);
                return Encoding.UTF8.GetString(unprotected);
            }

            // AES-GCM decryption
            if (fileBytes.Length < 1 + 12 + 16) return string.Empty;
            var version = fileBytes[0];
            if (version != 1) return string.Empty;

            var nonce = fileBytes.AsSpan(1, 12);
            var tag = fileBytes.AsSpan(13, 16);
            var ciphertext = fileBytes.AsSpan(29);

            var plaintext = new byte[ciphertext.Length];
            var keyBytes = DeriveMasterKey();

            using (var aesGcm = new AesGcm(keyBytes, 16))
            {
                aesGcm.Decrypt(nonce, ciphertext, tag, plaintext);
            }

            return Encoding.UTF8.GetString(plaintext);
        }
        catch
        {
            return string.Empty;
        }
    }

    public void Clear(string key)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        var targetFile = GetFilePath(key);
        if (File.Exists(targetFile))
        {
            try { File.Delete(targetFile); } catch { }
        }
    }

    private string GetFilePath(string key)
    {
        var sanitizedKey = string.Concat(key.Select(ch => Path.GetInvalidFileNameChars().Contains(ch) ? '_' : ch));
        return Path.Combine(storageDirectory, $"{sanitizedKey}.credential");
    }

    private static byte[] DeriveMasterKey()
    {
        var machineId = GetMachineIdentifier();
        var user = Environment.UserName;
        var seed = $"{machineId}::{user}::JogosRetroImporter";
        return Rfc2898DeriveBytes.Pbkdf2(Encoding.UTF8.GetBytes(seed), Salt, 100_000, HashAlgorithmName.SHA256, 32);
    }

    private static string GetMachineIdentifier()
    {
        // Try Linux machine ID locations
        if (File.Exists("/etc/machine-id"))
        {
            try { return File.ReadAllText("/etc/machine-id").Trim(); } catch { }
        }
        if (File.Exists("/var/lib/dbus/machine-id"))
        {
            try { return File.ReadAllText("/var/lib/dbus/machine-id").Trim(); } catch { }
        }
        return Environment.MachineName;
    }
}
