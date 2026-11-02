using InteriorAI.Data;
using InteriorAI.Domain.Entities;
using InteriorAI.Models.DTOs;
using Microsoft.EntityFrameworkCore;

namespace InteriorAI.Services;

public sealed record ChatBriefSnapshot(
    string? RoomType,
    string? StyleName,
    string FeatureId,
    string? ColorPreference,
    string? MustKeep,
    string? MustAvoid);

public sealed record DesignJobRequest(
    string UserId,
    IFormFile Image,
    int? StyleId,
    string RoomType,
    string FeatureId,
    string? ColorPreference,
    string? MustKeep,
    string? MustAvoid,
    string ConversationId,
    int BriefVersion,
    string ClientRequestId);

public interface IDesignJobCreator
{
    Task<DesignResult> CreateFromBriefAsync(DesignJobRequest request);
}

/// <summary>
/// Owns the Design Brief of a conversation. The LLM can only propose values through a patch that is
/// checked against allow-lists here, and only the confirm call can start an image job.
/// </summary>
public sealed class ChatBriefService
{
    private const int MaxFreeTextLength = 300;
    private const string RemoveFurniture = "remove_furniture";

    private static readonly IReadOnlyDictionary<string, string> RoomLabels =
        new Dictionary<string, string>(StringComparer.Ordinal)
        {
            ["living_room"] = "phòng khách",
            ["master_bedroom"] = "phòng ngủ",
            ["kitchen"] = "phòng bếp",
            ["bathroom"] = "phòng tắm",
            ["study_room"] = "phòng làm việc"
        };

    private static readonly HashSet<string> Features =
        new(["interior_design", "furnish_empty_room", RemoveFurniture], StringComparer.Ordinal);

    private readonly AppDbContext _context;
    private readonly IDesignJobCreator _jobs;

    public ChatBriefService(AppDbContext context, IDesignJobCreator jobs)
    {
        _context = context;
        _jobs = jobs;
    }

    public async Task<DesignBriefResponse> GetAsync(
        string userId, string conversationId, CancellationToken cancellationToken)
    {
        await EnsureOwnedAsync(userId, conversationId, cancellationToken);
        var brief = await _context.DesignBriefs.AsNoTracking()
            .FirstOrDefaultAsync(item => item.ConversationId == conversationId, cancellationToken);
        return await ToResponseAsync(brief ?? new DesignBrief { ConversationId = conversationId }, cancellationToken);
    }

    public async Task<ChatBriefSnapshot?> SnapshotAsync(string conversationId, CancellationToken cancellationToken)
    {
        var brief = await _context.DesignBriefs.AsNoTracking()
            .FirstOrDefaultAsync(item => item.ConversationId == conversationId, cancellationToken);
        if (brief == null) return null;
        var styleName = await StyleNameAsync(brief.StyleId, cancellationToken);
        return new ChatBriefSnapshot(brief.RoomType, styleName, brief.FeatureId,
            brief.ColorPreference, brief.MustKeep, brief.MustAvoid);
    }

    /// <summary>Stages a validated patch on the context; the caller saves it with the chat messages.</summary>
    public async Task<DesignBrief> ApplyPatchAsync(
        string conversationId, BriefPatch? patch, CancellationToken cancellationToken)
    {
        var brief = await LoadOrCreateAsync(conversationId, cancellationToken);
        if (patch == null) return brief;

        var changed = false;
        if (patch.RoomType != null && RoomLabels.ContainsKey(patch.RoomType) && brief.RoomType != patch.RoomType)
        {
            brief.RoomType = patch.RoomType;
            changed = true;
        }
        if (patch.StyleName != null)
        {
            var styleId = await _context.StyleAesthetics.AsNoTracking()
                .Where(style => style.StyleName == patch.StyleName)
                .Select(style => (int?)style.StyleID)
                .FirstOrDefaultAsync(cancellationToken);
            if (styleId != null && brief.StyleId != styleId)
            {
                brief.StyleId = styleId;
                changed = true;
            }
        }
        if (patch.FeatureId != null && Features.Contains(patch.FeatureId) && brief.FeatureId != patch.FeatureId)
        {
            brief.FeatureId = patch.FeatureId;
            changed = true;
        }
        changed |= SetText(patch.ColorPreference, brief.ColorPreference, value => brief.ColorPreference = value);
        changed |= SetText(patch.MustKeep, brief.MustKeep, value => brief.MustKeep = value);
        changed |= SetText(patch.MustAvoid, brief.MustAvoid, value => brief.MustAvoid = value);

        if (changed) MarkChanged(brief);
        return brief;
    }

    public async Task<DesignBriefResponse> UpdateAsync(
        string userId, string conversationId, UpdateDesignBriefRequest request, CancellationToken cancellationToken)
    {
        await EnsureOwnedAsync(userId, conversationId, cancellationToken);
        var brief = await LoadOrCreateAsync(conversationId, cancellationToken);
        if (brief.Version != request.ExpectedVersion)
        {
            throw new InvalidOperationException("Yêu cầu thiết kế đã thay đổi. Hãy tải lại rồi thử lại.");
        }

        if (request.RoomType != null && !RoomLabels.ContainsKey(request.RoomType))
        {
            throw new ArgumentException("Loại phòng không được hỗ trợ.");
        }
        if (request.FeatureId != null && !Features.Contains(request.FeatureId))
        {
            throw new ArgumentException("Tác vụ không được hỗ trợ.");
        }
        if (request.StyleId != null && await StyleNameAsync(request.StyleId, cancellationToken) == null)
        {
            throw new ArgumentException("Phong cách không tồn tại.");
        }

        var changed = false;
        if (request.RoomType != null && request.RoomType != brief.RoomType)
        {
            brief.RoomType = request.RoomType;
            changed = true;
        }
        if (request.StyleId != null && request.StyleId != brief.StyleId)
        {
            brief.StyleId = request.StyleId;
            changed = true;
        }
        if (request.FeatureId != null && request.FeatureId != brief.FeatureId)
        {
            brief.FeatureId = request.FeatureId;
            changed = true;
        }
        changed |= SetText(request.ColorPreference, brief.ColorPreference, value => brief.ColorPreference = value);
        changed |= SetText(request.MustKeep, brief.MustKeep, value => brief.MustKeep = value);
        changed |= SetText(request.MustAvoid, brief.MustAvoid, value => brief.MustAvoid = value);

        if (changed) MarkChanged(brief);
        await _context.SaveChangesAsync(cancellationToken);
        return await ToResponseAsync(brief, cancellationToken);
    }

    public async Task<ConfirmDesignBriefResponse> ConfirmAsync(
        string userId, string conversationId, ConfirmDesignBriefRequest request, CancellationToken cancellationToken)
    {
        if (!Guid.TryParse(request.ClientRequestId, out var requestGuid))
        {
            throw new ArgumentException("clientRequestId phải là UUID hợp lệ.");
        }
        var clientRequestId = requestGuid.ToString();
        var conversation = await EnsureOwnedAsync(userId, conversationId, cancellationToken);

        // A retry of a request that already created a job must not depend on the brief's current state.
        var existing = await _context.DesignResults.AsNoTracking()
            .FirstOrDefaultAsync(item => item.ConversationId == conversationId &&
                item.ClientRequestId == clientRequestId, cancellationToken);
        var brief = await LoadOrCreateAsync(conversationId, cancellationToken);
        if (existing == null)
        {
            if (brief.Version != request.BriefVersion)
            {
                throw new InvalidOperationException("Yêu cầu thiết kế đã thay đổi. Hãy xem lại trước khi xác nhận.");
            }
            var missing = MissingFields(brief);
            if (missing.Count > 0)
            {
                throw new ArgumentException($"Yêu cầu thiết kế còn thiếu: {string.Join(", ", missing)}.");
            }
            if (request.Image == null)
            {
                throw new InvalidDataException("Hãy chọn ảnh phòng trước khi xác nhận.");
            }

            existing = await _jobs.CreateFromBriefAsync(new DesignJobRequest(
                userId, request.Image, brief.StyleId, brief.RoomType!, brief.FeatureId,
                brief.ColorPreference, brief.MustKeep, brief.MustAvoid,
                conversationId, brief.Version, clientRequestId));
        }

        var message = await _context.ChatMessages.Include(item => item.Citations)
            .FirstOrDefaultAsync(item => item.ConversationId == conversationId &&
                item.DesignId == existing.Id, cancellationToken);
        if (message == null)
        {
            var now = DateTime.UtcNow;
            var styleName = await StyleNameAsync(brief.StyleId, cancellationToken);
            message = new ChatMessage
            {
                ConversationId = conversationId,
                Role = "assistant",
                Kind = ChatMessageKinds.DesignJob,
                DesignId = existing.Id,
                Content = JobMessage(brief, styleName),
                CreatedAt = now
            };
            _context.ChatMessages.Add(message);
            brief.Status = DesignBriefStatuses.Confirmed;
            brief.UpdatedAt = now;
            conversation.UpdatedAt = now;
            try
            {
                await _context.SaveChangesAsync(cancellationToken);
            }
            catch (DbUpdateException)
            {
                // A concurrent retry stored the same job message first.
                _context.ChangeTracker.Clear();
                message = await _context.ChatMessages.AsNoTracking().Include(item => item.Citations)
                    .FirstOrDefaultAsync(item => item.ConversationId == conversationId &&
                        item.DesignId == existing.Id, cancellationToken);
                if (message == null) throw;
                brief = await LoadOrCreateAsync(conversationId, cancellationToken);
            }
        }

        return new ConfirmDesignBriefResponse(
            existing.Id, existing.Status, ChatConversationService.ToResponse(message),
            await ToResponseAsync(brief, cancellationToken));
    }

    public async Task<DesignBriefResponse> ToResponseAsync(DesignBrief brief, CancellationToken cancellationToken) =>
        new(brief.ConversationId, brief.Version, brief.RoomType, brief.StyleId,
            await StyleNameAsync(brief.StyleId, cancellationToken), brief.FeatureId,
            brief.ColorPreference, brief.MustKeep, brief.MustAvoid, brief.Status, MissingFields(brief));

    private static string JobMessage(DesignBrief brief, string? styleName)
    {
        var room = brief.RoomType != null ? RoomLabels.GetValueOrDefault(brief.RoomType, "phòng") : "phòng";
        return brief.FeatureId == RemoveFurniture || styleName == null
            ? $"Đã nhận ảnh {room} và bắt đầu xử lý."
            : $"Đã nhận ảnh {room} và bắt đầu tạo phương án {styleName}.";
    }

    private static IReadOnlyList<string> MissingFields(DesignBrief brief)
    {
        var missing = new List<string>();
        if (brief.RoomType == null) missing.Add("loại phòng");
        if (brief.StyleId == null && brief.FeatureId != RemoveFurniture) missing.Add("phong cách");
        return missing;
    }

    private static void MarkChanged(DesignBrief brief)
    {
        brief.Version++;
        brief.Status = DesignBriefStatuses.Draft;
        brief.UpdatedAt = DateTime.UtcNow;
    }

    private static bool SetText(string? incoming, string? current, Action<string?> assign)
    {
        if (incoming == null) return false;
        var clean = new string(incoming.Where(character => !char.IsControl(character)).ToArray()).Trim();
        if (clean.Length > MaxFreeTextLength) clean = clean[..MaxFreeTextLength];
        var value = clean.Length == 0 ? null : clean;
        if (value == current) return false;
        assign(value);
        return true;
    }

    private async Task<DesignBrief> LoadOrCreateAsync(string conversationId, CancellationToken cancellationToken)
    {
        var brief = _context.DesignBriefs.Local.FirstOrDefault(item => item.ConversationId == conversationId)
            ?? await _context.DesignBriefs
                .FirstOrDefaultAsync(item => item.ConversationId == conversationId, cancellationToken);
        if (brief != null) return brief;
        brief = new DesignBrief { ConversationId = conversationId };
        _context.DesignBriefs.Add(brief);
        return brief;
    }

    private async Task<string?> StyleNameAsync(int? styleId, CancellationToken cancellationToken)
    {
        if (styleId == null) return null;
        return await _context.StyleAesthetics.AsNoTracking()
            .Where(style => style.StyleID == styleId)
            .Select(style => style.StyleName)
            .FirstOrDefaultAsync(cancellationToken);
    }

    private async Task<ChatConversation> EnsureOwnedAsync(
        string userId, string conversationId, CancellationToken cancellationToken) =>
        await _context.ChatConversations
            .FirstOrDefaultAsync(item => item.Id == conversationId && item.UserId == userId, cancellationToken)
        ?? throw new KeyNotFoundException("Không tìm thấy hội thoại.");
}
