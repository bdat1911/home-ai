using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace InteriorAI.Services;

public interface IImageGenerationService
{
    Task<string> GenerateImageFromUrlAsync(string prompt, string imageUrl, string? model = null, string? resolution = null);
}

public class NanoBananaImageGenerationService : IImageGenerationService
{
    private const int MaxPollAttempts = 60;
    private const int PollDelayMs = 5000;

    private readonly HttpClient _httpClient;
    private readonly IConfiguration _configuration;
    private readonly ILogger<NanoBananaImageGenerationService> _logger;

    public NanoBananaImageGenerationService(
        HttpClient httpClient,
        IConfiguration configuration,
        ILogger<NanoBananaImageGenerationService> logger)
    {
        _httpClient = httpClient;
        _configuration = configuration;
        _logger = logger;
    }

    public async Task<string> GenerateImageFromUrlAsync(string prompt, string imageUrl, string? model = null, string? resolution = null)
    {
        ValidateInput(prompt, imageUrl);

        var apiKey = _configuration["NanoBanana:ApiKey"];
        if (string.IsNullOrWhiteSpace(apiKey))
        {
            throw new InvalidOperationException("NanoBanana API Key is missing.");
        }

        var endpointModel = string.IsNullOrWhiteSpace(model) ? "generate-2" : model;
        var generateUrl = $"https://api.nanobananaapi.ai/api/v1/nanobanana/{endpointModel}";
        var res = string.IsNullOrWhiteSpace(resolution) ? "1K" : resolution;

        var payload = new
        {
            prompt = prompt,
            imageUrls = new[] { imageUrl },
            aspectRatio = "auto",
            resolution = res,
            outputFormat = "jpg"
        };

        var jsonPayload = JsonSerializer.Serialize(payload, new JsonSerializerOptions
        {
            DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
        });

        using var request = new HttpRequestMessage(HttpMethod.Post, generateUrl);
        request.Content = new StringContent(jsonPayload, Encoding.UTF8, "application/json");
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", apiKey);

        _logger.LogInformation("Sending NanoBanana generation request.");
        var response = await _httpClient.SendAsync(request);
        if (!response.IsSuccessStatusCode)
        {
            var errorDetail = await response.Content.ReadAsStringAsync();
            throw new Exception($"NanoBanana API failed with HTTP {response.StatusCode}: {errorDetail}");
        }

        var responseString = await response.Content.ReadAsStringAsync();
        using var document = JsonDocument.Parse(responseString);
        var root = document.RootElement;

        if (root.TryGetProperty("code", out var codeElement) && codeElement.GetInt32() != 200)
        {
            var code = codeElement.GetInt32();
            throw new Exception($"NanoBanana rejected request (code {code}): {GetErrorMessage(root, code)}");
        }

        var taskId = root.GetProperty("data").GetProperty("taskId").GetString();
        if (string.IsNullOrWhiteSpace(taskId))
        {
            throw new Exception("NanoBanana did not return taskId.");
        }

        _logger.LogInformation("NanoBanana task {TaskId} created.", taskId);
        return await PollResultAsync(taskId, apiKey);
    }

    private async Task<string> PollResultAsync(string taskId, string apiKey)
    {
        var recordInfoUrl = "https://api.nanobananaapi.ai/api/v1/nanobanana/record-info";

        for (var attempt = 0; attempt < MaxPollAttempts; attempt++)
        {
            await Task.Delay(PollDelayMs);

            using var pollRequest = new HttpRequestMessage(HttpMethod.Get, $"{recordInfoUrl}?taskId={taskId}");
            pollRequest.Headers.Authorization = new AuthenticationHeaderValue("Bearer", apiKey);

            var pollResponse = await _httpClient.SendAsync(pollRequest);
            if (!pollResponse.IsSuccessStatusCode)
            {
                var errorDetail = await pollResponse.Content.ReadAsStringAsync();
                throw new Exception($"NanoBanana status polling failed with HTTP {pollResponse.StatusCode}: {errorDetail}");
            }

            var pollString = await pollResponse.Content.ReadAsStringAsync();
            using var pollDocument = JsonDocument.Parse(pollString);
            var pollData = pollDocument.RootElement.GetProperty("data");
            var successFlag = pollData.GetProperty("successFlag").GetInt32();

            if (successFlag == 1)
            {
                var resultImageUrl = pollData.GetProperty("response").GetProperty("resultImageUrl").GetString();
                if (string.IsNullOrWhiteSpace(resultImageUrl))
                {
                    throw new Exception("NanoBanana completed but did not return resultImageUrl.");
                }

                _logger.LogInformation("NanoBanana task {TaskId} completed.", taskId);
                return resultImageUrl;
            }

            if (successFlag == 2 || successFlag == 3)
            {
                var errorMessage = pollData.TryGetProperty("errorMessage", out var errorElement)
                    ? errorElement.GetString()
                    : "Unknown";
                throw new Exception($"NanoBanana image processing failed: {errorMessage}");
            }
        }

        throw new Exception("Timed out waiting for NanoBanana result.");
    }

    private static string GetErrorMessage(JsonElement root, int code)
    {
        if (root.TryGetProperty("msg", out var msgElement) &&
            !string.IsNullOrWhiteSpace(msgElement.GetString()))
        {
            return msgElement.GetString()!;
        }

        if (root.TryGetProperty("message", out var messageElement) &&
            !string.IsNullOrWhiteSpace(messageElement.GetString()))
        {
            return messageElement.GetString()!;
        }

        if (root.TryGetProperty("data", out var dataElement) &&
            dataElement.ValueKind == JsonValueKind.Object &&
            dataElement.TryGetProperty("errorMessage", out var dataErrorElement) &&
            !string.IsNullOrWhiteSpace(dataErrorElement.GetString()))
        {
            return dataErrorElement.GetString()!;
        }

        return code switch
        {
            401 => "Unauthorized. API key is missing, invalid, or not allowed for this endpoint.",
            402 => "Insufficient credits. The NanoBanana account does not have enough credits for generation.",
            422 => "Validation error. Request parameters were rejected by NanoBanana.",
            429 => "Rate limited. Too many requests were sent to NanoBanana.",
            455 => "Service unavailable. NanoBanana is under maintenance.",
            505 => "Feature disabled. This generation endpoint is disabled for the account.",
            _ => "Unknown error"
        };
    }

    private static void ValidateInput(string prompt, string imageUrl)
    {
        if (string.IsNullOrWhiteSpace(prompt))
        {
            throw new ArgumentException("Prompt is required.", nameof(prompt));
        }

        if (string.IsNullOrWhiteSpace(imageUrl))
        {
            throw new ArgumentException("Image URL is required.", nameof(imageUrl));
        }

        if (!Uri.TryCreate(imageUrl, UriKind.Absolute, out var parsedUrl) ||
            (parsedUrl.Scheme != Uri.UriSchemeHttp && parsedUrl.Scheme != Uri.UriSchemeHttps))
        {
            throw new ArgumentException("Image URL must be an absolute HTTP or HTTPS URL.", nameof(imageUrl));
        }
    }
}
