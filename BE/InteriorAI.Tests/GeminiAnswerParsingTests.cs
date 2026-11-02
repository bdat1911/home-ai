using System.Text.Json;
using InteriorAI.Services;

namespace InteriorAI.Tests;

public class GeminiAnswerParsingTests
{
    private static readonly ChatAnswerRequest Request = new(
        "Câu hỏi", [], null,
        [new KnowledgeMatch("doc", "doc#a-1", "Tiêu đề", "Đoạn văn", null, true, 1)]);

    [Fact]
    public void ValidAnswerKeepsCitationsAndPatch()
    {
        var answer = GeminiChatAnswerGenerator.ParseAnswer(
            """{"reply":"Nên dùng gỗ sáng.","usedChunkIds":["doc#a-1"],"briefPatch":{"roomType":"kitchen","styleName":null}}""",
            Request);

        Assert.NotNull(answer);
        Assert.Equal(["doc#a-1"], answer!.UsedChunkIds);
        Assert.Equal("kitchen", answer.BriefPatch?.RoomType);
        Assert.Null(answer.BriefPatch?.StyleName);
    }

    [Fact]
    public void CitationOutsideRetrievedSetIsRejected()
    {
        Assert.Null(GeminiChatAnswerGenerator.ParseAnswer(
            """{"reply":"Nên dùng gỗ sáng.","usedChunkIds":["doc#khong-co-1"]}""", Request));
    }

    [Fact]
    public void ClaimWithoutEvidenceIsRejected()
    {
        Assert.Null(GeminiChatAnswerGenerator.ParseAnswer(
            """{"reply":"Gỗ sáng luôn là lựa chọn tốt nhất cho mọi phòng.","usedChunkIds":[]}""", Request));
    }

    [Fact]
    public void ClarifyingQuestionNeedsNoCitation()
    {
        var answer = GeminiChatAnswerGenerator.ParseAnswer(
            """{"reply":"Bạn muốn áp dụng cho phòng nào?","usedChunkIds":[]}""", Request);

        Assert.NotNull(answer);
        Assert.Empty(answer!.UsedChunkIds);
    }

    [Fact]
    public void InternalLabelsInReplyAreRejected()
    {
        Assert.Null(GeminiChatAnswerGenerator.ParseAnswer(
            """{"reply":"Theo bản nháp doc, nên dùng gỗ sáng.","usedChunkIds":["doc#a-1"]}""", Request));
    }

    [Fact]
    public void MalformedJsonThrowsForTheCallerToFallBack()
    {
        Assert.ThrowsAny<JsonException>(() => GeminiChatAnswerGenerator.ParseAnswer("{not json", Request));
    }
}
