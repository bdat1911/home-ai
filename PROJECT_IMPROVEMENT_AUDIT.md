# Báo cáo rà soát và kế hoạch cải thiện Home AI

**Ngày rà soát:** 14/09/2026  
**Phạm vi:** Android app `FE/ReRoom`, ASP.NET Core API `BE/InteriorAI`, database/seed, AI image pipeline, cấu hình, tài liệu và chatbot.  
**Cách đọc:** Các mục “đã xác minh” được đối chiếu trực tiếp từ mã nguồn hiện tại. Các mục “đề xuất” là backlog phát triển, không nên mô tả như chức năng đã hoàn thành.

## 1. Tóm tắt điều hành

Dự án đã có một luồng lõi có giá trị cho đồ án: người dùng chọn ảnh phòng, loại phòng/phong cách, gửi yêu cầu tạo ảnh AI, theo dõi trạng thái và xem lịch sử dự án. Front-end dùng Kotlin/Jetpack Compose; Back-end dùng ASP.NET Core, EF Core/SQL Server, ImgBB và NanoBanana.

Tuy nhiên, dự án hiện phù hợp để demo nội bộ hơn là triển khai thật. Các rủi ro lớn nhất là xác thực người dùng chưa an toàn, khóa API nằm trong cấu hình local, job AI không bền vững khi API restart, cấu hình mạng điện thoại thật phụ thuộc `adb reverse`, và tab Chat chưa phải chatbot đa lượt/RAG.

Các thay đổi Profile và Style đang có trong working tree đã cải thiện trải nghiệm lỗi mạng (Profile có retry; Style có loading/lỗi/retry) nhưng vẫn cần cài lại APK và kiểm thử trên điện thoại. Báo cáo này không coi các thay đổi chưa commit là đã bàn giao production.

## 2. Quy ước ưu tiên

- **P0 — Chặn demo công khai/triển khai:** gây lộ dữ liệu, tiêu tiền AI sai, hoặc làm APK release không dùng được.
- **P1 — Cần xử lý sớm:** làm mất job, sai chức năng chính, hoặc trải nghiệm người dùng rất kém.
- **P2 — Nợ kỹ thuật quan trọng:** chưa chặn demo nhưng gây khó bảo trì, mở rộng và đánh giá đồ án.
- **P3 — Hoàn thiện sau:** tối ưu, mở rộng sản phẩm và nâng chất lượng trình bày.

## 3. Lỗi và rủi ro đã xác minh

### P0-01 — Chưa có xác thực thật; `user-id` có thể bị giả mạo

**Bằng chứng:**

- `BE/InteriorAI/Controllers/DesignController.cs` đọc trực tiếp header `user-id` từ request.
- `BE/InteriorAI/Program.cs` không có `UseAuthentication`, `UseAuthorization` hay policy `[Authorize]`.
- API Profile nhận `userId` từ URL/form (`AuthController.cs`), không đối chiếu với danh tính người gọi.
- Khai báo `user-id` trong Swagger chỉ giúp nhập header, không phải cơ chế bảo mật.

**Tác động:** Người biết hoặc đoán được ID của người khác có thể gọi API bằng ID đó để xem, xóa lịch sử dự án hoặc thay đổi thông tin Profile/Avatar của họ.

**Cần làm:**

1. Thêm tài khoản và access token server-issued (JWT hoặc dịch vụ xác thực như Firebase/Auth0).
2. Đọc user ID duy nhất từ claim token; bỏ tin cậy vào `user-id` do client tự gửi.
3. Áp ownership check cho Design, Profile, Conversation, Attachment và Avatar.
4. Viết test: user A không thể đọc/sửa/xóa dữ liệu của user B.

### P0-02 — Khóa AI/storage đang nằm plaintext trong cấu hình development

**Bằng chứng:** `BE/InteriorAI/appsettings.Development.json` có cấu hình Gemini, NanoBanana và ImgBB chứa khóa thật. File hiện bị `.gitignore` bỏ qua và không được theo dõi trong working tree, nhưng secret vẫn nằm ở máy phát triển.

**Tác động:** Khóa có thể bị lộ khi chia sẻ thư mục, chụp màn hình, sao lưu máy, gửi zip dự án hoặc từng xuất hiện trong lịch sử Git.

**Cần làm ngay:**

1. Rotate/thu hồi toàn bộ khóa hiện tại; không ghi lại giá trị khóa vào tài liệu hoặc commit.
2. Dùng .NET User Secrets cho máy dev; environment variables hoặc secret manager khi deploy.
3. Thêm `appsettings.Example.json` không chứa giá trị nhạy cảm.
4. Kiểm tra lịch sử Git trước khi push public bằng secret scanner.

### P0-03 — Cấu hình Android chỉ chạy ổn qua USB debug; release chưa dùng được

**Bằng chứng:**

- `FE/ReRoom/app/build.gradle.kts` hard-code debug URL là `http://127.0.0.1:5207/`.
- Trên điện thoại thật, `127.0.0.1` là điện thoại nên bắt buộc dùng `adb reverse tcp:5207 tcp:5207` sau mỗi lần kết nối lại.
- Có `BASE_URL = 10.0.2.2` nhưng `ApiClient.kt` thực tế dùng `BuildConfig.API_BASE_URL`; tài liệu cũ còn hướng dẫn `10.0.2.2`.
- URL release hiện là placeholder `https://replace-with-production-api/`.
- `AndroidManifest.xml` hard-code `android:usesCleartextTraffic="true"`, nên cấu hình placeholder release không thể tắt HTTP như dự định.

**Tác động:** Đây là nguyên nhân trực tiếp của lỗi `Failed to connect to /127.0.0.1:5207`; APK release hiện không thể kết nối một API thật và có nguy cơ gửi ảnh/User ID qua HTTP.

**Cần làm:**

1. Tách flavor/config: `usbDebug`, `emulatorDebug`, `lanDebug`, `staging`, `production`.
2. Đọc base URL từ Gradle property/local configuration không commit; production bắt buộc HTTPS.
3. Dùng `${usesCleartextTraffic}` hoặc Network Security Config chỉ cho phép HTTP ở debug nội bộ.
4. Hiện màn chẩn đoán kết nối ở bản debug: URL đang dùng, trạng thái API và hướng dẫn `adb reverse`.

### P0-04 — UI Chat cho gửi text-only nhưng Repository/API lại bắt buộc có ảnh

**Bằng chứng:**

- `ChatViewModel.kt` cho gửi khi có text *hoặc* ảnh.
- `DesignRepository.sendMessage()` ném lỗi nếu không có `imageUri`.
- Back-end `ChatDesignRequest` cũng đánh dấu `Image` là bắt buộc.

**Tác động:** Người dùng nhìn thấy tin nhắn text đã xuất hiện trong màn Chat rồi nhận lỗi yêu cầu ảnh. Đây là mâu thuẫn chức năng rõ ràng và khiến tab Chat không thể được gọi là chatbot hội thoại.

**Cần làm:** Chọn một trong hai hướng và đồng bộ UI–API–tài liệu:

- **Tạm thời:** bắt buộc ảnh, disable Send trước khi thêm message vào UI và giải thích rõ yêu cầu.
- **Hướng đúng cho đồ án:** hỗ trợ text-only cho tư vấn; chỉ yêu cầu ảnh khi người dùng xác nhận tạo render.

### P1-01 — Job AI chạy bằng `Task.Run`, không bền vững khi API restart

**Bằng chứng:** `DesignManager.StartProcessingInBackground()` tạo fire-and-forget `Task.Run`; record được lưu `Pending` rồi xử lý trong process API.

**Tác động:** Restart/crash API có thể để job `Pending` vĩnh viễn. Không có hàng đợi, retry, resume, cancel, attempt count hoặc recovery. Task không bắt đầu được cũng chỉ log mà không luôn cập nhật trạng thái job.

**Cần làm:**

1. Tạo entity job bền vững: `queued`, `processing`, `completed`, `failed`, `cancelled`, `attemptCount`, `nextAttemptAt`, `providerTaskId`.
2. Dùng `BackgroundService` + database queue ở MVP, hoặc Hangfire/Quartz/RabbitMQ ở quy mô lớn hơn.
3. Worker phải claim job atomically, retry backoff, timeout và quét lại job bị kẹt khi khởi động.
4. Lưu `providerTaskId` để có thể resume polling thay vì tạo ảnh lại.

### P1-02 — Xóa dự án không hủy job AI và không dọn ảnh ngoài storage

**Bằng chứng:**

- `DeleteProjectAsync()` chỉ đặt `IsDeleted = true`.
- `ProcessDesignAsync()` vẫn có thể xử lý record đã bị xóa mềm.
- Ảnh gốc/kết quả được upload ImgBB trước hoặc trong pipeline; interface storage chỉ có upload, không có delete.
- Avatar cũ trong `wwwroot/uploads` cũng không có lifecycle dọn dẹp.

**Tác động:** Người dùng xóa dự án nhưng hệ thống vẫn có thể tốn credit AI, tạo ảnh mới và giữ ảnh công khai trên storage.

**Cần làm:**

1. “Cancel design” phải là API thật hoặc đổi nhãn UI thành “Rời khỏi màn hình” để không gây hiểu lầm.
2. Worker phải bỏ qua/cancel job `IsDeleted` trước và sau các bước tốn tiền.
3. Lưu storage key thay vì chỉ URL; có chính sách retention, purge và cleanup ảnh lỗi/xóa.
4. Thêm cảnh báo privacy: ảnh phòng là dữ liệu người dùng, không nên để public vô thời hạn.

### P1-03 — Model, resolution, prompt và tải từ URL provider chưa được kiểm soát đủ

**Bằng chứng:**

- `Model`/`Resolution` nhận từ multipart request và `model` được ghép vào URL NanoBanana.
- Chat prompt chưa có giới hạn độ dài rõ ràng.
- `UploadImageFromUrlAsync()` chỉ kiểm tra URL HTTP/HTTPS rồi backend tự tải URL đó; chưa allowlist hostname, chặn IP private, giới hạn response size hay xác minh MIME/dimensions.

**Tác động:** Request bất thường có thể làm lỗi provider, tăng chi phí, gây memory exhaustion hoặc mở ra rủi ro SSRF qua URL đầu ra không tin cậy.

**Cần làm:**

1. Allow-list `model`, `resolution`, `featureId`, `roomType`; server đặt default an toàn.
2. Giới hạn prompt, kích thước ảnh, pixel/dimensions và số request/user.
3. Allow-list domain provider/CDN, chặn localhost/private/link-local, đặt timeout/size cap khi tải URL.
4. Thêm rate limit, quota ảnh/token và idempotency key để chống double click/retry tạo job trùng.

### P1-04 — Luồng Exterior chưa có prompt/pipeline riêng

**Bằng chứng:** Front-end hiển thị `exterior_design`, `facade`, `landscaping`; nhưng `DesignPromptService.NormalizeFeatureId()` chỉ nhận các feature nội thất và quy mọi ID chưa biết về `interior_design`.

**Tác động:** UI quảng bá Exterior nhưng Back-end không có template/prompt riêng cho ngoại thất. Kết quả AI có thể không đúng phạm vi chức năng được trình bày.

**Cần làm:** Hoặc tạm ẩn Exterior ở demo, hoặc hoàn thiện mode Exterior với seed, prompt, validation và test riêng cho facade/landscaping.

### P1-05 — Polling/retry có thể đánh dấu sai trạng thái và tạo dự án trùng

**Bằng chứng:**

- Design polling và Chat polling có thời gian/logic khác nhau; Chat dừng khoảng 2 phút trong khi provider Back-end có thể chờ đến 5 phút.
- Một lỗi mạng ở polling Design có thể chuyển job thành failed dù server vẫn xử lý.
- `DesignViewModel.retry()` tạo request thiết kế mới thay vì resume job cũ.
- UI có “Cancel design?” nhưng chưa gọi API hủy server.

**Cần làm:** Lưu `designId` local, resume status khi mở app, dùng exponential backoff và lỗi HTTP có kiểu; chỉ tạo job mới khi user xác nhận retry generation.

### P1-06 — Gallery chưa phân biệt trạng thái rỗng với lỗi; phân trang chưa hoàn chỉnh

**Bằng chứng:**

- `HistoryScreen` hiển thị “No projects yet” kể cả khi API lỗi.
- `HistoryViewModel` có `currentPage`/`hasMore()` nhưng UI chỉ tải trang đầu, không có pull-to-refresh, Retry hoặc load-more.
- Badge UI tô đỏ cho mọi trạng thái khác `completed`, nên `pending/processing` dễ bị hiểu là failed.

**Cần làm:** Tạo `HistoryUiState` rõ ràng: Loading, Empty, Error, Content; thêm Retry, pull-to-refresh, pagination theo `totalPages`, trạng thái Completed/Processing/Failed và giữ dữ liệu cũ khi refresh lỗi.

### P1-07 — Quản lý device identity/session bị phân tán

**Bằng chứng:** `MainActivity`, `ProfileScreen/AuthViewModel`, `DesignRepository`, `UserManager` và `LocalUserIdProvider` đều có một phần logic nhận diện/đăng ký user. Hai class local còn trùng logic sinh/lưu `DEVICE_ID`.

**Tác động:** Dễ race condition, che mất lỗi đăng ký và khó thay sang login/JWT. `MainActivity` còn tạo `AuthViewModel()` thủ công, không thuộc ViewModelStore.

**Cần làm:** Gom thành `SessionRepository` duy nhất, inject qua DI; trả lỗi typed (`Offline`, `Unauthorized`, `NotFound`, `ServerError`) thay vì mặc định coi mọi lỗi là user chưa tồn tại.

### P1-08 — Pipeline nén/upload ảnh trên Android có nguy cơ OOM và sai MIME

**Bằng chứng:**

- `ImageCompressor.readAndCompressIfNeeded()` đọc toàn bộ ảnh vào RAM trước khi kiểm tra kích thước.
- Khi nén, byte đầu ra luôn JPEG nhưng `DesignRepository` vẫn gửi MIME type/extension của file gốc.
- `FileUtils.uriToFile()` có thể tạo file rỗng nếu stream null và luôn dùng đuôi `.jpg`.

**Cần làm:** Đọc stream theo luồng, decode bounds trước, giới hạn pixel; nếu re-encode JPEG thì gửi đồng bộ JPEG MIME/extension; fail rõ ràng khi không đọc được URI.

### P2-01 — Profile summary không phản ánh đủ trạng thái thiết kế

**Bằng chứng:** `ProjectCount` tính mọi project active nhưng API summary chỉ trả `CompletedDesignCount` và `PendingDesignCount`; job `Failed` không nằm ở cột nào trong khi UI gọi pending là “Processing”.

**Cần làm:** Thêm `FailedDesignCount` hoặc hiển thị rõ Total / Completed / Processing / Failed. Summary lỗi nên có trạng thái/retry riêng, không làm người dùng hiểu sai việc lưu Profile.

### P2-02 — Database migration và seed chưa phù hợp môi trường mới/production

**Bằng chứng:**

- `Program.cs` chạy seeder nhưng không gọi migration; database mới có thể lỗi vì bảng chưa tồn tại.
- Seeder chạy mỗi startup và ghi đè cấu hình style/prompt hiện có.
- `StyleName` chưa có unique index; `Variant` tồn tại nhưng lookup/unique key chưa hỗ trợ nhiều variant cho cùng style + room.
- Mobile có 8 loại phòng, trong khi seed prompt chi tiết hiện chỉ phủ một phần; các phòng còn lại rơi về prompt chung.

**Cần làm:**

1. Có migration step rõ ràng trong CI/CD hoặc startup có kiểm soát.
2. Thêm unique constraint đúng nghiệp vụ và version cho prompt/seed.
3. Không ghi đè cấu hình do admin chỉnh sửa; phân biệt default seed với dữ liệu runtime.
4. Phủ đủ prompt cho các room type đã hiển thị, hoặc không hiển thị tính năng chưa hỗ trợ.

### P2-03 — Dữ liệu `DesignResult` thiếu metadata cần cho retry, phân tích và chatbot

**Bằng chứng:** `DesignResult` hiện chủ yếu lưu URL, prompt, model/resolution, status và timestamp; chưa lưu design mode, feature ID, room type, style ID, prompt version, provider task ID, attempt lifecycle hay quan hệ cha-con của variation.

**Cần làm:** Bổ sung metadata có version, liên kết job/conversation và audit trail. Đây là nền tảng để resume job, đánh giá chất lượng AI, thống kê chi phí và phát triển chatbot sau này.

### P2-04 — API/error/observability chưa chuẩn hóa

**Bằng chứng:**

- Không có global exception handler/ProblemDetails, request correlation, health readiness cho DB/provider hoặc rate limiter.
- Một số endpoint có thể trả `ex.Message`/raw provider error về client.
- `/api/webhook/health` chỉ trả timestamp, không kiểm tra DB, AI hay storage.
- Controller/service chưa dùng `CancellationToken` xuyên suốt.

**Cần làm:** Global exception middleware, error code an toàn cho client, structured logging, correlation ID, `/health/live`, `/health/ready`, metrics (latency, success rate, cost) và timeout/retry policy cho HttpClient.

### P2-05 — Kiến trúc Front-end chưa thống nhất, khó test

**Bằng chứng:** Design/Chat dùng StateFlow; History dùng LiveData; Profile dùng `mutableStateOf`; `ApiClient` singleton được gọi trực tiếp; Factory tự tạo thủ công. Gallery Compose còn mở legacy XML Activity; dự án đồng thời có Coil/Glide, Retrofit suspend API/`ApiService.java` cũ, và các file Example/legacy chưa rõ sử dụng.

**Cần làm:** Chuẩn hóa Compose + Coil + Retrofit suspend + StateFlow, tách repository interface, dùng DI tối thiểu (Hilt/Koin hoặc manual container), rồi xóa code chết sau khi xác nhận không còn dùng.

### P2-06 — Khả năng tiếp cận, dark mode và responsive còn hạn chế

**Bằng chứng:** Nhiều text/màu/kích thước hard-code; status phụ thuộc đỏ/xanh; nhiều `Box.clickable` thiếu semantics; giao diện phần lớn cố định nền sáng; một số grid không scroll đủ tốt ở màn hình nhỏ/font scale lớn.

**Cần làm:** Chuyển strings vào resources, hỗ trợ vi/en nhất quán, kiểm thử font scale/landscape/tablet, thêm content description/role, không dùng màu là tín hiệu duy nhất và kiểm thử dark mode.

### P2-07 — Tài liệu, API mẫu và nhận diện sản phẩm đã lỗi thời

**Bằng chứng:**

- `BE/InteriorAI/README.md`, `FE/README_HISTORY.md`, `FE/ReRoom/LUONG_HOAT_DONG.md` còn chứa đường dẫn `d:/nhom5`, API/migration/tệp cũ, flow Settings/Profile cũ hoặc URL emulator `10.0.2.2`.
- `InteriorAI.http` còn có `/weatherforecast` không được map và ví dụ style không khớp seed hiện tại.
- Root README quá ngắn; chưa có configuration template và hướng dẫn chạy điện thoại thật đầy đủ.
- Tên `Home AI`, `InteriorAI`, `ReRoom` chưa được chốt thống nhất.

**Cần làm:** Có một README chạy dự án từ đầu, một `appsettings.Example.json`, sơ đồ kiến trúc, API contract hiện hành, checklist demo điện thoại thật và quy ước tên sản phẩm/đề tài thống nhất.

### P2-08 — Kiểm thử, CI/CD và tái lập môi trường gần như chưa có

**Bằng chứng:** Back-end chưa có test project; Android chỉ có test mẫu; không có GitHub Actions workflow; không có Docker/Compose cho API + SQL Server.

**Cần làm tối thiểu:**

1. Unit test prompt normalization, validation, image mapping, session và status mapping.
2. Integration test Profile/Design ownership, pagination, delete while processing và API contract.
3. Mock ImgBB/NanoBanana trong test.
4. CI: restore → build → test → format/lint → secret scan/dependency scan.
5. Docker Compose hoặc runbook SQL Server rõ ràng cho giảng viên/nhóm tái lập demo.

## 4. Các tính năng nên cải thiện hoặc phát triển

### 4.1. Hoàn thiện luồng thiết kế AI trước khi mở rộng

- Cho chọn/cấu hình model và resolution thực sự ở Design flow, hoặc bỏ option chưa dùng.
- Lưu tiêu chí người dùng: ngân sách, màu muốn giữ, đồ cần tránh, ánh sáng, số người dùng phòng.
- Có tính năng variation từ bản render cũ thay vì chỉ tạo lại từ đầu.
- Hiển thị chất lượng ảnh đầu vào (mờ/tối/quá nhỏ), cảnh báo trước khi tốn credit.
- Tạo lịch sử retry/cancel và trạng thái job rõ ràng.
- Thêm khôi phục/Purge project sau soft delete theo chính sách retention.

### 4.2. Hoàn thiện Gallery và Profile

- Gallery: filter theo status, sort theo ngày, search, pagination, refresh, retry, chi tiết dự án Compose thống nhất.
- Profile: ảnh avatar có resize/crop, thay avatar xóa file cũ, thống kê đủ Completed/Processing/Failed, quản lý quyền riêng tư/xóa dữ liệu.
- Khi có authentication thật: đăng nhập, logout, khôi phục tài khoản đa thiết bị thay cho device ID.

### 4.3. Phát triển Chat thành “Interior Design Copilot”

Hiện tại tab Chat là luồng một lần: prompt + ảnh → `DesignResult` → polling. Nó **chưa có** conversation, message persistence, memory, LLM text service, embedding, vector database hay RAG.

MVP phù hợp cho đồ án:

1. Tạo `Conversation`, `ChatMessage`, `ChatAttachment`, `DesignBrief` trong database.
2. Cho text-only để bot tư vấn style; chỉ yêu cầu ảnh khi cần tạo render.
3. Bot thu thập room type, style, yêu cầu phải giữ/phải tránh thành một Design Brief có cấu trúc.
4. Người dùng xem và xác nhận Brief trước khi gọi AI tạo ảnh.
5. Lưu lịch sử chat và liên kết kết quả `DesignResult` với conversation.
6. Hỗ trợ retry từng message, resume job và tạo variation từ render cũ.

### 4.4. RAG là hướng phát triển sau MVP, chưa phải tính năng hiện có

Gemini chỉ xuất hiện trong cấu hình; dự án chưa có code Gemini/embedding/retrieval/vector DB/citation. Vì vậy không nên nói “đã áp dụng RAG” trong báo cáo hoặc bảo vệ ở thời điểm này.

Khi nền tảng chat đã ổn định, RAG nên đi theo thứ tự:

1. Chuẩn bị knowledge base có nguồn đáng tin: style guide, nguyên tắc bố trí, material, ánh sáng, dữ liệu dự án mẫu.
2. Chunk, embedding và lưu vector index; quản lý phiên bản tài liệu.
3. Retrieve top-k theo câu hỏi + Design Brief; bot trả lời kèm nguồn/citation.
4. Giới hạn context, chống prompt injection và không cho LLM tự gọi AI image/SQL.
5. Viết bộ câu hỏi đánh giá tiếng Việt: đúng nguồn, hallucination, câu mơ hồ, thay đổi yêu cầu và safety.

### 4.5. Tính năng sản phẩm có thể tạo điểm nhấn đồ án

- Design Brief có cấu trúc và màn xác nhận trước khi tốn credit.
- So sánh nhiều variation trước/sau; lưu quan hệ cha-con giữa các bản thiết kế.
- Gợi ý phong cách theo ảnh phòng nhưng bắt buộc người dùng xác nhận.
- Danh sách nội thất/màu sắc mang tính tham khảo, không tuyên bố báo giá hoặc tư vấn kết cấu.
- Dashboard quản trị: tỷ lệ job thành công, latency P50/P95, số retry, chi phí từng provider, feedback người dùng.
- Feedback “hữu ích/không hữu ích” cho Chat và ảnh AI để có số liệu đánh giá đồ án.

## 5. Lộ trình thực hiện đề xuất

### Giai đoạn 0 — Ổn định demo cục bộ (2–4 ngày)

- [ ] Rotate API keys, tạo file cấu hình mẫu và hướng dẫn local.
- [ ] Hoàn thiện flavor USB/emulator/LAN, giữ API chạy và kiểm tra health trước demo.
- [ ] Sửa dứt điểm Chat text-only/ảnh bắt buộc.
- [ ] Cài và kiểm thử bản Style/Profile mới trên điện thoại thật.
- [ ] Thêm Retry/loading/error cho Gallery; sửa badge status.
- [ ] Sửa luồng cancel để không nói là đã hủy khi server chưa hủy.

**Tiêu chí hoàn thành:** demo trên điện thoại không có màn trắng/lỗi kỹ thuật mơ hồ; mỗi lỗi mạng có Retry; không cần sửa source mỗi lần đổi emulator/USB/LAN.

### Giai đoạn 1 — Đúng chức năng thiết kế và dữ liệu (1 tuần)

- [ ] Hoàn thiện prompt/seed riêng cho Exterior hoặc ẩn Exterior khỏi phạm vi demo.
- [ ] Đồng bộ toàn bộ room type UI với seed/prompt.
- [ ] Bổ sung metadata `DesignResult` và trạng thái job đầy đủ.
- [ ] Validate input/image/model/resolution ở cả app và API.
- [ ] Sửa lifecycle delete/cancel và cleanup ảnh.

**Tiêu chí hoàn thành:** mọi nút/chức năng xuất hiện trên UI đều có pipeline Back-end tương ứng và lịch sử dự án phản ánh đúng trạng thái.

### Giai đoạn 2 — Job bền vững và an toàn (1–2 tuần)

- [ ] Thay `Task.Run` bằng worker/queue bền vững.
- [ ] Retry backoff, resume sau restart, idempotency và quota.
- [ ] JWT/authentication, ownership check và HTTPS production.
- [ ] Health checks, ProblemDetails, logging/metrics không lộ dữ liệu nhạy cảm.

**Tiêu chí hoàn thành:** restart API không làm job treo vĩnh viễn; retry không tạo job trùng; user A không truy cập được dữ liệu user B.

### Giai đoạn 3 — Guided Design Chat MVP (1–2 sprint)

- [ ] Conversation/message/attachment/brief persistence.
- [ ] Quick reply/chip, structured brief và bước confirm generate.
- [ ] Liên kết Chat ↔ job AI ↔ Gallery, khôi phục lịch sử sau khi đóng app.
- [ ] Test offline/retry/process death cho Chat.

**Tiêu chí hoàn thành:** người dùng có thể tư vấn text, gửi ảnh khi cần, xác nhận brief, nhận render trong cùng conversation và mở lại app vẫn thấy lịch sử.

### Giai đoạn 4 — RAG, chất lượng và bảo vệ đồ án (sau MVP)

- [ ] Knowledge base, embedding, retrieval, citation và evaluation.
- [ ] Unit/integration/UI test, GitHub Actions, Docker/Compose, demo script.
- [ ] Chốt tên sản phẩm/đề tài, sơ đồ kiến trúc, giới hạn hệ thống và số liệu đánh giá.

**Tiêu chí hoàn thành:** có thể trình bày trung thực kỹ thuật đã làm, số liệu đo được và phạm vi không làm (không thay kiến trúc sư/bản vẽ kỹ thuật/tư vấn kết cấu).

## 6. Thứ tự ưu tiên khi thời gian đồ án có hạn

1. Bảo vệ secret, authentication và cấu hình mạng/release.
2. Ổn định luồng tạo ảnh: validation, job durable, retry/cancel, storage lifecycle.
3. Chỉ giữ các feature Interior/Exterior thực sự có prompt/pipeline hoạt động.
4. Hoàn thiện Gallery/Profile/error states để demo không gây hiểu nhầm.
5. Làm Chat MVP có Design Brief và lưu hội thoại.
6. Chỉ triển khai RAG khi các bước trên đã xong; nếu không, ghi RAG là hướng phát triển.
7. Dành thời gian cuối cho test, CI, tài liệu chạy dự án và demo script.

## 7. Kết luận dùng cho báo cáo đồ án

Nên mô tả hệ thống hiện tại là **ứng dụng di động hỗ trợ thiết kế không gian từ ảnh bằng AI với luồng tạo ảnh, quản lý dự án và định hướng phát triển trợ lý tư vấn nội thất**. Không nên tuyên bố hệ thống đã production-ready, đã có authentication an toàn, đã có chatbot đa lượt hoặc đã áp dụng RAG, vì các phần đó chưa tồn tại đầy đủ trong mã nguồn hiện tại.

Nếu hoàn thành Giai đoạn 0–3, điểm nhấn kỹ thuật hợp lý cho đồ án là: mobile AI design pipeline, job queue bền vững, Design Brief có cấu trúc, chatbot tư vấn đa lượt và kiểm soát action generate. RAG có thể là phần mở rộng có đánh giá rõ ràng thay vì một nhãn kỹ thuật chung chung.
