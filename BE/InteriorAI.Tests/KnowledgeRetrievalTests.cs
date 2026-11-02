using InteriorAI.Data;
using InteriorAI.Domain.Entities;
using InteriorAI.Services;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging.Abstractions;

namespace InteriorAI.Tests;

public sealed class KnowledgeRetrievalTests : IDisposable
{
    private readonly string _root = Path.Combine(Path.GetTempPath(), $"home-ai-tests-{Guid.NewGuid():N}");
    private readonly SqliteConnection _connection = new("DataSource=:memory:");
    private readonly ServiceProvider _provider;
    private readonly FakeEmbeddings _embeddings = new();

    public KnowledgeRetrievalTests()
    {
        Directory.CreateDirectory(Path.Combine(_root, "knowledge-base", "docs"));
        File.WriteAllText(Path.Combine(_root, "knowledge-base", "source-register.csv"),
            "source_id,title,author_or_publisher,source_url,license_or_permission,accessed_at,scope_of_use,reviewer,status\n" +
            "ext,\"Guide, with comma\",Publisher,https://example.org/guide,Allowed,2026-10-01,Use,Reviewer,approved\n" +
            "seed,Seed,Home AI,BE/Seed.cs,Internal,2026-10-01,Use,pending,proposed\n");
        WriteDocument("light.md", "doc-light", "Ánh sáng phòng khách", "draft", "[ext]", "[living_room]",
            "## Gợi ý\nĐặt đèn sàn cạnh sofa để tạo ánh sáng phụ cho phòng khách vào buổi tối.");
        WriteDocument("bath.md", "doc-bath", "Phòng tắm ẩm", "draft", "[seed]", "[bathroom]",
            "## Gợi ý\nPhòng tắm cần thông gió tốt và bề mặt dễ lau để hạn chế ẩm mốc lâu ngày.");

        _connection.Open();
        var services = new ServiceCollection();
        services.AddDbContext<AppDbContext>(options => options.UseSqlite(_connection));
        services.AddSingleton<IEmbeddingService>(_embeddings);
        services.AddSingleton(new KnowledgeBaseService(_root, includeDrafts: true));
        _provider = services.BuildServiceProvider();
        using var scope = _provider.CreateScope();
        scope.ServiceProvider.GetRequiredService<AppDbContext>().Database.EnsureCreated();
    }

    public void Dispose()
    {
        _provider.Dispose();
        _connection.Dispose();
        Directory.Delete(_root, recursive: true);
    }

    [Fact]
    public void ChunkIdStaysStableWhenWordingChanges()
    {
        var knowledge = new KnowledgeBaseService(_root, includeDrafts: true);
        var before = knowledge.GetDocuments().Single(item => item.Id == "doc-light").Chunks.Single();

        WriteDocument("light.md", "doc-light", "Ánh sáng phòng khách", "draft", "[ext]", "[living_room]",
            "## Gợi ý\nĐặt đèn bàn và đèn sàn cạnh sofa để tạo ánh sáng phụ dễ chịu vào buổi tối.");
        var after = knowledge.GetDocuments().Single(item => item.Id == "doc-light").Chunks.Single();

        Assert.Equal(before.Id, after.Id);
        Assert.NotEqual(before.ContentHash, after.ContentHash);
    }

    [Fact]
    public void LimitationsAndClarifyingQuestionAreKeptOutOfChunks()
    {
        WriteDocument("rules.md", "doc-rules", "Nguyên tắc", "draft", "[ext]", "[]",
            "## Gợi ý\nNên thử mẫu vật liệu dưới ánh sáng thật trước khi chọn.\n\n" +
            "## Cần hỏi thêm\nPhòng của bạn hướng nào và có nhiều nắng không?\n\n" +
            "## Giới hạn\nKhông thay thế tư vấn của người có chuyên môn tại công trình.\n\n" +
            "## Nguồn\n- ext: Guide cho nội dung về vật liệu và ánh sáng.");
        var knowledge = new KnowledgeBaseService(_root, includeDrafts: true);

        var document = knowledge.GetDocuments().Single(item => item.Id == "doc-rules");

        var chunk = Assert.Single(document.Chunks);
        Assert.DoesNotContain("Không thay thế", chunk.Text);
        Assert.Contains("Không thay thế", document.Limitations);
        Assert.Contains("hướng nào", document.ClarifyingQuestion);
    }

    [Fact]
    public void QuotedSourceRegisterIsParsedAndExternalUrlKept()
    {
        var knowledge = new KnowledgeBaseService(_root, includeDrafts: true);

        var source = knowledge.ResolveSources(knowledge.GetDocuments().Single(item => item.Id == "doc-light")).Single();

        Assert.Equal("Guide, with comma", source.Title);
        Assert.Equal("https://example.org/guide", source.Url);
    }

    [Fact]
    public void InternalSourceHasNoLinkAndIsLabelledInternal()
    {
        var knowledge = new KnowledgeBaseService(_root, includeDrafts: true);
        var document = knowledge.GetDocuments().Single(item => item.Id == "doc-bath");

        var citation = ChatConversationService.ToCitation(knowledge.ToMatch(document, document.Chunks[0], 1));

        Assert.Equal("Tài liệu nội bộ Home AI", citation.SourceLabel);
        Assert.Null(citation.SourceUrl);
    }

    [Fact]
    public void DraftsAreNotServedWithoutTheFlag()
    {
        var production = new KnowledgeBaseService(_root, includeDrafts: false);

        Assert.Empty(production.Search("đèn sàn phòng khách"));
    }

    [Fact]
    public async Task IngestEmbedsOnceAndDeactivatesRemovedChunks()
    {
        var first = await IngestAsync();
        Assert.Equal(2, first.Embedded);

        var second = await IngestAsync();
        Assert.Equal(0, second.Embedded);
        Assert.Equal(2, second.Unchanged);
        Assert.Equal(2, _embeddings.DocumentCalls);

        File.Delete(Path.Combine(_root, "knowledge-base", "docs", "bath.md"));
        var third = await IngestAsync();
        Assert.Equal(1, third.Deactivated);

        using var scope = _provider.CreateScope();
        var context = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        Assert.False((await context.KnowledgeDocuments.SingleAsync(item => item.Id == "doc-bath")).IsActive);
    }

    [Fact]
    public async Task IngestReembedsEditedChunk()
    {
        await IngestAsync();
        WriteDocument("light.md", "doc-light", "Ánh sáng phòng khách", "draft", "[ext]", "[living_room]",
            "## Gợi ý\nĐặt đèn bàn cạnh sofa để tạo ánh sáng phụ cho phòng khách vào buổi tối.");

        var report = await IngestAsync();

        Assert.Equal(1, report.Embedded);
        Assert.Equal(1, report.Unchanged);
    }

    [Fact]
    public async Task HybridAnswersFromSemanticMatchEvenWithoutSharedKeywords()
    {
        await IngestAsync();

        var matches = await RetrieveAsync("bị mốc");

        Assert.Equal("doc-bath", matches[0].DocumentId);
    }

    [Fact]
    public async Task HybridReturnsNothingBelowTheSimilarityThreshold()
    {
        await IngestAsync();

        Assert.Empty(await RetrieveAsync("công thức nấu phở"));
    }

    [Fact]
    public async Task BriefStyleDoesNotMakeAnOffTopicQuestionPassTheThreshold()
    {
        await IngestAsync();
        var retriever = new HybridRetriever(
            _provider.GetRequiredService<KnowledgeBaseService>(),
            new KnowledgeIndex(_provider.GetRequiredService<IServiceScopeFactory>()),
            _embeddings,
            new ConfigurationBuilder().Build(),
            NullLogger<HybridRetriever>.Instance);

        var matches = await retriever.RetrieveAsync(
            "Viết bài thơ về mùa thu", [], new BriefContext("living_room", "Đèn đẹp"), default);

        Assert.Empty(matches);
        Assert.DoesNotContain(_embeddings.Queries, query => query.Contains("Đèn đẹp"));
    }

    [Fact]
    public async Task HybridFallsBackToKeywordsWhenEmbeddingFails()
    {
        await IngestAsync();
        _embeddings.FailQueries = true;

        var matches = await RetrieveAsync("đèn sàn phòng khách");

        Assert.Equal("doc-light", matches[0].DocumentId);
    }

    [Fact]
    public async Task WeakKeywordOverlapIsRejectedWhenEmbeddingFails()
    {
        await IngestAsync();
        _embeddings.FailQueries = true;

        Assert.Empty(await RetrieveAsync("thông gió dễ lau nhà ở"));
    }

    [Fact]
    public async Task FollowUpStaysInTheRoomUnderDiscussion()
    {
        await IngestRoomsAsync();

        var matches = await Hybrid().RetrieveAsync("nên dùng đèn gì", [], new BriefContext("kitchen", null), default);

        Assert.Contains(matches, match => match.DocumentId == "doc-kitchen");
        Assert.DoesNotContain(matches, match => match.DocumentId == "doc-bedroom");
        Assert.Contains("nên dùng đèn gì", _embeddings.Queries);
    }

    [Fact]
    public async Task RoomNamedInTheQuestionOverridesTheBrief()
    {
        await IngestRoomsAsync();

        var matches = await Hybrid().RetrieveAsync(
            "đèn phòng ngủ", [], new BriefContext("kitchen", null), default);

        Assert.Contains(matches, match => match.DocumentId == "doc-bedroom");
        Assert.DoesNotContain(matches, match => match.DocumentId == "doc-kitchen");
    }

    [Fact]
    public async Task BriefRoomDoesNotMakeAnOffTopicQuestionPassWithoutEmbeddings()
    {
        await IngestRoomsAsync();
        _embeddings.FailQueries = true;

        var matches = await Hybrid().RetrieveAsync(
            "viết bài thơ mùa thu", [], new BriefContext("kitchen", null), default);

        Assert.Empty(matches);
    }

    [Fact]
    public async Task ReplyKeepsTheRoomFromHistoryWhenTheLlmIsUnavailable()
    {
        await IngestRoomsAsync();

        var reply = await Replies(new FakeAnswers(null)).ReplyAsync(
            "nên dùng đèn gì", [ChatTurn.User("cho phòng bếp")], null, default);

        Assert.Equal("rule", reply.Source);
        Assert.Contains("đèn bếp", reply.Content);
        Assert.DoesNotContain("giường", reply.Content);
    }

    [Fact]
    public async Task ReplyGivesTheLlmTheActiveRoomAndOnlyItsDocuments()
    {
        await IngestRoomsAsync();
        var answers = new FakeAnswers(new GeneratedChatAnswer("Dùng đèn bếp.", [], null));

        await Replies(answers).ReplyAsync("nên dùng đèn gì", [], Brief("kitchen"), default);

        Assert.Equal("phòng bếp", answers.Last?.ActiveRoom);
        Assert.DoesNotContain(answers.Last!.Retrieved, match => match.DocumentId == "doc-bedroom");
    }

    [Fact]
    public async Task RoomTheUserNamedSurvivesAnLlmPatchWithoutIt()
    {
        await IngestRoomsAsync();
        var answers = new FakeAnswers(new GeneratedChatAnswer(
            "Dùng đèn bếp.", [], new BriefPatch(StyleName: "Indochine")));

        var reply = await Replies(answers).ReplyAsync("đèn cho phòng bếp", [], null, default);

        Assert.Equal("kitchen", reply.BriefPatch?.RoomType);
        Assert.Equal("Indochine", reply.BriefPatch?.StyleName);
    }

    private async Task IngestRoomsAsync()
    {
        WriteDocument("kitchen.md", "doc-kitchen", "Đèn nhà bếp", "draft", "[ext]", "[kitchen]",
            "## Gợi ý\nĐặt đèn bếp phía trên mặt thao tác để nhìn rõ khi nấu ăn.");
        WriteDocument("bedroom.md", "doc-bedroom", "Đèn phòng ngủ", "draft", "[ext]", "[master_bedroom]",
            "## Gợi ý\nĐặt đèn ngủ cạnh giường với ánh sáng dịu để dễ thư giãn buổi tối.");
        await IngestAsync();
    }

    private HybridRetriever Hybrid() => new(
        _provider.GetRequiredService<KnowledgeBaseService>(),
        new KnowledgeIndex(_provider.GetRequiredService<IServiceScopeFactory>()),
        _embeddings,
        new ConfigurationBuilder().Build(),
        NullLogger<HybridRetriever>.Instance);

    private ChatReplyService Replies(IChatAnswerGenerator answers)
    {
        var knowledge = _provider.GetRequiredService<KnowledgeBaseService>();
        return new ChatReplyService(
            _provider.CreateScope().ServiceProvider.GetRequiredService<AppDbContext>(),
            new LexicalRetriever(knowledge), Hybrid(), answers, new ConfigurationBuilder().Build());
    }

    private static ChatBriefSnapshot Brief(string room) =>
        new(room, null, "interior_design", null, null, null);

    private sealed class FakeAnswers(GeneratedChatAnswer? answer) : IChatAnswerGenerator
    {
        public ChatAnswerRequest? Last { get; private set; }

        public Task<GeneratedChatAnswer?> GenerateAsync(ChatAnswerRequest request, CancellationToken cancellationToken)
        {
            Last = request;
            return Task.FromResult(answer);
        }
    }

    private async Task<IReadOnlyList<KnowledgeMatch>> RetrieveAsync(string question)
    {
        var retriever = new HybridRetriever(
            _provider.GetRequiredService<KnowledgeBaseService>(),
            new KnowledgeIndex(_provider.GetRequiredService<IServiceScopeFactory>()),
            _embeddings,
            new ConfigurationBuilder().Build(),
            NullLogger<HybridRetriever>.Instance);
        return await retriever.RetrieveAsync(question, [], null, default);
    }

    private async Task<KnowledgeIngestReport> IngestAsync()
    {
        using var scope = _provider.CreateScope();
        var service = new KnowledgeIngestService(
            scope.ServiceProvider.GetRequiredService<AppDbContext>(),
            scope.ServiceProvider.GetRequiredService<KnowledgeBaseService>(),
            _embeddings,
            NullLogger<KnowledgeIngestService>.Instance);
        return await service.IngestAsync(default);
    }

    private void WriteDocument(
        string file, string id, string title, string status, string sources, string rooms, string body) =>
        File.WriteAllText(Path.Combine(_root, "knowledge-base", "docs", file), $"""
            ---
            id: {id}
            title: {title}
            document_type: room_guide
            styles: []
            room_types: {rooms}
            source_ids: {sources}
            author: Tester
            license_note: Test
            reviewer: pending
            version: 1
            reviewed_at: null
            status: {status}
            ---
            # {title}
            {body}
            """.Replace("            ", ""));

    // Two topical axes are enough to make the similarity meaningful without calling a model.
    private sealed class FakeEmbeddings : IEmbeddingService
    {
        public int DocumentCalls { get; private set; }
        public bool FailQueries { get; set; }
        public List<string> Queries { get; } = [];
        public string ModelName => "fake-model";

        public Task<float[]?> EmbedAsync(string text, EmbeddingTask task, CancellationToken cancellationToken)
        {
            if (task == EmbeddingTask.Document) DocumentCalls++;
            else Queries.Add(text);
            if (task == EmbeddingTask.Query && FailQueries) return Task.FromResult<float[]?>(null);
            var normalized = text.ToLowerInvariant();
            float[] vector = normalized.Contains("đèn") ? [1, 0, 0]
                : normalized.Contains("mốc") || normalized.Contains("ẩm") ? [0, 1, 0]
                : [0, 0, 1];
            return Task.FromResult<float[]?>(vector);
        }
    }
}
