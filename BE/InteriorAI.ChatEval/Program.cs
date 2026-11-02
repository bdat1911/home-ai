using System.Diagnostics;
using System.Globalization;
using System.Text;
using InteriorAI.Data;
using InteriorAI.Extensions;
using InteriorAI.Services;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;

// Runs the evaluation questions against each answer mode and writes one CSV per mode.
// Usage: dotnet run -- [--modes Lexical,Hybrid,NoRag] [--retrieval-only] [--questions file.csv]
//                      [--min-cosine 0.62] [--delay-ms 6000] [--approved-only] [--model name]
var options = ParseOptions(args);
var repositoryRoot = KnowledgeBaseService.FindRepositoryRoot(AppContext.BaseDirectory);
var apiDirectory = Path.Combine(repositoryRoot, "BE", "InteriorAI");
var questionsPath = options.GetValueOrDefault("questions")
    ?? Path.Combine(repositoryRoot, "knowledge-base", "eval", "questions.csv");
var modes = (options.GetValueOrDefault("modes") ?? "Lexical,Hybrid,NoRag")
    .Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
var retrievalOnly = options.ContainsKey("retrieval-only");
var includeDrafts = !options.ContainsKey("approved-only");
var delay = int.Parse(options.GetValueOrDefault("delay-ms") ?? "6000", CultureInfo.InvariantCulture);
var questions = SimpleCsv.ReadRecords(questionsPath);
var outputDirectory = Path.Combine(repositoryRoot, "knowledge-base", "eval", "results");
Directory.CreateDirectory(outputDirectory);
var stamp = DateTime.Now.ToString("yyyyMMdd-HHmmss", CultureInfo.InvariantCulture);

Console.WriteLine($"Questions: {questions.Count}; corpus: {(includeDrafts ? "drafts included (provisional)" : "approved only")}");

foreach (var mode in modes)
{
    var overrides = new Dictionary<string, string?>
    {
        ["Chat:AnswerMode"] = mode,
        ["Chat:GeminiEnabled"] = "true"
    };
    if (options.TryGetValue("min-cosine", out var minCosine)) overrides["Knowledge:MinCosine"] = minCosine;
    if (options.TryGetValue("model", out var model)) overrides["Gemini:Model"] = model;
    var configuration = new ConfigurationBuilder()
        .AddJsonFile(Path.Combine(apiDirectory, "appsettings.json"), optional: true)
        .AddJsonFile(Path.Combine(apiDirectory, "appsettings.Development.json"), optional: true)
        .AddEnvironmentVariables()
        .AddInMemoryCollection(overrides)
        .Build();

    var services = new ServiceCollection();
    services.AddSingleton<IConfiguration>(configuration);
    services.AddLogging(builder => builder.AddSimpleConsole().SetMinimumLevel(LogLevel.Warning));
    services.AddDbContext<AppDbContext>(db => db.UseSqlServer(configuration.GetConnectionString("DefaultConnection")));
    services.AddSingleton(new KnowledgeBaseService(repositoryRoot, includeDrafts));
    services.AddChatbot();
    await using var provider = services.BuildServiceProvider();

    var rows = new List<string>
    {
        "id,mode,should_abstain,should_clarify,expected_documents,retrieved_documents,hit_at_5,reciprocal_rank,recall_at_5,source,abstained,citations,citations_valid,latency_ms,reply,score_groundedness_0_2,score_helpfulness_0_2,forbidden_claims,expected_points"
    };
    var hits = new List<bool>();
    var ranks = new List<double>();
    var recalls = new List<double>();
    var latencies = new List<long>();
    var abstainCorrect = 0;
    var abstainTotal = 0;
    var wrongAbstain = 0;
    var citationsTotal = 0;
    var citationsValid = 0;
    var llmAnswers = 0;
    var answered = 0;

    foreach (var question in questions)
    {
        var text = question["question"];
        var history = (question.GetValueOrDefault("conversation_context") ?? "")
            .Split(';', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
            .Select(ChatTurn.User)
            .ToArray();
        var expected = (question.GetValueOrDefault("expected_document_ids") ?? "")
            .Split('|', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
            .ToHashSet(StringComparer.Ordinal);
        var shouldAbstain = IsTrue(question.GetValueOrDefault("should_abstain"));
        var shouldClarify = IsTrue(question.GetValueOrDefault("should_clarify"));

        using var scope = provider.CreateScope();
        IReadOnlyList<KnowledgeMatch> retrieved = [];
        if (mode != ChatAnswerModes.NoRag)
        {
            IKnowledgeRetriever retriever = mode == ChatAnswerModes.Lexical
                ? scope.ServiceProvider.GetRequiredService<LexicalRetriever>()
                : scope.ServiceProvider.GetRequiredService<HybridRetriever>();
            retrieved = await retriever.RetrieveAsync(text, history, null, CancellationToken.None);
        }
        var documents = retrieved.Select(match => match.DocumentId).Distinct().ToArray();
        var firstHit = Array.FindIndex(documents, expected.Contains);
        var hit = firstHit >= 0;
        var reciprocal = hit ? 1d / (firstHit + 1) : 0d;
        var recall = expected.Count == 0 ? 0d : expected.Count(documents.Contains) / (double)expected.Count;
        if (!shouldAbstain && expected.Count > 0 && mode != ChatAnswerModes.NoRag)
        {
            hits.Add(hit);
            ranks.Add(reciprocal);
            recalls.Add(recall);
        }

        var reply = "";
        var source = "retrieval-only";
        var abstained = false;
        var citedCount = 0;
        var citedValid = true;
        long latency = 0;
        if (!retrievalOnly)
        {
            var watch = Stopwatch.StartNew();
            var result = await scope.ServiceProvider.GetRequiredService<IChatReplyService>()
                .ReplyAsync(text, history, null, CancellationToken.None);
            watch.Stop();
            latency = watch.ElapsedMilliseconds;
            latencies.Add(latency);
            reply = result.Content;
            source = result.Source;
            abstained = source.StartsWith("guard", StringComparison.Ordinal) || source == "abstain";
            citedCount = result.UsedMatches.Count;
            var allowed = retrieved.Select(match => match.ChunkId).ToHashSet(StringComparer.Ordinal);
            citedValid = result.UsedMatches.All(match => allowed.Contains(match.ChunkId));
            citationsTotal += citedCount;
            citationsValid += citedValid ? citedCount : 0;
            if (shouldAbstain)
            {
                abstainTotal++;
                if (abstained) abstainCorrect++;
            }
            else if (abstained) wrongAbstain++;
            if (source == "llm") llmAnswers++;
            answered++;
        }
        if (delay > 0) await Task.Delay(delay);

        rows.Add(string.Join(',', new[]
        {
            question["id"], mode, shouldAbstain.ToString(), shouldClarify.ToString(),
            string.Join('|', expected), string.Join('|', documents), hit.ToString(),
            reciprocal.ToString("0.000", CultureInfo.InvariantCulture),
            recall.ToString("0.000", CultureInfo.InvariantCulture),
            source, abstained.ToString(), citedCount.ToString(CultureInfo.InvariantCulture), citedValid.ToString(),
            latency.ToString(CultureInfo.InvariantCulture), reply, "", "",
            question.GetValueOrDefault("forbidden_claims"), question.GetValueOrDefault("expected_points")
        }.Select(SimpleCsv.Escape)));
    }

    var path = Path.Combine(outputDirectory, $"{stamp}-{mode}{(retrievalOnly ? "-retrieval" : "")}.csv");
    await File.WriteAllLinesAsync(path, rows, new UTF8Encoding(encoderShouldEmitUTF8Identifier: true));

    var summary = new StringBuilder($"[{mode}]");
    if (hits.Count > 0)
    {
        summary.Append(CultureInfo.InvariantCulture, $" Hit@5 {hits.Count(hit => hit) / (double)hits.Count:P1} ({hits.Count} q)");
        summary.Append(CultureInfo.InvariantCulture, $", MRR {ranks.Average():0.000}, Recall@5 {recalls.Average():0.000}");
    }
    if (!retrievalOnly)
    {
        latencies.Sort();
        if (abstainTotal > 0)
            summary.Append(CultureInfo.InvariantCulture, $", correct refusals {abstainCorrect}/{abstainTotal}");
        summary.Append(CultureInfo.InvariantCulture, $", wrong refusals {wrongAbstain}");
        summary.Append(CultureInfo.InvariantCulture, $", LLM answers {llmAnswers}/{answered}");
        summary.Append(CultureInfo.InvariantCulture, $", citations valid {citationsValid}/{citationsTotal}");
        if (latencies.Count > 0)
            summary.Append(CultureInfo.InvariantCulture,
                $", latency P50 {latencies[latencies.Count / 2]} ms, P95 {latencies[(int)(latencies.Count * 0.95)]} ms");
    }
    Console.WriteLine(summary);
    Console.WriteLine($"  -> {path}");
}

static bool IsTrue(string? value) => string.Equals(value?.Trim(), "true", StringComparison.OrdinalIgnoreCase);

static Dictionary<string, string> ParseOptions(string[] arguments)
{
    var result = new Dictionary<string, string>(StringComparer.Ordinal);
    for (var i = 0; i < arguments.Length; i++)
    {
        if (!arguments[i].StartsWith("--", StringComparison.Ordinal)) continue;
        var name = arguments[i][2..];
        var hasValue = i + 1 < arguments.Length && !arguments[i + 1].StartsWith("--", StringComparison.Ordinal);
        result[name] = hasValue ? arguments[++i] : "true";
    }
    return result;
}
