# Kế hoạch phát triển AI Interior Design Copilot

## 1. Mục tiêu

Phát triển tab **Chat** thành một trợ lý thiết kế nội thất đa lượt, hỗ trợ tiếng Việt và ảnh căn phòng. Trợ lý phải có thể:

- Tư vấn phong cách, màu sắc và ý tưởng bố trí.
- Thu thập yêu cầu thành một **Design Brief** có cấu trúc.
- Chỉ tạo ảnh AI khi người dùng xác nhận, tránh tạo ảnh ngoài ý muốn hoặc tốn credit không cần thiết.
- Hiển thị tiến trình, kết quả và các phiên bản chỉnh sửa trong cùng cuộc hội thoại.
- Lưu lịch sử để người dùng quay lại tiếp tục tư vấn hoặc chỉnh ảnh cũ.

Đây là **Interior Design Copilot**, không phải chatbot tổng quát.

## 2. Hiện trạng dự án

Tab Chat hiện tại là luồng một lần: người dùng gửi prompt kèm ảnh, backend tạo một `DesignResult`, gọi NanoBanana và client polling kết quả. Nó chưa phải chatbot đa lượt.

Các điểm cần khắc phục trước:

- `POST /api/design/chat` yêu cầu ảnh, trong khi UI cho phép nhập text không ảnh.
- Android chờ `ChatResponse`, nhưng backend hiện trả `DesignResponse`.
- Tin nhắn chỉ sống trong `ChatUiState`; đóng app hoặc tạo ViewModel mới sẽ mất lịch sử.
- Chưa có session, memory, brief, action card hoặc LLM text service.
- Client Chat polling khoảng 2 phút, trong khi backend có thể xử lý ảnh đến 5 phút.
- Job tạo ảnh đang chạy bằng `Task.Run`, nên có nguy cơ kẹt nếu API restart.

## 3. Luồng trải nghiệm đích

```text
Người dùng nhập yêu cầu hoặc gửi ảnh
        ↓
Bot nhận diện intent và hỏi thông tin còn thiếu
        ↓
Bot cập nhật Design Brief có cấu trúc
        ↓
Người dùng xem và xác nhận brief
        ↓
Backend tạo DesignResult qua pipeline hiện có
        ↓
Bot hiển thị trạng thái Pending / Completed / Failed
        ↓
Người dùng xem Before/After, tạo variation hoặc chỉnh tiếp
```

Màn hình Chat ban đầu nên có các hành động nhanh:

- Thiết kế lại căn phòng.
- Bố trí phòng trống.
- Xóa nội thất.
- Tư vấn phong cách phù hợp.

Mỗi lượt bot chỉ nên hỏi một thông tin quan trọng còn thiếu và ưu tiên chip/card thay vì bắt người dùng tự viết prompt dài.

## 4. Phạm vi MVP

| Intent | Ví dụ | Hành động |
|---|---|---|
| `recommend_style` | "Phòng nhỏ hợp phong cách nào?" | Tư vấn text, chưa tạo ảnh |
| `redesign_room` | "Làm lại phòng khách hiện đại" | Thu ảnh, room type, style và yêu cầu |
| `furnish_empty_room` | "Bố trí phòng trống này" | Dùng `featureId = furnish_empty_room` |
| `remove_furniture` | "Dọn hết đồ trong ảnh" | Dùng `featureId = remove_furniture` |
| `edit_existing_design` | "Đổi sofa render vừa tạo sang màu be" | Tạo variation từ render cũ |
| `project_status` | "Ảnh của tôi xong chưa?" | Kiểm tra `DesignResult` |

Không nằm trong MVP:

- Báo giá thi công chính xác.
- Tư vấn kết cấu, điện nước, cấp phép xây dựng.
- Shopping list hoặc giá sản phẩm thời gian thực.
- Tự động tạo ảnh mà không có bước xác nhận.

## 5. Design Brief

Bot không nên chỉ ghép raw prompt. Nó phải duy trì một object có cấu trúc để tránh quên yêu cầu của người dùng.

```json
{
  "intent": "redesign_room",
  "sourceImageId": "asset_123",
  "baseDesignId": null,
  "roomType": "living_room",
  "featureId": "interior_design",
  "styleId": 4,
  "customRequirements": [
    "tone be am",
    "giu nguyen cua so",
    "uu tien an toan cho tre nho"
  ],
  "mustPreserve": ["layout", "window_positions", "camera_perspective"],
  "mustAvoid": ["mau qua toi", "qua nhieu do"],
  "model": "generate-pro",
  "resolution": "2K",
  "status": "awaiting_confirmation"
}
```

Thông tin tối thiểu để generate:

| Tác vụ | Bắt buộc | Tùy chọn |
|---|---|---|
| Thiết kế lại | Ảnh, loại phòng, style | Màu, công năng, đồ cần giữ/tránh |
| Bố trí phòng trống | Ảnh, loại phòng, style | Số người dùng, lưu trữ, ngân sách |
| Xóa nội thất | Ảnh | Vật dụng cần giữ lại |
| Chỉnh render cũ | `baseDesignId` hoặc ảnh nguồn, yêu cầu mới | Style/màu mới |

## 6. Kiến trúc backend

```text
ChatController
    ↓
ChatOrchestrator
 ├── ConversationRepository
 ├── IChatModelService
 ├── DesignBriefValidator
 ├── IDesignPromptService
 └── DesignManager
        ↓
   Image generation + image storage
```

Thêm abstraction cho model hội thoại:

```csharp
public interface IChatModelService
{
    Task<ChatAssistantResponse> ReplyAsync(ChatContext context);
}
```

LLM chỉ được phép trả structured output. Ví dụ:

```json
{
  "reply": "Phong cách Scandinavian se phu hop voi khong gian nay.",
  "intent": "redesign_room",
  "missingFields": ["styleId"],
  "briefPatch": {
    "roomType": "living_room",
    "featureId": "interior_design",
    "mustKeep": ["window_positions", "camera_angle"]
  },
  "nextAction": "select_style"
}
```

Backend phải validate toàn bộ giá trị trước khi lưu hoặc tạo ảnh. LLM không được:

- Tự gọi NanoBanana hoặc provider khác.
- Tự truy cập SQL, URL bất kỳ hoặc dữ liệu của người dùng khác.
- Tự tạo `styleId`, `roomType`, `model` hoặc `resolution` không thuộc allow-list.
- Bỏ qua bước xác nhận trước action tốn credit.

`StyleAesthetic` và `RoomStylePrompt` hiện có là nguồn kiến thức chuẩn cho style/prompt. `IDesignPromptService` vẫn là nơi biên dịch prompt cuối cùng cho image model.

## 7. Dữ liệu và migration

Giữ `DesignResult` là nguồn dữ liệu chuẩn cho Gallery. Thêm các entity sau:

| Entity | Trường chính | Vai trò |
|---|---|---|
| `ChatConversation` | `Id`, `UserId`, `Title`, `Status`, timestamps | Một cuộc hội thoại |
| `ChatMessage` | `Id`, `ConversationId`, `Role`, `Content`, `Status`, `Sequence`, timestamps | Tin user, bot hoặc tool |
| `ChatAttachment` | `Id`, `MessageId`, `StorageKey`, `MimeType`, `ByteSize` | Ảnh đính kèm |
| `DesignBriefRevision` | `Id`, `ConversationId`, `Revision`, field brief, `Status` | Lịch sử thay đổi brief |
| `AssistantAction` | `Id`, `MessageId`, `Type`, `PayloadJson`, `RequiresConfirmation`, `Status` | Chip/card thao tác |
| `AiJob` | `Id`, `Type`, `Status`, `AttemptCount`, `NextAttemptAt`, `LastError` | Job chat và image bền vững |

Mở rộng `DesignResult` bằng các trường nullable:

```text
ConversationId
SourceMessageId
ParentDesignId
Operation
```

Index nên có:

```text
ChatConversations(UserId, Status, LastMessageAt DESC)
ChatMessages(ConversationId, Sequence) UNIQUE
ChatMessages(ConversationId, ClientMessageId) UNIQUE
ChatAttachments(MessageId)
AiJobs(Status, NextAttemptAt)
```

## 8. API v1 đề xuất

Không đổi nghĩa endpoint cũ `/api/design/chat` ngay; giữ tương thích trong giai đoạn chuyển đổi. Thêm namespace mới:

```text
POST   /api/v1/conversations
GET    /api/v1/conversations
GET    /api/v1/conversations/{id}/messages
POST   /api/v1/conversations/{id}/messages
POST   /api/v1/conversations/{id}/actions/{actionId}/execute
POST   /api/v1/messages/{messageId}/cancel
POST   /api/v1/messages/{messageId}/feedback
```

Ví dụ gửi message:

```json
{
  "clientMessageId": "uuid-from-android",
  "content": "Toi muon phong khach am va toi gian.",
  "attachmentIds": ["asset_123"],
  "locale": "vi-VN"
}
```

Nên trả `202 Accepted` khi reply AI đang xếp hàng:

```json
{
  "userMessageId": "message_1",
  "assistantMessageId": "message_2",
  "status": "queued",
  "pollUrl": "/api/v1/messages/message_2"
}
```

Action `generate_design` chỉ được execute sau confirm. Nó tạo một `DesignResult` liên kết với conversation/message và trả về `designId` hiện có để frontend dùng lại polling.

## 9. Thay đổi Android

Tạo `ChatRepository` riêng, tái sử dụng `ApiClient`, `ImageCompressor` và `LocalUserIdProvider`.

`ChatViewModel` cần quản lý:

```text
conversationId
messages
draft
selectedImage
pendingDesignBrief
isSending
isLoadingHistory
generationJobs
one-shot UI effects
```

UI cần bổ sung:

- New Chat tạo conversation thật.
- Danh sách conversation cũ.
- Message status: sending, queued, processing, completed, failed.
- Action chips cho room type, style, xác nhận brief và retry.
- Card review brief trước khi tạo ảnh.
- Card Before/After, tạo variation và mở Gallery.
- Khôi phục lịch sử từ server khi mở lại app.

Model và resolution nên nằm trong phần **Tùy chọn nâng cao**; server có default an toàn và allow-list hợp lệ.

## 10. Roadmap triển khai

### Giai đoạn 0 — Ổn định luồng hiện có (3–5 ngày)

1. Chuẩn hóa response giữa Android và backend cho endpoint chat cũ.
2. Không cho UI gửi text-only vào endpoint bắt buộc ảnh; hoặc chuyển text-only sang API chat mới.
3. Đồng bộ polling client tối thiểu với thời gian xử lý backend hoặc dùng status job bền vững.
4. Thêm Retry, Cancel và lỗi rõ ràng trên message bubble.
5. Đổi tên/document endpoint cũ là image-generation, không gọi nó là conversational chat.

**Acceptance criteria:** text-only không gây lỗi kỹ thuật; job pending không timeout sai; lỗi provider hiển thị được cho user.

### Giai đoạn 1 — Guided Design Chat MVP (1 sprint)

1. Tạo migration cho conversation, message, attachment và brief.
2. Xây API lưu/tải conversation theo owner.
3. Làm state machine + quick replies, chưa bắt buộc dùng LLM.
4. Thu ảnh, room type, style, constraint; tạo brief card.
5. Xác nhận brief rồi gọi lại `POST /api/design/analyze`.
6. Đồng bộ result vào Chat và Gallery.

**Acceptance criteria:** user hoàn tất flow tạo design từ chat, đóng/mở app vẫn thấy conversation và kết quả.

### Giai đoạn 2 — AI Copilot đa lượt (1–2 sprint)

1. Thêm `IChatModelService` và provider implementation ở backend.
2. Dùng structured output/schema cho `reply`, `briefPatch`, `missingFields`, `suggestions`, `actions`.
3. Dùng `StyleAesthetic`/`RoomStylePrompt` làm context có kiểm soát.
4. Gửi context giới hạn: brief hiện tại, summary, 10–20 message gần nhất.
5. Viết test corpus tiếng Việt: mơ hồ, đổi ý, thiếu ảnh, prompt injection và lỗi mạng.

**Acceptance criteria:** bot hiểu yêu cầu như “giữ sofa xanh nhưng làm phòng sáng hơn”, cập nhật brief đúng và không tự generate.

### Giai đoạn 3 — Visual Chat và variation (1 sprint)

1. Phân tích ảnh để gợi ý room type/style, nhưng luôn yêu cầu user xác nhận.
2. Liên kết `DesignResult` với assistant message chờ kết quả.
3. Hỗ trợ chỉnh render bằng `ParentDesignId`.
4. Chuyển tạo ảnh từ `Task.Run` sang queue lưu database + worker/hosted service.
5. Bảo đảm retry hoặc double-click không tạo job ảnh trùng.

**Acceptance criteria:** kết quả chat và Gallery cùng dùng một `designId`; mỗi variation giữ được lịch sử cha-con; restart API không làm mất job.

### Giai đoạn 4 — Bảo mật, chất lượng và beta (1 sprint)

1. Thay `user-id` header bằng auth server-issued trước khi public.
2. Áp ownership check trên conversation, attachment và design.
3. Thêm rate limit, quota token/image, idempotency và audit log.
4. Không log API key, token, ảnh URL nhạy cảm hoặc raw prompt không cần thiết.
5. Thiết kế chính sách retention/xóa conversation/attachment.
6. Thêm feature flag, dashboard và feedback thumbs up/down.

**Acceptance criteria:** user A không thể đọc/sửa dữ liệu user B; không có action generate không confirm; số liệu về success, latency, cost và feedback được ghi nhận.

## 11. Failure states bắt buộc

| Tình huống | Hành vi cần có |
|---|---|
| Text không có ảnh | Cho phép tư vấn, chỉ yêu cầu ảnh khi cần generate |
| Ảnh lỗi/quá 10 MB | Báo lý do rõ ràng, giữ nguyên brief |
| Ảnh tối/mờ | Cảnh báo và cho phép chọn ảnh khác hoặc tiếp tục |
| Thiếu style/room type | Hiện chip chọn nhanh, không trả lỗi kỹ thuật |
| Mạng lỗi | Lưu draft, Retry với `clientMessageId` |
| Provider hết credit | Báo minh bạch, không polling vô hạn |
| Job xử lý lâu | Hiện “Bạn có thể quay lại sau”, tiếp tục theo dõi bền vững |
| Job failed | Retry cùng brief, chỉnh brief hoặc chọn ảnh khác |
| Kết quả chưa đúng | Tạo variation, không ghi đè ảnh cũ |
| Prompt injection | Từ chối ngắn gọn, không thực thi tool/action tùy ý |

## 12. Chỉ số thành công

Theo dõi tối thiểu:

- Tỷ lệ bắt đầu chat và tỷ lệ quay lại trong 7 ngày.
- Tỷ lệ hoàn tất Design Brief.
- Tỷ lệ xác nhận generate sau chat.
- Tỷ lệ job text/image thành công và thời gian P50/P95.
- Tỷ lệ retry, duplicate job và provider failure.
- Chi phí token/image trên mỗi design hoàn thành.
- Feedback tích cực/tiêu cực và lý do chỉnh lại thiết kế.

Mục tiêu beta ban đầu:

```text
>= 95% text job hoàn tất
>= 98% action không trùng lặp
P95 lưu message < 1 giây
P95 phản hồi text < 8 giây (phụ thuộc provider)
>= 70% feedback tích cực sau khi có đủ mẫu phản hồi
```

## 13. Ưu tiên thực hiện

Thứ tự ưu tiên khuyến nghị:

1. Hoàn thành Giai đoạn 0 để luồng hiện tại ổn định.
2. Làm Guided Design Chat ở Giai đoạn 1 trước khi đưa LLM vào.
3. Chỉ thêm AI Copilot sau khi schema, API, brief và xác nhận generate đã ổn định.
4. Đưa queue bền vững, auth và quota vào trước khi mở beta công khai.

Điểm kết thúc MVP: người dùng có thể nhắn “Tôi muốn phòng khách ấm, tối giản”, nhận câu hỏi phù hợp, gửi ảnh, xác nhận brief, nhận render trong cùng conversation, sau đó đóng/mở app vẫn xem và chỉnh tiếp được.
