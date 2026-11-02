using InteriorAI.Services;

namespace InteriorAI.Tests;

public class InteriorAdvicePlannerTests
{
    [Fact]
    public void NewestPreferenceWinsOverOlderOne()
    {
        var reply = InteriorAdvicePlanner.Recommend(
            "Thôi tôi thích gỗ sáng hơn, hợp phong cách nào?",
            [ChatTurn.User("Tôi thích gỗ trầm"), ChatTurn.Assistant("Bạn có thể thử Indochine.")]);

        Assert.Contains("Scandinavian", reply);
        Assert.DoesNotContain("Indochine", reply);
    }

    [Fact]
    public void NegatedPreferenceIsIgnored()
    {
        var reply = InteriorAdvicePlanner.Recommend(
            "Tôi không thích gỗ trầm, muốn gỗ sáng, hợp phong cách nào?", []);

        Assert.Contains("Scandinavian", reply);
    }

    [Fact]
    public void StyleNamedByAssistantCanBeReferredTo()
    {
        var answer = InteriorAdvicePlanner.StyleAndRoom(
            "Phòng cách đó dùng cho phòng khách được không? phong cách đó",
            [ChatTurn.User("Tôi thích không gian sáng"), ChatTurn.Assistant("Bạn có thể thử Scandinavian.")],
            []);

        Assert.NotNull(answer);
        Assert.Contains("Scandinavian", answer!.Text);
    }

    [Fact]
    public void BriefTakesRoomAndChosenStyleFromMessage()
    {
        var patch = InteriorAdvicePlanner.ExtractBrief("Tôi muốn làm phòng khách kiểu Indochine", []);

        Assert.Equal("living_room", patch?.RoomType);
        Assert.Equal("Indochine", patch?.StyleName);
    }

    [Fact]
    public void BriefIgnoresRejectedStyle()
    {
        var patch = InteriorAdvicePlanner.ExtractBrief("Tôi không thích Industrial", []);

        Assert.Null(patch);
    }

    [Fact]
    public void BriefDoesNotPickOneOfTwoComparedStyles()
    {
        var patch = InteriorAdvicePlanner.ExtractBrief("Indochine khác Scandinavian thế nào?", []);

        Assert.Null(patch);
    }

    [Fact]
    public void BriefResolvesAgreementToAssistantSuggestion()
    {
        var patch = InteriorAdvicePlanner.ExtractBrief(
            "Được, chốt phong cách đó", [ChatTurn.Assistant("Bạn có thể thử Neoclassic.")]);

        Assert.Equal("Neoclassic", patch?.StyleName);
    }

    [Fact]
    public void BriefTakesTheNewestRoomWhenUserMovesOn()
    {
        var patch = InteriorAdvicePlanner.ExtractBrief("Thôi từ phòng ngủ chuyển sang phòng bếp", []);

        Assert.Equal("kitchen", patch?.RoomType);
    }

    [Fact]
    public void BriefIgnoresRejectedRoom()
    {
        var patch = InteriorAdvicePlanner.ExtractBrief("Phòng bếp, không phải phòng ngủ", []);

        Assert.Equal("kitchen", patch?.RoomType);
    }

    [Fact]
    public void ActiveRoomPrefersMessageThenBriefThenHistory()
    {
        ChatTurn[] history = [ChatTurn.User("cho phòng tắm")];

        Assert.Equal("kitchen", InteriorAdvicePlanner.ActiveRoom("còn bếp", history, "living_room"));
        Assert.Equal("living_room", InteriorAdvicePlanner.ActiveRoom("màu nào đẹp", history, "living_room"));
        Assert.Equal("bathroom", InteriorAdvicePlanner.ActiveRoom("màu nào đẹp", history, null));
        Assert.Null(InteriorAdvicePlanner.ActiveRoom("màu nào đẹp", [], null));
    }

    [Fact]
    public void ActiveRoomIgnoresRoomsTheAssistantMentioned()
    {
        ChatTurn[] history = [ChatTurn.Assistant("Bạn có thể tham khảo phòng ngủ.")];

        Assert.Null(InteriorAdvicePlanner.ActiveRoom("màu nào đẹp", history, null));
    }

    [Fact]
    public void FallbackAnswerDropsExamplesAboutOtherRooms()
    {
        var match = new KnowledgeMatch("principle-lighting-01", "principle-lighting-01#goi-y-1", "Ánh sáng",
            "Bếp cần cân nhắc ánh sáng trên mặt thao tác. Phòng ngủ có thể muốn điều chỉnh ánh sáng khi nghỉ.",
            null, true, 1);

        var answer = InteriorAdvicePlanner.FromMatch(match, "kitchen");

        Assert.Contains("mặt thao tác", answer.Text);
        Assert.DoesNotContain("Phòng ngủ", answer.Text);
    }

    [Fact]
    public void FallbackAnswerDoesNotExposeInternalWording()
    {
        var match = new KnowledgeMatch("room-living-01", "room-living-01#goi-y-1", "Phòng khách",
            "Seed phòng khách dùng nền sáng. Nếu người dùng muốn ấm hơn, thử thêm thảm. Prompt ảnh không xác nhận kích thước.",
            null, true, 1);

        var answer = InteriorAdvicePlanner.FromMatch(match);

        Assert.DoesNotContain("Seed", answer.Text);
        Assert.DoesNotContain("Prompt", answer.Text);
        Assert.DoesNotContain("người dùng", answer.Text);
        Assert.Contains("Nếu bạn muốn ấm hơn", answer.Text);
    }

    [Fact]
    public void FallbackAnswerAsksTheDocumentsClarifyingQuestion()
    {
        var match = new KnowledgeMatch("room-living-01", "room-living-01#goi-y-1", "Phòng khách",
            "Bố trí sofa theo lối đi chính.", "Phòng rộng bao nhiêu?", true, 1);

        var answer = InteriorAdvicePlanner.FromMatch(match);

        Assert.Contains("Phòng rộng bao nhiêu?", answer.Text);
        Assert.Same(match, answer.Source);
    }
}
