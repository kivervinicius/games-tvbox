using System.Collections.ObjectModel;
using JogosRetro.Downloads;

namespace JogosRetro.Desktop.ViewModels;

public sealed class DownloadItemViewModel : ViewModelBase
{
    private readonly DownloadJob job;
    private double progress;
    private string speedText = "";
    private string stateText = "";
    private string errorText = "";

    public string Id => job.Id;
    public string Filename => job.Filename;
    public long TotalBytes => job.TotalBytes;

    public double Progress
    {
        get => progress;
        set => SetProperty(ref progress, value);
    }

    public string SpeedText
    {
        get => speedText;
        set => SetProperty(ref speedText, value);
    }

    public string StateText
    {
        get => stateText;
        set => SetProperty(ref stateText, value);
    }

    public string ErrorText
    {
        get => errorText;
        set => SetProperty(ref errorText, value);
    }

    public DownloadItemViewModel(DownloadJob job)
    {
        this.job = job;
        UpdateFromJob();
    }

    public void UpdateFromJob()
    {
        StateText = job.State.ToString();
        ErrorText = job.ErrorMessage ?? "";
        if (job.TotalBytes > 0)
        {
            Progress = (double)job.DownloadedBytes / job.TotalBytes * 100.0;
        }
    }

    public void UpdateProgress(DownloadProgressSnapshot snapshot)
    {
        Progress = snapshot.ProgressPercentage;
        StateText = snapshot.State.ToString();
        ErrorText = snapshot.ErrorMessage ?? "";
        SpeedText = snapshot.SpeedBytesPerSecond > 0
            ? $"{snapshot.SpeedBytesPerSecond / (1024 * 1024):F1} MB/s"
            : "";
    }
}

public sealed class DownloadsViewModel : ViewModelBase
{
    private readonly DownloadManager downloadManager;
    public ObservableCollection<DownloadItemViewModel> Items { get; } = [];

    private DownloadItemViewModel? selectedItem;
    public DownloadItemViewModel? SelectedItem
    {
        get => selectedItem;
        set => SetProperty(ref selectedItem, value);
    }

    public DownloadsViewModel(DownloadManager? downloadManager = null)
    {
        this.downloadManager = downloadManager ?? new DownloadManager();
        this.downloadManager.ProgressChanged += OnProgressChanged;
        this.downloadManager.StateChanged += OnStateChanged;
        Refresh();
    }

    public void Refresh()
    {
        Items.Clear();
        foreach (var j in downloadManager.GetAllJobs())
        {
            Items.Add(new DownloadItemViewModel(j));
        }
    }

    public async Task PauseSelectedAsync()
    {
        if (SelectedItem != null)
        {
            await downloadManager.PauseAsync(SelectedItem.Id);
        }
    }

    public async Task ResumeSelectedAsync()
    {
        if (SelectedItem != null)
        {
            await downloadManager.ResumeAsync(SelectedItem.Id);
        }
    }

    public async Task CancelSelectedAsync()
    {
        if (SelectedItem != null)
        {
            await downloadManager.CancelAsync(SelectedItem.Id);
        }
    }

    private void OnProgressChanged(object? sender, DownloadProgressSnapshot e)
    {
        var item = Items.FirstOrDefault(i => i.Id == e.JobId);
        item?.UpdateProgress(e);
    }

    private void OnStateChanged(object? sender, DownloadJob e)
    {
        var item = Items.FirstOrDefault(i => i.Id == e.Id);
        if (item != null)
        {
            item.UpdateFromJob();
        }
        else
        {
            Items.Insert(0, new DownloadItemViewModel(e));
        }
    }
}
