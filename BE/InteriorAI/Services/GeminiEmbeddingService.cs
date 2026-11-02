using System.Net;
using System.Net.Http.Json;
using System.Text.Json;

namespace InteriorAI.Services;

public enum EmbeddingTask
{
    Document,
    Query
}

public interface IEmbeddingService
{
    string ModelName { get; }
    Task<float[]?> EmbedAsync(string text, EmbeddingTask task, CancellationToken cancellationToken);
}

public sealed class GeminiEmbeddingService : IEmbeddingService
{
    private const int Dimensions = 768;
    private const int MaxAttempts = 4;

    private readonly HttpClient _httpClient;
    private readonly IConfiguration _configuration;
    private readonly ILogger<GeminiEmbeddingService> _logger;

    public GeminiEmbeddingService(
        HttpClient httpClient, IConfiguration configuration, ILogger<GeminiEmbeddingService> logger)
    {
        _httpClient = httpClient;
        _configuration = configuration;
        _logger = logger;
    }

    public string ModelName => _configuration["Knowledge:EmbeddingModel"] ?? "gemini-embedding-2";

    public async Task<float[]?> EmbedAsync(string text, EmbeddingTask task, CancellationToken cancellationToken)
    {
        var key = _configuration["Gemini:ApiKey"];
        if (string.IsNullOrWhiteSpace(key) || string.IsNullOrWhiteSpace(text)) return null;

        var payload = new
        {
            content = new { parts = new[] { new { text } } },
            taskType = task == EmbeddingTask.Query ? "RETRIEVAL_QUERY" : "RETRIEVAL_DOCUMENT",
            outputDimensionality = Dimensions
        };

        // A live question should fall back to lexical search quickly; ingestion can afford to wait.
        var attempts = task == EmbeddingTask.Query ? 2 : MaxAttempts;
        for (var attempt = 1; attempt <= attempts; attempt++)
        {
            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
            timeout.CancelAfter(TimeSpan.FromSeconds(task == EmbeddingTask.Query ? 8 : 30));
            try
            {
                using var request = new HttpRequestMessage(HttpMethod.Post,
                    $"https://generativelanguage.googleapis.com/v1beta/models/{ModelName}:embedContent");
                request.Headers.Add("x-goog-api-key", key);
                request.Content = JsonContent.Create(payload);
                using var response = await _httpClient.SendAsync(request, timeout.Token);
                if (response.StatusCode == HttpStatusCode.TooManyRequests && attempt < attempts)
                {
                    await Task.Delay(task == EmbeddingTask.Query ? TimeSpan.FromSeconds(1) : TimeSpan.FromSeconds(20 * attempt), cancellationToken);
                    continue;
                }
                if (!response.IsSuccessStatusCode)
                {
                    _logger.LogWarning("Embedding request failed with status {StatusCode}", response.StatusCode);
                    return null;
                }

                await using var body = await response.Content.ReadAsStreamAsync(timeout.Token);
                using var json = await JsonDocument.ParseAsync(body, cancellationToken: timeout.Token);
                var values = json.RootElement.GetProperty("embedding").GetProperty("values")
                    .EnumerateArray().Select(value => value.GetSingle()).ToArray();
                return VectorMath.Normalize(values);
            }
            catch (OperationCanceledException) when (!cancellationToken.IsCancellationRequested)
            {
                _logger.LogWarning("Embedding request timed out");
            }
            catch (HttpRequestException ex)
            {
                _logger.LogWarning("Embedding request failed: {Reason}", ex.GetType().Name);
            }
            catch (JsonException)
            {
                _logger.LogWarning("Embedding response was not valid JSON");
            }
            catch (KeyNotFoundException)
            {
                _logger.LogWarning("Embedding response was missing required fields");
            }
        }
        return null;
    }
}

public static class VectorMath
{
    public static float[] Normalize(float[] values)
    {
        var length = Math.Sqrt(values.Sum(value => (double)value * value));
        if (length == 0) return values;
        return values.Select(value => (float)(value / length)).ToArray();
    }

    /// <summary>Cosine similarity of two vectors that are already unit length.</summary>
    public static double Dot(float[] left, float[] right)
    {
        var sum = 0d;
        for (var i = 0; i < left.Length && i < right.Length; i++) sum += left[i] * right[i];
        return sum;
    }

    public static byte[] ToBytes(float[] values)
    {
        var bytes = new byte[values.Length * sizeof(float)];
        Buffer.BlockCopy(values, 0, bytes, 0, bytes.Length);
        return bytes;
    }

    public static float[] FromBytes(byte[] bytes)
    {
        var values = new float[bytes.Length / sizeof(float)];
        Buffer.BlockCopy(bytes, 0, values, 0, bytes.Length);
        return values;
    }
}
