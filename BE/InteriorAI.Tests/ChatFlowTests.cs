using InteriorAI.Data;
using InteriorAI.Domain.Entities;
using InteriorAI.Models.DTOs;
using InteriorAI.Services;
using Microsoft.AspNetCore.Http;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging.Abstractions;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;

namespace InteriorAI.Tests;

public sealed class ChatFlowTests : IDisposable
{
    private const string UserA = "user-a";
    private const string UserB = "user-b";

    private readonly SqliteConnection _connection = new("DataSource=:memory:");
    private readonly AppDbContext _context;
    private readonly FakeReplyService _replies = new();
    private readonly FakeImageStorage _storage = new();
    private readonly ChatBriefService _briefs;
    private readonly ChatConversationService _chat;
    private readonly string _conversationId = Guid.NewGuid().ToString();
    private readonly int _indochineId;
    private readonly int _scandinavianId;

    public ChatFlowTests()
    {
        _connection.Open();
        _context = new AppDbContext(new DbContextOptionsBuilder<AppDbContext>().UseSqlite(_connection).Options);
        _context.Database.EnsureCreated();
        _context.Users.AddRange(new User { Id = UserA, Name = "A" }, new User { Id = UserB, Name = "B" });
        var indochine = new StyleAesthetic { StyleName = "Indochine", CoreAesthetic = "x", TechnicalSpecs = "y" };
        var scandinavian = new StyleAesthetic { StyleName = "Scandinavian", CoreAesthetic = "x", TechnicalSpecs = "y" };
        _context.StyleAesthetics.AddRange(indochine, scandinavian);
        _context.SaveChanges();
        _indochineId = indochine.StyleID;
        _scandinavianId = scandinavian.StyleID;

        var designs = new DesignManager(
            _context, _storage, new NoImageGeneration(), new FixedPromptService(), new NoBackgroundWork(),
            NullLogger<DesignManager>.Instance);
        _briefs = new ChatBriefService(_context, designs);
        _chat = new ChatConversationService(_context, _replies, _briefs);
        _chat.CreateAsync(UserA, _conversationId, default).GetAwaiter().GetResult();
    }

    public void Dispose()
    {
        _context.Dispose();
        _connection.Dispose();
    }

    [Fact]
    public async Task CitationsAreStoredWithTheAnswerAndReturnedOnReload()
    {
        _replies.Next = new ChatReplyResult("Dùng gỗ sáng.",
            [new KnowledgeMatch("doc-1", "doc-1#a-1", "Bài", "Đoạn", null, true, 1, Version: "2", Sources:
                [new KnowledgeSource("s", "Lighting guide", "DOE", "https://example.org/x")])],
            null, "llm");

        var sent = await Send("Hỏi");
        var citation = Assert.Single(sent.AssistantMessage.Citations);
        Assert.Equal("doc-1#a-1", citation.ChunkId);
        Assert.Equal("Lighting guide (DOE)", citation.SourceLabel);

        _context.ChangeTracker.Clear();
        var messages = await _chat.GetMessagesAsync(UserA, _conversationId, default);
        Assert.Equal("doc-1#a-1", Assert.Single(messages.Last().Citations).ChunkId);
    }

    [Fact]
    public async Task HistoryPassedToReplyIncludesAssistantTurnsInOrder()
    {
        await Send("Một");
        await Send("Hai");

        Assert.Equal(["user", "assistant"], _replies.LastHistory!.Select(turn => turn.Role));
        Assert.Equal("Một", _replies.LastHistory![0].Content);
    }

    [Fact]
    public async Task RetrySameClientMessageDoesNotCallReplyTwice()
    {
        var id = Guid.NewGuid().ToString();
        var first = await _chat.SendAsync(UserA, _conversationId, id, "Hỏi", default);
        _context.ChangeTracker.Clear();
        var second = await _chat.SendAsync(UserA, _conversationId, id, "Hỏi", default);

        Assert.Equal(1, _replies.Calls);
        Assert.Equal(first.AssistantMessage.Id, second.AssistantMessage.Id);
    }

    [Fact]
    public async Task PatchUpdatesBriefAndNewestValueWins()
    {
        _replies.Next = new ChatReplyResult("OK", [], new BriefPatch("kitchen", "Indochine"), "llm");
        var first = await Send("Bếp Indochine");
        Assert.Equal(1, first.Brief.Version);
        Assert.Equal(_indochineId, first.Brief.StyleId);
        Assert.Equal("Indochine", first.Brief.StyleName);

        _replies.Next = new ChatReplyResult("OK", [], new BriefPatch(StyleName: "Scandinavian"), "llm");
        var second = await Send("Đổi sang Scandinavian");
        Assert.Equal(2, second.Brief.Version);
        Assert.Equal(_scandinavianId, second.Brief.StyleId);
        Assert.Equal("kitchen", second.Brief.RoomType);
    }

    [Fact]
    public async Task InvalidPatchValuesAreDiscarded()
    {
        _replies.Next = new ChatReplyResult("OK", [],
            new BriefPatch("exterior", "Khong Co That", "hack_feature"), "llm");

        var sent = await Send("Hỏi");

        Assert.Equal(0, sent.Brief.Version);
        Assert.Null(sent.Brief.RoomType);
        Assert.Null(sent.Brief.StyleId);
        Assert.Equal("interior_design", sent.Brief.FeatureId);
    }

    [Fact]
    public async Task LongFreeTextIsTruncatedAndControlCharactersRemoved()
    {
        _replies.Next = new ChatReplyResult("OK", [],
            new BriefPatch(MustKeep: "giữ\nsofa" + new string('x', 400)), "llm");

        var sent = await Send("Hỏi");

        Assert.Equal(300, sent.Brief.MustKeep!.Length);
        Assert.DoesNotContain('\n', sent.Brief.MustKeep);
    }

    [Fact]
    public async Task UnchangedPatchDoesNotBumpVersion()
    {
        _replies.Next = new ChatReplyResult("OK", [], new BriefPatch("kitchen"), "llm");
        await Send("Bếp");
        _replies.Next = new ChatReplyResult("OK", [], new BriefPatch("kitchen"), "llm");
        var sent = await Send("Bếp nữa");

        Assert.Equal(1, sent.Brief.Version);
    }

    [Fact]
    public async Task ManualUpdateWithStaleVersionConflicts()
    {
        await _briefs.UpdateAsync(UserA, _conversationId,
            new UpdateDesignBriefRequest { ExpectedVersion = 0, RoomType = "kitchen" }, default);

        await Assert.ThrowsAsync<InvalidOperationException>(() => _briefs.UpdateAsync(UserA, _conversationId,
            new UpdateDesignBriefRequest { ExpectedVersion = 0, RoomType = "bathroom" }, default));
    }

    [Fact]
    public async Task ManualUpdateRejectsUnknownRoomAndStyle()
    {
        await Assert.ThrowsAsync<ArgumentException>(() => _briefs.UpdateAsync(UserA, _conversationId,
            new UpdateDesignBriefRequest { RoomType = "exterior" }, default));
        await Assert.ThrowsAsync<ArgumentException>(() => _briefs.UpdateAsync(UserA, _conversationId,
            new UpdateDesignBriefRequest { StyleId = 999999 }, default));
    }

    [Fact]
    public async Task ConfirmRequiresCompleteBrief()
    {
        await Assert.ThrowsAsync<ArgumentException>(() => _briefs.ConfirmAsync(UserA, _conversationId,
            new ConfirmDesignBriefRequest
            {
                Image = PngFile(), BriefVersion = 0, ClientRequestId = Guid.NewGuid().ToString()
            }, default));
        Assert.Equal(0, _storage.Uploads);
    }

    [Fact]
    public async Task ConfirmRejectsStaleBriefVersion()
    {
        var version = await CompleteBrief();

        await Assert.ThrowsAsync<InvalidOperationException>(() => _briefs.ConfirmAsync(UserA, _conversationId,
            new ConfirmDesignBriefRequest
            {
                Image = PngFile(), BriefVersion = version - 1, ClientRequestId = Guid.NewGuid().ToString()
            }, default));
        Assert.Equal(0, _storage.Uploads);
    }

    [Fact]
    public async Task ConfirmCreatesJobLinkedToConversationAndHistory()
    {
        var version = await CompleteBrief();

        var confirmed = await _briefs.ConfirmAsync(UserA, _conversationId, new ConfirmDesignBriefRequest
        {
            Image = PngFile(), BriefVersion = version, ClientRequestId = Guid.NewGuid().ToString()
        }, default);

        Assert.Equal(DesignBriefStatuses.Confirmed, confirmed.Brief.Status);
        Assert.Equal(ChatMessageKinds.DesignJob, confirmed.Message.Kind);
        var job = await _context.DesignResults.AsNoTracking().SingleAsync();
        Assert.Equal(_conversationId, job.ConversationId);
        Assert.Equal(version, job.BriefVersion);
        Assert.Equal(UserA, job.UserId);

        _context.ChangeTracker.Clear();
        var messages = await _chat.GetMessagesAsync(UserA, _conversationId, default);
        Assert.Equal(job.Id, messages.Last().DesignId);
    }

    [Fact]
    public async Task DoubleConfirmCreatesOneJobAndOneMessage()
    {
        var version = await CompleteBrief();
        var requestId = Guid.NewGuid().ToString();
        ConfirmDesignBriefRequest Request() => new()
        {
            Image = PngFile(), BriefVersion = version, ClientRequestId = requestId
        };

        var first = await _briefs.ConfirmAsync(UserA, _conversationId, Request(), default);
        _context.ChangeTracker.Clear();
        var second = await _briefs.ConfirmAsync(UserA, _conversationId, Request(), default);

        Assert.Equal(first.DesignId, second.DesignId);
        Assert.Equal(first.Message.Id, second.Message.Id);
        Assert.Equal(1, _storage.Uploads);
        Assert.Equal(1, await _context.DesignResults.CountAsync());
        Assert.Equal(1, await _context.ChatMessages.CountAsync(item => item.Kind == ChatMessageKinds.DesignJob));
    }

    [Fact]
    public async Task ChangingConfirmedBriefReturnsItToDraft()
    {
        var version = await CompleteBrief();
        await _briefs.ConfirmAsync(UserA, _conversationId, new ConfirmDesignBriefRequest
        {
            Image = PngFile(), BriefVersion = version, ClientRequestId = Guid.NewGuid().ToString()
        }, default);

        var updated = await _briefs.UpdateAsync(UserA, _conversationId,
            new UpdateDesignBriefRequest { ExpectedVersion = version, RoomType = "bathroom" }, default);

        Assert.Equal(DesignBriefStatuses.Draft, updated.Status);
    }

    [Fact]
    public async Task RemoveFurnitureNeedsNoStyle()
    {
        var updated = await _briefs.UpdateAsync(UserA, _conversationId, new UpdateDesignBriefRequest
        {
            ExpectedVersion = 0, RoomType = "kitchen", FeatureId = "remove_furniture"
        }, default);

        Assert.Empty(updated.MissingFields);
    }

    [Fact]
    public async Task OtherUsersCannotReachConversationOrBrief()
    {
        await Assert.ThrowsAsync<KeyNotFoundException>(() =>
            _chat.GetMessagesAsync(UserB, _conversationId, default));
        await Assert.ThrowsAsync<KeyNotFoundException>(() =>
            _briefs.GetAsync(UserB, _conversationId, default));
        await Assert.ThrowsAsync<KeyNotFoundException>(() => _briefs.ConfirmAsync(UserB, _conversationId,
            new ConfirmDesignBriefRequest { Image = PngFile(), ClientRequestId = Guid.NewGuid().ToString() }, default));
    }

    [Fact]
    public void BriefRequirementsAreAppendedOnlyWhenPresent()
    {
        Assert.Equal("Base", DesignPromptService.AppendBriefRequirements("Base", null, " ", null));
        var prompt = DesignPromptService.AppendBriefRequirements("Base", "xanh navy", "sofa", "thảm");
        Assert.Contains("xanh navy", prompt);
        Assert.Contains("keep unchanged: sofa", prompt);
        Assert.Contains("avoid: thảm", prompt);
    }

    private Task<SendChatMessageResponse> Send(string content) =>
        _chat.SendAsync(UserA, _conversationId, Guid.NewGuid().ToString(), content, default);

    private async Task<int> CompleteBrief()
    {
        var brief = await _briefs.UpdateAsync(UserA, _conversationId, new UpdateDesignBriefRequest
        {
            ExpectedVersion = 0, RoomType = "kitchen", StyleId = _indochineId
        }, default);
        return brief.Version;
    }

    private static IFormFile PngFile()
    {
        var stream = new MemoryStream();
        using (var image = new Image<Rgba32>(2, 2)) image.SaveAsPng(stream);
        stream.Position = 0;
        return new FormFile(stream, 0, stream.Length, "image", "room.png")
        {
            Headers = new HeaderDictionary(), ContentType = "image/png"
        };
    }

    private sealed class FakeReplyService : IChatReplyService
    {
        public ChatReplyResult? Next { get; set; }
        public int Calls { get; private set; }
        public IReadOnlyList<ChatTurn>? LastHistory { get; private set; }

        public Task<ChatReplyResult> ReplyAsync(
            string message, IReadOnlyList<ChatTurn> history, ChatBriefSnapshot? brief, CancellationToken cancellationToken)
        {
            Calls++;
            LastHistory = history;
            var result = Next ?? new ChatReplyResult($"Trả lời: {message}", [], null, "llm");
            Next = null;
            return Task.FromResult(result);
        }
    }

    private sealed class FakeImageStorage : IImageStorageService
    {
        public int Uploads { get; private set; }

        public Task<string> UploadImageAsync(string base64Image)
        {
            Uploads++;
            return Task.FromResult($"https://images.test/{Uploads}.png");
        }

        public Task<string> UploadImageFromUrlAsync(string imageUrl) => Task.FromResult(imageUrl);
    }

    private sealed class NoImageGeneration : IImageGenerationService
    {
        public Task<string> GenerateImageFromUrlAsync(
            string prompt, string imageUrl, string? model = null, string? resolution = null) =>
            throw new InvalidOperationException("Image generation must not run in these tests.");
    }

    private sealed class FixedPromptService : IDesignPromptService
    {
        public Task<List<DesignStyleResponse>> GetDesignStylesAsync() => Task.FromResult(new List<DesignStyleResponse>());

        public Task<string> GetConfiguredPromptAsync(
            int? styleId, string? styleName, string? legacyStyle, string? roomType = null, string? featureId = null) =>
            Task.FromResult("Base prompt");
    }

    private sealed class NoBackgroundWork : Microsoft.Extensions.DependencyInjection.IServiceScopeFactory
    {
        public Microsoft.Extensions.DependencyInjection.IServiceScope CreateScope() =>
            throw new InvalidOperationException("Background processing is not part of these tests.");
    }
}
