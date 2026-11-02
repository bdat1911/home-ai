using System.ComponentModel.DataAnnotations;

namespace InteriorAI.Domain.Entities;

public class KnowledgeDocument
{
    [Key]
    [MaxLength(120)]
    public string Id { get; set; } = string.Empty;

    [Required]
    [MaxLength(300)]
    public string Title { get; set; } = string.Empty;

    [Required]
    [MaxLength(40)]
    public string DocumentType { get; set; } = string.Empty;

    [Required]
    [MaxLength(40)]
    public string Status { get; set; } = string.Empty;

    [MaxLength(20)]
    public string? Version { get; set; }

    public bool IsActive { get; set; } = true;
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

    public ICollection<KnowledgeChunk> Chunks { get; set; } = new List<KnowledgeChunk>();
}

public class KnowledgeChunk
{
    [Key]
    [MaxLength(240)]
    public string Id { get; set; } = string.Empty;

    [Required]
    [MaxLength(120)]
    public string DocumentId { get; set; } = string.Empty;

    public int Ordinal { get; set; }

    [Required]
    public string Text { get; set; } = string.Empty;

    [Required]
    [MaxLength(64)]
    public string ContentHash { get; set; } = string.Empty;

    [Required]
    public byte[] Embedding { get; set; } = [];

    [Required]
    [MaxLength(80)]
    public string EmbeddingModel { get; set; } = string.Empty;

    public bool IsActive { get; set; } = true;
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

    public KnowledgeDocument? Document { get; set; }
}

/// <summary>
/// A snapshot of what an answer relied on, so old answers keep their sources after the corpus changes.
/// </summary>
public class ChatMessageCitation
{
    [Key]
    public string Id { get; set; } = Guid.NewGuid().ToString();

    [Required]
    public string MessageId { get; set; } = string.Empty;

    [Required]
    [MaxLength(240)]
    public string ChunkId { get; set; } = string.Empty;

    [Required]
    [MaxLength(120)]
    public string DocumentId { get; set; } = string.Empty;

    [MaxLength(20)]
    public string? DocumentVersion { get; set; }

    [Required]
    [MaxLength(300)]
    public string Title { get; set; } = string.Empty;

    [Required]
    [MaxLength(300)]
    public string SourceLabel { get; set; } = string.Empty;

    [MaxLength(2048)]
    public string? SourceUrl { get; set; }

    public ChatMessage? Message { get; set; }
}
