# Kế hoạch thực hiện đồ án tốt nghiệp — Home AI

## Đề tài

**Nghiên cứu và xây dựng ứng dụng di động hỗ trợ thiết kế không gian sống từ ảnh bằng trí tuệ nhân tạo, tích hợp chatbot tư vấn dựa trên kỹ thuật RAG.**

## Mục tiêu sản phẩm

Xây dựng ứng dụng Android cho phép người dùng tải ảnh không gian sống, chọn yêu cầu thiết kế để AI tạo ảnh minh họa, lưu kết quả vào Gallery và trao đổi với chatbot tư vấn nội thất. Chatbot sử dụng RAG để truy hồi tri thức nội thất có nguồn trước khi trả lời; việc tạo ảnh chỉ diễn ra sau khi người dùng xác nhận Design Brief.

## Kế hoạch thực hiện

| TT | Nội dung thực hiện | Thời gian | Kết quả dự kiến |
|---:|---|---:|---|
| 1 | Nghiên cứu và rà soát công nghệ đang dùng: Kotlin, Jetpack Compose, ASP.NET Core .NET 8, EF Core/SQL Server, API tạo ảnh AI, LLM, embedding và RAG. Đánh giá mã nguồn hiện có để xác định phần đã hoàn thành và phần cần bổ sung. | 1 tuần | Nắm được kiến trúc hệ thống, quy trình tạo ảnh AI và hướng triển khai chatbot RAG phù hợp với dự án. |
| 2 | Phân tích yêu cầu; xác định chức năng, phạm vi và luồng nghiệp vụ. Chuẩn bị dữ liệu style/room type, tài liệu tri thức nội thất có nguồn, câu hỏi đánh giá RAG và kịch bản demo. | 2 tuần | Có đặc tả chức năng, kiến trúc đề xuất, Design Brief, danh sách dữ liệu tri thức và bộ tiêu chí đánh giá. |
| 3 | Hoàn thiện nền tảng Home AI: luồng tải ảnh → chọn loại phòng/phong cách → tạo thiết kế → theo dõi trạng thái → Gallery/Profile; chuẩn hóa API Android–backend; xử lý job tạo ảnh bền vững, lỗi mạng, kiểm tra quyền sở hữu và dữ liệu cấu hình an toàn. | 4 tuần | Ứng dụng tạo và quản lý được dự án thiết kế từ ảnh trên điện thoại thật; kết quả được lưu, xem lại và xử lý lỗi cơ bản. |
| 4 | Phát triển chatbot tư vấn đa lượt: lưu conversation/message, tạo và cập nhật Design Brief, xác nhận trước khi tạo ảnh. Xây dựng RAG gồm ingestion tài liệu, chunking, embedding, truy hồi top-k và hiển thị citation; liên kết Chat với `DesignResult` và Gallery. | 3 tuần | Chatbot tư vấn bằng text có nguồn tham khảo, thu thập yêu cầu thiết kế nhiều lượt và tạo ảnh sau khi người dùng xác nhận. |
| 5 | Hoàn thiện và kiểm thử hệ thống: kiểm thử API, UI Android và luồng trên thiết bị thật; kiểm tra mất mạng/retry, job xử lý lâu, lịch sử chat, quyền dữ liệu và lỗi provider. Đánh giá RAG bằng cùng bộ câu hỏi ở hai chế độ có/không có RAG. | 3 tuần | Ứng dụng vận hành ổn định cho kịch bản demo; có số liệu về retrieval, citation, độ hữu ích phản hồi, thời gian xử lý và tỷ lệ thành công. |
| 6 | Hoàn thiện báo cáo, tài liệu hướng dẫn cài đặt/chạy dự án, slide, video và kịch bản bảo vệ; sửa các lỗi phát hiện trong quá trình diễn tập. | 2 tuần | Hoàn thiện sản phẩm, báo cáo đồ án và tài liệu bảo vệ để nộp. |

**Tổng thời gian dự kiến: 15 tuần.**

## Sản phẩm đầu ra dự kiến

- Ứng dụng Android Home AI chạy trên điện thoại thật.
- Backend ASP.NET Core quản lý người dùng, dự án thiết kế, ảnh kết quả và trạng thái xử lý.
- Luồng AI image-to-image tạo phương án thiết kế nội thất từ ảnh đầu vào.
- Gallery lưu lịch sử dự án và màn hình Profile cơ bản.
- Chatbot tư vấn nội thất đa lượt với Design Brief và cơ chế xác nhận tạo ảnh.
- Kho tri thức RAG có nguồn, embedding, retrieval và citation trong câu trả lời.
- Báo cáo đánh giá so sánh chatbot có RAG và không sử dụng RAG.

## Giới hạn của đề tài

- Ảnh tạo ra là phương án minh họa, không thay thế bản vẽ kỹ thuật hoặc tư vấn kiến trúc chuyên nghiệp.
- Chatbot không tư vấn kết cấu, điện nước, pháp lý xây dựng hoặc báo giá thi công chính xác.
- Không huấn luyện mô hình tạo ảnh hay LLM từ đầu; hệ thống tích hợp API/mô hình có sẵn.
- Phạm vi chính là Android; chưa phát triển iOS và web.
