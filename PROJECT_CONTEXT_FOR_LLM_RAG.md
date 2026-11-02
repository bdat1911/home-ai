# Home AI — hồ sơ ngữ cảnh dự án cho phát triển chatbot LLM + RAG

**Thời điểm đối chiếu:** 26/10/2026.  
**Mục đích:** Giúp người phát triển hoặc ChatGPT hiểu hệ thống *đang có gì*, dữ liệu nào đáng tin và chatbot LLM + RAG còn phải làm gì. Đây là tài liệu kỹ thuật tổng hợp, **không phải corpus tư vấn nội thất đã được duyệt** và không nên tự động đưa toàn bộ file này vào `knowledge-base/docs/`.

> **Cập nhật 01/11/2026:** phần chatbot đã thay đổi lớn sau thời điểm đối chiếu: đã có embedding, truy hồi kết hợp, citation, Design Brief và xác nhận tạo ảnh, đồng thời bỏ `POST /api/design/chat` và luồng tạo ảnh bằng prompt tự do. Các mục 5, 6, 7, 8, 10, 12 và 13 mô tả trạng thái cũ ở những điểm này. Xem `BE/InteriorAI/CHATBOT_RUNBOOK.md` cho trạng thái hiện tại.

> Quy ước đọc: **Hiện có** = thấy trong mã nguồn/tài liệu dữ liệu của worktree; **Bản thử nghiệm** = chỉ phù hợp Development; **Dự kiến** = mục tiêu trong kế hoạch, chưa có API/luồng chạy hoàn chỉnh. Khi tài liệu cũ mâu thuẫn với mã nguồn, kiểm tra mã nguồn và API thực tế trước; không suy từ tên màn hình hoặc tên đề tài rằng tính năng đã hoàn thành.

## 1. Đề tài, mục tiêu và phạm vi

Tên đề tài dự kiến: **“Nghiên cứu và xây dựng ứng dụng di động hỗ trợ thiết kế không gian sống từ ảnh bằng trí tuệ nhân tạo, tích hợp chatbot tư vấn dựa trên kỹ thuật RAG.”** Home AI là ứng dụng Android giúp người dùng tải ảnh phòng, chọn tác vụ/loại phòng/phong cách, nhận ảnh thiết kế minh họa từ AI, xem lại dự án trong Gallery và trao đổi về nội thất ở tab Chat.

Ứng dụng ưu tiên tiếng Việt cho phần tư vấn. Ảnh AI chỉ là **phương án tham khảo trực quan**; không phải bản vẽ kỹ thuật, cam kết giữ đúng kết cấu hiện trạng hay tư vấn thi công. Đề tài không huấn luyện LLM/mô hình tạo ảnh từ đầu, không có iOS/web trong phạm vi hiện tại.

Ba use case chatbot mong muốn, theo thứ tự hợp lý:

1. Trả lời câu hỏi về phong cách, loại phòng, phối màu, ánh sáng, vật liệu và cách dùng ứng dụng; kiểm tra nguồn nội bộ, hỏi làm rõ/từ chối khi thiếu căn cứ. Giao diện hiện chỉ hiển thị lời tư vấn, không hiển thị mã nguồn tài liệu.
2. Thu thập yêu cầu nhiều lượt thành **Design Brief** có cấu trúc; người dùng xem và xác nhận riêng trước khi tạo ảnh tốn chi phí.
3. Theo dõi `designId` đã tạo và mở cùng kết quả trong Chat/Gallery.

Use case 1 mới ở mức **bản thử nghiệm**. Android debug đã nối tư vấn → chọn phong cách/phòng/ảnh → xác nhận tạo thiết kế → xem trạng thái và ảnh ngay trong Chat; Design Brief và liên kết job với lịch sử hội thoại vẫn chưa được lưu bền vững.

## 2. Bản đồ hệ thống hiện có

```text
Android Kotlin / Jetpack Compose (FE/ReRoom)
  ├─ Interior / Exterior / Chat / Gallery / My Profile
  └─ Retrofit + OkHttp → ASP.NET Core API (BE/InteriorAI, .NET 8)
                          ├─ EF Core → SQL Server (người dùng, thiết kế, hội thoại, seed)
                          ├─ ImgBB → URL ảnh đầu vào và ảnh kết quả
                          ├─ NanoBanana → xử lý image-to-image
                          └─ Gemini tùy chọn → diễn đạt câu trả lời trên đoạn tài liệu truy hồi

knowledge-base/docs/*.md → KnowledgeBaseService (tra cứu từ khóa tại Development)
```

Không có vector database, bảng `KnowledgeDocument`/`KnowledgeChunk`, embedding hoặc worker hàng đợi ảnh bền vững trong mã hiện tại. `Gemini` cho Chat và `NanoBanana` cho ảnh là hai vai trò khác nhau.

| Vị trí | Trách nhiệm chính |
|---|---|
| `FE/ReRoom/` | APK Android, giao diện Compose, state bằng ViewModel/StateFlow, API Retrofit, ảnh bằng Coil. |
| `BE/InteriorAI/Controllers/` | REST API cho hồ sơ, thiết kế, Chat thử nghiệm, health. |
| `BE/InteriorAI/Services/` | Nghiệp vụ thiết kế, sinh prompt, lưu ảnh, gọi AI, hội thoại và tra cứu tài liệu. |
| `BE/InteriorAI/Data/` | `AppDbContext`, seed phong cách/phòng và migration EF Core. |
| `knowledge-base/docs/` | 55 bài tiếng Việt đang ở trạng thái nháp; đây mới là nội dung có thể duyệt để dùng cho RAG. |
| `knowledge-base/source-register.csv` | Sổ nguồn và trạng thái quyền sử dụng. |
| `knowledge-base/eval/` | Câu hỏi kiểm tra thử; chưa phải bộ đánh giá hoàn chỉnh. |

## 3. Chức năng Android đang thể hiện

Thanh điều hướng chính gồm **Interior, Exterior, Chat, Gallery, My Profile** (`AppNavigation.kt`).

| Khu vực | Hành vi thấy trong code | Giới hạn cần nhớ |
|---|---|---|
| Interior | Có `interior_design`, `furnish_empty_room`, `remove_furniture`. Với hai tác vụ đầu: chọn ảnh → loại phòng → style → gửi tạo ảnh. `remove_furniture` đi từ ảnh sang xử lý, không bắt chọn style. | Cần có ảnh hợp lệ; style được tải từ API. |
| Exterior | Có giao diện và các lựa chọn `exterior_design`, `facade`, `landscaping` (UI còn hiển thị chip “Add Greenery”). | Backend **chưa có prompt/pipeline ngoại thất riêng**; feature ID lạ bị chuẩn hóa về `interior_design`, `roomType=exterior` không có seed tương ứng nên thường dùng prompt style-only. Không mô tả đây là chức năng ngoại thất đã hoàn thiện. |
| Chat — tư vấn text | Bản debug mở `AdviceChatScreen`: hỏi sở thích, nhận gợi ý ngắn, chọn phong cách/phòng và gửi ảnh để tạo thiết kế qua API thiết kế hiện có. Ảnh kết quả hiển thị tạm trong Chat và lưu ở Gallery. | API Chat text chỉ ở Development + loopback; ảnh/job không được lưu vào lịch sử `ChatConversation`, chưa có Design Brief bền vững. |
| Chat — tạo ảnh từ prompt | `ChatScreen` còn luồng ảnh riêng: người dùng chọn ảnh + nhập prompt, mỗi lần gửi là một yêu cầu tạo `DesignResult`. | Không phải tiếp nối tự động của hội thoại tư vấn; không có bước review/confirm brief. |
| Gallery | Xem danh sách dự án theo trang, trạng thái, ảnh trước/sau, tải lại/tải thêm; xóa dự án có xác nhận trên UI. | Backend xóa mềm `DesignResult`; không bảo đảm xóa file ở ImgBB hay hủy job. |
| My Profile | Xem/sửa tên, avatar, số dự án/hoàn thành/đang xử lý. Debug có thẻ kiểm tra/lưu địa chỉ server. | Định danh hiện dựa trên ID lưu ở thiết bị, chưa phải tài khoản có token xác thực. |

Android giữ riêng trạng thái `Idle`, `Uploading`, `Processing`, `StatusUnavailable`, `SubmissionUncertain`, `Completed`, `Failed` cho luồng tạo ảnh. Khi đã có `designId`, nút kiểm tra lại chỉ gọi API status, không POST thêm. Nếu mất phản hồi của POST trước khi nhận `designId`, client không biết server đã nhận hay chưa; người dùng nên kiểm tra Gallery trước khi gửi lại.

## 4. Danh mục phong cách, phòng và tác vụ

`DesignStyleSeedData` tạo **6 phong cách**. ID số của style do database cấp, vì vậy chatbot/client phải lấy ID hợp lệ từ `/api/design/styles`, không đoán một số cố định.

| Style seed | Mô tả ngắn theo preset Home AI, không phải định nghĩa học thuật toàn diện |
|---|---|
| `Indochine` | Nền sáng, gỗ sẫm, nét cân đối, sắc xanh ngọc/vàng ấm và chi tiết gợi Đông Dương. |
| `Minimalist Luxury` | Gọn, sáng, nền trung tính/trắng-be, gỗ sáng, đá hoặc bề mặt mịn, ít chi tiết có chủ đích. |
| `Modern Luxury` | Hiện đại, sáng, kem/ngà/be, đá/kính/bề mặt bóng và điểm ánh kim vừa phải. |
| `Industrial` | Bề mặt thô, gạch, thép/kim loại, gỗ mộc, da, tông than/đen; có thể cân bằng bằng ánh sáng ấm. |
| `Neoclassic` | Cân đối, thanh lịch, kem/ngà/taupe, phào chỉ và vật liệu mềm; không bắt buộc trang trí cầu kỳ. |
| `Scandinavian` | Bắc Âu sáng và ấm: trắng/trắng ngà, gỗ sáng, vải mềm, pastel, công năng. |

`RoomStylePromptSeedData` hiện có **30 cặp = 6 style × 5 loại phòng**: `living_room` (phòng khách), `master_bedroom` (phòng ngủ chính), `kitchen` (bếp), `bathroom` (phòng tắm), `study_room` (phòng làm việc/học). Mỗi cặp seed có ánh sáng, vật liệu, màu, đồ nội thất, không khí, prompt template và điều cần tránh. Seeder chạy khi backend khởi động để thêm/cập nhật style và prompt phòng theo seed.

Android còn hiển thị `dining_room`, `kids_room`, `walk_in_closet` (tổng 8 loại phòng ở UI), nhưng ba loại này **không có cặp room–style trong seed hiện tại**. Backend có thể rơi về prompt theo style, không nên khẳng định có hướng dẫn phòng chuyên biệt hoặc tài liệu RAG tương ứng.

Backend hiện chuẩn hóa ba `featureId` nội thất: `interior_design` (mặc định), `furnish_empty_room`, `remove_furniture`. Đây là mã tác vụ tạo ảnh, không phải intent LLM tổng quát. Nếu nhận feature ID chưa nhận diện, service sinh prompt dùng mặc định `interior_design`; cần sửa/validate khi mở rộng.

**Lưu ý dữ liệu:** các tỷ lệ màu, vật liệu và câu chữ trong seed phục vụ **prompt hình ảnh**. Không chuyển chúng thành “quy chuẩn” phối màu, hướng dẫn thi công hoặc khẳng định an toàn. Corpus tư vấn đã diễn giải lại kèm điều kiện, giới hạn và nguồn.

## 5. Luồng tạo ảnh và lưu dự án hiện có

1. Android chọn/tối ưu ảnh, đăng ký/lấy device user ID nếu cần, gửi multipart `image`, `styleId` (trừ `remove_furniture`), `roomType`, `featureId`, `model`, `resolution` tới `POST /api/design/analyze` với header `user-id`.
2. `DesignPromptService` đọc `StyleAesthetic`/`RoomStylePrompt` để tạo prompt theo style + room + tác vụ; khi thiếu prompt phòng có thể dùng prompt style-only.
3. `DesignManager` kiểm tra ảnh (tối đa **10 MB**, giải mã bằng ImageSharp), tải ảnh lên ImgBB, lưu `DesignResult` trạng thái `pending` rồi trả `designId`, URL ảnh gốc và status. API hiện trả **200 OK**, không phải contract `202 Accepted` trong kế hoạch tương lai.
4. `Task.Run` trong tiến trình API gọi NanoBanana, tự polling kết quả provider rồi tải ảnh hoàn thành lên ImgBB; cập nhật `completed` hoặc `failed` trong SQL Server.
5. Android polling `GET /api/design/status/{designId}`; Gallery lấy `GET /api/design/projects` và lọc `IsDeleted=false`. Xóa chỉ gán `IsDeleted=true`.

`POST /api/design/chat` là **endpoint tạo ảnh legacy**: cần `Prompt` + `Image`, cũng tạo `DesignResult`; không phải endpoint hội thoại text. `POST /api/design/generate-design` là đường preview chỉ cho Development.

Chưa có bảng `DesignJob`, chưa lưu `providerTaskId` để resume sau restart, chưa có idempotency server cho việc tạo ảnh. API restart có thể làm mất tác vụ đang chạy. Không để LLM tự gọi image provider; khi xây luồng từ Chat sang tạo ảnh phải có brief hợp lệ, xác nhận riêng, kiểm tra owner và job bền vững.

## 6. REST API hiện có — không nhầm với API dự kiến

| Nhóm | Endpoint hiện có | Mục đích / lưu ý |
|---|---|---|
| Health | `GET /api/webhook/health` | Chỉ báo API truy cập được; không xác nhận DB/provider AI khỏe. |
| Auth | `POST /api/auth/register-device` | Tạo/lấy user theo ID do client gửi. |
| Auth | `GET /api/auth/profile/{userId}`; `PUT /api/auth/profile/{userId}` | Xem/sửa tên hiển thị. |
| Auth | `POST /api/auth/upload-avatar` | Upload avatar, giới hạn 5 MB, JPG/PNG/WEBP. |
| Auth | `GET /api/auth/profile/{userId}/summary` | Đếm dự án không bị xóa, `completed`, `pending`. |
| Design | `GET /api/design/styles` | Style seed đang được hỗ trợ, kèm `styleId`, tên và core aesthetic. |
| Design | `POST /api/design/analyze` | Tạo dự án ảnh từ ảnh/room/style/feature; trả `designId` và `pending`. |
| Design | `POST /api/design/chat` | Tạo ảnh từ prompt + ảnh; **không** lưu vào `ChatConversation`. |
| Design | `GET /api/design/status/{designId}` | Đọc trạng thái `pending/completed/failed`, ảnh và lỗi theo `user-id`. |
| Design | `GET /api/design/projects?page=&pageSize=` | Danh sách dự án của user, tối đa 50 mục/trang; response có `data`, `page`, `pageSize`, `total`, `totalPages`. |
| Design | `DELETE /api/design/{designId}` | Xóa mềm dự án sau khi kiểm tra user ID. |
| Chat text | `POST /api/v1/chat/conversations`; `GET /api/v1/chat/conversations` | Tạo/liệt kê conversation text. Tạo nhận `clientConversationId` UUID. |
| Chat text | `GET /api/v1/chat/conversations/{id}/messages` | Đọc tối đa 100 tin nhắn gần nhất của hội thoại thuộc user. |
| Chat text | `POST /api/v1/chat/conversations/{id}/messages` | Gửi `clientMessageId` UUID + `content` 1–2000 ký tự; trả user message và assistant message. |

Các endpoint Chat text chỉ trả dữ liệu khi **ASPNETCORE_ENVIRONMENT=Development và request tới từ loopback**. `user-id` là header do client tự khai; lọc theo owner hiện tại **không đồng nghĩa xác thực an toàn**. Chưa có endpoint chat attachment, brief, confirm generate hay project-status trong `/api/v1/chat`.

## 7. Dữ liệu và quan hệ đang lưu

`AppDbContext` dùng EF Core + SQL Server. Các bảng/DbSet đáng chú ý:

| Entity | Nội dung và vai trò |
|---|---|
| `User` | `Id` từ thiết bị/client, tên, URL avatar, timestamps. |
| `StyleAesthetic` | `StyleID`, `StyleName`, `CoreAesthetic`, `TechnicalSpecs`; cấu hình preset ảnh. |
| `RoomStylePrompt` | Một dòng duy nhất cho mỗi `StyleId + RoomTypeKey`; prompt, màu, vật liệu, ánh sáng, đồ, điều tránh. |
| `DesignResult` | Chủ sở hữu `UserId`, URL ảnh đầu vào/kết quả, `DesignPrompt`, `Model`, `Resolution`, `Status`, `ErrorMessage`, `IsDeleted`, timestamps. Đây là nguồn dữ liệu của Gallery. |
| `ChatConversation` | `Id`, `UserId`, tiêu đề và timestamps; chưa có `CurrentBriefJson`/`BriefVersion`. |
| `ChatMessage` | `Id`, `ConversationId`, `ClientMessageId`, `ReplyToMessageId`, `Role`, `Content`, `CreatedAt`; chưa có attachment/citation metadata dạng trường riêng. |
| `RoomInterior` | Entity cũ về phòng/style; không phải kho bài RAG. |

`ChatMessage` có unique index lọc trên `(ConversationId, ClientMessageId)` khi ID khác null. Retry cùng `clientMessageId` và cùng nội dung được trả lại tin nhắn đã lưu; dùng cùng ID cho nội dung khác bị từ chối. `ChatConversation`/`ChatMessage` được lấy theo `UserId` của hội thoại, nhưng ID đó vẫn do client gửi qua header.

Chưa có entity hoặc migration cho `ChatAttachment`, `DesignBrief`, `DesignJob`, `KnowledgeDocument`, `KnowledgeChunk`; những tên này trong kế hoạch là **đề xuất**, không được giả định đã có ở DB. `DesignResult` hiện chưa liên kết bằng `ConversationId` hoặc `SourceMessageId`.

## 8. Chatbot tư vấn text: thực tế đang chạy ở mức nào

Luồng hiện tại: Android `AdviceChatScreen` → `ChatApi` → `ChatController` → `ChatConversationService` → `ChatReplyService` → `KnowledgeBaseService` → Markdown. Hội thoại và tin nhắn được lưu SQL Server. Khi gửi câu mới, service lấy tối đa 3 tin nhắn **người dùng** gần nhất. Bộ tra cứu có quy tắc nhận diện một số câu nối tiếp (“còn…”, “thế…”, “màu đó…”) rồi bổ sung style/room/màu đã nhắc tới.

`KnowledgeBaseService` chuẩn hóa tiếng Việt không dấu, tra cứu từ khóa + metadata style/room/color/app, chọn tối đa 4 bài/đoạn cho `ChatReplyService`. ID đoạn dựa trên document ID + hash nội dung đoạn. Câu trả lời hiện ưu tiên gợi ý ngắn theo sở thích/phòng/phong cách và hỏi một bước tiếp theo; ID nguồn và thông báo bản nháp không được đưa vào bong bóng chat. Một số câu ngoài phạm vi (bịa nguồn, giá thi công chính xác, kết cấu/điện/chống thấm, chữa bệnh) được xử lý bằng quy tắc từ chối trước khi tra cứu.

`GeminiChatAnswerGenerator` đã được nối vào backend nhưng **tắt mặc định** (`Chat__GeminiEnabled` chưa bật). Khi bật trong Development, backend gửi câu hỏi, tối đa 3 tin nhắn người dùng gần nhất và các đoạn đã truy hồi tới Gemini; yêu cầu JSON `{ reply, usedChunkIds }`. Server kiểm tra ID đoạn ở nội bộ và chỉ trả nội dung gợi ý; lỗi mạng/JSON/ID không hợp lệ thì quay lại trả lời cục bộ. Key ở backend, không nằm trong Android. Việc bật dịch vụ ngoài có thể phát sinh chi phí và chuyển nội dung hỏi/tài liệu tới nhà cung cấp; không dùng dữ liệu nhạy cảm để thử.

Đây là **LLM tùy chọn + truy hồi từ khóa**, *không phải semantic RAG hoàn chỉnh*: chưa có embedding, vector similarity, ingestion/versioned chunk store, 60 câu đánh giá hay corpus approved. API Android chưa có citation metadata dạng trường riêng.

## 9. Kho tri thức nội thất hiện có

`knowledge-base/docs/` chứa **55 bài Markdown tiếng Việt, toàn bộ `status: draft`**:

| Thư mục | Số bài | Phạm vi |
|---|---:|---|
| `styles/` | 6 | Hồ sơ sáu phong cách seed. |
| `rooms/` | 5 | Phòng khách, ngủ chính, bếp, tắm, làm việc. |
| `style-room/` | 30 | Đủ 30 cặp phong cách–phòng có prompt seed; 15 bài mới được biên tập từ bộ hồ sơ nháp. |
| `colors/` | 5 | Xanh lam, xanh lá, trung tính ấm, xám/đen, pastel. |
| `principles/` | 5 | Ánh sáng; vật liệu/bảo trì; không gian nhỏ/lưu trữ; bố trí/công năng; giữ hiện trạng từ ảnh. |
| `app/` | 4 | Cách dùng thiết kế, Chat, Gallery, Profile/kết nối. |

15 bài `style-room/` cũ được giữ nguyên ID; 15 cặp còn thiếu đã được thêm với metadata chuẩn và trạng thái nháp. Bao phủ đủ cặp không đồng nghĩa nội dung đã được thẩm định chuyên môn.

ID bài hướng dẫn ứng dụng: `app-design-flow-01`, `app-chat-help-01`, `app-gallery-help-01`, `app-profile-connection-01`. ID bài màu: `color-blue-01`, `color-green-01`, `color-warm-neutrals-01`, `color-grey-black-01`, `color-pastel-01`. ID nguyên tắc: `principle-lighting-01`, `principle-materials-01`, `principle-small-space-01`, `principle-layout-01`, `principle-preserve-01`. Cần lấy nội dung và metadata thực tế từ file tương ứng khi trả lời, không dựa vào riêng danh sách ID này.

Mỗi bài có front matter gồm `id`, `title`, `document_type`, `styles`, `room_types`, `source_ids`, tác giả, reviewer, version, ngày duyệt, status; phần thân có gợi ý, điều cần hỏi thêm, giới hạn và nguồn. `source-register.csv` hiện có **13 nguồn đều `proposed`**, chưa nguồn nào `approved`. Không sửa `status` thành approved chỉ để bot trả lời; phải kiểm tra tính đúng, nguồn, quyền sử dụng, reviewer và ngày duyệt. Runtime ngoài Development chỉ nhận bài approved có nguồn approved và metadata duyệt hợp lệ, nhưng Chat API ngoài Development hiện vẫn bị khóa.

`knowledge-base/eval/smoke-questions.csv` có **15 câu nháp** với expected document/ý chính/điều cần hỏi hoặc từ chối; `BE/InteriorAI.KnowledgeSmoke/Program.cs` kiểm tra 16 câu truy hồi mẫu, độ bao phủ 30 cặp, khả năng tìm tiêu đề, câu nối tiếp và cổng draft. Đây chưa phải bộ đánh giá 60 câu đã duyệt hoặc kết quả đo chất lượng LLM.

**Nội dung nên đưa vào corpus RAG sau khi duyệt:** các bài `docs/` thuộc phạm vi tư vấn nội thất và hướng dẫn app, với version và nguồn. **Không ingest như tri thức chung:** prompt tạo ảnh thô, API key, connection string, ảnh/hội thoại riêng, dữ liệu cá nhân, giá thị trường thay đổi nhanh, chỉ dẫn kết cấu/điện/nước/pháp lý chưa có chuyên môn duyệt. File bối cảnh này giúp *người phát triển* hiểu sản phẩm; nó không thay cho từng bài tư vấn có nguồn.

## 10. Thiết kế LLM + RAG đích — CHƯA hoàn thành

Đây là hướng phát triển từ `CHATBOT_DEVELOPMENT_PLAN.md` và `CHATBOT_KNOWLEDGE_BASE_PLAN.md`, không phải mô tả API đang chạy:

```text
Tài liệu/nguồn được duyệt
  → chuẩn hóa metadata, version, content hash
  → chia đoạn theo heading, tạo embedding, lưu document/chunk
  → lọc theo style/room + truy hồi top-k + ngưỡng liên quan
  → LLM trả lời tiếng Việt có cấu trúc và usedChunkIds
  → server kiểm tra ID, quyền truy cập, gắn citation thật
  → lưu message; nếu thiếu căn cứ thì hỏi thêm hoặc từ chối
```

Mốc kỹ thuật đề xuất trong kế hoạch: 30–50 tài liệu có nguồn, khoảng 150–400 chunk; thử 300–600 token/chunk và overlap 50–100 token rồi hiệu chỉnh theo test; lấy top-k ban đầu khoảng 4–6. Có thể dùng SQL Server + cache/cosine ở quy mô nhỏ, không bắt buộc vector DB riêng trước khi đo. Chỉ dùng **approved** khi public. Tài liệu truy hồi là dữ liệu không đáng tin để thực thi lệnh; system instruction/schema/validator thuộc backend.

**Design Brief dự kiến** lưu mục đích tạo ảnh, `sourceAttachmentId`, `roomType`, `featureId`, `styleId`, yêu cầu màu/công năng, `mustPreserve`, `mustAvoid`, trạng thái và version. Luồng thử nghiệm hiện dùng ảnh người dùng chọn và style/room hiển thị trong Chat rồi gọi `POST /api/design/analyze` sau nút xác nhận. Đây chưa phải brief được lưu: chưa có attachment thuộc hội thoại, version brief, endpoint confirm riêng, liên kết `DesignResult` với conversation hoặc queue ảnh có thể resume.

Trước khi tuyên bố “chatbot dùng RAG” trong báo cáo: phải có pipeline embedding/truy hồi thật, citation kiểm chứng được tới chunk đã dùng, corpus approved và số liệu so sánh trên cùng tập câu hỏi. Kế hoạch đề xuất 60 câu: 35 có nguồn, 10 đa lượt/đổi ý, 10 ngoài phạm vi/thiếu nguồn, 5 prompt-injection; đo retrieval riêng với chất lượng trả lời.

## 11. Quy tắc sản phẩm cho chatbot tương lai

- Tư vấn nhiều phương án **có điều kiện**, không khẳng định “màu X chỉ hợp duy nhất style Y”; hỏi về phòng, sắc độ, ánh sáng, diện tích, ngân sách tương đối, đồ/cửa sổ cần giữ khi những thông tin đó ảnh hưởng câu trả lời.
- Dựa vào tài liệu **đã truy hồi và được duyệt**, nói rõ điều nào là preset của Home AI, điều nào là gợi ý thẩm mỹ của nhóm. Không dùng tỷ lệ màu trong image prompt như quy định thi công.
- Không bịa citation/URL/ID tài liệu. Khi không đủ bằng chứng, nói “chưa đủ căn cứ”, hỏi thêm hoặc chuyển sang người có chuyên môn.
- Không xem được ảnh, trạng thái job hay dữ liệu riêng chỉ vì người dùng nhắc tới; cần API, attachment và quyền truy cập phù hợp. Không khẳng định đã tạo ảnh/đã lưu dự án nếu chưa có `designId` và status từ server.
- Không tự tạo ảnh, gọi provider hoặc tiêu credit từ một câu trả lời text. Yêu cầu Design Brief và xác nhận riêng của người dùng.
- Không quyết định phá tường, điện, chống thấm, an toàn kết cấu, pháp lý xây dựng, giá thi công hiện tại hoặc tác dụng chữa bệnh từ ảnh/preset nội thất.
- Khi người dùng đổi ý trong hội thoại, ưu tiên yêu cầu **mới nhất** và cập nhật brief có version; không lặp lại sở thích cũ như thể vẫn còn hiệu lực.

## 12. Rủi ro và sự thật dễ bị hiểu sai

| Nhận định dễ sai | Sự thật theo worktree hiện tại |
|---|---|
| “Có Chat nên đã có RAG” | Chỉ có lexical retrieval; Gemini trả lời tùy chọn, tắt mặc định. Chưa embedding/semantic retrieval và corpus approved. |
| “Chat text tự tạo ảnh ngay khi được hỏi” | Chat chỉ đề xuất; người dùng phải chọn phòng, phong cách, ảnh gốc và bấm tạo. Android debug dùng API thiết kế hiện có, kết quả lưu Gallery; lịch sử hội thoại chưa lưu ảnh/job. |
| “Có lịch sử Chat nên có Design Brief” | Có conversation/message, chưa lưu brief/attachment/action/generation link. |
| “Có tab Exterior nên AI ngoại thất đã hoàn thiện” | UI có, backend chưa có feature/prompt ngoại thất riêng. |
| “Có 8 phòng nên RAG có 8 phòng” | UI có 8; seed prompt và corpus phòng chính chỉ có 5. |
| “`user-id` là đăng nhập an toàn” | ID do client tự gửi; cần token server cấp/claim/ownership trước khi public. |
| “`pending` job sẽ tiếp tục sau restart” | Hiện dùng `Task.Run`; chưa có durable queue/provider task ID trong DB. |
| “Xóa trong Gallery là xóa vĩnh viễn” | Chỉ `IsDeleted=true`; ảnh ở dịch vụ ngoài không được dọn tự động. |
| “Health trả healthy thì mọi thứ hoạt động” | Health chưa kiểm tra SQL Server, ImgBB, NanoBanana hay Gemini. |

Ngoài ra, Android debug cho phép đổi địa chỉ backend; USB cần `adb reverse tcp:5207 tcp:5207`, dùng `http://127.0.0.1:5207/` trên điện thoại. `127.0.0.1` **không tự trỏ về PC** khi chưa reverse; LAN IP thay đổi theo mạng. API Chat text giới hạn loopback dù API khác có thể truy cập qua LAN. Bản release cần HTTPS và xác thực đúng trước khi dùng từ nơi khác.

## 13. Việc cần làm theo thứ tự để có chatbot LLM + RAG thật

1. **Ổn định nền:** xác thực server-side/ownership, giới hạn request và chi phí; chuẩn hóa feature/room/style ID và giải quyết chênh lệch UI–seed; hoàn thiện job ảnh bền vững trước khi cho Chat tạo ảnh.
2. **Duyệt nội dung:** kiểm tra 55 bài, nguồn/quyền dùng, sửa mâu thuẫn; phê duyệt tối thiểu tập pilot; hoàn thiện bộ 60 câu và đáp án/citation kỳ vọng.
3. **RAG kỹ thuật:** ingestion lặp lại được, content hash/version, chunk/embedding, semantic + metadata retrieval, threshold và kiểm tra citation; lưu/log số liệu phù hợp, không log secret hay nội dung riêng quá mức.
4. **LLM có kiểm soát:** structured reply + usedChunkIds, bảo vệ prompt injection, fallback khi provider lỗi, so sánh với chế độ không RAG. Gemini tùy chọn hiện có chỉ là điểm bắt đầu.
5. **Guided Chat:** attachment thuộc owner, brief/version, hỏi trường còn thiếu, review/confirm, tạo job idempotent, trả `designId`, cập nhật trạng thái và mở Gallery. Android cần card nguồn/brief/job và lưu lịch sử bền vững tương ứng.
6. **Kiểm thử trên điện thoại:** câu hỏi nhiều lượt/đổi ý, mất mạng, restart API, retry/double tap, quyền của user A/B, chất lượng citation, độ trễ và chi phí; chỉ sau đó mới mô tả sản phẩm là chatbot RAG hoàn chỉnh.

## 14. Chỉ mục nguồn cho ChatGPT/người triển khai

- **Luồng và UI Android:** `FE/ReRoom/app/src/main/java/com/example/revroom/navigation/AppNavigation.kt`, `features/design_studio/`, `features/chat/`, `features/history/`, `features/auth/`.
- **Contract Android:** `FE/ReRoom/app/src/main/java/com/example/revroom/data/remote/{DesignApi,ChatApi,AuthApi}.kt`; endpoint debug trong `core/network/ServerEndpoint.kt`.
- **API backend:** `BE/InteriorAI/Controllers/{DesignController,ChatController,AuthController,WebhookController}.cs`.
- **Nghiệp vụ:** `BE/InteriorAI/Services/{DesignManager,DesignPromptService,ChatConversationService,ChatReplyService,KnowledgeBaseService,GeminiChatAnswerGenerator}.cs`.
- **Dữ liệu:** `BE/InteriorAI/Data/AppDbContext.cs`, `BE/InteriorAI/Models/Entities/`, `BE/InteriorAI/Data/Seed/{DesignStyleSeedData,RoomStylePromptSeedData,DesignStudioSeeder}.cs`.
- **Kho nội dung:** `knowledge-base/README.md`, `knowledge-base/docs/`, `knowledge-base/source-register.csv`, `knowledge-base/eval/smoke-questions.csv`.
- **Kế hoạch tương lai:** `BE/InteriorAI/CHATBOT_DEVELOPMENT_PLAN.md`, `BE/InteriorAI/CHATBOT_KNOWLEDGE_BASE_PLAN.md`, `kehoach/PROJECT_EXECUTION_PLAN.md`.
- **Vận hành demo:** `BE/InteriorAI/CHATBOT_RUNBOOK.md`, `kehoach/DEVELOPMENT_GUIDE.md`, `run-usb-dev.ps1`.

Khi giao file này cho ChatGPT để **viết mã**, hãy yêu cầu nó kiểm tra các file nguồn liên quan ngay trước khi sửa, phân biệt “hiện có” với “dự kiến”, giữ nguyên các thay đổi chưa commit của dự án và không đưa API key/connection string vào câu trả lời. Khi dùng cho **RAG trả lời người dùng cuối**, hãy truy hồi từng bài nội thất/hướng dẫn ứng dụng đã duyệt trong `knowledge-base/docs/`; đừng coi riêng bản tổng quan kỹ thuật này là bằng chứng chuyên môn nội thất.
