using InteriorAI.Data;
using Microsoft.EntityFrameworkCore;

namespace InteriorAI.Services;

public static class ChatAnswerModes
{
    public const string NoRag = "NoRag";
    public const string Lexical = "Lexical";
    public const string Hybrid = "Hybrid";
}

public sealed record ChatReplyResult(
    string Content,
    IReadOnlyList<KnowledgeMatch> UsedMatches,
    BriefPatch? BriefPatch,
    string Source);

public interface IChatReplyService
{
    Task<ChatReplyResult> ReplyAsync(
        string message, IReadOnlyList<ChatTurn> history, ChatBriefSnapshot? brief,
        CancellationToken cancellationToken);
}

/// <summary>
/// One chat turn: safety rules, then retrieval, then the LLM over what was retrieved, then a local
/// answer when the LLM is unavailable. Chat:AnswerMode selects the retrieval used for evaluation.
/// </summary>
public sealed class ChatReplyService : IChatReplyService
{
    private const string NoEvidenceReply =
        "Mình chưa có đủ căn cứ trong tài liệu để trả lời chắc chắn câu này. Bạn cho mình biết loại phòng, màu sắc hoặc phong cách muốn tìm hiểu nhé.";

    private static readonly string[] StyleListPhrases =
    [
        "nhung phong cach", "danh sach phong cach", "co phong cach nao", "co bao nhieu phong cach",
        "cac phong cach", "may phong cach"
    ];

    private readonly AppDbContext _context;
    private readonly LexicalRetriever _lexical;
    private readonly HybridRetriever _hybrid;
    private readonly IChatAnswerGenerator _answerGenerator;
    private readonly IConfiguration _configuration;

    public ChatReplyService(
        AppDbContext context, LexicalRetriever lexical, HybridRetriever hybrid,
        IChatAnswerGenerator answerGenerator, IConfiguration configuration)
    {
        _context = context;
        _lexical = lexical;
        _hybrid = hybrid;
        _answerGenerator = answerGenerator;
        _configuration = configuration;
    }

    private string Mode => _configuration["Chat:AnswerMode"] ?? ChatAnswerModes.Hybrid;

    public async Task<ChatReplyResult> ReplyAsync(
        string message, IReadOnlyList<ChatTurn> history, ChatBriefSnapshot? brief,
        CancellationToken cancellationToken)
    {
        var guard = ChatSafetyGuard.Evaluate(message);
        if (guard != null) return new ChatReplyResult(guard.Reply, [], null, $"guard:{guard.Category}");

        var normalized = KnowledgeBaseService.Normalize(message);
        var smallTalk = SmallTalkReply(normalized);
        if (smallTalk != null) return new ChatReplyResult(smallTalk, [], null, "smalltalk");

        var ruleBrief = InteriorAdvicePlanner.ExtractBrief(message, history);

        if (StyleListPhrases.Any(phrase => KnowledgeBaseService.ContainsPhrase(normalized, phrase)))
        {
            var styles = await _context.StyleAesthetics.AsNoTracking()
                .OrderBy(style => style.StyleName)
                .Select(style => style.StyleName)
                .ToListAsync(cancellationToken);
            return new ChatReplyResult(
                styles.Count == 0
                    ? "Dữ liệu phong cách chưa sẵn sàng. Vui lòng thử lại sau."
                    : $"Ứng dụng hiện có các phong cách: {string.Join(", ", styles)}. Bạn quan tâm loại phòng nào?",
                [], ruleBrief, "catalog");
        }

        if (Mode == ChatAnswerModes.NoRag)
        {
            var unsupported = await _answerGenerator.GenerateAsync(
                new ChatAnswerRequest(message, history, brief, [], AllowNoReferences: true), cancellationToken);
            return unsupported != null
                ? new ChatReplyResult(unsupported.Reply, [], unsupported.BriefPatch ?? ruleBrief, "llm")
                : new ChatReplyResult(NoEvidenceReply, [], ruleBrief, "abstain");
        }

        var activeRoom = InteriorAdvicePlanner.ActiveRoom(message, history, brief?.RoomType);
        IKnowledgeRetriever retriever = Mode == ChatAnswerModes.Lexical ? _lexical : _hybrid;
        var matches = await retriever.RetrieveAsync(
            message, history, new BriefContext(activeRoom, brief?.StyleName), cancellationToken);

        if (matches.Count > 0)
        {
            var generated = await _answerGenerator.GenerateAsync(
                new ChatAnswerRequest(message, history, brief, matches,
                    ActiveRoom: InteriorAdvicePlanner.RoomLabel(activeRoom)),
                cancellationToken);
            if (generated != null)
            {
                var used = matches.Where(match => generated.UsedChunkIds.Contains(match.ChunkId)).ToArray();
                return new ChatReplyResult(generated.Reply, used, MergeBrief(generated.BriefPatch, ruleBrief), "llm");
            }
        }

        var recommendation = InteriorAdvicePlanner.Recommend(message, history);
        if (recommendation != null) return new ChatReplyResult(recommendation, [], ruleBrief, "rule");

        var planned = InteriorAdvicePlanner.StyleAndRoom(message, history, matches)
            ?? (matches.Count > 0 ? InteriorAdvicePlanner.FromMatch(matches[0], activeRoom) : null);
        if (planned != null)
        {
            return new ChatReplyResult(
                planned.Text, planned.Source == null ? [] : [planned.Source], ruleBrief, "rule");
        }

        return new ChatReplyResult(NoEvidenceReply, [], ruleBrief, "abstain");
    }

    // A room or style the user named must not be lost when the LLM leaves that field null,
    // and a room the user named outranks one the LLM inferred.
    private static BriefPatch? MergeBrief(BriefPatch? generated, BriefPatch? rule)
    {
        if (generated == null || rule == null) return generated ?? rule;
        return generated with
        {
            RoomType = rule.RoomType ?? generated.RoomType,
            StyleName = generated.StyleName ?? rule.StyleName
        };
    }

    private static string? SmallTalkReply(string normalized)
    {
        var words = normalized.Split(' ', StringSplitOptions.RemoveEmptyEntries);
        if (words.Length > 6) return null;
        var text = string.Join(' ', words).TrimEnd('.', '!', '?');
        if (text.StartsWith("xin chao", StringComparison.Ordinal) || text == "chao" ||
            text.StartsWith("chao ban", StringComparison.Ordinal) || text is "hello" or "hi" or "alo")
            return "Chào bạn! Bạn đang muốn thiết kế phòng nào, và thích màu sắc hay cảm giác ra sao?";
        if (text.StartsWith("cam on", StringComparison.Ordinal) || text is "thanks" or "thank you")
            return "Không có gì nhé! Khi cần, bạn cứ hỏi thêm hoặc gửi ảnh phòng để mình tạo phương án thử.";
        return null;
    }
}
