namespace InteriorAI.Data.Seed;

internal sealed record StyleAestheticSeed(
    string StyleName,
    string CoreAesthetic,
    IReadOnlyList<string> LightingOptions,
    IReadOnlyList<string> MaterialOptions,
    IReadOnlyList<string> ColorRuleOptions,
    IReadOnlyList<string> AtmosphereOptions,
    string TechnicalSpecs);

internal sealed record RoomStylePromptSeed(
    string StyleName,
    string RoomTypeKey,
    string RoomTypeName,
    string Variant,
    string Lighting,
    string Material,
    string Color,
    string Furniture,
    string Atmosphere,
    string BaseStructuralPrompt,
    string PromptTemplate,
    string SpecificNegative);
