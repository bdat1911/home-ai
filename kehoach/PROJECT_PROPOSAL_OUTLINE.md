# Đề cương ý tưởng đồ án — Home AI

## 1. Tên đề tài đề xuất

**Nghiên cứu và xây dựng ứng dụng di động hỗ trợ thiết kế không gian sống từ ảnh bằng trí tuệ nhân tạo, tích hợp chatbot tư vấn dựa trên kỹ thuật RAG.**

Nếu tại thời điểm bảo vệ chưa hoàn thiện đầy đủ cơ sở tri thức, embedding và truy hồi tài liệu, nên dùng tên an toàn hơn:

> **Nghiên cứu và xây dựng ứng dụng di động hỗ trợ thiết kế không gian sống từ ảnh bằng trí tuệ nhân tạo, có định hướng tích hợp chatbot tư vấn.**

## 2. Tóm tắt ý tưởng

Home AI là ứng dụng Android hỗ trợ người dùng hình dung phương án thiết kế lại không gian sống từ ảnh thật. Người dùng tải ảnh căn phòng, chọn loại phòng, phong cách và nhu cầu thiết kế; hệ thống tạo một ảnh minh họa bằng mô hình AI tạo sinh. Kết quả được lưu thành dự án để xem lại, quản lý hoặc tiếp tục trao đổi với trợ lý tư vấn.

Điểm phát triển tiếp theo của đề tài là chatbot tư vấn thiết kế. Thay vì chỉ trả lời chung chung, chatbot được định hướng xây dựng theo kỹ thuật **Retrieval-Augmented Generation (RAG)**: truy hồi kiến thức nội thất phù hợp trước khi mô hình ngôn ngữ tạo câu trả lời. Nhờ đó, trợ lý có thể giải thích vì sao một phong cách phù hợp, gợi ý vật liệu, màu sắc hoặc cách bố trí dựa trên căn phòng và thiết kế người dùng vừa tạo.

## 3. Lý do chọn đề tài

- Người dùng khó hình dung không gian sau khi cải tạo chỉ từ mô tả chữ hoặc ảnh tham khảo rời rạc.
- Tư vấn thiết kế nội thất truyền thống cần thời gian, kiến thức chuyên môn và chi phí ban đầu tương đối cao.
- Công cụ tạo ảnh AI hiện có thường rời rạc, chưa tạo thành một quy trình di động khép kín: ảnh thật → lựa chọn yêu cầu → phương án minh họa → lưu dự án → tư vấn tiếp.
- Chatbot thông thường có thể đưa ra gợi ý thiếu căn cứ. RAG giúp chatbot bám vào bộ tri thức nội thất đã chọn lọc, giảm câu trả lời chung chung và tăng khả năng giải thích.

## 4. Mục tiêu

### 4.1. Mục tiêu tổng quát

Xây dựng một ứng dụng di động hỗ trợ người dùng tạo và tham khảo phương án thiết kế không gian sống từ ảnh bằng AI, đồng thời tích hợp trợ lý hội thoại tư vấn theo ngữ cảnh thiết kế.

### 4.2. Mục tiêu cụ thể

1. Xây dựng ứng dụng Android cho phép tải ảnh, chọn loại không gian, phong cách và nhu cầu thiết kế.
2. Xây dựng Backend quản lý người dùng, yêu cầu tạo thiết kế, lịch sử dự án và ảnh kết quả.
3. Tích hợp dịch vụ AI tạo ảnh để sinh ảnh minh họa phương án nội thất.
4. Xây dựng bộ prompt có cấu trúc theo loại phòng, phong cách và chức năng thiết kế.
5. Tích hợp chatbot tư vấn; ở phiên bản hoàn chỉnh, chatbot dùng RAG để truy hồi kiến thức nội thất trước khi phản hồi.
6. Đánh giá hệ thống về chức năng, thời gian phản hồi, mức độ hữu ích của tư vấn và trải nghiệm người dùng.

## 5. Đối tượng, phạm vi và giới hạn

| Nội dung | Phạm vi đề tài |
|---|---|
| Nền tảng | Android; không phát triển iOS hoặc web trong phạm vi chính. |
| Không gian | Ưu tiên phòng khách, phòng ngủ, bếp, phòng ăn, phòng tắm, phòng học/làm việc. |
| Đầu vào | Một ảnh không gian thực tế, loại phòng, phong cách và yêu cầu thiết kế. |
| Đầu ra | Ảnh minh họa phương án thiết kế, lịch sử dự án và lời khuyên dạng hội thoại. |
| AI hình ảnh | Tích hợp API tạo ảnh; không huấn luyện mô hình từ đầu. |
| Chatbot | Tư vấn phong cách, vật liệu, màu sắc, bố trí; RAG dùng tri thức đã chọn lọc. |
| Giới hạn | Kết quả là gợi ý trực quan, không thay thế bản vẽ kỹ thuật, dự toán xây dựng hoặc kiến trúc sư. |

## 6. Hiện trạng và định hướng phát triển

### 6.1. Các phần đã có trong dự án

- Ứng dụng Android viết bằng Kotlin và Jetpack Compose.
- Luồng tải ảnh → chọn loại phòng → chọn phong cách → tạo thiết kế.
- API ASP.NET Core .NET 8, Entity Framework Core và SQL Server.
- Bộ dữ liệu seed cho sáu phong cách nội thất: Indochine, Minimalist Luxury, Modern Luxury, Industrial, Neoclassic và Scandinavian.
- Tạo thiết kế AI theo cơ chế xử lý nền và theo dõi trạng thái kết quả.
- Gallery/lịch sử dự án, xóa mềm dự án, đăng ký định danh thiết bị và API hồ sơ cơ bản.
- Tab Chat hiện tại hỗ trợ một lượt yêu cầu tạo thiết kế từ ảnh; **chưa phải chatbot RAG đa lượt hoàn chỉnh**.

### 6.2. Các phần dự kiến hoàn thiện cho đồ án

- Tách My Profile thành tab độc lập và hoàn thiện luồng cập nhật hồ sơ.
- Bổ sung trạng thái loading, lỗi, rỗng và Retry cho các màn gọi API.
- Phát triển chatbot đa lượt có ngữ cảnh của dự án thiết kế.
- Xây dựng kho tri thức nội thất, embedding và truy hồi tài liệu cho RAG.
- Hiển thị nguồn hoặc ngữ cảnh tham khảo phù hợp trong phản hồi chatbot.
- Tăng cường xác thực, bảo vệ API key, kiểm thử và theo dõi lỗi.

## 7. Chức năng chính

### 7.1. Người dùng

1. Đăng ký/nhận dạng người dùng và quản lý hồ sơ.
2. Chọn chế độ thiết kế: thiết kế lại nội thất, bố trí phòng trống hoặc loại bỏ nội thất.
3. Tải ảnh không gian cần thiết kế.
4. Chọn loại phòng và phong cách thiết kế.
5. Gửi yêu cầu tạo ảnh thiết kế bằng AI.
6. Theo dõi trạng thái xử lý và xem ảnh kết quả.
7. Lưu, xem lại và xóa mềm lịch sử dự án trong Gallery.
8. Trò chuyện với trợ lý để hỏi về phong cách, vật liệu, màu sắc, cách bố trí và phương án vừa tạo.

### 7.2. Quản trị và hệ thống

1. Quản lý danh sách phong cách, prompt và dữ liệu tri thức.
2. Lưu yêu cầu thiết kế, ảnh đầu vào, ảnh kết quả và trạng thái xử lý.
3. Xử lý lỗi gọi dịch vụ AI, lỗi tải ảnh và lỗi mạng.
4. Kiểm soát dữ liệu cấu hình, API key, loại ảnh và dung lượng ảnh tải lên.

## 8. Kỹ thuật AI áp dụng

### 8.1. Tạo ảnh thiết kế từ ảnh đầu vào

Ứng dụng sử dụng mô hình AI tạo sinh theo hướng **image-to-image**. Ảnh căn phòng và prompt có cấu trúc được gửi tới dịch vụ AI để tạo ảnh minh họa phương án mới.

Prompt được xây dựng từ các thành phần:

```text
Loại phòng + phong cách + ánh sáng + vật liệu + màu sắc
+ ràng buộc giữ bố cục kiến trúc + yêu cầu chức năng
```

Ví dụ: cùng một ảnh phòng khách có thể tạo các biến thể Scandinavian, Indochine hoặc Neoclassic, trong khi vẫn cố gắng giữ cấu trúc cửa sổ, tường và tỷ lệ phòng.

### 8.2. Chatbot tư vấn sử dụng RAG

Luồng RAG dự kiến:

```text
Câu hỏi người dùng
      ↓
Chuẩn hóa câu hỏi và lấy ngữ cảnh dự án hiện tại
      ↓
Embedding truy vấn
      ↓
Tìm kiếm các đoạn tri thức liên quan trong vector database
      ↓
Ghép ngữ cảnh truy hồi + thông tin thiết kế vào prompt
      ↓
LLM tạo câu trả lời tư vấn có căn cứ
```

Kho tri thức ban đầu có thể gồm:

- Mô tả các phong cách nội thất.
- Nguyên tắc phối màu, ánh sáng, vật liệu và bố trí theo từng loại phòng.
- Prompt template và mô tả thiết kế đã được kiểm duyệt.
- Câu hỏi thường gặp, lưu ý về công năng và giới hạn ngân sách ở mức tham khảo.

RAG không nhằm thay thế chuyên gia mà giúp câu trả lời bám sát tri thức đã chọn lọc và ngữ cảnh của người dùng.

## 9. Kiến trúc hệ thống đề xuất

```text
┌───────────────────────────────────────────────┐
│ Android App: Kotlin, Jetpack Compose           │
│ Upload ảnh · Chọn style · Gallery · Profile    │
│ Chatbot UI                                     │
└───────────────────────┬───────────────────────┘
                        │ REST API
┌───────────────────────▼───────────────────────┐
│ ASP.NET Core .NET 8 Backend                    │
│ ├─ User / Profile / Design Project APIs        │
│ ├─ Prompt & Design Orchestration               │
│ ├─ Image storage                               │
│ ├─ AI image-generation provider                │
│ └─ Chatbot / RAG service                       │
└───────┬───────────────────┬───────────────────┘
        │                   │
┌───────▼────────┐  ┌───────▼──────────────────┐
│ SQL Server      │  │ Knowledge base + Vector  │
│ User, project,  │  │ store + LLM/Embedding    │
│ design history  │  │ provider                 │
└────────────────┘  └──────────────────────────┘
```

## 10. Dữ liệu và mô hình dữ liệu chính

| Nhóm dữ liệu | Mục đích |
|---|---|
| User/Profile | Nhận dạng người dùng, tên, avatar và thông tin hồ sơ. |
| DesignResult/Project | Lưu ảnh gốc, ảnh AI, prompt, trạng thái, thời gian tạo và xóa mềm. |
| StyleAesthetic | Lưu thông tin phong cách và tham số prompt. |
| RoomStylePrompt | Lưu prompt theo tổ hợp phong cách và loại phòng. |
| ChatSession/ChatMessage (đề xuất) | Lưu lịch sử hội thoại và ngữ cảnh thiết kế. |
| KnowledgeDocument/Chunk (đề xuất) | Lưu tài liệu tri thức, đoạn văn, embedding và nguồn tham chiếu. |

## 11. Quy trình nghiệp vụ minh họa

### 11.1. Quy trình tạo thiết kế

```text
Tải ảnh → Chọn tính năng → Chọn loại phòng → Chọn phong cách
→ Sinh prompt theo ngữ cảnh → Gọi AI tạo ảnh
→ Theo dõi trạng thái → Hiển thị và lưu kết quả vào Gallery
```

### 11.2. Quy trình tư vấn bằng chatbot

```text
Người dùng hỏi về phòng/ảnh/kết quả vừa tạo
→ Hệ thống truy hồi kiến thức và ngữ cảnh thiết kế liên quan
→ Chatbot trả lời, giải thích và gợi ý bước tiếp theo
→ Người dùng có thể yêu cầu tạo biến thể thiết kế mới
```

## 12. Phương pháp triển khai và đánh giá

### 12.1. Phương pháp thực hiện

1. Khảo sát yêu cầu người dùng và phân tích các ứng dụng liên quan.
2. Thiết kế kiến trúc, cơ sở dữ liệu, API và wireframe giao diện.
3. Phát triển theo từng vòng lặp: mobile → backend → tích hợp AI → kiểm thử.
4. Xây dựng và đánh giá bộ tri thức RAG theo từng chủ đề nội thất.
5. Kiểm thử trên điện thoại Android thật qua USB và các điều kiện mạng khác nhau.

### 12.2. Tiêu chí đánh giá

| Nhóm | Tiêu chí gợi ý |
|---|---|
| Chức năng | Hoàn thành luồng tải ảnh → chọn style → tạo → xem lại thiết kế. |
| Hiệu năng | Thời gian phản hồi API, thời gian tạo ảnh và tỷ lệ yêu cầu thành công. |
| Độ tin cậy | Xử lý được mất kết nối, API lỗi, ảnh không hợp lệ và trạng thái xử lý thất bại. |
| RAG | Mức liên quan của tài liệu truy hồi; câu trả lời bám theo ngữ cảnh và hạn chế bịa thông tin. |
| Trải nghiệm | Đánh giá người dùng về dễ sử dụng, hữu ích và hài lòng; có thể dùng bảng hỏi SUS. |
| Bảo mật | Không công khai API key, kiểm soát ảnh tải lên và chỉ cho phép truy cập dữ liệu đúng người dùng. |

## 13. Sản phẩm đầu ra dự kiến

- Ứng dụng Android Home AI có thể cài đặt và chạy trên điện thoại thật.
- Backend ASP.NET Core, database SQL Server và tài liệu API.
- Chức năng tạo ảnh thiết kế theo phong cách nội thất.
- Gallery, Profile và quản lý lịch sử dự án.
- Module chatbot tư vấn; nếu hoàn thiện RAG sẽ kèm kho tri thức, embedding và truy hồi ngữ cảnh.
- Báo cáo đồ án, sơ đồ kiến trúc, video demo và hướng dẫn cài đặt.

## 14. Tiến độ tham khảo (10 tuần)

| Giai đoạn | Nội dung chính |
|---|---|
| Tuần 1–2 | Khảo sát, xác định yêu cầu, nghiên cứu AI tạo ảnh và RAG. |
| Tuần 3 | Thiết kế kiến trúc, CSDL, API, giao diện và bộ tiêu chí đánh giá. |
| Tuần 4–5 | Hoàn thiện luồng Android, API, Profile/Gallery và độ ổn định kết nối. |
| Tuần 6 | Hoàn thiện prompt theo loại phòng/phong cách, kiểm thử tạo ảnh. |
| Tuần 7–8 | Xây dựng kho tri thức, embedding, retrieval và chatbot RAG. |
| Tuần 9 | Kiểm thử chức năng, hiệu năng, người dùng và khắc phục lỗi. |
| Tuần 10 | Viết báo cáo, chuẩn bị slide, video và kịch bản demo. |

## 15. Điểm nhấn khi trình bày với giảng viên

> Đề tài không chỉ gọi một API tạo ảnh. Giá trị chính là xây dựng một quy trình khép kín trên di động: **ảnh không gian thực tế → lựa chọn nhu cầu → AI tạo phương án → quản lý dự án → chatbot giải thích và tư vấn tiếp theo dựa trên tri thức nội thất**.

Khi demo, nên trình bày theo kịch bản:

1. Chọn ảnh một căn phòng thật.
2. Chọn loại phòng và một phong cách nội thất.
3. Tạo ảnh minh họa kết quả bằng AI.
4. Mở Gallery để cho thấy dự án được lưu lại.
5. Hỏi chatbot: “Vì sao phong cách này phù hợp?”, “Nên chọn vật liệu và màu sắc nào?”
6. Cho thấy chatbot sử dụng ngữ cảnh thiết kế và tri thức nội thất thay vì trả lời chung chung.

## 16. Rủi ro và định hướng xử lý

| Rủi ro | Hướng xử lý |
|---|---|
| API tạo ảnh chậm hoặc lỗi | Dùng trạng thái Pending/Completed/Failed, polling, timeout và Retry. |
| Ảnh AI làm sai bố cục phòng | Bổ sung ràng buộc kiến trúc trong prompt và cho phép tạo biến thể. |
| Chatbot trả lời thiếu căn cứ | Dùng RAG, giới hạn phạm vi tri thức và hiển thị nguồn/ngữ cảnh khi phù hợp. |
| Lộ API key hoặc dữ liệu ảnh | Giữ config ngoài Git, xác thực API, kiểm tra file upload và quản lý storage. |
| Phạm vi quá lớn | Ưu tiên luồng tạo thiết kế ổn định, sau đó làm chatbot RAG ở mức MVP. |

## 17. Bố cục báo cáo dự kiến

1. **Chương 1 — Tổng quan:** lý do chọn đề tài, mục tiêu, phạm vi và đóng góp.
2. **Chương 2 — Cơ sở lý thuyết:** AI tạo sinh, prompt engineering, RAG, Android và kiến trúc REST API.
3. **Chương 3 — Phân tích và thiết kế:** yêu cầu, use case, CSDL, kiến trúc, API và UI/UX.
4. **Chương 4 — Cài đặt hệ thống:** Front-end, Back-end, AI image generation và chatbot/RAG.
5. **Chương 5 — Kiểm thử và đánh giá:** kịch bản kiểm thử, số liệu, khảo sát người dùng và phân tích kết quả.
6. **Chương 6 — Kết luận và hướng phát triển:** kết quả đạt được, hạn chế và kế hoạch mở rộng.
