namespace InteriorAI.Services;

public sealed record PlannedAnswer(string Text, KnowledgeMatch? Source = null);

/// <summary>
/// Small deterministic guide used when answer generation is unavailable.
/// Suggestions describe Home AI presets; they do not infer someone's taste from personality alone.
/// </summary>
public static class InteriorAdvicePlanner
{
    private static readonly (string Name, string[] Aliases, string Description)[] Styles =
    [
        ("Indochine", ["indochine", "dong duong"], "gỗ sẫm, nền sáng và những điểm nhấn ấm"),
        ("Minimalist Luxury", ["minimalist luxury", "toi gian sang trong"], "bố cục gọn, màu trung tính và vật liệu tinh tế"),
        ("Modern Luxury", ["modern luxury", "hien dai sang trong"], "hình khối hiện đại, nền kem và điểm nhấn sang trọng"),
        ("Industrial", ["industrial", "cong nghiep"], "gạch, kim loại, gỗ mộc và bề mặt giàu kết cấu"),
        ("Neoclassic", ["neoclassic", "tan co dien"], "bố cục cân đối, đường nét cổ điển và tông kem ấm"),
        ("Scandinavian", ["scandinavian", "bac au"], "gỗ sáng, nền sáng và cảm giác ấm cúng")
    ];

    private static readonly (string Code, string Label, string[] Aliases)[] Rooms =
    [
        ("living_room", "phòng khách", ["phong khach"]),
        ("master_bedroom", "phòng ngủ", ["phong ngu"]),
        ("kitchen", "phòng bếp", ["phong bep", "nha bep", "bep"]),
        ("bathroom", "phòng tắm", ["phong tam", "nha tam", "phong ve sinh"]),
        ("study_room", "phòng làm việc", ["phong lam viec", "phong hoc", "goc lam viec"])
    ];

    private static readonly (string[] Phrases, string Style, string Reason)[] PreferenceRules =
    [
        (["go tram", "go sam", "go toi", "mau tram", "tong tram"], "Indochine", "gỗ sẫm và bảng màu ấm"),
        (["go sang", "go nhat", "bac au", "sang thoang", "am cung"], "Scandinavian", "gỗ sáng và cảm giác thoáng, ấm cúng"),
        (["ca tinh", "gach tho", "thep", "kim loai", "loft"], "Industrial", "chất liệu thô và điểm nhấn cá tính"),
        (["co dien", "tan co dien", "doi xung", "phao chi"], "Neoclassic", "đường nét cân đối và vẻ cổ điển nhẹ"),
        (["toi gian", "it do", "gon gang", "tinh te"], "Minimalist Luxury", "sự gọn gàng và vật liệu tinh tế"),
        (["hien dai", "sang trong", "bong", "khach san"], "Modern Luxury", "bố cục hiện đại và điểm nhấn sang trọng")
    ];

    private static readonly string[] NegationPrefixes =
        ["khong thich ", "khong muon ", "khong dung ", "khong can ", "tranh ", "bo ", "ghet ", "khong phai "];

    public static string? Recommend(string question, IReadOnlyList<ChatTurn> history)
    {
        var current = KnowledgeBaseService.Normalize(question);
        if (!ContainsAny(current, "phong cach nao", "phong cach gi", "hop voi phong cach",
                "phu hop voi phong cach", "goi y phong cach", "nen chon phong cach", "hop phong cach"))
            return null;

        // An explicit preset is handled by the document-backed style/room answer instead.
        if (FindStyle(current) != null) return null;

        var userTexts = history.Where(turn => turn.IsUser).TakeLast(3)
            .Select(turn => KnowledgeBaseService.Normalize(turn.Content))
            .Append(current)
            .ToArray();
        var room = FindRoom(current) ?? userTexts.Reverse().Select(FindRoom).FirstOrDefault(code => code != null);
        var roomText = room == null ? null : Rooms.First(item => item.Code == room).Label;

        // The newest stated preference wins, so a user who changes their mind is not held to the old one.
        foreach (var text in userTexts.Reverse())
        {
            var preference = LatestPreference(text);
            if (preference != null) return Result(preference.Value.Style, preference.Value.Reason, roomText);
            if (ContainsAny(text, "xanh lam", "xanh duong", "xanh navy"))
                return roomText == null
                    ? "Nếu thích xanh lam, bạn có thể thử Scandinavian với gỗ sáng hoặc Modern Luxury với nền trung tính và điểm nhấn xanh. Bạn muốn dùng cho phòng nào và thích xanh nhạt hay navy?"
                    : $"Với {roomText}, bạn có thể thử Scandinavian nếu thích không gian sáng hoặc Modern Luxury nếu muốn vẻ sang trọng hơn. Bạn muốn xanh nhạt hay navy? Gửi ảnh phòng để mình thiết kế thử khi bạn chọn được phong cách.";
        }

        return roomText == null
            ? "Bạn thích không gian sáng hay trầm, ít đồ hay nhiều chi tiết? Cho mình biết thêm một chút để gợi ý phong cách phù hợp."
            : $"Với {roomText}, bạn thích tông sáng hay trầm và muốn phong cách hiện đại hay cổ điển?";
    }

    public static PlannedAnswer? StyleAndRoom(
        string question, IReadOnlyList<ChatTurn> history, IReadOnlyList<KnowledgeMatch> matches)
    {
        var current = KnowledgeBaseService.Normalize(question);
        var requestedStyles = Styles.Where(item =>
            item.Aliases.Any(alias => ContainsPhrase(current, alias))).ToArray();
        if (requestedStyles.Length > 1)
            return new PlannedAnswer($"{requestedStyles[0].Name} thiên về {requestedStyles[0].Description}; " +
                   $"{requestedStyles[1].Name} thiên về {requestedStyles[1].Description}. " +
                   "Bạn muốn áp dụng cho phòng nào để mình gợi ý sát hơn?");
        var room = FindRoom(current);
        var explicitStyle = FindStyle(current);
        var style = explicitStyle;
        var refersToPreviousStyle = IsRoomFollowUp(current) ||
            ContainsAny(current, "phong cach do", "kieu do", "ban vua goi y");
        if (style == null && refersToPreviousStyle)
            style = FindStyleInHistory(history);
        if (style == null && room != null && history.Count > 0 && refersToPreviousStyle)
        {
            foreach (var turn in history.Where(turn => turn.IsUser).Reverse())
            {
                var preference = LatestPreference(KnowledgeBaseService.Normalize(turn.Content));
                if (preference == null) continue;
                style = Styles.First(item => item.Name == preference.Value.Style);
                break;
            }
        }
        if (style == null) return null;
        if (room == null)
        {
            if (explicitStyle == null && !ContainsAny(current, "phong cach do", "kieu do"))
                return null;
            return new PlannedAnswer($"{style.Value.Name} gợi {style.Value.Description}. Bạn muốn áp dụng cho phòng nào?");
        }

        var roomLabel = Rooms.First(item => item.Code == room).Label;
        var roomTitleKeyword = room == "kitchen" ? "bếp" : roomLabel;
        var passage = matches.FirstOrDefault(match =>
            match.DocumentId.StartsWith("style-room-", StringComparison.Ordinal) &&
            match.Title.Contains(style.Value.Name, StringComparison.OrdinalIgnoreCase) &&
            match.Title.Contains(roomTitleKeyword, StringComparison.OrdinalIgnoreCase));
        var quoted = passage == null ? "" : FirstSentences(passage.Passage, 300);
        var suggestion = quoted.Length == 0
            ? $"Với {roomLabel}, bạn có thể thử {style.Value.Name}: {style.Value.Description}."
            : quoted;
        return new PlannedAnswer(
            $"{suggestion} Gửi ảnh {roomLabel} hiện tại; sau khi bạn xác nhận, mình sẽ tạo phương án {style.Value.Name} từ ảnh.",
            passage);
    }

    public static PlannedAnswer FromMatch(KnowledgeMatch match, string? activeRoom = null)
    {
        var suggestion = FirstSentences(match.Passage, 330, activeRoom);
        if (suggestion.Length == 0)
            suggestion = $"Mình có tài liệu \"{match.Title}\" nhưng cần thêm thông tin để gợi ý sát hơn.";
        var question = !string.IsNullOrWhiteSpace(match.ClarifyingQuestion)
            ? FirstSentences(match.ClarifyingQuestion!, 140)
            : match.DocumentId.StartsWith("room-", StringComparison.Ordinal) ||
              match.DocumentId.StartsWith("style-room-", StringComparison.Ordinal)
                ? "Bạn muốn gửi ảnh phòng để tạo một phương án thiết kế thử không?"
                : "Bạn muốn áp dụng ý tưởng này cho phòng nào?";
        return new PlannedAnswer($"{suggestion} {question}", match);
    }

    /// <summary>The room under discussion: what this message names, else the brief, else the latest room the user named.</summary>
    public static string? ActiveRoom(string message, IReadOnlyList<ChatTurn> history, string? briefRoom)
    {
        var named = FindRoom(KnowledgeBaseService.Normalize(message));
        if (named != null) return named;
        if (briefRoom != null) return briefRoom;
        return history.Where(turn => turn.IsUser).Reverse()
            .Select(turn => FindRoom(KnowledgeBaseService.Normalize(turn.Content)))
            .FirstOrDefault(room => room != null);
    }

    public static string? RoomLabel(string? code) =>
        Rooms.FirstOrDefault(item => item.Code == code).Label;

    /// <summary>Reads only what the user chose, so a question about a preset does not overwrite a confirmed room.</summary>
    public static BriefPatch? ExtractBrief(string message, IReadOnlyList<ChatTurn> history)
    {
        var current = KnowledgeBaseService.Normalize(message);
        var room = FindRoom(current);

        string? style = null;
        var named = Styles.Where(item => item.Aliases.Any(alias =>
            ContainsPhrase(current, alias) && !IsNegated(current, alias))).ToArray();
        if (named.Length == 1) style = named[0].Name;
        else if (named.Length == 0 && ContainsAny(current, "phong cach do", "kieu do", "chot phong cach"))
            style = FindStyleInHistory(history)?.Name;

        return room == null && style == null ? null : new BriefPatch(room, style);
    }

    private static (string Style, string Reason)? LatestPreference(string text)
    {
        (string Style, string Reason)? best = null;
        var bestIndex = -1;
        foreach (var rule in PreferenceRules)
        {
            foreach (var phrase in rule.Phrases)
            {
                foreach (System.Text.RegularExpressions.Match match in
                         System.Text.RegularExpressions.Regex.Matches(text, PhrasePattern(phrase)))
                {
                    var before = text[..match.Index];
                    if (NegationPrefixes.Any(prefix => before.EndsWith(prefix, StringComparison.Ordinal))) continue;
                    if (match.Index <= bestIndex) continue;
                    bestIndex = match.Index;
                    best = (rule.Style, rule.Reason);
                }
            }
        }
        return best;
    }

    private static bool IsNegated(string text, string phrase)
    {
        var match = System.Text.RegularExpressions.Regex.Match(text, PhrasePattern(phrase));
        if (!match.Success) return false;
        var before = text[..match.Index];
        return NegationPrefixes.Any(prefix => before.EndsWith(prefix, StringComparison.Ordinal));
    }

    // The assistant is often the one who named the style that "phong cách đó" points to.
    private static (string Name, string[] Aliases, string Description)? FindStyleInHistory(IReadOnlyList<ChatTurn> history)
    {
        for (var i = history.Count - 1; i >= 0; i--)
        {
            var style = FindStyle(KnowledgeBaseService.Normalize(history[i].Content));
            if (style != null) return style;
        }
        return null;
    }

    private static string Result(string style, string reason, string? room)
    {
        var prefix = $"Bạn có thể thử {style}: phong cách này hợp với sở thích {reason} bạn vừa mô tả.";
        return room == null
            ? $"{prefix} Bạn muốn áp dụng cho phòng nào?"
            : $"{prefix} Với {room}, mình có thể gợi ý cách bố trí cụ thể. Gửi ảnh phòng hiện tại để mình tạo phương án {style} từ ảnh.";
    }

    private static (string Name, string[] Aliases, string Description)? FindStyle(string normalized)
    {
        (string Name, string[] Aliases, string Description)? best = null;
        var bestIndex = int.MaxValue;
        foreach (var item in Styles)
        {
            foreach (var alias in item.Aliases)
            {
                var match = System.Text.RegularExpressions.Regex.Match(normalized, PhrasePattern(alias));
                if (match.Success && match.Index < bestIndex)
                {
                    bestIndex = match.Index;
                    best = item;
                }
            }
        }
        return best;
    }

    // The newest room wins, so "từ phòng ngủ chuyển sang bếp" means the kitchen.
    private static string? FindRoom(string normalized)
    {
        string? latest = null;
        var latestIndex = -1;
        foreach (var room in Rooms)
        {
            foreach (var alias in room.Aliases)
            {
                foreach (System.Text.RegularExpressions.Match match in
                         System.Text.RegularExpressions.Regex.Matches(normalized, PhrasePattern(alias)))
                {
                    var before = normalized[..match.Index];
                    if (NegationPrefixes.Any(prefix => before.EndsWith(prefix, StringComparison.Ordinal))) continue;
                    if (match.Index <= latestIndex) continue;
                    latestIndex = match.Index;
                    latest = room.Code;
                }
            }
        }
        return latest;
    }

    private static bool NamesOtherRoom(string normalized, string activeRoom) =>
        Rooms.Any(room => room.Code != activeRoom &&
            room.Aliases.Any(alias => ContainsPhrase(normalized, alias)));

    private static bool ContainsAny(string text, params string[] phrases) =>
        phrases.Any(phrase => ContainsPhrase(text, phrase));

    private static bool IsRoomFollowUp(string normalized)
    {
        var cleaned = normalized.Trim().TrimEnd('?', '.', '!');
        return Rooms.Any(room => room.Aliases.Any(alias =>
            cleaned == alias || cleaned == $"con {alias}" || cleaned == $"vay {alias}" ||
            cleaned == $"{alias} thi sao" || cleaned == $"con {alias} thi sao"));
    }

    private static string PhrasePattern(string phrase) =>
        $"(?<![a-z0-9]){System.Text.RegularExpressions.Regex.Escape(phrase)}(?![a-z0-9])";

    private static bool ContainsPhrase(string text, string phrase) =>
        System.Text.RegularExpressions.Regex.IsMatch(text, PhrasePattern(phrase));

    // Articles are written for reviewers ("Seed...", "người dùng"), so direct quotes are reworded for the reader.
    // General articles give one example per room, and only the active room's example belongs in the answer.
    private static string FirstSentences(string passage, int maxLength, string? activeRoom = null)
    {
        var sentences = System.Text.RegularExpressions.Regex
            .Split(passage.Replace("**", "", StringComparison.Ordinal).Replace('\n', ' '), @"(?<=[.?!])\s+")
            .Where(sentence => !ContainsPhrase(KnowledgeBaseService.Normalize(sentence), "seed") &&
                               !ContainsPhrase(KnowledgeBaseService.Normalize(sentence), "preset") &&
                               !ContainsPhrase(KnowledgeBaseService.Normalize(sentence), "prompt") &&
                               (activeRoom == null || !NamesOtherRoom(KnowledgeBaseService.Normalize(sentence), activeRoom)))
            .Select(sentence => sentence.Replace("người dùng", "bạn", StringComparison.OrdinalIgnoreCase).Trim());
        var clean = string.Join(' ', sentences).Trim();
        if (clean.Length <= maxLength) return clean;
        var sentence = clean.LastIndexOfAny(['.', '?', '!'], maxLength - 1);
        return sentence > maxLength / 2 ? clean[..(sentence + 1)] : clean[..maxLength].TrimEnd() + "…";
    }
}
