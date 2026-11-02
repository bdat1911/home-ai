---
id: app-design-flow-01
title: Cách tạo thiết kế nội thất trong Home AI
language: vi
document_type: app_guide
styles: []
room_types: []
topics: [app, design, interior, exterior, photo]
source_type: internal_derived
source_ids: [app-navigation, app-design-api]
source_url: null
license_note: Hướng dẫn mới do nhóm biên soạn từ mã nguồn ứng dụng
author: Home AI content draft
reviewer: pending
version: 1
reviewed_at: null
status: draft
---

# Cách tạo thiết kế nội thất trong Home AI

## Hướng dẫn
Ở tab Interior, chọn tác vụ như Interior Design hoặc Furnish Empty Room, tải ảnh phòng, chọn loại phòng và phong cách rồi nhấn Generate design. Tác vụ Remove Furniture đi từ bước tải ảnh sang xử lý, không cần bước chọn phong cách. Sau khi gửi, ứng dụng chuyển sang màn hình chờ và có thể xem kết quả khi xử lý xong.

Nếu màn chọn phong cách trống hoặc báo không kết nối, cần kiểm tra server và thử tải lại; nút Generate design không thể hoạt động khi chưa có dữ liệu phong cách hợp lệ. Ảnh AI là phương án tham khảo, không phải bản vẽ thi công hay cam kết giữ chính xác mọi chi tiết.

## Cần hỏi thêm
Bạn đang dừng ở bước tải ảnh/chọn phòng/chọn style/xử lý, và màn hình hiện thông báo lỗi gì?

## Giới hạn
Chat tư vấn không tự tạo ảnh; ảnh chỉ được tạo khi bạn xác nhận yêu cầu thiết kế trong Chat hoặc dùng luồng Interior. Ứng dụng hiện chỉ hỗ trợ thiết kế nội thất, chưa hỗ trợ ngoại thất. Không cam kết thời gian hoàn thành hoặc chi phí mỗi ảnh khi chưa kiểm tra hệ thống.

## Nguồn
- app-navigation: luồng màn hình hiện tại.
- app-design-api: API tạo và kiểm tra kết quả.
