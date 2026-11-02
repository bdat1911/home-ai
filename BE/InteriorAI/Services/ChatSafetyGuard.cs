namespace InteriorAI.Services;

public sealed record GuardDecision(string Category, string Reply);

/// <summary>
/// Deterministic refusals that run before retrieval and the LLM, so they hold when the LLM is off.
/// </summary>
public static class ChatSafetyGuard
{
    private static readonly string[] Manipulation =
    [
        "bia nguon", "nguon gia", "bo qua tai lieu", "bo qua huong dan", "bo qua chi dan",
        "bo qua quy tac", "bo qua moi", "bo qua cac", "ignore previous", "ignore all", "ignore your",
        "system prompt", "prompt he thong", "chi dan he thong", "tiet lo chi dan", "tiet lo prompt",
        "in ra prompt", "api key", "khoa api", "connection string"
    ];

    private static readonly string[] Pricing =
    [
        "bao gia", "gia thi cong", "chi phi thi cong", "gia hom nay", "het bao nhieu tien"
    ];

    private static readonly string[] Construction =
    [
        "pha tuong", "dap tuong", "di day dien", "di ong nuoc", "chong tham", "ket cau nha",
        "ket cau tuong", "ket cau cong trinh", "ket cau chiu luc", "tuong chiu luc",
        "cot chiu luc", "dam chiu luc", "tai trong"
    ];

    // "Kết cấu" also means surface texture in interior writing, so on its own it is not a refusal.
    private static readonly string[] ConstructionRisk =
    [
        "an toan", "chiu luc", "sap do", "nut tuong", "pha do", "duc tuong", "cat dam", "cat cot"
    ];

    private static readonly string[] Medical =
    [
        "chua mat ngu", "chua benh", "tri benh", "tri mat ngu", "phong thuy chua"
    ];

    public static GuardDecision? Evaluate(string message)
    {
        var normalized = KnowledgeBaseService.Normalize(message);
        if (ContainsAny(normalized, Manipulation))
            return new GuardDecision("manipulation",
                "Tôi không thể bịa nguồn, bỏ qua giới hạn của tài liệu hay tiết lộ cấu hình nội bộ. Bạn có thể hỏi về phong cách, màu sắc hoặc cách bố trí một loại phòng cụ thể.");
        if (ContainsAny(normalized, Pricing))
            return new GuardDecision("pricing",
                "Kho tài liệu chưa có giá thi công hiện tại. Bạn cần cung cấp hiện trạng, vật liệu và địa điểm để đơn vị thi công khảo sát, báo giá.");
        if (ContainsAny(normalized, Construction) ||
            (KnowledgeBaseService.ContainsPhrase(normalized, "ket cau") && ContainsAny(normalized, ConstructionRisk)))
            return new GuardDecision("construction",
                "Tôi không thể kết luận về kết cấu, điện hoặc chống thấm từ ảnh và tài liệu tư vấn nội thất. Hãy nhờ người có chuyên môn khảo sát hiện trạng trước khi thi công.");
        if (ContainsAny(normalized, Medical))
            return new GuardDecision("medical",
                "Màu sắc và phong cách nội thất không phải phương pháp điều trị. Tôi có thể gợi ý phối màu theo sở thích, nhưng không thể khẳng định tác dụng chữa bệnh.");
        return null;
    }

    private static bool ContainsAny(string text, string[] phrases) =>
        phrases.Any(phrase => KnowledgeBaseService.ContainsPhrase(text, phrase));
}
