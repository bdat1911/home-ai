using System.Globalization;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using Microsoft.AspNetCore.Hosting;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Hosting;

namespace InteriorAI.Services;

public sealed record ChatTurn(string Role, string Content)
{
    public static ChatTurn User(string content) => new("user", content);
    public static ChatTurn Assistant(string content) => new("assistant", content);
    public bool IsUser => Role == "user";
}

public sealed record KnowledgeSource(string Id, string Title, string Publisher, string? Url)
{
    public bool IsExternal => Url != null;
}

public sealed record KnowledgeMatch(
    string DocumentId,
    string ChunkId,
    string Title,
    string Passage,
    string? ClarifyingQuestion,
    bool IsDraft,
    double Score,
    string? Limitations = null,
    string? Version = null,
    IReadOnlyList<KnowledgeSource>? Sources = null);

public sealed record KnowledgeQuery(
    string Text,
    IReadOnlySet<string> Styles,
    IReadOnlySet<string> Rooms,
    bool IsAppTopic);

public sealed record KnowledgeChunkRecord(
    string Id, string DocumentId, int Ordinal, string Section, string Text, string ContentHash);

public sealed record ParsedKnowledgeDocument(
    string Id,
    string Title,
    string Type,
    string Status,
    string? Version,
    IReadOnlyList<string> SourceIds,
    HashSet<string> Styles,
    HashSet<string> Rooms,
    IReadOnlyList<KnowledgeChunkRecord> Chunks,
    string? ClarifyingQuestion,
    string? Limitations,
    bool IsReviewed);

/// <summary>
/// Reads the Markdown corpus and provides the lexical channel of retrieval.
/// Parsed files are cached and refreshed when a file changes.
/// </summary>
public sealed class KnowledgeBaseService
{
    private const int MaxWordsPerChunk = 180;

    private static readonly Regex WordRegex = new("[a-z0-9]+", RegexOptions.Compiled);
    private static readonly HashSet<string> StopWords = new(StringComparer.Ordinal)
    {
        "a", "ai", "ban", "bang", "bi", "cho", "co", "cua", "de", "den", "duoc",
        "gi", "giu", "hay", "hon", "khac", "khong", "la", "lam", "loai", "mot",
        "muon", "nao", "nen", "nhieu", "nhu", "noi", "o", "phai", "phong", "sao",
        "se", "the", "thi", "theo", "thu", "toi", "trong", "tu", "va", "voi"
    };

    private static readonly IReadOnlyDictionary<string, string[]> StyleAliases =
        new Dictionary<string, string[]>(StringComparer.Ordinal)
        {
            ["Indochine"] = ["indochine", "dong duong"],
            ["Minimalist Luxury"] = ["minimalist luxury", "toi gian sang trong", "toi gian"],
            ["Modern Luxury"] = ["modern luxury", "hien dai sang trong"],
            ["Industrial"] = ["industrial", "cong nghiep"],
            ["Neoclassic"] = ["neoclassic", "tan co dien"],
            ["Scandinavian"] = ["scandinavian", "bac au"]
        };

    private static readonly IReadOnlyDictionary<string, string[]> RoomAliases =
        new Dictionary<string, string[]>(StringComparer.Ordinal)
        {
            ["living_room"] = ["phong khach"],
            ["master_bedroom"] = ["phong ngu"],
            ["kitchen"] = ["nha bep", "phong bep", "bep"],
            ["bathroom"] = ["phong tam", "nha tam", "phong ve sinh"],
            ["study_room"] = ["phong lam viec", "goc lam viec", "phong hoc"]
        };

    private static readonly IReadOnlyDictionary<string, string[]> ColorAliases =
        new Dictionary<string, string[]>(StringComparer.Ordinal)
        {
            ["color-blue-01"] = ["xanh lam", "xanh navy"],
            ["color-green-01"] = ["xanh la", "xanh ngoc", "xanh sage"],
            ["color-warm-neutrals-01"] = ["trung tinh am", "mau be", "mau kem"],
            ["color-grey-black-01"] = ["mau xam", "mau den", "xam than"],
            ["color-pastel-01"] = ["pastel"]
        };

    private readonly string _documentRoot;
    private readonly string _sourceRegisterPath;
    private readonly bool _includeDrafts;
    private readonly object _cacheLock = new();
    private string? _cacheFingerprint;
    private CorpusSnapshot _cache = CorpusSnapshot.Empty;

    public KnowledgeBaseService(IHostEnvironment environment, IConfiguration configuration)
        : this(FindRepositoryRoot(environment.ContentRootPath),
            configuration.GetValue<bool>("Knowledge:IncludeDrafts"))
    {
    }

    public static string FindRepositoryRoot(string contentRoot)
    {
        for (var directory = new DirectoryInfo(contentRoot); directory != null; directory = directory.Parent)
        {
            if (Directory.Exists(Path.Combine(directory.FullName, "knowledge-base", "docs")))
                return directory.FullName;
        }
        return contentRoot;
    }

    public KnowledgeBaseService(string repositoryRoot, bool includeDrafts)
    {
        _documentRoot = Path.Combine(repositoryRoot, "knowledge-base", "docs");
        _sourceRegisterPath = Path.Combine(repositoryRoot, "knowledge-base", "source-register.csv");
        _includeDrafts = includeDrafts;
    }

    public IReadOnlyList<ParsedKnowledgeDocument> GetDocuments() => Snapshot().Documents;

    public IReadOnlyList<KnowledgeSource> ResolveSources(ParsedKnowledgeDocument document)
    {
        var sources = Snapshot().Sources;
        return document.SourceIds
            .Select(id => sources.GetValueOrDefault(id))
            .Where(source => source != null)
            .Select(source => source!.Source)
            .ToArray();
    }

    /// <summary>A document may reach users when it is reviewed and every source is approved.</summary>
    public bool IsPublishable(ParsedKnowledgeDocument document)
    {
        var sources = Snapshot().Sources;
        return document.Status == "approved" && document.IsReviewed && document.SourceIds.Count > 0 &&
               document.SourceIds.All(id => sources.TryGetValue(id, out var source) && source.IsApproved);
    }

    public bool IsServable(ParsedKnowledgeDocument document) =>
        IsPublishable(document) || (_includeDrafts && document.Status == "draft");

    public static string? RoomPhrase(string roomType) =>
        RoomAliases.TryGetValue(roomType, out var aliases) ? aliases[0] : null;

    public KnowledgeQuery Analyze(string question, IReadOnlyList<ChatTurn>? history = null)
    {
        var expanded = ExpandFollowUp(question, history);
        var normalized = Normalize(expanded).Replace("xanh duong", "xanh lam", StringComparison.Ordinal);
        return new KnowledgeQuery(
            expanded,
            StyleAliases.Where(item => item.Value.Any(alias => ContainsPhrase(normalized, alias)))
                .Select(item => item.Key).ToHashSet(StringComparer.Ordinal),
            RoomAliases.Where(item => item.Value.Any(alias => ContainsPhrase(normalized, alias)))
                .Select(item => item.Key).ToHashSet(StringComparer.Ordinal),
            FindAppDocument(normalized) != null);
    }

    public IReadOnlyList<KnowledgeMatch> Search(
        string question, int take = 2, IReadOnlyList<ChatTurn>? history = null, int maxPerDocument = 1)
    {
        if (string.IsNullOrWhiteSpace(question) || !Directory.Exists(_documentRoot))
            return [];

        question = ExpandFollowUp(question, history);
        var normalized = Normalize(question).Replace("xanh duong", "xanh lam", StringComparison.Ordinal);
        var queryTerms = Terms(normalized);
        if (queryTerms.Count == 0) return [];

        var styles = StyleAliases
            .Where(item => item.Value.Any(alias => ContainsPhrase(normalized, alias)))
            .Select(item => item.Key).ToHashSet(StringComparer.Ordinal);
        var rooms = RoomAliases
            .Where(item => item.Value.Any(alias => ContainsPhrase(normalized, alias)))
            .Select(item => item.Key).ToHashSet(StringComparer.Ordinal);
        var asksColor = normalized.Contains("mau", StringComparison.Ordinal)
            || normalized.Contains("xanh", StringComparison.Ordinal)
            || normalized.Contains("pastel", StringComparison.Ordinal);
        var namedColors = ColorAliases
            .Where(item => item.Value.Any(alias => ContainsPhrase(normalized, alias)))
            .Select(item => item.Key).ToHashSet(StringComparer.Ordinal);
        var appDoc = FindAppDocument(normalized);

        var matches = new List<KnowledgeMatch>();
        foreach (var document in GetDocuments())
        {
            if (document.Type == "color_guide" && namedColors.Count > 0 &&
                !namedColors.Contains(document.Id)) continue;
            if (namedColors.Count > 0 && styles.Count == 0 && rooms.Count == 0 &&
                document.Type != "color_guide") continue;
            if (document.Type == "app_guide" && document.Id != appDoc) continue;
            if (appDoc != null && styles.Count == 0 && rooms.Count == 0 &&
                document.Type != "app_guide") continue;
            if (rooms.Count > 0 && styles.Count == 0 &&
                document.Type == "style_room_guide") continue;
            if (!IsServable(document)) continue;

            var metadataScore = 0d;
            if (styles.Count > 0)
            {
                if (document.Type == "style_profile" && document.Styles.Overlaps(styles)) metadataScore += 8;
                else if (document.Type == "style_room_guide" && document.Styles.Overlaps(styles)) metadataScore += 5;
                else if (document.Styles.Count > 0 && !document.Styles.Overlaps(styles)) metadataScore -= 8;
            }
            if (rooms.Count > 0)
            {
                if (document.Type == "room_guide" && document.Rooms.Overlaps(rooms)) metadataScore += 7;
                else if (document.Type == "style_room_guide" && document.Rooms.Overlaps(rooms))
                    metadataScore += styles.Count > 0 ? 7 : 2;
                else if (document.Rooms.Count > 0 && !document.Rooms.Overlaps(rooms)) metadataScore -= 8;
            }
            if (asksColor && document.Type == "color_guide")
            {
                metadataScore += 9;
                if (namedColors.Count > 0)
                    metadataScore += namedColors.Contains(document.Id) ? 12 : -6;
            }
            if (styles.Count > 0 && rooms.Count > 0 && document.Type == "style_room_guide" &&
                document.Styles.Overlaps(styles) && document.Rooms.Overlaps(rooms))
                metadataScore += 8;
            if (document.Id == "app-profile-connection-01" &&
                new[] { "usb", "adb", "localhost", "ket noi", "server" }
                    .Any(term => normalized.Contains(term, StringComparison.Ordinal)))
                metadataScore += 9;
            if (document.Id == "app-gallery-help-01" &&
                new[] { "gallery", "du an", "lich su thiet ke" }
                    .Any(term => normalized.Contains(term, StringComparison.Ordinal)))
                metadataScore += 8;

            var titleTerms = Terms(Normalize(document.Title));
            foreach (var chunk in document.Chunks)
            {
                var passageTerms = Terms(Normalize(chunk.Text));
                var overlap = queryTerms.Count(passageTerms.Contains);
                var titleOverlap = queryTerms.Count(titleTerms.Contains);
                var score = metadataScore + overlap * 1.5 + titleOverlap * 2;
                if (score < 4 || (overlap == 0 && titleOverlap == 0 && metadataScore < 7))
                    continue;
                matches.Add(ToMatch(document, chunk, score));
            }
        }

        return matches
            .GroupBy(match => match.DocumentId, StringComparer.Ordinal)
            .SelectMany(group => group.OrderByDescending(match => match.Score).Take(Math.Max(1, maxPerDocument)))
            .OrderByDescending(match => match.Score)
            .ThenBy(match => match.ChunkId, StringComparer.Ordinal)
            .Take(Math.Clamp(take, 1, 30))
            .ToArray();
    }

    public KnowledgeMatch ToMatch(ParsedKnowledgeDocument document, KnowledgeChunkRecord chunk, double score) =>
        new(document.Id, chunk.Id, document.Title, chunk.Text, document.ClarifyingQuestion,
            document.Status == "draft", score, document.Limitations, document.Version, ResolveSources(document));

    private static string? FindAppDocument(string normalized)
    {
        if (ContainsAnyPhrase(normalized, "usb", "adb", "localhost", "ket noi", "server", "profile", "may chu"))
            return "app-profile-connection-01";
        if (ContainsAnyPhrase(normalized, "gallery", "du an", "lich su thiet ke", "thiet ke da tao", "anh da tao"))
            return "app-gallery-help-01";
        if (ContainsAnyPhrase(normalized, "chat", "tro chuyen", "hoi thoai"))
            return "app-chat-help-01";
        if (ContainsAnyPhrase(normalized, "tao anh", "tao thiet ke", "interior"))
            return "app-design-flow-01";
        return null;
    }

    private static bool ContainsAnyPhrase(string text, params string[] phrases) =>
        phrases.Any(phrase => ContainsPhrase(text, phrase));

    private static string ExpandFollowUp(string question, IReadOnlyList<ChatTurn>? history)
    {
        if (history is not { Count: > 0 }) return question;
        var current = Normalize(question);
        var refersBack = current.StartsWith("con ", StringComparison.Ordinal) ||
            current.StartsWith("the ", StringComparison.Ordinal) ||
            current.StartsWith("vay ", StringComparison.Ordinal) ||
            new[] { "mau do", "phong do", "kieu do", "phong cach do" }
                .Any(phrase => ContainsPhrase(current, phrase));
        if (!refersBack || new[] { "gallery", "profile", "usb", "adb", "ket noi", "tao anh" }
                .Any(term => current.Contains(term, StringComparison.Ordinal)))
            return question;

        string? FindPreviousAlias(IReadOnlyDictionary<string, string[]> aliases, bool useKey, bool includeAssistant)
        {
            for (var i = history.Count - 1; i >= 0; i--)
            {
                if (!includeAssistant && !history[i].IsUser) continue;
                var earlier = Normalize(history[i].Content);
                foreach (var entry in aliases)
                {
                    var alias = entry.Value.FirstOrDefault(value => ContainsPhrase(earlier, value));
                    if (alias != null) return useKey ? entry.Key : alias;
                }
            }
            return null;
        }

        var context = new List<string>();
        if (!StyleAliases.Values.SelectMany(values => values).Any(alias => ContainsPhrase(current, alias)))
        {
            // The assistant may be the one who named the style the user now refers to.
            var style = FindPreviousAlias(StyleAliases, useKey: true, includeAssistant: true);
            if (style != null) context.Add(style);
        }
        if (!RoomAliases.Values.SelectMany(values => values).Any(alias => ContainsPhrase(current, alias)))
        {
            var room = FindPreviousAlias(RoomAliases, useKey: false, includeAssistant: false);
            if (room != null) context.Add(room);
        }
        if (!ColorAliases.Values.SelectMany(values => values).Any(alias => ContainsPhrase(current, alias)))
        {
            var color = FindPreviousAlias(ColorAliases, useKey: false, includeAssistant: false);
            if (color != null) context.Add(color);
        }
        return context.Count == 0 ? question : $"{question} {string.Join(' ', context)}";
    }

    internal static string Normalize(string value)
    {
        var decomposed = value.ToLowerInvariant().Replace('đ', 'd').Normalize(NormalizationForm.FormD);
        var builder = new StringBuilder(decomposed.Length);
        foreach (var character in decomposed)
        {
            if (CharUnicodeInfo.GetUnicodeCategory(character) != UnicodeCategory.NonSpacingMark)
                builder.Append(character);
        }
        return builder.ToString().Normalize(NormalizationForm.FormC);
    }

    internal static bool ContainsPhrase(string text, string phrase) =>
        Regex.IsMatch(text, $"(?<![a-z0-9]){Regex.Escape(phrase)}(?![a-z0-9])");

    private static HashSet<string> Terms(string text) =>
        WordRegex.Matches(text)
            .Select(match => match.Value)
            .Where(word => word.Length > 1 && !StopWords.Contains(word))
            .ToHashSet(StringComparer.Ordinal);

    private CorpusSnapshot Snapshot()
    {
        if (!Directory.Exists(_documentRoot)) return CorpusSnapshot.Empty;
        var fingerprint = BuildFingerprint();
        lock (_cacheLock)
        {
            if (fingerprint == _cacheFingerprint) return _cache;
            var documents = Directory.EnumerateFiles(_documentRoot, "*.md", SearchOption.AllDirectories)
                .OrderBy(path => path, StringComparer.Ordinal)
                .Select(ReadDocument)
                .Where(document => document != null)
                .Select(document => document!)
                .ToArray();
            _cache = new CorpusSnapshot(documents, ReadSources());
            _cacheFingerprint = fingerprint;
            return _cache;
        }
    }

    private string BuildFingerprint()
    {
        var builder = new StringBuilder();
        foreach (var path in Directory.EnumerateFiles(_documentRoot, "*.md", SearchOption.AllDirectories)
                     .Append(_sourceRegisterPath))
        {
            var file = new FileInfo(path);
            if (!file.Exists) continue;
            builder.Append(path).Append('|').Append(file.Length).Append('|')
                .Append(file.LastWriteTimeUtc.Ticks).Append('\n');
        }
        return builder.ToString();
    }

    private Dictionary<string, RegisteredSource> ReadSources()
    {
        var result = new Dictionary<string, RegisteredSource>(StringComparer.Ordinal);
        if (!File.Exists(_sourceRegisterPath)) return result;
        foreach (var row in SimpleCsv.ReadRecords(_sourceRegisterPath))
        {
            var id = row.GetValueOrDefault("source_id")?.Trim();
            if (string.IsNullOrEmpty(id)) continue;
            var url = row.GetValueOrDefault("source_url")?.Trim();
            result[id] = new RegisteredSource(
                new KnowledgeSource(
                    id,
                    row.GetValueOrDefault("title")?.Trim() ?? id,
                    row.GetValueOrDefault("author_or_publisher")?.Trim() ?? "",
                    url != null && url.StartsWith("http", StringComparison.OrdinalIgnoreCase) ? url : null),
                string.Equals(row.GetValueOrDefault("status")?.Trim(), "approved", StringComparison.OrdinalIgnoreCase));
        }
        return result;
    }

    private static ParsedKnowledgeDocument? ReadDocument(string path)
    {
        var file = new FileInfo(path);
        if (file.Length > 64 * 1024) return null;
        var text = File.ReadAllText(path);
        var lines = text.Replace("\r\n", "\n", StringComparison.Ordinal).Split('\n');
        if (lines.Length < 4 || lines[0].Trim() != "---") return null;
        var frontMatterEnd = Array.FindIndex(lines, 1, line => line.Trim() == "---");
        if (frontMatterEnd < 2) return null;

        var metadata = new Dictionary<string, string>(StringComparer.Ordinal);
        foreach (var line in lines[1..frontMatterEnd].Where(line => line.Contains(':')))
        {
            var parts = line.Split(':', 2);
            metadata[parts[0].Trim()] = parts[1].Trim();
        }
        if (!metadata.TryGetValue("id", out var id) ||
            !metadata.TryGetValue("title", out var title) ||
            !metadata.TryGetValue("document_type", out var type) ||
            !metadata.TryGetValue("status", out var status) ||
            !metadata.TryGetValue("source_ids", out var sourceIds) ||
            !Regex.IsMatch(id, "^[a-z0-9-]+$"))
            return null;

        var sections = new List<(string Heading, List<string> Paragraphs)>();
        var section = "";
        var paragraphs = new List<string>();
        var paragraph = new StringBuilder();
        void FlushParagraph()
        {
            var value = paragraph.ToString().Trim();
            paragraph.Clear();
            if (value.Length >= 25) paragraphs.Add(value);
        }
        void FlushSection()
        {
            FlushParagraph();
            if (paragraphs.Count > 0) sections.Add((section, paragraphs));
            paragraphs = [];
        }

        foreach (var line in lines[(frontMatterEnd + 1)..])
        {
            if (line.StartsWith("## ", StringComparison.Ordinal))
            {
                FlushSection();
                section = line[3..].Trim();
            }
            else if (line.StartsWith("# ", StringComparison.Ordinal))
            {
                FlushSection();
                section = "";
            }
            else if (string.IsNullOrWhiteSpace(line))
            {
                FlushParagraph();
            }
            else
            {
                if (paragraph.Length > 0) paragraph.Append(' ');
                paragraph.Append(line.Trim().TrimStart('-', ' '));
            }
        }
        FlushSection();

        string? clarifyingQuestion = null;
        string? limitations = null;
        var chunks = new List<KnowledgeChunkRecord>();
        foreach (var (heading, content) in sections)
        {
            if (heading is "Cần hỏi thêm" or "Cần hỏi người dùng")
            {
                clarifyingQuestion ??= string.Join(' ', content);
                continue;
            }
            if (heading == "Giới hạn")
            {
                limitations ??= string.Join(' ', content);
                continue;
            }
            if (heading == "Nguồn") continue;
            chunks.AddRange(BuildChunks(id, heading, content, chunks.Count));
        }

        return chunks.Count == 0 ? null : new ParsedKnowledgeDocument(
            id, title, type, status, metadata.GetValueOrDefault("version"), ParseList(sourceIds),
            ParseList(metadata.GetValueOrDefault("styles")).ToHashSet(StringComparer.Ordinal),
            ParseList(metadata.GetValueOrDefault("room_types")).ToHashSet(StringComparer.Ordinal),
            chunks, clarifyingQuestion, limitations,
            !string.IsNullOrWhiteSpace(metadata.GetValueOrDefault("author")) &&
            !string.IsNullOrWhiteSpace(metadata.GetValueOrDefault("license_note")) &&
            !string.IsNullOrWhiteSpace(metadata.GetValueOrDefault("version")) &&
            !string.IsNullOrWhiteSpace(metadata.GetValueOrDefault("reviewer")) &&
            !metadata.GetValueOrDefault("reviewer")!.Equals("pending", StringComparison.OrdinalIgnoreCase) &&
            DateTime.TryParse(metadata.GetValueOrDefault("reviewed_at"), out _));
    }

    // Articles are short, so a section is normally one chunk. The ID stays stable when wording
    // changes; ContentHash is what detects edits during ingestion.
    private static IEnumerable<KnowledgeChunkRecord> BuildChunks(
        string documentId, string heading, List<string> paragraphs, int startOrdinal)
    {
        var slug = Slug(heading);
        var number = 1;
        var ordinal = startOrdinal;
        var buffer = new List<string>();
        var words = 0;

        KnowledgeChunkRecord Emit()
        {
            var text = string.Join('\n', buffer);
            buffer.Clear();
            words = 0;
            var hash = Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(text))).ToLowerInvariant();
            return new KnowledgeChunkRecord($"{documentId}#{slug}-{number++}", documentId, ordinal++, heading, text, hash);
        }

        foreach (var paragraph in paragraphs)
        {
            var count = paragraph.Split(' ', StringSplitOptions.RemoveEmptyEntries).Length;
            if (buffer.Count > 0 && words + count > MaxWordsPerChunk) yield return Emit();
            buffer.Add(paragraph);
            words += count;
        }
        if (buffer.Count > 0) yield return Emit();
    }

    private static string Slug(string heading)
    {
        var slug = Regex.Replace(Normalize(heading), "[^a-z0-9]+", "-").Trim('-');
        return slug.Length == 0 ? "tong-quan" : slug;
    }

    private static List<string> ParseList(string? value)
    {
        if (string.IsNullOrWhiteSpace(value)) return [];
        value = value.Trim();
        if (!value.StartsWith('[') || !value.EndsWith(']')) return [];
        return value[1..^1].Split(',', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries)
            .Select(item => item.Trim('"', '\'')).ToList();
    }

    private sealed record RegisteredSource(KnowledgeSource Source, bool IsApproved);

    private sealed record CorpusSnapshot(
        IReadOnlyList<ParsedKnowledgeDocument> Documents,
        IReadOnlyDictionary<string, RegisteredSource> Sources)
    {
        public static readonly CorpusSnapshot Empty =
            new([], new Dictionary<string, RegisteredSource>(StringComparer.Ordinal));
    }
}

public static class SimpleCsv
{
    public static List<Dictionary<string, string>> ReadRecords(string path)
    {
        var rows = Parse(File.ReadAllText(path));
        if (rows.Count == 0) return [];
        var header = rows[0];
        return rows.Skip(1)
            .Where(row => row.Any(cell => cell.Length > 0))
            .Select(row =>
            {
                var record = new Dictionary<string, string>(StringComparer.Ordinal);
                for (var i = 0; i < header.Count && i < row.Count; i++) record[header[i]] = row[i];
                return record;
            })
            .ToList();
    }

    public static string Escape(string? value)
    {
        value ??= "";
        return value.IndexOfAny([',', '"', '\n', '\r']) >= 0 ? $"\"{value.Replace("\"", "\"\"")}\"" : value;
    }

    private static List<List<string>> Parse(string text)
    {
        var rows = new List<List<string>>();
        var row = new List<string>();
        var cell = new StringBuilder();
        var quoted = false;
        for (var i = 0; i < text.Length; i++)
        {
            var character = text[i];
            if (quoted)
            {
                if (character == '"' && i + 1 < text.Length && text[i + 1] == '"')
                {
                    cell.Append('"');
                    i++;
                }
                else if (character == '"') quoted = false;
                else cell.Append(character);
            }
            else if (character == '"') quoted = true;
            else if (character == ',')
            {
                row.Add(cell.ToString());
                cell.Clear();
            }
            else if (character == '\n')
            {
                row.Add(cell.ToString().TrimEnd('\r'));
                cell.Clear();
                rows.Add(row);
                row = [];
            }
            else cell.Append(character);
        }
        if (cell.Length > 0 || row.Count > 0)
        {
            row.Add(cell.ToString());
            rows.Add(row);
        }
        return rows;
    }
}
