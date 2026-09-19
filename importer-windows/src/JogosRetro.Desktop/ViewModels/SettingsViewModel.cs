using JogosRetroImporter.Core;

namespace JogosRetro.Desktop.ViewModels;

public sealed class SettingsViewModel : ViewModelBase
{
    private readonly SecureTokenStore tokenStore;
    private readonly ToolchainResolver toolchainResolver;
    private readonly IAppPaths appPaths;

    private string cloudUrl = "https://jogosretro.kiver.dev/";
    private string tokenStatus = "";
    private string chdmanPath = "";
    private string toolsStatus = "";
    private string configDir = "";
    private string cacheDir = "";
    private string dataDir = "";

    public string CloudUrl
    {
        get => cloudUrl;
        set => SetProperty(ref cloudUrl, value);
    }

    public string TokenStatus
    {
        get => tokenStatus;
        set => SetProperty(ref tokenStatus, value);
    }

    public string ChdmanPath
    {
        get => chdmanPath;
        set => SetProperty(ref chdmanPath, value);
    }

    public string ToolsStatus
    {
        get => toolsStatus;
        set => SetProperty(ref toolsStatus, value);
    }

    public string ConfigDir
    {
        get => configDir;
        set => SetProperty(ref configDir, value);
    }

    public string CacheDir
    {
        get => cacheDir;
        set => SetProperty(ref cacheDir, value);
    }

    public string DataDir
    {
        get => dataDir;
        set => SetProperty(ref dataDir, value);
    }

    public SettingsViewModel(
        SecureTokenStore? tokenStore = null,
        ToolchainResolver? toolchainResolver = null,
        IAppPaths? appPaths = null)
    {
        this.tokenStore = tokenStore ?? new SecureTokenStore();
        this.toolchainResolver = toolchainResolver ?? new ToolchainResolver();
        this.appPaths = appPaths ?? AppPaths.Default;

        Refresh();
    }

    public void Refresh()
    {
        var token = tokenStore.Load();
        TokenStatus = !string.IsNullOrEmpty(token) ? "Autenticado (Token Presente)" : "Não Autenticado (Necessário Pareamento)";

        ChdmanPath = toolchainResolver.ResolveChdmanPath();
        ToolsStatus = File.Exists(ChdmanPath) ? "Instalado e Pronto" : "Não Encontrado no Sistema";

        ConfigDir = appPaths.ConfigDirectory;
        CacheDir = appPaths.CacheDirectory;
        DataDir = appPaths.DataDirectory;
    }

    public void ClearToken()
    {
        tokenStore.Clear();
        Refresh();
    }
}
