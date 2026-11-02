---
id: app-chat-help-01
title: Cách dùng Chat tư vấn và lịch sử hội thoại
language: vi
document_type: app_guide
styles: []
room_types: []
topics: [app, chat, history, advice]
source_type: internal_derived
source_ids: [app-chat-ui, app-chat-api]
source_url: null
license_note: Hướng dẫn mới do nhóm biên soạn từ mã nguồn ứng dụng
author: Home AI content draft
reviewer: pending
version: 1
reviewed_at: null
status: draft
---

# Cách dùng Chat tư vấn và lịch sử hội thoại

## Hướng dẫn
Tab Chat là nơi tư vấn nội thất. Bạn có thể nhập câu hỏi về phong cách, màu, phòng hoặc cách dùng ứng dụng rồi gửi; câu trả lời dựa trên tài liệu sẽ có mục Nguồn tham khảo. Dấu cộng tạo cuộc trò chuyện mới; mục Lịch sử cho phép chọn lại các cuộc trò chuyện đã lưu. Khi trò chuyện, phòng và phong cách bạn chọn được ghi vào Yêu cầu thiết kế. Bạn có thể sửa yêu cầu này, chọn ảnh phòng và bấm xác nhận để tạo ảnh; bot không tự tạo ảnh nếu bạn chưa xác nhận. Kết quả hiện ngay trong cuộc trò chuyện và được lưu ở Gallery.

Nếu Chat báo lỗi kết nối, thử Tải lại sau khi xác nhận backend đang chạy. Chat API hiện chỉ cho môi trường Development qua kết nối loopback; với điện thoại cắm USB cần adb reverse và địa chỉ localhost trên điện thoại. Máy chủ công khai chưa bật Chat vì chưa có xác thực an toàn.

## Cần hỏi thêm
Bạn hỏi về nội dung tư vấn hay lỗi kết nối, và điện thoại có đang kết nối USB với máy chạy backend không?

## Giới hạn
Bot không xem được ảnh, giá thị trường, dữ liệu cá nhân hay trạng thái dự án nếu thông tin đó không được cung cấp qua API phù hợp. Đừng nhập mật khẩu/khóa API vào hội thoại.

## Nguồn
- app-chat-ui: chế độ tư vấn, yêu cầu thiết kế và lịch sử.
- app-chat-api: phạm vi API hội thoại.
