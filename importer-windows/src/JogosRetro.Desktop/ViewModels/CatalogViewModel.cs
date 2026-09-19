using System.Collections.ObjectModel;
using JogosRetro.Downloads;
using JogosRetroImporter.Core;

namespace JogosRetro.Desktop.ViewModels;

public sealed class CatalogViewModel : ViewModelBase
{
    private readonly IGameSourceProvider sourceProvider;
    private readonly DownloadManager downloadManager;

    private string searchQuery = "";
    private string selectedPlatform = "Todos";
    private bool isBusy;
    private string statusMessage = "Digite o nome de um jogo para buscar no Retrostic.";
    private GameSearchResult? selectedGame;

    public ObservableCollection<string> Platforms { get; } = ["Todos", "ps1", "snes", "nes", "megadrive", "gba"];
    public ObservableCollection<GameSearchResult> SearchResults { get; } = [];

    public string SearchQuery
    {
        get => searchQuery;
        set => SetProperty(ref searchQuery, value);
    }

    public string SelectedPlatform
    {
        get => selectedPlatform;
        set => SetProperty(ref selectedPlatform, value);
    }

    public bool IsBusy
    {
        get => isBusy;
        set => SetProperty(ref isBusy, value);
    }

    public string StatusMessage
    {
        get => statusMessage;
        set => SetProperty(ref statusMessage, value);
    }

    public GameSearchResult? SelectedGame
    {
        get => selectedGame;
        set => SetProperty(ref selectedGame, value);
    }

    public CatalogViewModel(IGameSourceProvider? sourceProvider = null, DownloadManager? downloadManager = null)
    {
        this.sourceProvider = sourceProvider ?? new RetrosticSourceProvider();
        this.downloadManager = downloadManager ?? new DownloadManager();
    }

    public async Task SearchAsync()
    {
        if (string.IsNullOrWhiteSpace(SearchQuery)) return;

        IsBusy = true;
        StatusMessage = $"Buscando '{SearchQuery}' no Retrostic…";
        SearchResults.Clear();

        try
        {
            var platformFilter = SelectedPlatform == "Todos" ? null : SelectedPlatform;
            var results = await sourceProvider.SearchAsync(new GameSearchQuery(SearchQuery, platformFilter));

            foreach (var r in results) SearchResults.Add(r);
            StatusMessage = results.Count > 0 ? $"{results.Count} jogos encontrados." : "Nenhum jogo encontrado.";
        }
        catch (Exception ex)
        {
            StatusMessage = $"Erro na busca: {ex.Message}";
        }
        finally
        {
            IsBusy = false;
        }
    }

    public async Task DownloadSelectedAsync()
    {
        if (SelectedGame == null) return;

        IsBusy = true;
        StatusMessage = $"Resolvendo ticket de download para {SelectedGame.Title}…";

        try
        {
            var descriptor = await sourceProvider.ResolveDownloadAsync(SelectedGame.Id);
            var job = await downloadManager.EnqueueAsync(descriptor);
            StatusMessage = $"Download iniciado: {descriptor.Filename}";
        }
        catch (Exception ex)
        {
            StatusMessage = $"Falha ao iniciar download: {ex.Message}";
        }
        finally
        {
            IsBusy = false;
        }
    }
}
