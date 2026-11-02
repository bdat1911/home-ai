using System.Security.Cryptography;
using System.Text;
using InteriorAI.Data;
using InteriorAI.Domain.Entities;
using Microsoft.EntityFrameworkCore;

namespace InteriorAI.Services;

public sealed record KnowledgeIngestReport(int Documents, int Embedded, int Unchanged, int Deactivated, int Failed);

/// <summary>
/// Repeatable corpus import: unchanged chunks are not embedded again, removed chunks are deactivated.
/// </summary>
public sealed class KnowledgeIngestService
{
    private readonly AppDbContext _context;
    private readonly KnowledgeBaseService _knowledgeBase;
    private readonly IEmbeddingService _embeddings;
    private readonly ILogger<KnowledgeIngestService> _logger;

    public KnowledgeIngestService(
        AppDbContext context, KnowledgeBaseService knowledgeBase, IEmbeddingService embeddings,
        ILogger<KnowledgeIngestService> logger)
    {
        _context = context;
        _knowledgeBase = knowledgeBase;
        _embeddings = embeddings;
        _logger = logger;
    }

    public async Task<KnowledgeIngestReport> IngestAsync(CancellationToken cancellationToken)
    {
        var parsed = _knowledgeBase.GetDocuments();
        var documents = await _context.KnowledgeDocuments.ToDictionaryAsync(item => item.Id, cancellationToken);
        var chunks = await _context.KnowledgeChunks.ToDictionaryAsync(item => item.Id, cancellationToken);
        var seenChunks = new HashSet<string>(StringComparer.Ordinal);
        var embedded = 0;
        var unchanged = 0;
        var failed = 0;
        var now = DateTime.UtcNow;

        foreach (var document in parsed)
        {
            if (!documents.TryGetValue(document.Id, out var row))
            {
                row = new KnowledgeDocument { Id = document.Id };
                _context.KnowledgeDocuments.Add(row);
                documents[document.Id] = row;
            }
            row.Title = document.Title;
            row.DocumentType = document.Type;
            row.Status = document.Status;
            row.Version = document.Version;
            row.IsActive = true;
            row.UpdatedAt = now;

            foreach (var chunk in document.Chunks)
            {
                seenChunks.Add(chunk.Id);
                var embeddingText = BuildEmbeddingText(document, chunk);
                var hash = Hash(embeddingText);
                chunks.TryGetValue(chunk.Id, out var chunkRow);
                if (chunkRow != null && chunkRow.ContentHash == hash &&
                    chunkRow.EmbeddingModel == _embeddings.ModelName)
                {
                    chunkRow.Ordinal = chunk.Ordinal;
                    chunkRow.IsActive = true;
                    unchanged++;
                    continue;
                }

                var vector = await _embeddings.EmbedAsync(embeddingText, EmbeddingTask.Document, cancellationToken);
                if (vector == null)
                {
                    failed++;
                    _logger.LogWarning("Could not embed chunk {ChunkId}", chunk.Id);
                    continue;
                }

                if (chunkRow == null)
                {
                    chunkRow = new KnowledgeChunk { Id = chunk.Id, DocumentId = document.Id };
                    _context.KnowledgeChunks.Add(chunkRow);
                    chunks[chunk.Id] = chunkRow;
                }
                chunkRow.Ordinal = chunk.Ordinal;
                chunkRow.Text = chunk.Text;
                chunkRow.ContentHash = hash;
                chunkRow.Embedding = VectorMath.ToBytes(vector);
                chunkRow.EmbeddingModel = _embeddings.ModelName;
                chunkRow.IsActive = true;
                chunkRow.UpdatedAt = now;
                embedded++;
            }

            // Save per document so an interrupted run keeps the embeddings it already paid for.
            await _context.SaveChangesAsync(cancellationToken);
        }

        var deactivated = 0;
        var liveDocuments = parsed.Select(item => item.Id).ToHashSet(StringComparer.Ordinal);
        foreach (var chunk in chunks.Values.Where(item => item.IsActive && !seenChunks.Contains(item.Id)))
        {
            chunk.IsActive = false;
            deactivated++;
        }
        foreach (var document in documents.Values.Where(item => item.IsActive && !liveDocuments.Contains(item.Id)))
            document.IsActive = false;
        await _context.SaveChangesAsync(cancellationToken);

        return new KnowledgeIngestReport(parsed.Count, embedded, unchanged, deactivated, failed);
    }

    private static string BuildEmbeddingText(ParsedKnowledgeDocument document, KnowledgeChunkRecord chunk) =>
        $"{document.Title}\n{chunk.Section}\n{chunk.Text}";

    private static string Hash(string text) =>
        Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(text))).ToLowerInvariant();
}
