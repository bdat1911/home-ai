using InteriorAI.Services;

var repositoryRoot = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "..", "..", "..", "..", ".."));
if (!Directory.Exists(Path.Combine(repositoryRoot, "knowledge-base", "docs")))
    throw new InvalidOperationException($"Knowledge base not found: {repositoryRoot}");

var preview = new KnowledgeBaseService(repositoryRoot, includeDrafts: true);
var cases = new (string Question, string ExpectedId)[]
{
    ("Tôi thích màu xanh lam thì hợp phong cách gì?", "color-blue-01"),
    ("Phòng khách Indochine nhỏ nên bố trí thế nào?", "style-room-indochine-living-01"),
    ("Phòng tắm Scandinavian cần lưu ý gì?", "style-room-scandinavian-bathroom-01"),
    ("Làm sao xem dự án trong Gallery?", "app-gallery-help-01"),
    ("Kết nối điện thoại bằng USB localhost thế nào?", "app-profile-connection-01"),
    ("Phong cách Minimalist Luxury có gì đặc trưng?", "style-minimalist-luxury-01"),
    ("Màu xanh lá hợp Indochine hay Scandinavian?", "color-green-01"),
    ("Màu xám đen có làm phòng tối không?", "color-grey-black-01"),
    ("Nhà bếp cần đèn ở mặt bàn không?", "room-kitchen-01"),
    ("Phòng tắm hay bị ẩm mốc nên làm gì?", "room-bathroom-01"),
    ("Màn hình máy tính bị chói trong phòng làm việc", "room-study-01"),
    ("Tạo cuộc trò chuyện mới trong Chat thế nào?", "app-chat-help-01"),
    ("Làm sao giữ cửa sổ khi thiết kế lại từ ảnh?", "principle-preserve-01"),
    ("Bếp Indochine nên bố trí và chọn màu ra sao?", "style-room-indochine-kitchen-01"),
    ("Phòng tắm Industrial có cần lưu ý khu ướt không?", "style-room-industrial-bathroom-01"),
    ("Phòng làm việc Scandinavian có bị chói màn hình không?", "style-room-scandinavian-study-room-01")
};

foreach (var (question, expectedId) in cases)
{
    var matches = preview.Search(question, take: 2);
    if (!matches.Any(match => match.DocumentId == expectedId))
        throw new Exception($"No relevant document for '{question}'. Got: {string.Join(", ", matches.Select(match => match.DocumentId))}");
    if (matches.Any(match => !match.ChunkId.StartsWith(match.DocumentId + "#", StringComparison.Ordinal)))
        throw new Exception($"Invalid chunk citation for '{question}'.");
    Console.WriteLine($"OK {expectedId}: {question} -> {string.Join(", ", matches.Select(match => match.DocumentId))}");
}

var documentPaths = Directory.EnumerateFiles(
    Path.Combine(repositoryRoot, "knowledge-base", "docs"), "*.md", SearchOption.AllDirectories).ToArray();
if (documentPaths.Length != 55)
    throw new Exception($"Expected 55 knowledge articles; found {documentPaths.Length}.");
var allIds = documentPaths.Select(path => File.ReadLines(path)
    .First(line => line.StartsWith("id: ", StringComparison.Ordinal))[4..]).ToArray();
if (allIds.Distinct(StringComparer.Ordinal).Count() != allIds.Length)
    throw new Exception("Knowledge articles contain duplicate document IDs.");
var pairKeys = documentPaths.Select(path => File.ReadAllLines(path))
    .Where(lines => lines.Contains("document_type: style_room_guide"))
    .Select(lines =>
    {
        var style = lines.First(line => line.StartsWith("styles: [", StringComparison.Ordinal))[9..^1];
        var room = lines.First(line => line.StartsWith("room_types: [", StringComparison.Ordinal))[13..^1];
        return $"{style}|{room}";
    }).ToArray();
if (pairKeys.Length != 30 || pairKeys.Distinct(StringComparer.Ordinal).Count() != 30)
    throw new Exception("Expected all 30 distinct style-room knowledge articles.");
foreach (var path in documentPaths)
{
    var lines = File.ReadAllLines(path);
    var id = lines.First(line => line.StartsWith("id: ", StringComparison.Ordinal))[4..];
    var title = lines.First(line => line.StartsWith("title: ", StringComparison.Ordinal))[7..];
    var matches = preview.Search(title, take: 5);
    if (!matches.Any(match => match.DocumentId == id))
        throw new Exception($"Document title could not retrieve its article: {id}, {title}");
}
Console.WriteLine($"All {documentPaths.Length} article titles are retrievable.");

var recommendation = InteriorAdvicePlanner.Recommend(
    "Tôi thích gỗ trầm và cảm giác ấm, hợp phong cách nào?", []);
if (recommendation == null || !recommendation.Contains("Indochine", StringComparison.Ordinal) ||
    recommendation.Contains("[Nguồn:", StringComparison.Ordinal) ||
    recommendation.Contains("bản nháp", StringComparison.OrdinalIgnoreCase))
    throw new Exception("Warm dark-wood preference should receive a clean Indochine suggestion.");

var roomFollowUp = InteriorAdvicePlanner.StyleAndRoom("Phòng khách", [
    ChatTurn.User("Tôi thích gỗ trầm và cảm giác ấm, hợp phong cách nào?")],
    preview.Search("Phòng khách Indochine", take: 4));
if (roomFollowUp == null || !roomFollowUp.Text.Contains("Gửi ảnh", StringComparison.Ordinal) ||
    !roomFollowUp.Text.Contains("Indochine", StringComparison.Ordinal))
    throw new Exception("Room follow-up should invite a photo for the chosen style.");

if (InteriorAdvicePlanner.StyleAndRoom("Phòng tắm bị mốc", [ChatTurn.User("Tôi thích Indochine")], []) != null)
    throw new Exception("An unrelated room problem should not be hijacked by the previous style.");

if (preview.Search("xin chào bạn").Count != 0)
    throw new Exception("Unrelated greeting should not retrieve a design document.");

var followUp = preview.Search("Còn phòng ngủ thì sao?", history:
    [ChatTurn.User("Tôi thích màu xanh lam cho phòng khách.")]);
if (!followUp.Any(match => match.DocumentId == "color-blue-01"))
    throw new Exception("Follow-up question lost the previous color preference.");

var newTopic = preview.Search("Làm sao xem Gallery?", history:
    [ChatTurn.User("Tôi thích màu xanh lam cho phòng khách.")]);
if (!newTopic.Any(match => match.DocumentId == "app-gallery-help-01"))
    throw new Exception("A new topic should not inherit the previous design preference.");

var bluePassage = preview.Search("Tôi thích màu xanh lam", take: 1).Single();
var repeatedBluePassage = preview.Search("Tôi thích màu xanh lam", take: 1).Single();
if (bluePassage.ChunkId != repeatedBluePassage.ChunkId)
    throw new Exception("Chunk citation IDs must be stable for unchanged content.");

var production = new KnowledgeBaseService(repositoryRoot, includeDrafts: false);
if (production.Search("Tôi thích màu xanh lam").Count != 0)
    throw new Exception("Draft documents or unapproved sources must not be served in production.");

var fixtureRoot = Path.Combine(Path.GetTempPath(), $"home-ai-kb-smoke-{Guid.NewGuid():N}");
var fixtureDocs = Path.Combine(fixtureRoot, "knowledge-base", "docs");
Directory.CreateDirectory(fixtureDocs);
try
{
    var sourceRegister = Path.Combine(fixtureRoot, "knowledge-base", "source-register.csv");
    File.WriteAllText(sourceRegister,
        "source_id,title,author_or_publisher,source_url,license_or_permission,accessed_at,scope_of_use,reviewer,status\n" +
        "test-source,Test source,Tester,internal,Internal test,2026-10-25,Test only,Reviewer,approved\n");
    var fixturePath = Path.Combine(fixtureDocs, "approved-test.md");
    var fixture = """
        ---
        id: approved-test-01
        title: Thử nghiệm phòng khách
        document_type: room_guide
        source_ids: [test-source]
        styles: []
        room_types: [living_room]
        author: Tester
        license_note: Internal test
        reviewer: pending
        reviewed_at: null
        version: 1
        status: approved
        ---
        # Thử nghiệm phòng khách
        ## Gợi ý
        Đây là đoạn thử nghiệm về cách bố trí phòng khách để kiểm tra quy trình duyệt tài liệu.
        """;
    File.WriteAllText(fixturePath, fixture);
    var fixtureSearch = new KnowledgeBaseService(fixtureRoot, includeDrafts: false);
    if (fixtureSearch.Search("Thử nghiệm phòng khách").Count != 0)
        throw new Exception("An unreviewed article must not be public even if status is approved.");

    File.WriteAllText(fixturePath, fixture.Replace("reviewer: pending", "reviewer: Reviewer")
        .Replace("reviewed_at: null", "reviewed_at: 2026-10-25"));
    if (!fixtureSearch.Search("Thử nghiệm phòng khách").Any(match =>
            match.DocumentId == "approved-test-01" && !match.IsDraft))
        throw new Exception("A reviewed article with an approved source should be searchable.");
}
finally
{
    Directory.Delete(fixtureRoot, recursive: true);
}

Console.WriteLine("Knowledge smoke checks passed; production draft gate passed.");
