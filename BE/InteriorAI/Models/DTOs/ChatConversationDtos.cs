using System.ComponentModel.DataAnnotations;

namespace InteriorAI.Models.DTOs;

public sealed class CreateChatConversationRequest
{
    [Required]
    public string ClientConversationId { get; set; } = string.Empty;
}

public sealed class SendChatMessageRequest
{
    [Required]
    public string ClientMessageId { get; set; } = string.Empty;

    [Required]
    [StringLength(2000, MinimumLength = 1)]
    public string Content { get; set; } = string.Empty;
}

public sealed class UpdateDesignBriefRequest
{
    public int ExpectedVersion { get; set; }
    public string? RoomType { get; set; }
    public int? StyleId { get; set; }
    public string? FeatureId { get; set; }

    [StringLength(300)]
    public string? ColorPreference { get; set; }

    [StringLength(300)]
    public string? MustKeep { get; set; }

    [StringLength(300)]
    public string? MustAvoid { get; set; }
}

public sealed class ConfirmDesignBriefRequest
{
    public IFormFile? Image { get; set; }
    public int BriefVersion { get; set; }
    public string ClientRequestId { get; set; } = string.Empty;
}

public sealed record ChatConversationResponse(
    string Id,
    string Title,
    DateTime CreatedAt,
    DateTime UpdatedAt);

public sealed record ChatCitationResponse(
    string ChunkId,
    string DocumentId,
    string Title,
    string SourceLabel,
    string? SourceUrl);

public sealed record ChatMessageResponse(
    string Id,
    string Role,
    string Content,
    DateTime CreatedAt,
    string Kind,
    string? DesignId,
    IReadOnlyList<ChatCitationResponse> Citations);

public sealed record DesignBriefResponse(
    string ConversationId,
    int Version,
    string? RoomType,
    int? StyleId,
    string? StyleName,
    string FeatureId,
    string? ColorPreference,
    string? MustKeep,
    string? MustAvoid,
    string Status,
    IReadOnlyList<string> MissingFields);

public sealed record SendChatMessageResponse(
    ChatMessageResponse UserMessage,
    ChatMessageResponse AssistantMessage,
    DesignBriefResponse Brief);

public sealed record ConfirmDesignBriefResponse(
    string DesignId,
    string Status,
    ChatMessageResponse Message,
    DesignBriefResponse Brief);
