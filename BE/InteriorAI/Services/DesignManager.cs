using InteriorAI.Data;
using InteriorAI.Domain.Entities;
using InteriorAI.Models.DTOs;
using Microsoft.AspNetCore.Http;
using Microsoft.EntityFrameworkCore;
using SixLabors.ImageSharp;

namespace InteriorAI.Services
{
    public class DesignManager
    {
        private const long MaxImageBytes = 10 * 1024 * 1024;

        private readonly AppDbContext _context;
        private readonly IImageStorageService _imageStorageService;
        private readonly IImageGenerationService _imageGenerationService;
        private readonly IDesignPromptService _promptService;
        private readonly IServiceScopeFactory _scopeFactory;
        private readonly ILogger<DesignManager> _logger;

        public DesignManager(
            AppDbContext context,
            IImageStorageService imageStorageService,
            IImageGenerationService imageGenerationService,
            IDesignPromptService promptService,
            IServiceScopeFactory scopeFactory,
            ILogger<DesignManager> logger)
        {
            _context = context;
            _imageStorageService = imageStorageService;
            _imageGenerationService = imageGenerationService;
            _promptService = promptService;
            _scopeFactory = scopeFactory;
            _logger = logger;
        }

        public async Task<DesignResult> CreateDesignAsync(
            string userId,
            IFormFile image,
            int? styleId,
            string? styleName,
            string? style,
            string? roomType,
            string? featureId,
            string? model = null,
            string? resolution = null)
        {
            if (string.IsNullOrWhiteSpace(userId))
            {
                throw new ArgumentException("UserId is required.", nameof(userId));
            }

            if (!await _context.Users.AnyAsync(user => user.Id == userId))
            {
                throw new KeyNotFoundException("User does not exist.");
            }

            var designPrompt = await _promptService.GetConfiguredPromptAsync(styleId, styleName, style, roomType, featureId);
            var imageBytes = await ReadAndValidateImageAsync(image);
            var base64Image = Convert.ToBase64String(imageBytes);
            var originalImageUrl = await _imageStorageService.UploadImageAsync(base64Image);

            var design = new DesignResult
            {
                UserId = userId,
                OriginalImageUrl = originalImageUrl,
                DesignPrompt = designPrompt,
                Model = model,
                Resolution = resolution,
                Status = DesignStatuses.Pending,
                CreatedAt = DateTime.UtcNow,
                UpdatedAt = DateTime.UtcNow
            };

            _context.DesignResults.Add(design);
            await _context.SaveChangesAsync();

            _logger.LogInformation("Design job {DesignId} was created for user {UserId}.", design.Id, userId);
            StartProcessingInBackground(design.Id);

            return design;
        }

        public async Task<DesignResult> CreateChatDesignAsync(
            string userId,
            IFormFile image,
            string prompt,
            string? model = null,
            string? resolution = null)
        {
            if (string.IsNullOrWhiteSpace(userId))
            {
                throw new ArgumentException("UserId is required.", nameof(userId));
            }

            if (string.IsNullOrWhiteSpace(prompt))
            {
                throw new ArgumentException("Prompt is required.", nameof(prompt));
            }

            if (!await _context.Users.AnyAsync(user => user.Id == userId))
            {
                throw new KeyNotFoundException("User does not exist.");
            }

            var imageBytes = await ReadAndValidateImageAsync(image);
            var base64Image = Convert.ToBase64String(imageBytes);
            var originalImageUrl = await _imageStorageService.UploadImageAsync(base64Image);

            var design = new DesignResult
            {
                UserId = userId,
                OriginalImageUrl = originalImageUrl,
                DesignPrompt = prompt,
                Model = model,
                Resolution = resolution,
                Status = DesignStatuses.Pending,
                CreatedAt = DateTime.UtcNow,
                UpdatedAt = DateTime.UtcNow
            };

            _context.DesignResults.Add(design);
            await _context.SaveChangesAsync();

            _logger.LogInformation("Chat design job {DesignId} was created for user {UserId}.", design.Id, userId);
            StartProcessingInBackground(design.Id);

            return design;
        }

        public Task<List<DesignStyleResponse>> GetDesignStylesAsync()
        {
            return _promptService.GetDesignStylesAsync();
        }

        public Task<string> GetConfiguredDesignPromptAsync(
            int? styleId,
            string? styleName,
            string? style,
            string? roomType = null,
            string? featureId = null)
        {
            return _promptService.GetConfiguredPromptAsync(styleId, styleName, style, roomType, featureId);
        }

        public async Task<(string OriginalImageUrl, string DesignedImageUrl, string DesignPrompt)> GenerateDesignPreviewAsync(
            IFormFile image,
            int? styleId,
            string? styleName,
            string? style,
            string? roomType = null,
            string? featureId = null,
            string? model = null,
            string? resolution = null)
        {
            var designPrompt = await _promptService.GetConfiguredPromptAsync(styleId, styleName, style, roomType, featureId);
            var imageBytes = await ReadAndValidateImageAsync(image);
            var originalImageUrl = await _imageStorageService.UploadImageAsync(Convert.ToBase64String(imageBytes));
            var temporaryDesignedImageUrl = await _imageGenerationService.GenerateImageFromUrlAsync(designPrompt, originalImageUrl, model, resolution);
            var designedImageUrl = await _imageStorageService.UploadImageFromUrlAsync(temporaryDesignedImageUrl);

            return (originalImageUrl, designedImageUrl, designPrompt);
        }

        public async Task<DesignResult?> GetDesignStatusAsync(string designId, string userId)
        {
            if (string.IsNullOrWhiteSpace(designId))
            {
                throw new ArgumentException("DesignId is required.", nameof(designId));
            }

            if (string.IsNullOrWhiteSpace(userId))
            {
                throw new ArgumentException("UserId is required.", nameof(userId));
            }

            if (!await _context.Users.AnyAsync(user => user.Id == userId))
            {
                throw new KeyNotFoundException("User does not exist.");
            }

            var design = await _context.DesignResults
                .AsNoTracking()
                .FirstOrDefaultAsync(item => item.Id == designId && !item.IsDeleted);

            if (design == null)
            {
                return null;
            }

            if (design.UserId != userId)
            {
                throw new UnauthorizedAccessException("Design does not belong to this user.");
            }

            return design;
        }

        public async Task ProcessDesignAsync(string designId)
        {
            var design = await _context.DesignResults.FirstOrDefaultAsync(item => item.Id == designId);
            if (design == null)
            {
                _logger.LogWarning("Design job {DesignId} was not found for processing.", designId);
                return;
            }

            if (design.Status == DesignStatuses.Completed)
            {
                _logger.LogInformation("Design job {DesignId} is already completed. Skipping duplicate processing.", designId);
                return;
            }

            try
            {
                if (string.IsNullOrWhiteSpace(design.DesignPrompt))
                {
                    throw new InvalidOperationException("Design prompt is not configured for this job.");
                }

                var temporaryDesignedImageUrl = await _imageGenerationService.GenerateImageFromUrlAsync(
                    design.DesignPrompt,
                    design.OriginalImageUrl,
                    design.Model,
                    design.Resolution);
                var designedImageUrl = await _imageStorageService.UploadImageFromUrlAsync(temporaryDesignedImageUrl);

                design.DesignedImageUrl = designedImageUrl;
                design.Status = DesignStatuses.Completed;
                design.ErrorMessage = null;
                design.UpdatedAt = DateTime.UtcNow;

                await _context.SaveChangesAsync();
                _logger.LogInformation("Design job {DesignId} completed.", designId);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Design job {DesignId} failed.", designId);

                design.Status = DesignStatuses.Failed;
                design.ErrorMessage = Truncate(ex.Message, 2000);
                design.UpdatedAt = DateTime.UtcNow;

                await _context.SaveChangesAsync();
            }
        }

        private void StartProcessingInBackground(string designId)
        {
            _ = Task.Run(async () =>
            {
                try
                {
                    using var scope = _scopeFactory.CreateScope();
                    var manager = scope.ServiceProvider.GetRequiredService<DesignManager>();

                    await manager.ProcessDesignAsync(designId);
                }
                catch (Exception ex)
                {
                    _logger.LogError(ex, "Unable to start background processing for design job {DesignId}.", designId);
                }
            });
        }

        private static async Task<byte[]> ReadAndValidateImageAsync(IFormFile image)
        {
            if (image == null || image.Length == 0)
            {
                throw new InvalidDataException("Please upload an image of the room.");
            }

            if (image.Length > MaxImageBytes)
            {
                throw new InvalidDataException("Image is too large. Maximum size is 10MB.");
            }

            if (!string.IsNullOrWhiteSpace(image.ContentType) &&
                !image.ContentType.StartsWith("image/", StringComparison.OrdinalIgnoreCase))
            {
                throw new InvalidDataException("The uploaded file must be an image.");
            }

            using var memoryStream = new MemoryStream();
            await image.CopyToAsync(memoryStream);
            var imageBytes = memoryStream.ToArray();

            try
            {
                if (Image.Identify(imageBytes) == null)
                {
                    throw new InvalidDataException("The uploaded file is not a valid image.");
                }
            }
            catch (InvalidDataException)
            {
                throw;
            }
            catch (Exception)
            {
                throw new InvalidDataException("The uploaded file is not a valid image.");
            }

            return imageBytes;
        }

        public async Task<(List<DesignResult>, int)> GetUserProjectsAsync(
            string userId,
            int page,
            int pageSize)
        {
            var query = _context.DesignResults
                .AsNoTracking()
                .Where(d => d.UserId == userId && !d.IsDeleted);

            var total = await query.CountAsync();

            var projects = await query
                .OrderByDescending(d => d.CreatedAt)
                .Skip((page - 1) * pageSize)
                .Take(pageSize)
                .ToListAsync();

            return (projects, total);
        }

        public async Task DeleteProjectAsync(
            string designId,
            string userId)
        {
            var design = await _context.DesignResults
                .FirstOrDefaultAsync(d => d.Id == designId);

            if (design == null)
            {
                throw new KeyNotFoundException("Project not found.");
            }

            if (design.UserId != userId)
            {
                throw new UnauthorizedAccessException("Design does not belong to this user.");
            }

            design.IsDeleted = true;
            design.UpdatedAt = DateTime.UtcNow;

            await _context.SaveChangesAsync();
        }

        private static string Truncate(string value, int maxLength)
        {
            return value.Length <= maxLength ? value : value[..maxLength];
        }
    }
}
