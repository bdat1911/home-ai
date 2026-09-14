using System.Text;
using SmartFormat;
using InteriorAI.Data;
using InteriorAI.Data.Seed;
using InteriorAI.Domain.Entities;
using InteriorAI.Models.DTOs;
using Microsoft.EntityFrameworkCore;

namespace InteriorAI.Services;

public interface IDesignPromptService
{
    Task<List<DesignStyleResponse>> GetDesignStylesAsync();
    Task<string> GetConfiguredPromptAsync(
        int? styleId,
        string? styleName,
        string? legacyStyle,
        string? roomType = null,
        string? featureId = null);
}

public class DesignPromptService : IDesignPromptService
{
    private readonly AppDbContext _context;
    private readonly ILogger<DesignPromptService> _logger;

    public DesignPromptService(AppDbContext context, ILogger<DesignPromptService> logger)
    {
        _context = context;
        _logger = logger;
    }

    public async Task<List<DesignStyleResponse>> GetDesignStylesAsync()
    {
        var supportedStyleNames = DesignStyleSeedData.SupportedStyleNames;

        return await _context.StyleAesthetics
            .AsNoTracking()
            .Where(style => supportedStyleNames.Contains(style.StyleName))
            .OrderBy(style => style.StyleName)
            .Select(style => new DesignStyleResponse
            {
                StyleId = style.StyleID,
                StyleName = style.StyleName,
                CoreAesthetic = style.CoreAesthetic
            })
            .ToListAsync();
    }

    public async Task<string> GetConfiguredPromptAsync(
        int? styleId,
        string? styleName,
        string? legacyStyle,
        string? roomType = null,
        string? featureId = null)
    {
        var styleKey = FirstNotEmpty(styleName, legacyStyle);
        var normalizedFeatureId = NormalizeFeatureId(featureId);
        var roomTypeKey = NormalizeRoomTypeKey(roomType);
        if (styleId == null && string.IsNullOrWhiteSpace(styleKey))
        {
            if (normalizedFeatureId == "remove_furniture")
            {
                return BuildRemoveFurniturePrompt(GetFallbackRoomTypeName(roomTypeKey));
            }

            throw new ArgumentException("Design styleId or styleName is required.");
        }

        var styleConfig = await FindStyleAsync(styleId, styleKey);
        if (styleConfig == null)
        {
            var requestedStyle = styleId?.ToString() ?? styleKey;
            throw new KeyNotFoundException($"Design style '{requestedStyle}' was not found. Use an existing styleId or styleName.");
        }

        if (!string.IsNullOrWhiteSpace(roomTypeKey))
        {
            var roomPrompt = await _context.RoomStylePrompts
                .AsNoTracking()
                .FirstOrDefaultAsync(prompt =>
                    prompt.StyleId == styleConfig.StyleID &&
                    prompt.RoomTypeKey == roomTypeKey);

            if (roomPrompt != null)
            {
                var generatedPrompt = normalizedFeatureId switch
                {
                    "furnish_empty_room" => BuildFurnishEmptyRoomPrompt(styleConfig, roomPrompt),
                    "remove_furniture" => BuildRemoveFurniturePrompt(roomPrompt.RoomTypeName, roomPrompt),
                    _ => BuildRoomPrompt(styleConfig, roomPrompt)
                };

                _logger.LogInformation("Generated prompt for Room: {RoomType}, Style: {StyleName}. Content: {Prompt}", 
                    roomPrompt.RoomTypeName, styleConfig.StyleName, generatedPrompt);

                return generatedPrompt;
            }

            _logger.LogInformation(
                "No room-specific prompt found for style {StyleId} and roomType {RoomTypeKey}. Falling back to style-only prompt.",
                styleConfig.StyleID,
                roomTypeKey);
        }

        if (normalizedFeatureId == "remove_furniture")
        {
            var removePrompt = BuildRemoveFurniturePrompt(GetFallbackRoomTypeName(roomTypeKey));
            _logger.LogInformation("Generated fallback remove_furniture prompt. Content: {Prompt}", removePrompt);
            return removePrompt;
        }

        var prompt = BuildPrompt(styleConfig);
        if (string.IsNullOrWhiteSpace(prompt))
        {
            throw new InvalidOperationException($"Design style '{styleConfig.StyleName}' does not have a configured prompt.");
        }

        _logger.LogInformation("Generated style fallback prompt for Style: {StyleName}. Content: {Prompt}", styleConfig.StyleName, prompt);
        return prompt;
    }

    private async Task<StyleAesthetic?> FindStyleAsync(int? styleId, string? styleKey)
    {
        StyleAesthetic? styleConfig = null;
        if (styleId != null)
        {
            styleConfig = await _context.StyleAesthetics
                .AsNoTracking()
                .FirstOrDefaultAsync(style => style.StyleID == styleId.Value);
        }

        if (styleConfig != null || string.IsNullOrWhiteSpace(styleKey))
        {
            return styleConfig;
        }

        styleKey = styleKey.Trim();
        if (int.TryParse(styleKey, out var parsedStyleId))
        {
            styleConfig = await _context.StyleAesthetics
                .AsNoTracking()
                .FirstOrDefaultAsync(style => style.StyleID == parsedStyleId);
        }

        return styleConfig ?? await _context.StyleAesthetics
            .AsNoTracking()
            .FirstOrDefaultAsync(style => style.StyleName == styleKey);
    }

    private static string? FirstNotEmpty(params string?[] values)
    {
        return values.FirstOrDefault(value => !string.IsNullOrWhiteSpace(value));
    }

    private static string? NormalizeRoomTypeKey(string? roomType)
    {
        if (string.IsNullOrWhiteSpace(roomType))
        {
            return null;
        }

        var normalized = roomType.Trim().ToLowerInvariant()
            .Replace("-", " ")
            .Replace("_", " ");

        return normalized switch
        {
            "living room" => "living_room",
            "bedroom" or "master bedroom" or "master" => "master_bedroom",
            "kitchen" => "kitchen",
            "dining room" => "dining_room",
            "bathroom" => "bathroom",
            "study room" or "office" => "study_room",
            "kids room" or "kid room" or "children room" => "kids_room",
            "walk in closet" or "walk-in closet" or "closet" => "walk_in_closet",
            "hallway" => "hallway",
            "guest room" => "guest_room",
            _ => normalized.Replace(" ", "_")
        };
    }

    private static string NormalizeFeatureId(string? featureId)
    {
        if (string.IsNullOrWhiteSpace(featureId))
        {
            return "interior_design";
        }

        var normalized = featureId.Trim().ToLowerInvariant()
            .Replace("-", "_")
            .Replace(" ", "_");

        return normalized switch
        {
            "interior_design" => "interior_design",
            "furnish_empty_room" or "furnish_room" or "empty_room" => "furnish_empty_room",
            "remove_furniture" or "clear_room" or "empty_existing_room" => "remove_furniture",
            _ => "interior_design"
        };
    }

    private static string GetFallbackRoomTypeName(string? roomTypeKey)
    {
        return roomTypeKey switch
        {
            "living_room" => "Living Room",
            "master_bedroom" => "Master Bedroom",
            "kitchen" => "Kitchen",
            "dining_room" => "Dining Room",
            "bathroom" => "Bathroom",
            "study_room" => "Study Room",
            "kids_room" => "Kids Room",
            "walk_in_closet" => "Walk-in Closet",
            _ => "room"
        };
    }

    private static string BuildRoomPrompt(StyleAesthetic style, RoomStylePrompt roomPrompt)
    {
        var prompt = Smart.Format(roomPrompt.PromptTemplate, roomPrompt);

        return AppendAvoidClause(prompt, roomPrompt.SpecificNegative);
    }

    private static string BuildFurnishEmptyRoomPrompt(StyleAesthetic style, RoomStylePrompt roomPrompt)
    {
        var styleCore = style.StyleName switch
        {
            "Japandi" => "Japandi (Japanese-Scandinavian fusion)",
            "Tropical" => "Tropical",
            _ => style.CoreAesthetic
        };

        var prompt = $"A photorealistic architectural interior photography of an empty or mostly empty {roomPrompt.RoomTypeName}. " +
                     $"Furnish the space from scratch as a residential {styleCore} while preserving the original architecture, camera angle, walls, floor, ceiling, windows, doors, and room geometry. " +
                     $"The scene features {roomPrompt.Lighting}. " +
                     $"Materials strictly limited to {roomPrompt.Material}. " +
                     $"Color grading follows a strict rule: {roomPrompt.Color}. " +
                     $"Add suitable furniture and decor for this room type, including {roomPrompt.Furniture}. " +
                     $"The atmosphere is {roomPrompt.Atmosphere}. " +
                     "Photorealistic, natural room lighting, hyper-detailed, architectural photography, 8k.";

        return AppendAvoidClause(prompt, roomPrompt.SpecificNegative);
    }

    private static string BuildRemoveFurniturePrompt(string roomTypeName, RoomStylePrompt? roomPrompt = null)
    {
        var prompt = $"A photorealistic architectural interior photography of a {roomTypeName}. " +
                     "Remove all existing furniture, loose decor, rugs, clutter, personal items, and movable objects from the uploaded room. " +
                     "Preserve the original architecture, camera angle, walls, floor, ceiling, windows, doors, built-in fixtures, and room geometry. " +
                     "Realistically reconstruct any hidden floor, wall, baseboard, ceiling, shadow, and surface areas that were covered by removed objects. " +
                     "Keep the result as a clean empty room with natural room lighting and believable material continuity. " +
                     "Do not add new furniture. Do not add decorative styling. Do not introduce style-heavy decor, plants, artwork, rugs, people, text, or watermark. " +
                     "Avoid distorted architecture, warped walls, broken windows, unrealistic reconstruction, people, text, and watermark. " +
                     "Photorealistic, natural room lighting, hyper-detailed, architectural photography, 8k.";

        return AppendAvoidClause(prompt, roomPrompt?.SpecificNegative);
    }

    private static string BuildPrompt(StyleAesthetic style)
    {
        var promptBuilder = new StringBuilder();

        AppendLineIfPresent(promptBuilder, style.CoreAesthetic);
        AppendLineIfPresent(promptBuilder, style.TechnicalSpecs);

        return promptBuilder.ToString().Trim();
    }

    private static string AppendAvoidClause(string prompt, string? specificNegative)
    {
        var trimmedPrompt = prompt.Trim();
        if (string.IsNullOrWhiteSpace(specificNegative))
        {
            return trimmedPrompt;
        }

        return $"{trimmedPrompt} Avoid: {specificNegative.Trim()}";
    }

    private static void AppendLineIfPresent(StringBuilder promptBuilder, string? value)
    {
        if (!string.IsNullOrWhiteSpace(value))
        {
            promptBuilder.AppendLine(value.Trim());
        }
    }

    private static void AppendListIfPresent(StringBuilder promptBuilder, string label, IEnumerable<string> values)
    {
        var configuredValues = values
            .Where(value => !string.IsNullOrWhiteSpace(value))
            .Select(value => value.Trim())
            .ToArray();

        if (configuredValues.Length == 0)
        {
            return;
        }

        promptBuilder.Append(label);
        promptBuilder.Append(": ");
        promptBuilder.AppendLine(string.Join(", ", configuredValues));
    }
}
