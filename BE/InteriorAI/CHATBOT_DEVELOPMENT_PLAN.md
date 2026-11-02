# Kế hoạch phát triển AI Interior Design Copilot sử dụng RAG

**Ngày rà soát mã nguồn ban đầu:** 18/09/2026
**Cập nhật triển khai:** 16/10/2026
**Trạng thái:** Kế hoạch triển khai; các phần chưa hoàn thành được ghi rõ bên dưới.

**Cập nhật kỹ thuật sau mốc đầu:** Backend Development đã đọc kho Markdown bản nháp, tra cứu theo từ khóa, nhận diện một số câu hỏi nối tiếp và dẫn ID đoạn truy hồi. Gemini có thể tạo câu trả lời từ các đoạn này khi bật `Chat__GeminiEnabled=true`, nhưng mặc định tắt; ID đoạn trả về được server kiểm tra. Đây **chưa phải RAG ngữ nghĩa** vì chưa có embedding/ingestion và tập tài liệu approved; chưa hoàn thành Design Brief, xác thực public hay job ảnh bền vững.

## 0. Đánh giá mức độ phù hợp và phần đã bắt đầu

Hướng **Interior Design Copilot + RAG** phù hợp với Home AI vì có thể dùng chung dữ liệu phong cách, luồng tạo ảnh và Gallery hiện có. Thứ tự triển khai cần tách hai đường: hội thoại tư vấn bằng text có thể xây trước; chatbot chỉ được gọi tạo ảnh sau khi có Design Brief xác nhận và image job bền vững.

Đã triển khai ở mốc khởi đầu:

- Backend có `ChatConversation`, `ChatMessage`, migration `AddChatConversations` và API tạo/liệt kê hội thoại, đọc/gửi tin nhắn. Tin nhắn có `clientMessageId` để retry không tạo bản ghi trùng; dữ liệu được lọc theo owner trong phạm vi định danh hiện tại.
- Migration đã được áp dụng lên SQL Server `InteriorAI` cục bộ trên máy phát triển. Máy hoặc database khác cần chạy `dotnet ef database update` trước khi sử dụng.
- Android bản debug có màn tư vấn text, New Chat, chọn lại hội thoại và tải lịch sử sau khi mở app. Luồng tạo ảnh từ ảnh cũ vẫn truy cập được bằng nút **Tạo ảnh**.
- Đã sửa contract Android của endpoint tạo ảnh cũ để đọc `DesignResponse` đúng với backend và không cho gửi prompt rỗng.
- Ở mốc khởi đầu, backend chỉ phản hồi từ danh sách phong cách đã seed. Các bước bổ sung sau mốc này được ghi ở phần cập nhật kỹ thuật bên trên.

Giới hạn an toàn hiện tại: API hội thoại mới chỉ mở trong môi trường `Development` và chỉ nhận kết nối loopback (điện thoại USB có thể dùng `adb reverse tcp:5207 tcp:5207`), vì dự án vẫn dùng `user-id` do client tự gửi. Không triển khai API này ra Internet trước khi có danh tính/token do server cấp và kiểm tra quyền sở hữu bằng claim. Bản release Android chưa hiển thị màn tư vấn mới.

Mốc tiếp theo: hoàn thiện xác thực và image job bền vững; sau đó thêm attachment, Design Brief có version, bước xác nhận và liên kết `DesignResult`. Song song chuẩn bị tài liệu có nguồn và bộ câu hỏi đánh giá để tích hợp RAG.

Để thử mốc đầu trên điện thoại USB: chạy backend bằng `dotnet run`, chạy `adb reverse tcp:5207 tcp:5207`, đặt địa chỉ API trong bản Android debug là `http://127.0.0.1:5207/`, rồi mở tab Chat và hỏi “Phong cách Scandinavian phù hợp với phòng nào?”. Nếu dùng database khác máy đang phát triển, chạy `dotnet ef database update` trước. API tư vấn hiện chỉ nhận loopback; kết nối trực tiếp qua IP Wi-Fi/LAN sẽ trả `404` cho đến khi có xác thực phù hợp.

## 1. Mục tiêu

Phát triển tab **Chat** thành một trợ lý thiết kế nội thất đa lượt, hỗ trợ tiếng Việt, ảnh căn phòng và RAG. Trợ lý tập trung vào ba nhiệm vụ:

- Tư vấn phong cách, màu sắc, ánh sáng, vật liệu và bố trí dựa trên kho tri thức có nguồn.
- Thu thập yêu cầu thành một **Design Brief** có cấu trúc.
- Chỉ tạo ảnh AI khi người dùng xác nhận, tránh tạo ảnh ngoài ý muốn hoặc tốn credit không cần thiết.
- Theo dõi kết quả trong cùng cuộc hội thoại và dùng chung `designId` với Gallery.
- Lưu lịch sử để người dùng quay lại tiếp tục tư vấn.

Đây là **Interior Design Copilot**, không phải chatbot tổng quát.

Nếu tên đề tài tiếp tục ghi **“chatbot tư vấn dựa trên kỹ thuật RAG”**, RAG là đầu ra bắt buộc. Chỉ đưa `StyleAesthetic` hoặc `RoomStylePrompt` vào prompt chưa được xem là RAG vì chưa có ingestion, embedding, semantic retrieval, citation và đánh giá.

## 2. Hiện trạng dự án

Trước mốc 16/10, tab Chat chỉ là luồng một lần: người dùng gửi prompt kèm ảnh, backend tạo `DesignResult`, gọi NanoBanana và client polling kết quả. Mốc đầu tiên đã bổ sung nhánh hội thoại text có lưu lịch sử; phần tạo ảnh vẫn là luồng cũ.

Các điểm được đối chiếu với mã nguồn mới nhất:

- `POST /api/design/chat` vẫn yêu cầu prompt và ảnh, được giữ cho nhánh tạo ảnh cũ. API hội thoại text mới là `/api/v1/chat/conversations`.
- Nhánh tạo ảnh đã chặn prompt rỗng; Android đọc đúng `DesignResponse` của backend.
- Tin nhắn tư vấn text được lưu trong `ChatConversation`/`ChatMessage` và tải lại khi mở app. Tin nhắn của nhánh tạo ảnh cũ vẫn chỉ nằm trong ViewModel; ảnh kết quả xem lại ở Gallery.
- **New Chat** đã hoạt động cho tư vấn text. Chưa có Design Brief, citation, action card, LLM text service hoặc RAG.
- Client hiện polling tối đa khoảng 5 phút và cho kiểm tra tiếp cùng `designId`; cần giữ hành vi này khi tách API chat mới.
- Job tạo ảnh đang chạy bằng `Task.Run`, nên có nguy cơ kẹt nếu API restart.
- `providerTaskId` chỉ sống trong tiến trình đang chạy; restart có thể dẫn tới mất khả năng tiếp tục polling hoặc submit lại tác vụ tốn credit.
- `user-id` do client tự gửi chưa phải authentication an toàn cho môi trường public.

## 3. Luồng trải nghiệm đích

```text
Người dùng nhập câu hỏi hoặc gửi ảnh
        ↓
Backend lấy conversation + brief hiện tại
        ↓
RAG truy hồi tri thức nếu đây là câu hỏi tư vấn
        ↓
LLM trả lời có cấu trúc; server validate và cập nhật brief
        ↓
Nếu cần tạo ảnh, bot hỏi từng trường còn thiếu
        ↓
Người dùng xem và xác nhận brief
        ↓
Backend tạo image job bền vững và trả designId
        ↓
Bot hiển thị Pending / Completed / Failed; Gallery dùng cùng designId
```

Màn hình Chat ban đầu nên có ba hành động nhanh:

- Hỏi tư vấn nội thất.
- Tạo thiết kế từ ảnh.
- Kiểm tra thiết kế đang xử lý.

Mỗi lượt bot chỉ nên hỏi một thông tin quan trọng còn thiếu và ưu tiên chip/card thay vì bắt người dùng tự viết prompt dài.

## 4. Phạm vi MVP

| Use case | Ví dụ | Hành động |
|---|---|---|
| `interior_advice` | "Phòng nhỏ hợp phong cách nào?" | Tư vấn text bằng RAG, có citation, chưa tạo ảnh. |
| `generate_design` | "Làm lại phòng khách hiện đại và giữ cửa sổ" | Thu ảnh, room type, style, constraint; hiện brief và chờ xác nhận. |
| `project_status` | "Ảnh của tôi xong chưa?" | Kiểm tra đúng `DesignResult` và mở Gallery. |

Không nằm trong MVP:

- Báo giá thi công chính xác.
- Tư vấn kết cấu, điện nước, cấp phép xây dựng.
- Shopping list hoặc giá sản phẩm thời gian thực.
- Tự động tạo ảnh mà không có bước xác nhận.
- Voice, AR/3D, marketplace, visual analysis phức tạp, variation nhiều cấp và dashboard quản trị.
- Streaming token, WebSocket/SSE và một vector database riêng ở giai đoạn đầu.

## 5. Design Brief

Bot không nên chỉ ghép raw prompt. Nó phải duy trì một object có cấu trúc để tránh quên yêu cầu của người dùng.

```json
{
  "intent": "generate_design",
  "sourceAttachmentId": "attachment_123",
  "roomType": "living_room",
  "featureId": "interior_design",
  "styleId": 4,
  "customRequirements": [
    "tông be ấm",
    "giữ nguyên cửa sổ",
    "ưu tiên an toàn cho trẻ nhỏ"
  ],
  "mustPreserve": ["layout", "window_positions", "camera_perspective"],
  "mustAvoid": ["màu quá tối", "quá nhiều đồ"],
  "status": "awaiting_confirmation",
  "version": 3
}
```

Quy ước duy nhất là `mustPreserve`, không dùng lẫn với `mustKeep`. `model` và `resolution` không phải trường nghiệp vụ chính của brief; chúng nằm trong **Tùy chọn nâng cao**, lấy từ capability/allow-list của server.

Thông tin tối thiểu để generate trong MVP:

| Tác vụ | Bắt buộc | Tùy chọn |
|---|---|---|
| Tư vấn | Câu hỏi text | Room type, style và brief hiện tại |
| Tạo thiết kế | Ảnh thuộc đúng owner, loại phòng, style, brief version mới nhất và xác nhận riêng | Màu, công năng, đồ cần giữ/tránh |
| Kiểm tra kết quả | `designId` thuộc đúng owner | Mở Gallery/Before-After |

## 6. Kiến trúc backend

```text
ChatController
    ↓
ChatOrchestrator
 ├── ConversationRepository
 ├── IChatModelService
 ├── DesignBriefValidator
 ├── IRetrievalService
 │    ├── StyleAesthetic + RoomStylePrompt
 │    └── KnowledgeDocument + KnowledgeChunk
 ├── IDesignPromptService
 └── DesignJobService
        ↓
   Durable DB queue + BackgroundService
        ↓
   DesignManager + image generation + image storage
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
  "reply": "Phong cách Scandinavian sẽ phù hợp với không gian này.",
  "intent": "redesign_room",
  "missingFields": ["styleId"],
  "briefPatch": {
    "roomType": "living_room",
    "featureId": "interior_design",
    "mustPreserve": ["window_positions", "camera_angle"]
  },
  "nextAction": "select_style",
  "usedChunkIds": ["chunk_18"]
}
```

Backend phải validate toàn bộ giá trị trước khi lưu hoặc tạo ảnh. LLM không được:

- Tự gọi NanoBanana hoặc provider khác.
- Tự truy cập SQL, URL bất kỳ hoặc dữ liệu của người dùng khác.
- Tự tạo `styleId`, `roomType`, `model` hoặc `resolution` không thuộc allow-list.
- Bỏ qua bước xác nhận trước action tốn credit.

`StyleAesthetic` và `RoomStylePrompt` là structured context và nguồn cấu hình prompt, không phải semantic RAG. `IDesignPromptService` vẫn là nơi biên dịch prompt cuối cùng cho image model; không truyền nguyên văn output LLM sang provider ảnh.

## 7. Dữ liệu và migration

Giữ `DesignResult` là nguồn dữ liệu chuẩn cho Gallery. Thêm các entity sau:

| Entity | Trường chính | Vai trò |
|---|---|---|
| `ChatConversation` | `Id`, `UserId`, `Title`, `Status`, `CurrentBriefJson`, `BriefVersion`, `LastMessageAt`, timestamps | Conversation và brief hiện tại. |
| `ChatMessage` | `Id`, `ConversationId`, `ClientMessageId`, `Role`, `Type`, `Content`, `Status`, `MetadataJson`, `DesignId`, `Sequence`, timestamps | Message bền vững, citation/action được trả qua metadata/DTO. |
| `ChatAttachment` | `Id`, `ConversationId`, `OwnerUserId`, `Url`, `MimeType`, `ByteSize`, timestamps | Metadata ảnh đã upload; phù hợp storage hiện trả URL. |
| `DesignJob` | `Id`, `DesignId`, `ClientRequestId`, `Status`, `ProviderTaskId`, `AttemptCount`, `NextAttemptAt`, `LeaseUntil`, `LastError` | Queue tạo ảnh bền vững. |
| `KnowledgeDocument` | `Id`, `Title`, `SourceUrl`, `Publisher`, `LicenseNote`, `Version`, `ContentHash`, `Status` | Nguồn tri thức có version. |
| `KnowledgeChunk` | `Id`, `DocumentId`, `Content`, `Embedding`, `RoomType`, `StyleId`, `Topic`, `ChunkIndex` | Đơn vị semantic retrieval và citation. |

Chưa tạo `DesignBriefRevision` và `AssistantAction` table trong MVP. Chỉ bổ sung khi có yêu cầu đã chứng minh; action card trước mắt là DTO sinh từ state đã validate.

Mở rộng `DesignResult` bằng các trường nullable:

```text
ConversationId
SourceMessageId
Operation
```

Index nên có:

```text
ChatConversations(UserId, Status, LastMessageAt DESC)
ChatMessages(ConversationId, Sequence) UNIQUE
ChatMessages(ConversationId, ClientMessageId) UNIQUE WHERE ClientMessageId IS NOT NULL
ChatAttachments(OwnerUserId, ConversationId)
DesignJobs(ClientRequestId) UNIQUE
DesignJobs(Status, NextAttemptAt)
KnowledgeChunks(DocumentId, ChunkIndex) UNIQUE
```

`ClientMessageId` chỉ áp dụng cho message do client gửi; assistant message có thể để null, vì vậy index phải là filtered unique index trên SQL Server.

### 7.1. Vòng đời job ảnh bền vững

Chỉ thêm bảng queue là chưa đủ. Interface provider cần tách thành:

```text
SubmitAsync(...) → providerTaskId
GetStatusAsync(providerTaskId) → Pending / Completed / Failed
```

Worker phải lưu `ProviderTaskId` ngay sau khi provider nhận job, trước khi polling. Sau restart, worker tiếp tục kiểm tra task cũ thay vì submit lại một lần tạo ảnh có thể tốn credit. Dùng lease/lock, retry có backoff, giới hạn attempt và `ClientRequestId` unique để chống xử lý song song hoặc double-click.

## 8. API v1 đề xuất

Giữ endpoint cũ `/api/design/chat` trong giai đoạn chuyển đổi nhưng tài liệu hóa nó là **legacy image-generation endpoint**, không tiếp tục mở rộng thành conversational API. Thêm namespace riêng:

```text
POST   /api/v1/chat/conversations
GET    /api/v1/chat/conversations?page=1&pageSize=20
GET    /api/v1/chat/conversations/{id}
GET    /api/v1/chat/conversations/{id}/messages?before=&pageSize=30
POST   /api/v1/chat/attachments
POST   /api/v1/chat/conversations/{id}/messages
POST   /api/v1/chat/conversations/{id}/generate
GET    /api/design/status/{designId}
```

Ví dụ gửi message:

```json
{
  "clientMessageId": "uuid-from-android",
  "content": "Tôi muốn phòng khách ấm và tối giản.",
  "attachmentIds": ["asset_123"],
  "locale": "vi-VN"
}
```

`ClientMessageId` phải idempotent: retry cùng ID nhận lại cùng message, không tạo bản ghi thứ hai. MVP trả text chat đồng bộ bằng `200 OK`; chỉ image generation dùng queue và polling.

Ví dụ response text:

```json
{
  "assistantMessage": {
    "id": "message_2",
    "content": "Bạn nên ưu tiên tông sáng...",
    "status": "completed",
    "citations": [
      {
        "documentId": "doc_4",
        "chunkId": "chunk_18",
        "title": "Nguyên tắc phối màu cho phòng nhỏ",
        "sourceUrl": "https://example.org/source"
      }
    ]
  },
  "currentBrief": {
    "version": 2,
    "status": "collecting"
  }
}
```

`POST .../{id}/generate` nhận `clientRequestId`, `briefVersion` và `confirmed: true`; trả `202 Accepted` gồm `designId`, `status` và `pollUrl` hiện có. Nó chỉ chạy khi brief hợp lệ và tạo một `DesignResult` liên kết với conversation/message.

API attachment phải validate nội dung ảnh, dung lượng và MIME; kiểm tra owner; dọn attachment không dùng. Bổ sung đường xử lý `CreateDesignFromStoredImageAsync` để dùng ảnh đã lưu thay vì bắt Android upload lại sau bước xác nhận.

## 9. Thay đổi Android

Tạo `ChatApi` và `ChatRepository` riêng, tái sử dụng `ApiClient`, `ImageCompressor` và cơ chế endpoint ổn định. Không mở rộng tiếp `DesignRepository` để gánh cả hội thoại.

`ChatViewModel` cần quản lý:

```text
conversationId
conversations
messages
draft
selectedAttachment
currentDesignBrief
isSending
isLoadingHistory
generationJobs
citations/actions
one-shot UI effects + pagination/error state
```

UI cần bổ sung:

- New Chat tạo conversation thật.
- Danh sách conversation cũ.
- Message status: sending, queued, processing, completed, failed.
- Action chips cho room type, style, xác nhận brief và retry.
- Card review brief trước khi tạo ảnh.
- Card nguồn tham khảo; card trạng thái kết quả và mở Before/After/Gallery.
- Khôi phục lịch sử từ server khi mở lại app.
- Text-only dùng được; chỉ yêu cầu ảnh khi người dùng đi tới action generate.
- Dùng durable message ID, stable key và chặn double tap trên action tốn credit.

Model và resolution nên nằm trong phần **Tùy chọn nâng cao**; server có default an toàn và allow-list hợp lệ.

Server là nguồn lịch sử chuẩn. `SavedStateHandle` chỉ giữ `conversationId` và draft cần thiết; chưa cần Room, Hilt, Paging, WorkManager, WebSocket hoặc SSE cho MVP. Message dùng URL/attachment ID từ server làm tham chiếu bền vững, không dùng local `Uri` làm dữ liệu lịch sử.

## 10. Thiết kế RAG phù hợp với đồ án

### 10.1. Phạm vi kho tri thức

- 30–50 tài liệu có nguồn, tác giả/nhà xuất bản và quyền sử dụng rõ ràng.
- Khoảng 150–400 chunk tiếng Việt.
- Nội dung ưu tiên: sáu style đang có trong seed, 4–6 loại phòng, phối màu, ánh sáng, vật liệu, bố trí, công năng và lưu ý cho không gian nhỏ.
- Không đưa ảnh phòng, hội thoại hoặc dữ liệu cá nhân của người dùng vào knowledge base chung.
- Không ingest dữ liệu báo giá biến động, tư vấn kết cấu/điện nước hoặc tài liệu pháp lý không kiểm soát.

### 10.2. Pipeline ingestion

```text
Nguồn được duyệt
   ↓
Chuẩn hóa text + source/license metadata
   ↓
Chia chunk theo heading, khoảng 300–600 token, overlap 50–100 token
   ↓
Tạo embedding theo batch
   ↓
Lưu document/chunk/version/content hash
   ↓
Chạy bộ kiểm tra retrieval trước khi publish corpus
```

Script ingestion phải chạy lặp lại được: `ContentHash` không đổi thì không tạo embedding lại; tài liệu đổi version phải vô hiệu hóa chunk cũ.

Với vài trăm chunk, MVP có thể lưu vector đã chuẩn hóa trong SQL Server dưới dạng dữ liệu tuần tự hóa, nạp cache và tính cosine similarity trong C#. Cách này giảm hạ tầng cho đồ án nhưng phải ghi rõ giới hạn hiệu năng. Chỉ chuyển sang Qdrant/pgvector khi corpus đủ lớn để chứng minh nhu cầu.

### 10.3. Retrieval, citation và fallback

1. Ghép câu hỏi hiện tại với `roomType`, `styleId` và constraint chính trong brief.
2. Lọc metadata khi room/style đã rõ.
3. Semantic retrieval lấy top-k ban đầu; dùng `k = 4–6` rồi hiệu chỉnh bằng tập đánh giá.
4. Bỏ chunk dưới ngưỡng liên quan và nội dung trùng.
5. Ghép structured context từ `StyleAesthetic`/`RoomStylePrompt` với chunk truy hồi.
6. Yêu cầu LLM trả `usedChunkIds`; server chỉ chấp nhận ID thuộc retrieval set của lượt đó.
7. Map `chunkId` về title/source URL để hiển thị citation.
8. Nếu không có nguồn đủ liên quan, bot nói rõ chưa đủ căn cứ và hỏi thêm thông tin; không bịa nguồn.

Chống prompt injection:

- System instruction và schema do server sở hữu.
- Nội dung retrieval được coi là dữ liệu tham khảo, không phải instruction.
- Chỉ ingest nguồn allow-list, có source/version.
- LLM không được gọi SQL, URL tùy ý hoặc image provider.
- Generate là endpoint riêng có confirmation, brief version và validator.

## 11. Roadmap triển khai

| Tuần | Công việc chính | Definition of Done |
|---|---|---|
| 1 | Chốt schema/API; sửa prompt rỗng và response legacy; đổi endpoint cũ thành image generation; allow-list và chuẩn hóa error/log; chốt auth cho demo/public. | Contract cũ rõ ràng, test input chính chạy được và không mô tả nó là chatbot. |
| 2 | Tạo `DesignJob`; tách submit/poll provider; lưu `ProviderTaskId`; worker resume, lease/retry và idempotency. | Restart API không làm mất hoặc submit trùng job trong integration test. |
| 3 | Migration conversation/message/attachment/current brief; API create/list/load/send; ownership. | Đóng/mở client vẫn tải đúng conversation của owner. |
| 4 | Android `ChatApi`/`ChatRepository`, New Chat, history, guided brief, attachment và confirm generate. | Hoàn tất text → brief → confirm → cùng `designId` trong Chat/Gallery. |
| 5 | `IChatModelService`, structured output, validator, giới hạn history/summary và lỗi fallback. | Bot cập nhật đúng brief khi người dùng bổ sung/đổi ý và không tự generate. |
| 6 | Corpus, ingestion, embedding, retrieval, citation và fallback thiếu căn cứ. | 30–50 tài liệu có version; RAG hoạt động end-to-end. |
| 7 | Bộ đánh giá RAG; test ownership, retry, process death, prompt injection; đo latency/chi phí. | Có bảng so sánh no-RAG/RAG và các lỗi chính đã được xử lý. |
| 8 | Sửa lỗi cuối, deploy môi trường demo, báo cáo, slide, video và diễn tập. | Kịch bản chính và phương án dự phòng đã được kiểm thử trên điện thoại thật. |

Corpus và ground-truth nên được chuẩn bị song song từ tuần 2; không chờ tới tuần 6 mới bắt đầu tìm nguồn. Nếu chỉ còn 4–5 tuần, cắt variation, visual analysis, dashboard, admin và streaming trước; không cắt ba use case chính hoặc RAG nếu tên đề tài đã cam kết.

### Yêu cầu nền trước khi public backend

1. Thay `user-id` tự khai bằng access token server-issued và lấy owner từ claim.
2. Áp ownership cho conversation, message, attachment và design.
3. Rate limit chat/upload/generate; quota token/image.
4. Không log API key, token, ảnh URL nhạy cảm, raw prompt không cần thiết hoặc response provider chưa lọc.
5. Có retention/xóa conversation/attachment và không commit secret vào Git.
6. Nếu chỉ demo local bằng device ID, ghi rõ đây không phải authentication production.

## 12. Failure states bắt buộc

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
| Prompt injection | Từ chối ngắn gọn, không thực thi tool/action tùy ý |
| Retrieval không đủ liên quan | Nói rõ chưa đủ căn cứ, không bịa câu trả lời hoặc citation |
| LLM trả sai schema | Không lưu patch chưa validate; retry giới hạn hoặc trả fallback an toàn |
| Double tap/Retry POST | Trả lại kết quả của cùng client ID, không tạo message/job mới |
| Backend restart | Worker tiếp tục poll bằng `ProviderTaskId` đã lưu, không submit lại |

## 13. Kiểm thử và đánh giá

### 13.1. Bộ test chức năng

Backend cần có test cho conversation/message pagination, ownership, idempotency, brief version conflict, generate chưa confirm, LLM sai schema/timeout, citation ngoài retrieval set và worker resume sau restart. Android cần test text-only, New Chat, history reload, guided brief, double tap confirm, mất mạng, endpoint đổi, ảnh hỏng/quá lớn; bổ sung MockWebServer contract test và Compose UI test cho flow chính.

### 13.2. Bộ đánh giá RAG cố định

Chuẩn bị khoảng 60 câu tiếng Việt:

| Nhóm | Số lượng gợi ý | Mục đích |
|---|---:|---|
| Có đáp án trong corpus | 35 | Retrieval và grounded answer |
| Nhiều lượt/đổi yêu cầu | 10 | Dùng brief và ngữ cảnh |
| Ngoài phạm vi/không có dữ liệu | 10 | Từ chối đúng, không bịa |
| Prompt injection | 5 | Ranh giới instruction/action |

Mỗi câu answerable có ground-truth document/chunk. So sánh **cùng model, cùng temperature** ở hai chế độ no-RAG và RAG.

Chỉ số báo cáo:

- Retrieval `Hit@5` hoặc `Recall@5`.
- Citation validity và citation precision.
- Groundedness/faithfulness theo rubric 0–2.
- Answer relevance/helpfulness theo rubric 0–2.
- Tỷ lệ hallucination và từ chối đúng.
- Task completion cho ba use case chính.
- P50/P95 latency text, tỷ lệ image job thành công, chi phí token/image.
- Số duplicate message/job trong test retry/double-click.
- SUS với nhóm người dùng nhỏ nếu có điều kiện.

Mục tiêu nội bộ ban đầu, hiệu chỉnh sau khi có baseline:

```text
Hit@5 trên tập answerable >= 80%
Citation thuộc retrieval set = 100%
Từ chối đúng trên tập out-of-scope >= 80%
Hoàn tất ba kịch bản chính >= 90%
Duplicate image job trong test idempotency = 0
Mất lịch sử sau đóng/mở app = 0
```

Không dùng retention 7 ngày hoặc tỷ lệ feedback 70% làm KPI chính khi chưa có đủ người dùng thực tế.

## 14. Tiêu chí hoàn thành và thứ tự ưu tiên

Thứ tự ưu tiên khuyến nghị:

1. Sửa prompt rỗng và response contract hiện tại; đổi tên/document endpoint cũ là image generation.
2. Hoàn thiện durable image job + `ProviderTaskId` + idempotency trước khi chatbot được phép gọi generate.
3. Tạo conversation/message/current brief và Guided Chat có bước xác nhận.
4. Thêm LLM structured output sau khi schema/validator ổn định.
5. Hoàn thiện corpus, retrieval, citation và đánh giá RAG.
6. Hoàn thiện auth/ownership trước khi public backend; trong demo local phải ghi rõ giới hạn device ID.

Checklist kết thúc:

- [ ] Text-only hoạt động mà không cần ảnh.
- [ ] Conversation/message còn nguyên sau khi đóng/mở ứng dụng.
- [ ] Người dùng đổi yêu cầu thì brief cập nhật đúng version.
- [ ] Không tạo ảnh trước hành động xác nhận riêng.
- [ ] Retry/double-click không tạo message hoặc image job trùng.
- [ ] API restart không làm mất job; worker tiếp tục bằng `ProviderTaskId` đã lưu.
- [ ] Chat và Gallery dùng cùng một `designId`.
- [ ] Citation trỏ tới document/chunk tồn tại và hỗ trợ câu trả lời.
- [ ] Khi không có bằng chứng, bot nói rõ không đủ căn cứ.
- [ ] User A không truy cập được conversation, attachment hoặc design của user B.
- [ ] Có bảng so sánh no-RAG và RAG trên cùng bộ câu hỏi.
- [ ] Không có API key/secret hoặc dữ liệu nhạy cảm trong Git và log.

Điểm kết thúc phù hợp cho đồ án: người dùng hỏi tư vấn nội thất bằng tiếng Việt và xem nguồn; tiếp tục hội thoại để hình thành brief có cấu trúc; xác nhận tạo ảnh; theo dõi cùng kết quả trong Chat và Gallery; mở lại app vẫn còn lịch sử; báo cáo có số liệu so sánh RAG với no-RAG trên tập kiểm thử cố định.

## 15. Rủi ro và kịch bản demo

| Rủi ro | Giảm thiểu |
|---|---|
| Provider tạo ảnh chậm/hết credit | Preflight health/quota; lỗi rõ ràng; chuẩn bị conversation/kết quả mẫu đã lưu và ghi rõ là dữ liệu demo. |
| Mạng trường không ổn định | Deploy backend HTTPS hoặc tunnel đã kiểm thử; có hotspot/USB fallback; không hard-code IP LAN. |
| RAG truy hồi sai | Corpus nhỏ, metadata, threshold và fallback “chưa đủ căn cứ”; chạy test trước mỗi bản demo. |
| LLM trả sai schema | Structured output, retry giới hạn, validator và fallback an toàn. |
| Trễ tiến độ | Giữ ba use case; cắt variation, streaming, dashboard, visual analysis và admin trước. |
| Nguồn không rõ quyền sử dụng | Lưu source/license note, ưu tiên tài liệu được phép trích dẫn và chỉ hiển thị trích đoạn ngắn. |

Kịch bản demo chính:

1. Hỏi text-only về màu/phong cách cho phòng nhỏ và mở nguồn trích dẫn.
2. Yêu cầu thiết kế phòng khách, bổ sung ảnh, chọn style bằng chip và sửa một constraint.
3. Xem brief, xác nhận tạo ảnh đúng một lần, theo dõi trạng thái rồi mở cùng kết quả ở Gallery.
4. Đóng/mở ứng dụng và tiếp tục đúng conversation.
5. Hỏi câu ngoài knowledge base để chứng minh bot không bịa thông tin.
