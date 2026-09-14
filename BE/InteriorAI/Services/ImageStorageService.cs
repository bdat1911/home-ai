using System.Text.Json;

namespace InteriorAI.Services;

public interface IImageStorageService
{
    Task<string> UploadImageAsync(string base64Image);
    Task<string> UploadImageFromUrlAsync(string imageUrl);
}

public class ImgBBImageStorageService : IImageStorageService
{
    private readonly HttpClient _httpClient;
    private readonly IConfiguration _configuration;
    private readonly ILogger<ImgBBImageStorageService> _logger;

    public ImgBBImageStorageService(
        HttpClient httpClient,
        IConfiguration configuration,
        ILogger<ImgBBImageStorageService> logger)
    {
        _httpClient = httpClient;
        _configuration = configuration;
        _logger = logger;
    }

    public async Task<string> UploadImageAsync(string base64Image)
    {
        var apiKey = _configuration["ImgBB:ApiKey"];
        if (string.IsNullOrWhiteSpace(apiKey))
        {
            throw new InvalidOperationException("ImgBB API Key is missing.");
        }

        var url = $"https://api.imgbb.com/1/upload?key={apiKey}";
        var content = new FormUrlEncodedContent(new[]
        {
            new KeyValuePair<string, string>("image", base64Image)
        });

        _logger.LogInformation("Uploading image to ImgBB.");
        var response = await _httpClient.PostAsync(url, content);
        var responseString = await response.Content.ReadAsStringAsync();

        if (!response.IsSuccessStatusCode)
        {
            throw new Exception($"ImgBB upload failed: {responseString}");
        }

        using var document = JsonDocument.Parse(responseString);
        var root = document.RootElement;

        if (root.TryGetProperty("data", out var data) && data.TryGetProperty("url", out var imageUrlElement))
        {
            var publicUrl = imageUrlElement.GetString();
            if (!string.IsNullOrWhiteSpace(publicUrl))
            {
                return publicUrl;
            }
        }

        throw new Exception("ImgBB response did not contain a public image URL.");
    }

    public async Task<string> UploadImageFromUrlAsync(string imageUrl)
    {
        if (string.IsNullOrWhiteSpace(imageUrl))
        {
            throw new ArgumentException("Image URL is required.", nameof(imageUrl));
        }

        if (!Uri.TryCreate(imageUrl, UriKind.Absolute, out var parsedUrl) ||
            (parsedUrl.Scheme != Uri.UriSchemeHttp && parsedUrl.Scheme != Uri.UriSchemeHttps))
        {
            throw new ArgumentException("Image URL must be an absolute HTTP or HTTPS URL.", nameof(imageUrl));
        }

        _logger.LogInformation("Downloading generated image before storing it.");
        var response = await _httpClient.GetAsync(parsedUrl);
        if (!response.IsSuccessStatusCode)
        {
            var errorDetail = await response.Content.ReadAsStringAsync();
            throw new Exception($"Could not download image from URL {imageUrl}: {errorDetail}");
        }

        var imageBytes = await response.Content.ReadAsByteArrayAsync();
        return await UploadImageAsync(Convert.ToBase64String(imageBytes));
    }
}
