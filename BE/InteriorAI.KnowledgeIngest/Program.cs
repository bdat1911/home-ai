using InteriorAI.Data;
using InteriorAI.Extensions;
using InteriorAI.Services;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;

// Imports knowledge-base/docs into SQL Server with embeddings. Safe to run repeatedly.
var repositoryRoot = KnowledgeBaseService.FindRepositoryRoot(AppContext.BaseDirectory);
var apiDirectory = Path.Combine(repositoryRoot, "BE", "InteriorAI");
var configuration = new ConfigurationBuilder()
    .AddJsonFile(Path.Combine(apiDirectory, "appsettings.json"), optional: true)
    .AddJsonFile(Path.Combine(apiDirectory, "appsettings.Development.json"), optional: true)
    .AddEnvironmentVariables()
    .Build();

var services = new ServiceCollection();
services.AddSingleton<IConfiguration>(configuration);
services.AddLogging(builder => builder.AddSimpleConsole().SetMinimumLevel(LogLevel.Warning));
services.AddDbContext<AppDbContext>(options =>
    options.UseSqlServer(configuration.GetConnectionString("DefaultConnection")));
services.AddSingleton(new KnowledgeBaseService(repositoryRoot, includeDrafts: true));
services.AddChatbot();

await using var provider = services.BuildServiceProvider();
using var scope = provider.CreateScope();
var report = await scope.ServiceProvider.GetRequiredService<KnowledgeIngestService>()
    .IngestAsync(CancellationToken.None);

Console.WriteLine(
    $"Documents: {report.Documents}, embedded: {report.Embedded}, unchanged: {report.Unchanged}, " +
    $"deactivated: {report.Deactivated}, failed: {report.Failed}");
return report.Failed == 0 ? 0 : 1;
