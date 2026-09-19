namespace JogosRetroImporter.Core;

public sealed class SecureTokenStore
{
    private readonly ICredentialStore credentialStore;
    private const string TokenKey = "publisher_token";

    public SecureTokenStore(string? path = null)
    {
        var dir = !string.IsNullOrWhiteSpace(path) ? Path.GetDirectoryName(path) : AppPaths.Default.ConfigDirectory;
        credentialStore = new CrossPlatformCredentialStore(dir);
    }

    public SecureTokenStore(ICredentialStore credentialStore)
    {
        this.credentialStore = credentialStore ?? throw new ArgumentNullException(nameof(credentialStore));
    }

    public void Save(string token) => credentialStore.Save(TokenKey, token);
    public string Load() => credentialStore.Load(TokenKey);
    public void Clear() => credentialStore.Clear(TokenKey);
}
