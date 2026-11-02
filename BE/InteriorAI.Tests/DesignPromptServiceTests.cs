using InteriorAI.Services;
using Microsoft.Extensions.Logging.Abstractions;

namespace InteriorAI.Tests;

public class DesignPromptServiceTests
{
    [Fact]
    public async Task RemoveFurnitureStripsBuiltInsAndFinishesDownToTheShell()
    {
        // Without a style this path never reads the database.
        var service = new DesignPromptService(null!, NullLogger<DesignPromptService>.Instance);

        var prompt = await service.GetConfiguredPromptAsync(null, null, null, "kitchen", "remove_furniture");

        Assert.Contains("kitchen cabinets, countertops, kitchen islands", prompt);
        Assert.Contains("bare concrete floor", prompt);
        Assert.Contains("window and door openings", prompt);
        Assert.DoesNotContain("built-in fixtures", prompt);
    }
}
