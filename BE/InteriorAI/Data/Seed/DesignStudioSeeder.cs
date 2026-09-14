using InteriorAI.Domain.Entities;
using Microsoft.EntityFrameworkCore;

namespace InteriorAI.Data.Seed;

public class DesignStudioSeeder
{
    private readonly AppDbContext _context;
    private readonly ILogger<DesignStudioSeeder> _logger;

    public DesignStudioSeeder(AppDbContext context, ILogger<DesignStudioSeeder> logger)
    {
        _context = context;
        _logger = logger;
    }
    public async Task EnsureSeedDataAsync()
    {
        await EnsureStylesAsync();
        await EnsureRoomStylePromptsAsync();
        _logger.LogInformation("Ensured default design prompt styles and room-specific prompts.");
    }
    public async Task EnsureStylesAsync()
    {
        foreach (var seed in DesignStyleSeedData.All)
        {
            var existingStyle = await _context.StyleAesthetics
                .FirstOrDefaultAsync(style => style.StyleName == seed.StyleName);

            if (existingStyle == null)
            {
                _context.StyleAesthetics.Add(CreateStyle(seed));
            }
            else
            {
                ApplyStyleDefaults(existingStyle, seed);
            }
        }
        await _context.SaveChangesAsync();
    }

    public async Task EnsureRoomStylePromptsAsync()
    {
        var seededStyleNames = RoomStylePromptSeedData.All
            .Select(seed => seed.StyleName)
            .Distinct()
            .ToArray();

        var styles = await _context.StyleAesthetics
            .Where(style => seededStyleNames.Contains(style.StyleName))
            .ToDictionaryAsync(style => style.StyleName);

        foreach (var seed in RoomStylePromptSeedData.All)
        {
            if (!styles.TryGetValue(seed.StyleName, out var style))
            {
                continue;
            }

            var existingPrompt = await _context.RoomStylePrompts
                .FirstOrDefaultAsync(prompt =>
                    prompt.StyleId == style.StyleID &&
                    prompt.RoomTypeKey == seed.RoomTypeKey);

            if (existingPrompt == null)
            {
                _context.RoomStylePrompts.Add(new RoomStylePrompt
                {
                    StyleId = style.StyleID,
                    RoomTypeKey = seed.RoomTypeKey,
                    RoomTypeName = seed.RoomTypeName,
                    Variant = seed.Variant,
                    Lighting = seed.Lighting,
                    Material = seed.Material,
                    Color = seed.Color,
                    Furniture = seed.Furniture,
                    Atmosphere = seed.Atmosphere,
                    BaseStructuralPrompt = seed.BaseStructuralPrompt,
                    PromptTemplate = seed.PromptTemplate,
                    SpecificNegative = seed.SpecificNegative,
                    CreatedAt = DateTime.UtcNow,
                    UpdatedAt = DateTime.UtcNow
                });
                continue;
            }

            existingPrompt.RoomTypeName = seed.RoomTypeName;
            existingPrompt.Variant = seed.Variant;
            existingPrompt.Lighting = seed.Lighting;
            existingPrompt.Material = seed.Material;
            existingPrompt.Color = seed.Color;
            existingPrompt.Furniture = seed.Furniture;
            existingPrompt.Atmosphere = seed.Atmosphere;
            existingPrompt.BaseStructuralPrompt = seed.BaseStructuralPrompt;
            existingPrompt.PromptTemplate = seed.PromptTemplate;
            existingPrompt.SpecificNegative = seed.SpecificNegative;
            existingPrompt.UpdatedAt = DateTime.UtcNow;
        }

        await _context.SaveChangesAsync();
    }

    private static StyleAesthetic CreateStyle(StyleAestheticSeed seed)
    {
        return new StyleAesthetic
        {
            StyleName = seed.StyleName,
            CoreAesthetic = seed.CoreAesthetic,
            TechnicalSpecs = seed.TechnicalSpecs
        };
    }

    private static void ApplyStyleDefaults(StyleAesthetic target, StyleAestheticSeed source)
    {
        target.CoreAesthetic = source.CoreAesthetic;
        target.TechnicalSpecs = source.TechnicalSpecs;
    }
}
