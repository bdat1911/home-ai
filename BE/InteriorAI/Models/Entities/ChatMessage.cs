using System.ComponentModel.DataAnnotations;

namespace InteriorAI.Domain.Entities;

public class ChatMessage
{
    [Key]
    public string Id { get; set; } = Guid.NewGuid().ToString();

    [Required]
    public string ConversationId { get; set; } = string.Empty;

    [MaxLength(36)]
    public string? ClientMessageId { get; set; }

    [MaxLength(36)]
    public string? ReplyToMessageId { get; set; }

    [Required]
    [MaxLength(16)]
    public string Role { get; set; } = string.Empty;

    [Required]
    [MaxLength(4000)]
    public string Content { get; set; } = string.Empty;

    [Required]
    [MaxLength(16)]
    public string Kind { get; set; } = ChatMessageKinds.Text;

    [MaxLength(36)]
    public string? DesignId { get; set; }

    [MaxLength(16)]
    public string? AnswerMode { get; set; }

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public ChatConversation? Conversation { get; set; }
    public ICollection<ChatMessageCitation> Citations { get; set; } = new List<ChatMessageCitation>();
}

public static class ChatMessageKinds
{
    public const string Text = "text";
    public const string DesignJob = "design_job";
}
