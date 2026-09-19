using JogosRetro.Downloads;
using JogosRetroImporter.Core;

namespace JogosRetro.Desktop.ViewModels;

public sealed class MainWindowViewModel : ViewModelBase
{
    private ViewModelBase currentView;

    public CatalogViewModel CatalogVM { get; }
    public DownloadsViewModel DownloadsVM { get; }
    public LocalImportViewModel LocalImportVM { get; }
    public SettingsViewModel SettingsVM { get; }

    public ViewModelBase CurrentView
    {
        get => currentView;
        set => SetProperty(ref currentView, value);
    }

    public MainWindowViewModel()
    {
        var sourceProvider = new RetrosticSourceProvider();
        var downloadManager = new DownloadManager(sourceProvider: sourceProvider);

        CatalogVM = new CatalogViewModel(sourceProvider, downloadManager);
        DownloadsVM = new DownloadsViewModel(downloadManager);
        LocalImportVM = new LocalImportViewModel();
        SettingsVM = new SettingsViewModel();

        currentView = CatalogVM;
    }

    public void NavigateToCatalog() => CurrentView = CatalogVM;
    public void NavigateToDownloads() => CurrentView = DownloadsVM;
    public void NavigateToLocalImport() => CurrentView = LocalImportVM;
    public void NavigateToSettings() => CurrentView = SettingsVM;
}
