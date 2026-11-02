using System.ComponentModel.DataAnnotations;

namespace InteriorAI.Domain.Entities;

public class ChatConversation
{
    [Key]
    public string Id { get; set; } = Guid.NewGuid().ToString();

    [Required]
    public string UserId { get; set; } = string.Empty;

    [Required]
    [MaxLength(120)]
    public string Title { get; set; } = "Cuộc trò chuyện mới";

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

    public User? User { get; set; }
    public ICollection<ChatMessage> Messages { get; set; } = new List<ChatMessage>();
}
