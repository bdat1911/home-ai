using InteriorAI.Data;
using InteriorAI.Domain.Entities;
using InteriorAI.Models.DTOs;
using Microsoft.EntityFrameworkCore;

namespace InteriorAI.Services;

public sealed class ChatConversationService
{
    private const int HistoryTurns = 6;

    private readonly AppDbContext _context;
    private readonly IChatReplyService _replyService;
    private readonly ChatBriefService _briefs;

    public ChatConversationService(
        AppDbContext context, IChatReplyService replyService, ChatBriefService briefs)
    {
        _context = context;
        _replyService = replyService;
        _briefs = briefs;
    }

    public async Task<ChatConversationResponse> CreateAsync(
        string userId, string clientConversationId, CancellationToken cancellationToken)
    {
        if (!Guid.TryParse(clientConversationId, out var id))
        {
            throw new ArgumentException("clientConversationId phải là UUID hợp lệ.");
        }

        var conversationId = id.ToString();
        var existing = await _context.ChatConversations.AsNoTracking()
            .FirstOrDefaultAsync(item => item.Id == conversationId, cancellationToken);
        if (existing != null)
        {
            if (existing.UserId != userId) throw new InvalidOperationException("Mã hội thoại đã được sử dụng.");
            return ToResponse(existing);
        }

        if (!await _context.Users.AnyAsync(user => user.Id == userId, cancellationToken))
        {
            throw new KeyNotFoundException("Không tìm thấy người dùng.");
        }

        var conversation = new ChatConversation { Id = conversationId, UserId = userId };
        _context.ChatConversations.Add(conversation);
        try
        {
            await _context.SaveChangesAsync(cancellationToken);
            return ToResponse(conversation);
        }
        catch (DbUpdateException)
        {
            var saved = await _context.ChatConversations.AsNoTracking()
                .FirstOrDefaultAsync(item => item.Id == conversationId && item.UserId == userId, cancellationToken);
            if (saved == null) throw;
            return ToResponse(saved);
        }
    }

    public async Task<List<ChatConversationResponse>> ListAsync(string userId, CancellationToken cancellationToken)
    {
        var conversations = await _context.ChatConversations.AsNoTracking()
            .Where(item => item.UserId == userId)
            .OrderByDescending(item => item.UpdatedAt)
            .Take(50)
            .ToListAsync(cancellationToken);
        return conversations.Select(ToResponse).ToList();
    }

    public async Task<List<ChatMessageResponse>> GetMessagesAsync(
        string userId, string conversationId, CancellationToken cancellationToken)
    {
        await EnsureOwnedAsync(userId, conversationId, cancellationToken);
        var messages = await _context.ChatMessages.AsNoTracking()
            .Include(item => item.Citations)
            .Where(item => item.ConversationId == conversationId)
            .OrderByDescending(item => item.CreatedAt)
            .ThenByDescending(item => item.Id)
            .Take(100)
            .ToListAsync(cancellationToken);
        messages.Reverse();
        return messages.Select(ToResponse).ToList();
    }

    public async Task<SendChatMessageResponse> SendAsync(
        string userId, string conversationId, string clientMessageId, string content,
        CancellationToken cancellationToken)
    {
        if (!Guid.TryParse(clientMessageId, out var messageGuid))
        {
            throw new ArgumentException("clientMessageId phải là UUID hợp lệ.");
        }

        content = content.Trim();
        if (content.Length is < 1 or > 2000)
        {
            throw new ArgumentException("Nội dung phải dài từ 1 đến 2000 ký tự.");
        }

        var conversation = await EnsureOwnedAsync(userId, conversationId, cancellationToken);
        var normalizedClientId = messageGuid.ToString();
        var existing = await _context.ChatMessages.AsNoTracking()
            .Include(item => item.Citations)
            .FirstOrDefaultAsync(item => item.ConversationId == conversationId &&
                item.ClientMessageId == normalizedClientId, cancellationToken);
        if (existing != null) return await GetExistingReplyAsync(userId, existing, content, cancellationToken);

        var history = await _context.ChatMessages.AsNoTracking()
            .Where(item => item.ConversationId == conversationId && item.Kind == ChatMessageKinds.Text)
            .OrderByDescending(item => item.CreatedAt)
            .ThenByDescending(item => item.Id)
            .Take(HistoryTurns)
            .Select(item => new ChatTurn(item.Role, item.Content))
            .ToListAsync(cancellationToken);
        history.Reverse();

        var snapshot = await _briefs.SnapshotAsync(conversationId, cancellationToken);
        var reply = await _replyService.ReplyAsync(content, history, snapshot, cancellationToken);
        var now = DateTime.UtcNow;
        var userMessage = new ChatMessage
        {
            ConversationId = conversationId,
            ClientMessageId = normalizedClientId,
            Role = "user",
            Content = content,
            CreatedAt = now
        };
        var assistantMessage = new ChatMessage
        {
            ConversationId = conversationId,
            ReplyToMessageId = userMessage.Id,
            Role = "assistant",
            Content = reply.Content,
            AnswerMode = reply.Source.Length <= 16 ? reply.Source : reply.Source[..16],
            CreatedAt = now.AddTicks(1)
        };
        foreach (var match in reply.UsedMatches)
        {
            assistantMessage.Citations.Add(ToCitation(match));
        }

        _context.ChatMessages.AddRange(userMessage, assistantMessage);
        var brief = await _briefs.ApplyPatchAsync(conversationId, reply.BriefPatch, cancellationToken);
        conversation.UpdatedAt = now;
        if (conversation.Title == "Cuộc trò chuyện mới")
        {
            conversation.Title = content.Length <= 80 ? content : content[..80];
        }

        try
        {
            await _context.SaveChangesAsync(cancellationToken);
            return new SendChatMessageResponse(
                ToResponse(userMessage), ToResponse(assistantMessage),
                await _briefs.ToResponseAsync(brief, cancellationToken));
        }
        catch (DbUpdateException)
        {
            _context.ChangeTracker.Clear();
            var saved = await _context.ChatMessages.AsNoTracking()
                .Include(item => item.Citations)
                .FirstOrDefaultAsync(item => item.ConversationId == conversationId &&
                    item.ClientMessageId == normalizedClientId, cancellationToken);
            if (saved == null) throw;
            return await GetExistingReplyAsync(userId, saved, content, cancellationToken);
        }
    }

    internal static ChatMessageCitation ToCitation(KnowledgeMatch match)
    {
        var external = match.Sources?.FirstOrDefault(source => source.IsExternal);
        var label = external != null
            ? (string.IsNullOrWhiteSpace(external.Publisher) ? external.Title : $"{external.Title} ({external.Publisher})")
            : "Tài liệu nội bộ Home AI";
        return new ChatMessageCitation
        {
            ChunkId = match.ChunkId,
            DocumentId = match.DocumentId,
            DocumentVersion = match.Version,
            Title = match.Title,
            SourceLabel = label.Length <= 300 ? label : label[..300],
            SourceUrl = external?.Url
        };
    }

    private async Task<ChatConversation> EnsureOwnedAsync(
        string userId, string conversationId, CancellationToken cancellationToken)
    {
        return await _context.ChatConversations
            .FirstOrDefaultAsync(item => item.Id == conversationId && item.UserId == userId, cancellationToken)
            ?? throw new KeyNotFoundException("Không tìm thấy hội thoại.");
    }

    private async Task<SendChatMessageResponse> GetExistingReplyAsync(
        string userId, ChatMessage userMessage, string content, CancellationToken cancellationToken)
    {
        if (userMessage.Content != content)
        {
            throw new InvalidOperationException("clientMessageId đã được dùng cho nội dung khác.");
        }

        var assistantMessage = await _context.ChatMessages.AsNoTracking()
            .Include(item => item.Citations)
            .FirstOrDefaultAsync(item => item.ConversationId == userMessage.ConversationId &&
                item.ReplyToMessageId == userMessage.Id, cancellationToken)
            ?? throw new InvalidOperationException("Tin nhắn đang được xử lý. Vui lòng tải lại hội thoại.");
        return new SendChatMessageResponse(
            ToResponse(userMessage), ToResponse(assistantMessage),
            await _briefs.GetAsync(userId, userMessage.ConversationId, cancellationToken));
    }

    private static ChatConversationResponse ToResponse(ChatConversation item) =>
        new(item.Id, item.Title, item.CreatedAt, item.UpdatedAt);

    internal static ChatMessageResponse ToResponse(ChatMessage item) =>
        new(item.Id, item.Role, item.Content, item.CreatedAt, item.Kind, item.DesignId,
            item.Citations
                .Select(citation => new ChatCitationResponse(
                    citation.ChunkId, citation.DocumentId, citation.Title, citation.SourceLabel, citation.SourceUrl))
                .ToList());
}
