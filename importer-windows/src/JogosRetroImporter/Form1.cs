using System.Diagnostics;
using System.Text.Json.Nodes;
using JogosRetroImporter.Core;

namespace JogosRetroImporter;

public partial class Form1 : Form
{
    private readonly TextBox cloudUrl = new() { Text = "https://jogos-retro-cloud.kivervinicius.workers.dev", Dock = DockStyle.Fill };
    private readonly TextBox source = new() { ReadOnly = true, Dock = DockStyle.Fill };
    private readonly TextBox title = new() { Dock = DockStyle.Fill };
    private readonly ComboBox platform = new() { DropDownStyle = ComboBoxStyle.DropDownList, Dock = DockStyle.Fill };
    private readonly Label pairingCode = new() { Text = "Importador ainda não pareado", AutoSize = true };
    private readonly Label status = new() { Text = "Escolha um jogo para começar.", AutoSize = true, ForeColor = Color.FromArgb(190, 207, 235) };
    private readonly ProgressBar progress = new() { Minimum = 0, Maximum = 100, Dock = DockStyle.Fill };
    private readonly PictureBox cover = new() { SizeMode = PictureBoxSizeMode.Zoom, Dock = DockStyle.Fill, BackColor = Color.FromArgb(13, 29, 55) };
    private readonly Button pair = ActionButton("Parear importador");
    private readonly Button completePair = ActionButton("Concluir pareamento");
    private readonly Button choose = ActionButton("Escolher arquivo");
    private readonly Button prepare = ActionButton("Analisar e converter");
    private readonly Button publish = ActionButton("Publicar na biblioteca");
    private readonly Button repair = ActionButton("Reparar ferramentas");
    private readonly SecureTokenStore tokens = new();
    private readonly ToolchainManager tools = new();
    private PairingInfo? pendingPairing;
    private PreparedGame? prepared;

    private string LockPath => Path.Combine(AppContext.BaseDirectory, "tools", "toolchain.lock.json");
    private string BundledChdman => Path.Combine(AppContext.BaseDirectory, "tools", "chdman.exe");

    public Form1()
    {
        InitializeComponent(); BuildUi();
        platform.Items.Clear();
        foreach (var p in PlatformRegistry.Platforms)
        {
            var label = p.Status == PipelineStatus.Supported ? p.DisplayName : $"{p.DisplayName} (Em breve)";
            platform.Items.Add(label);
        }
        platform.SelectedIndex = 0;
        pair.Click += async (_, _) => await StartPairingAsync(); completePair.Click += async (_, _) => await CompletePairingAsync();
        choose.Click += (_, _) => ChooseFile(); prepare.Click += async (_, _) => await PrepareAsync(); publish.Click += async (_, _) => await PublishAsync(); repair.Click += async (_, _) => await RepairAsync();
        completePair.Enabled = false; publish.Enabled = false;
        if (!string.IsNullOrEmpty(tokens.Load())) pairingCode.Text = "Importador pareado e pronto para publicar";
    }

    private void BuildUi()
    {
        BackColor = Color.FromArgb(5, 11, 24); ForeColor = Color.White; Font = new Font("Segoe UI", 10f);
        var shell = new TableLayoutPanel { Dock = DockStyle.Fill, Padding = new Padding(34), ColumnCount = 2, RowCount = 1 };
        shell.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 67)); shell.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 33));
        var left = new TableLayoutPanel { Dock = DockStyle.Fill, Padding = new Padding(0, 0, 24, 0), AutoScroll = true, ColumnCount = 1 };
        left.Controls.Add(Header("JOGOS RETRO IMPORTER", 25, Color.FromArgb(56, 217, 255)));
        left.Controls.Add(Header("Prepare, revise e publique jogos sem alterar o arquivo original.", 11, Color.FromArgb(174, 194, 226)));
        left.Controls.Add(Card("1 · CONEXÃO SEGURA", Field("Endereço do Jogos Retro Cloud", cloudUrl), pairingCode, Flow(pair, completePair)));
        left.Controls.Add(Card("2 · ARQUIVO E FERRAMENTAS", Field("Arquivo original", source), Flow(choose, repair), prepare));
        left.Controls.Add(Card("3 · REVISÃO", Field("Título exibido", title), Field("Plataforma", platform), progress, status, publish));
        shell.Controls.Add(left, 0, 0);
        var preview = Card("CAPA LOCALIZADA", cover, Header("A capa e os dados podem ser revisados antes da publicação.", 10, Color.FromArgb(174, 194, 226)));
        shell.Controls.Add(preview, 1, 0); Controls.Add(shell);
    }

    private async Task StartPairingAsync()
    {
        await RunAsync(async () => {
            var client = new CloudPublisherClient(cloudUrl.Text); var id = "importer-" + Environment.MachineName.ToLowerInvariant().Replace(' ', '-');
            pendingPairing = await client.StartPairingAsync(id); pairingCode.Text = $"Código: {pendingPairing.Code}"; completePair.Enabled = true;
            Process.Start(new ProcessStartInfo(pendingPairing.PairingUrl) { UseShellExecute = true }); status.Text = "Aprove o importador na página aberta e volte para concluir.";
        });
    }

    private async Task CompletePairingAsync()
    {
        if (pendingPairing is null) return;
        await RunAsync(async () => {
            var token = await new CloudPublisherClient(cloudUrl.Text).CompletePairingAsync(pendingPairing);
            if (token is null) { status.Text = "A aprovação ainda não chegou. Aprove no navegador e tente novamente."; return; }
            tokens.Save(token); pairingCode.Text = "Importador pareado e pronto para publicar"; completePair.Enabled = false; status.Text = "Conexão segura concluída.";
        });
    }

    private void ChooseFile()
    {
        using var dialog = new OpenFileDialog { Filter = "Jogos suportados|*.7z;*.zip;*.cue;*.iso;*.chd|Todos os arquivos|*.*", Title = "Escolha o jogo do seu acervo" };
        if (dialog.ShowDialog(this) != DialogResult.OK) return; source.Text = dialog.FileName; title.Text = GameTitleParser.Parse(dialog.FileName).Title; prepared = null; publish.Enabled = false; status.Text = "Arquivo selecionado. Clique em Analisar e converter.";
        var detected = PlatformRegistry.FindByExtension(dialog.FileName);
        if (detected != null)
        {
            for (int i = 0; i < PlatformRegistry.Platforms.Count; i++)
            {
                if (PlatformRegistry.Platforms[i].Id == detected.Id)
                {
                    platform.SelectedIndex = i;
                    break;
                }
            }
        }
    }

    private async Task RepairAsync()
    {
        await RunAsync(async () => { await tools.RepairAsync(BundledChdman, LockPath); status.Text = "Ferramentas verificadas e reparadas."; });
    }

    private async Task PrepareAsync()
    {
        if (!File.Exists(source.Text)) { MessageBox.Show(this, "Escolha um arquivo primeiro.", "Jogos Retro", MessageBoxButtons.OK, MessageBoxIcon.Information); return; }
        var selectedIdx = platform.SelectedIndex;
        if (selectedIdx >= 0 && selectedIdx < PlatformRegistry.Platforms.Count)
        {
            var def = PlatformRegistry.Platforms[selectedIdx];
            if (def.Status != PipelineStatus.Supported)
            {
                MessageBox.Show(this, $"O pipeline de importação automatizada para '{def.DisplayName}' está planejado para a próxima versão de emuladores. No momento, o pipeline suporta PlayStation.", "Plataforma planejada", MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }
        }
        await RunAsync(async () => {
            if (!await tools.IsHealthyAsync(LockPath)) throw new InvalidOperationException("As ferramentas precisam ser reparadas antes da conversão.");
            prepared = await new ImportPipeline(tools.ChdmanPath).PreparePlayStationAsync(source.Text, new Progress<string>(message => status.Text = message));
            title.Text = prepared.Metadata.Title; if (File.Exists(prepared.Metadata.CoverPath)) { cover.Image?.Dispose(); cover.Image = Image.FromFile(prepared.Metadata.CoverPath); }
            status.Text = $"Pronto: {new FileInfo(prepared.FinalFile).Length / 1048576d:F1} MB · confiança da capa {prepared.Metadata.Confidence}%"; publish.Enabled = true;
        });
    }

    private async Task PublishAsync()
    {
        if (prepared is null) return; var token = tokens.Load(); if (string.IsNullOrEmpty(token)) { MessageBox.Show(this, "Pareie o importador antes de publicar."); return; }
        await RunAsync(async () => {
            var client = new CloudPublisherClient(cloudUrl.Text); string coverId = ""; var report = new Progress<double>(value => progress.Value = Math.Clamp((int)Math.Round(value * 100), 0, 100));
            if (File.Exists(prepared.Metadata.CoverPath)) {
                status.Text = "Publicando capa…";
                var coverResult = await client.PublishFileAsync(token, prepared.Metadata.CoverPath, "cover", new JsonObject { ["kind"] = "cover", ["category"] = "asset", ["label"] = title.Text + " · capa", ["visibility"] = "private" }, report); coverId = coverResult.PublishedId;
            }
            status.Text = "Publicando jogo convertido…";
            var safeName = string.Concat(title.Text.Select(ch => Path.GetInvalidFileNameChars().Contains(ch) ? '_' : ch)) + ".chd";
            var item = new JsonObject { ["kind"] = "rom", ["category"] = "game", ["label"] = title.Text.Trim(), ["platform"] = "PlayStation", ["localPath"] = "/sdcard/roms/ps1/" + safeName, ["corePath"] = "/data/user/0/com.retroarch.ra32/cores/pcsx_rearmed_libretro_android.so", ["coverAssetId"] = coverId, ["visibility"] = "private", ["description"] = "Importado e convertido para CHD pelo Jogos Retro Importer.", ["tags"] = new JsonArray("ps1", "chd") };
            var result = await client.PublishFileAsync(token, prepared.FinalFile, "rom", item, report); status.Text = $"Publicado. Revisão {result.Revision}. As TVs verão o card na próxima sincronização.";
        });
    }

    private async Task RunAsync(Func<Task> action)
    {
        UseWaitCursor = true; try { await action(); } catch (Exception error) { status.Text = error.Message; MessageBox.Show(this, error.Message, "Não foi possível concluir", MessageBoxButtons.OK, MessageBoxIcon.Warning); } finally { UseWaitCursor = false; }
    }

    private static Button ActionButton(string text) => new() { Text = text, AutoSize = true, FlatStyle = FlatStyle.Flat, BackColor = Color.FromArgb(35, 77, 126), ForeColor = Color.White, Padding = new Padding(9, 5, 9, 5), Margin = new Padding(4) };
    private static Label Header(string text, float size, Color color) => new() { Text = text, AutoSize = true, Font = new Font("Segoe UI", size, size > 15 ? FontStyle.Bold : FontStyle.Regular), ForeColor = color, Margin = new Padding(0, 4, 0, 10) };
    private static Control Field(string label, Control control) { var panel = new TableLayoutPanel { Dock = DockStyle.Top, AutoSize = true, ColumnCount = 1, Margin = new Padding(0, 7, 0, 7) }; panel.Controls.Add(Header(label, 9, Color.FromArgb(174, 194, 226))); panel.Controls.Add(control); return panel; }
    private static FlowLayoutPanel Flow(params Control[] controls) { var panel = new FlowLayoutPanel { AutoSize = true, Dock = DockStyle.Top, WrapContents = true }; panel.Controls.AddRange(controls); return panel; }
    private static Panel Card(string heading, params Control[] controls) { var panel = new TableLayoutPanel { Dock = DockStyle.Top, AutoSize = true, BackColor = Color.FromArgb(13, 27, 49), Padding = new Padding(20), Margin = new Padding(0, 10, 0, 10), ColumnCount = 1 }; panel.Controls.Add(Header(heading, 12, Color.FromArgb(56, 217, 255))); foreach (var control in controls) panel.Controls.Add(control); return panel; }
}
