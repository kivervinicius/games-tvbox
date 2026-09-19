using System.Net;
using System.Text;

namespace JogosRetroImporter.Tests;

public sealed class HttpFaultServer : IDisposable
{
    private readonly HttpListener listener;
    private readonly CancellationTokenSource cts = new();
    public string BaseUrl { get; }
    public Func<HttpListenerRequest, HttpListenerResponse, Task>? RequestHandler { get; set; }

    public HttpFaultServer()
    {
        listener = new HttpListener();
        var port = GetAvailablePort();
        BaseUrl = $"http://127.0.0.1:{port}/";
        listener.Prefixes.Add(BaseUrl);
        listener.Start();
        _ = Task.Run(ListenLoopAsync);
    }

    private static int GetAvailablePort()
    {
        using var tcp = new System.Net.Sockets.TcpListener(IPAddress.Loopback, 0);
        tcp.Start();
        var port = ((IPEndPoint)tcp.LocalEndpoint).Port;
        tcp.Stop();
        return port;
    }

    private async Task ListenLoopAsync()
    {
        while (!cts.Token.IsCancellationRequested && listener.IsListening)
        {
            try
            {
                var context = await listener.GetContextAsync();
                _ = Task.Run(async () =>
                {
                    try
                    {
                        if (RequestHandler != null)
                        {
                            await RequestHandler(context.Request, context.Response);
                        }
                        else
                        {
                            context.Response.StatusCode = 404;
                            context.Response.Close();
                        }
                    }
                    catch
                    {
                        try { context.Response.Abort(); } catch { }
                    }
                });
            }
            catch (Exception) when (cts.Token.IsCancellationRequested)
            {
                break;
            }
            catch
            {
                // Continue listening
            }
        }
    }

    public void Dispose()
    {
        cts.Cancel();
        try { listener.Stop(); } catch { }
        try { listener.Close(); } catch { }
        cts.Dispose();
    }
}
