using InteriorAI.Data;
using Microsoft.EntityFrameworkCore;

namespace InteriorAI.Services;

public sealed record BriefContext(string? RoomType, string? StyleName);

public interface IKnowledgeRetriever
{
    Task<IReadOnlyList<KnowledgeMatch>> RetrieveAsync(
        string question, IReadOnlyList<ChatTurn> history, BriefContext? brief, CancellationToken cancellationToken);
}

/// <summary>Keyword-only retrieval: the baseline the hybrid retriever is measured against.</summary>
public sealed class LexicalRetriever : IKnowledgeRetriever
{
    private readonly KnowledgeBaseService _knowledgeBase;

    public LexicalRetriever(KnowledgeBaseService knowledgeBase) => _knowledgeBase = knowledgeBase;

    public Task<IReadOnlyList<KnowledgeMatch>> RetrieveAsync(
        string question, IReadOnlyList<ChatTurn> history, BriefContext? brief, CancellationToken cancellationToken) =>
        Task.FromResult(_knowledgeBase.Search(question, take: 5, history: history));
}

/// <summary>Embeddings of the active corpus, loaded once from SQL Server and searched in memory.</summary>
public sealed class KnowledgeIndex
{
    private readonly IServiceScopeFactory _scopeFactory;
    private readonly SemaphoreSlim _loadLock = new(1, 1);
    private IReadOnlyDictionary<string, float[]>? _vectors;

    public KnowledgeIndex(IServiceScopeFactory scopeFactory) => _scopeFactory = scopeFactory;

    public async Task<IReadOnlyDictionary<string, float[]>> GetVectorsAsync(
        string model, CancellationToken cancellationToken)
    {
        if (_vectors != null) return _vectors;
        await _loadLock.WaitAsync(cancellationToken);
        try
        {
            if (_vectors != null) return _vectors;
            using var scope = _scopeFactory.CreateScope();
            var context = scope.ServiceProvider.GetRequiredService<AppDbContext>();
            var rows = await context.KnowledgeChunks.AsNoTracking()
                .Where(chunk => chunk.IsActive && chunk.EmbeddingModel == model)
                .Select(chunk => new { chunk.Id, chunk.Embedding })
                .ToListAsync(cancellationToken);
            _vectors = rows.ToDictionary(row => row.Id, row => VectorMath.FromBytes(row.Embedding), StringComparer.Ordinal);
            return _vectors;
        }
        finally
        {
            _loadLock.Release();
        }
    }

    public void Invalidate() => _vectors = null;
}

/// <summary>
/// Merges semantic and keyword rankings with Reciprocal Rank Fusion, then keeps only chunks whose
/// cosine similarity clears a threshold, which is what lets the bot say it has no evidence.
/// Falls back to keywords alone when embeddings are unavailable.
/// </summary>
public sealed class HybridRetriever : IKnowledgeRetriever
{
    private const int RrfConstant = 60;
    private const int CandidateCount = 20;
    private const int ResultCount = 5;
    private const int MaxChunksPerDocument = 2;
    private const double MinKeywordOnlyScore = 7;

    private readonly KnowledgeBaseService _knowledgeBase;
    private readonly KnowledgeIndex _index;
    private readonly IEmbeddingService _embeddings;
    private readonly IConfiguration _configuration;
    private readonly ILogger<HybridRetriever> _logger;

    public HybridRetriever(
        KnowledgeBaseService knowledgeBase, KnowledgeIndex index, IEmbeddingService embeddings,
        IConfiguration configuration, ILogger<HybridRetriever> logger)
    {
        _knowledgeBase = knowledgeBase;
        _index = index;
        _embeddings = embeddings;
        _configuration = configuration;
        _logger = logger;
    }

    private double MinCosine => _configuration.GetValue("Knowledge:MinCosine", 0.62);

    public async Task<IReadOnlyList<KnowledgeMatch>> RetrieveAsync(
        string question, IReadOnlyList<ChatTurn> history, BriefContext? brief, CancellationToken cancellationToken)
    {
        // The brief only narrows which documents fit. The similarity gate must judge what the user
        // actually asked, or a remembered style would let an off-topic question through.
        var enriched = Enrich(question, history, brief);
        var query = _knowledgeBase.Analyze(enriched, history);
        var asked = _knowledgeBase.Analyze(question, history);
        var lexical = _knowledgeBase.Search(enriched, take: CandidateCount, history: history,
            maxPerDocument: MaxChunksPerDocument);

        var vectors = await _index.GetVectorsAsync(_embeddings.ModelName, cancellationToken);
        var queryVector = vectors.Count == 0
            ? null
            : await _embeddings.EmbedAsync(asked.Text, EmbeddingTask.Query, cancellationToken);
        if (queryVector == null)
        {
            // Without similarity scores, shared common words prove little, so only accept chunks
            // whose score shows a named style, room, color or app topic.
            _logger.LogWarning("Semantic retrieval unavailable; using strong keyword results only");
            // The remembered room and style score highly on their own, so the question itself must qualify.
            if (!_knowledgeBase.Search(question, take: 1, history: history)
                    .Any(match => match.Score >= MinKeywordOnlyScore))
                return [];
            return lexical.Where(match => match.Score >= MinKeywordOnlyScore).Take(ResultCount).ToArray();
        }

        var candidates = _knowledgeBase.GetDocuments()
            .Where(_knowledgeBase.IsServable)
            .Where(document => IsCompatible(document, query))
            .SelectMany(document => document.Chunks.Select(chunk => (document, chunk)))
            .Select(item => (item.document, item.chunk,
                cosine: vectors.TryGetValue(item.chunk.Id, out var vector) ? VectorMath.Dot(queryVector, vector) : -1d))
            .ToArray();
        var byChunk = candidates.ToDictionary(item => item.chunk.Id, StringComparer.Ordinal);
        var semanticRanking = candidates.Where(item => item.cosine >= 0)
            .OrderByDescending(item => item.cosine)
            .Take(CandidateCount)
            .Select(item => item.chunk.Id);
        var lexicalRanking = lexical.Select(match => match.ChunkId);

        var scores = new Dictionary<string, double>(StringComparer.Ordinal);
        foreach (var ranking in new[] { semanticRanking, lexicalRanking })
        {
            var rank = 0;
            foreach (var chunkId in ranking)
            {
                rank++;
                scores[chunkId] = scores.GetValueOrDefault(chunkId) + 1d / (RrfConstant + rank);
            }
        }

        return scores
            .Where(item => byChunk.TryGetValue(item.Key, out var candidate) && candidate.cosine >= MinCosine)
            .OrderByDescending(item => item.Value)
            .ThenBy(item => item.Key, StringComparer.Ordinal)
            .Select(item => (candidate: byChunk[item.Key], score: item.Value))
            .GroupBy(item => item.candidate.document.Id, StringComparer.Ordinal)
            .SelectMany(group => group.Take(MaxChunksPerDocument))
            .OrderByDescending(item => item.score)
            .Take(ResultCount)
            .Select(item => _knowledgeBase.ToMatch(item.candidate.document, item.candidate.chunk, item.candidate.cosine))
            .ToArray();
    }

    // The brief supplies what the question leaves unsaid, unless the user moved on to another topic.
    private string Enrich(string question, IReadOnlyList<ChatTurn> history, BriefContext? brief)
    {
        if (brief == null) return question;
        var query = _knowledgeBase.Analyze(question, history);
        if (query.IsAppTopic) return question;
        var additions = new List<string>();
        if (query.Styles.Count == 0 && brief.StyleName != null) additions.Add(brief.StyleName);
        if (query.Rooms.Count == 0 && brief.RoomType != null &&
            KnowledgeBaseService.RoomPhrase(brief.RoomType) is { } room) additions.Add(room);
        return additions.Count == 0 ? question : $"{question} {string.Join(' ', additions)}";
    }

    private static bool IsCompatible(ParsedKnowledgeDocument document, KnowledgeQuery query)
    {
        if (document.Type == "app_guide" && !query.IsAppTopic) return false;
        if (query.IsAppTopic && query.Styles.Count == 0 && query.Rooms.Count == 0 &&
            document.Type != "app_guide") return false;
        if (query.Styles.Count > 0 && document.Styles.Count > 0 && !document.Styles.Overlaps(query.Styles))
            return false;
        if (query.Rooms.Count > 0 && document.Rooms.Count > 0 && !document.Rooms.Overlaps(query.Rooms))
            return false;
        return true;
    }
}
