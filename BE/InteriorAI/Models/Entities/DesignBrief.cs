using System.ComponentModel.DataAnnotations;

namespace InteriorAI.Domain.Entities;

public static class DesignBriefStatuses
{
    public const string Draft = "draft";
    public const string Confirmed = "confirmed";
}

public class DesignBrief
{
    [Key]
    public string ConversationId { get; set; } = string.Empty;

    public int Version { get; set; }

    [MaxLength(64)]
    public string? RoomType { get; set; }

    public int? StyleId { get; set; }

    [Required]
    [MaxLength(64)]
    public string FeatureId { get; set; } = "interior_design";

    [MaxLength(300)]
    public string? ColorPreference { get; set; }

    [MaxLength(300)]
    public string? MustKeep { get; set; }

    [MaxLength(300)]
    public string? MustAvoid { get; set; }

    [Required]
    [MaxLength(16)]
    public string Status { get; set; } = DesignBriefStatuses.Draft;

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

    public ChatConversation? Conversation { get; set; }
}
