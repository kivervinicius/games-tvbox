using System.Collections.ObjectModel;
using JogosRetroImporter.Core;

namespace JogosRetro.Desktop.ViewModels;

public sealed class LocalImportViewModel : ViewModelBase
{
    private readonly ImportPipeline pipeline;
    private readonly CloudPublisherClient cloudClient;
    private readonly SecureTokenStore tokenStore;

    private string filePath = "";
    private string selectedPlatform = "ps1";
    private string coverPath = "";
    private bool isBusy;
    private string statusMessage = "Selecione um arquivo de jogo para processar e publicar.";
    private double progress;

    public ObservableCollection<string> SupportedPlatforms { get; } = ["ps1", "snes", "nes", "megadrive", "gba"];

    public string FilePath
    {
        get => filePath;
        set => SetProperty(ref filePath, value);
    }

    public string SelectedPlatform
    {
        get => selectedPlatform;
        set => SetProperty(ref selectedPlatform, value);
    }

    public string CoverPath
    {
        get => coverPath;
        set => SetProperty(ref coverPath, value);
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

    public double Progress
    {
        get => progress;
        set => SetProperty(ref progress, value);
    }

    public LocalImportViewModel(
        ImportPipeline? pipeline = null,
        CloudPublisherClient? cloudClient = null,
        SecureTokenStore? tokenStore = null)
    {
        var toolchain = new ToolchainResolver();
        this.pipeline = pipeline ?? new ImportPipeline(toolchain.ResolveChdmanPath());
        this.cloudClient = cloudClient ?? new CloudPublisherClient("https://jogosretro.kiver.dev/");
        this.tokenStore = tokenStore ?? new SecureTokenStore();
    }

    public async Task ProcessAndPublishAsync()
    {
        if (string.IsNullOrWhiteSpace(FilePath) || !File.Exists(FilePath))
        {
            StatusMessage = "Arquivo inválido ou não encontrado.";
            return;
        }

        IsBusy = true;
        Progress = 0;
        StatusMessage = "Iniciando processamento do jogo…";

        try
        {
            var statusProgress = new Progress<string>(msg => StatusMessage = msg);
            var prepared = await pipeline.ProcessAsync(FilePath, SelectedPlatform, statusProgress);

            StatusMessage = $"Jogo processado: {prepared.Metadata.Title} ({prepared.ContentId})";

            var token = tokenStore.Load();
            if (!string.IsNullOrEmpty(token))
            {
                StatusMessage = "Enviando artefatos e publicando no catálogo da nuvem…";
                var uploadProgress = new Progress<double>(pct => Progress = pct * 100);
                var pubResult = await cloudClient.PublishGameAsync(token, prepared, string.IsNullOrWhiteSpace(CoverPath) ? null : CoverPath, uploadProgress);
                StatusMessage = $"Publicação concluída com sucesso! Revisão: {pubResult.Revision}";
            }
            else
            {
                StatusMessage = $"Processamento local concluído: {prepared.FinalFile}. (Sem token de nuvem configurado para publicação).";
            }
        }
        catch (Exception ex)
        {
            StatusMessage = $"Erro no processamento/publicação: {ex.Message}";
        }
        finally
        {
            IsBusy = false;
        }
    }
}
