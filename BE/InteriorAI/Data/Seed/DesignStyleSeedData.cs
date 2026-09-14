namespace InteriorAI.Data.Seed;

internal static class DesignStyleSeedData
{
    public static IReadOnlyList<StyleAestheticSeed> All { get; } = new[]
    {
        new StyleAestheticSeed(
            "Indochine",
            "Refined Indochine aesthetic",
            new[] { "soft ambient cove lighting from a round recessed ceiling", "warm glows from symmetrical table lamps", "hanging lantern-style pendants" },
            new[] { "dark mahogany wood", "crisp white painted moldings", "rich silk and velvet textiles" },
            new[] { "50% crisp white", "30% dark espresso wood", "20% jade green with subtle yellow accents" },
            new[] { "elegant", "highly symmetrical", "culturally rich" },
            "Photorealistic, natural room lighting, hyper-detailed, architectural photography, 4k."),

        new StyleAestheticSeed(
            "Minimalist Luxury",
            "Residential Minimalist Luxury style",
            new[] { "bright diffused morning daylight flooding the space through large windows, complemented by soft indirect ambient illumination, subtle architectural lighting, and natural shadow transitions that enhance spatial clarity and material texture" },
            new[] { "matte white plaster walls, light natural oak wood flooring, smooth microcement accents, premium stone surfaces, textured wool fabrics, sheer linen textiles, brushed metal details, and high-quality minimalist architectural finishes" },
            new[] { "80% pure white and warm light wood tones, supported by soft beige neutrals and subtle matte black accents, creating a clean, airy, and harmonious minimalist palette with no saturated colors" },
            new[] { "peaceful, highly structured, sophisticated, bright, calm, uncluttered, contemporary, and timelessly elegant, emphasizing simplicity, balance, and architectural purity" },
            "Ultra-realistic architectural photography, luxury interior magazine quality, HDR rendering, realistic global illumination, physically based materials, ultra-detailed textures, soft shadows, realistic reflections, 8K resolution, wide-angle lens, professional composition, and flawless craftsmanship."),

        new StyleAestheticSeed(
            "Modern Luxury",
            "Residential Modern Luxury style",
            new[] { "abundant soft natural daylight blended with warm indirect architectural lighting" },
            new[] { "polished Calacatta marble flooring, glossy lacquer cabinetry, champagne gold metal detailing, mirror panels, clear glass surfaces, premium ivory boucle and linen upholstery, and silk-blend area rugs" },
            new[] { "monochromatic warm neutrals, ivory white, champagne beige, soft cream, pale taupe, light greige, and subtle metallic gold accents only" },
            new[] { "elegant, bright, tranquil, sophisticated, upscale, hotel-inspired, and penthouse-inspired with a strong sense of openness, comfort, and refinement" },
            "Ultra-realistic luxury interior photography, architectural digest style, cinematic natural lighting, realistic reflections, global illumination, ray tracing, physically based rendering, physically accurate materials, realistic shadows, depth of field, ultra-detailed textures, professional HDR photography, wide-angle lens, magazine-quality composition, 8K resolution, masterpiece quality."),

        new StyleAestheticSeed(
            "Industrial",
            "Modern residential industrial loft aesthetic",
            new[] { "warm glowing LED under-shelf lighting paired with industrial pendant lights", "dramatic lighting from large grid-pane factory windows", "warm glowing Edison bulbs suspended from black cords" },
            new[] { "exposed red brick", "matte black wood", "stainless steel", "distressed leather", "reclaimed rough wood", "matte dark charcoal walls" },
            new[] { "50% matte black, 30% rustic brick red, 20% warm wood", "60% dark charcoal, 30% warm cognac leather, 10% subtle brass" },
            new[] { "moody, bold, and highly textured", "raw, masculine, and edgy", "urban, rugged, and historic" },
            "Photorealistic, natural room lighting, hyper-detailed, architectural photography, 8k."),

        new StyleAestheticSeed(
            "Neoclassic",
            "Refined residential Neoclassic style",
            new[] { "soft ambient spring morning light with warm shadows", "bright airy summer daylight", "romantic autumn evening lighting" },
            new[] { "polished marble", "painted white wood moldings", "premium velvet", "chevron oak wood flooring", "brushed gold hardware" },
            new[] { "60% creamy whites", "30% champagne and taupe", "10% dusty rose", "10% brushed gold and brass metallic accents" },
            new[] { "elegant", "romantic", "luxurious", "majestic", "refined" },
            "Ultra-realistic architectural photography, luxury interior magazine quality, HDR rendering, realistic global illumination, physically based materials, ultra-detailed textures, soft shadows, realistic reflections, 8K resolution."),

        new StyleAestheticSeed(
            "Scandinavian",
            "Cozy, 'Hygge'-inspired residential Scandinavian style",
            new[] { "abundant bright natural daylight", "soft warm indoor lighting against a cold window view", "diffused overcast daylight" },
            new[] { "light pine wood", "birch wood veneer", "chunky knit wool", "matte white surfaces", "boucle fabric" },
            new[] { "70% crisp white", "20% light wood", "10% pastel accents", "60% warm off-white" },
            new[] { "cozy", "hygge", "inviting", "bright", "functional", "serene" },
            "Ultra-realistic architectural photography, luxury interior magazine quality, HDR rendering, realistic global illumination, physically based materials, ultra-detailed textures, soft shadows, realistic reflections, 8K resolution.")
    };

    public static IReadOnlyList<string> SupportedStyleNames { get; } = All
        .Select(style => style.StyleName)
        .ToArray();
}