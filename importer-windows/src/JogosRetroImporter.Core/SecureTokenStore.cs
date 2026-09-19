using System.Security.Cryptography;
using System.Text;

namespace JogosRetroImporter.Core;

public sealed class SecureTokenStore
{
    private readonly string path;
    public SecureTokenStore(string? path = null) => this.path = path ?? System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "JogosRetro", "Importer", "publisher.token");

    public void Save(string token)
    {
        Directory.CreateDirectory(System.IO.Path.GetDirectoryName(path)!);
        var protectedBytes = ProtectedData.Protect(Encoding.UTF8.GetBytes(token), Encoding.UTF8.GetBytes("JogosRetroImporter"), DataProtectionScope.CurrentUser);
        File.WriteAllBytes(path, protectedBytes);
    }

    public string Load()
    {
        if (!File.Exists(path)) return "";
        try { return Encoding.UTF8.GetString(ProtectedData.Unprotect(File.ReadAllBytes(path), Encoding.UTF8.GetBytes("JogosRetroImporter"), DataProtectionScope.CurrentUser)); }
        catch { return ""; }
    }

    public void Clear() { if (File.Exists(path)) File.Delete(path); }
}
