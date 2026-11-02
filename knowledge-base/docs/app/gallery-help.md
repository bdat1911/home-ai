---
id: app-gallery-help-01
title: Xem dự án trong Gallery
language: vi
document_type: app_guide
styles: []
room_types: []
topics: [app, gallery, project, history]
source_type: internal_derived
source_ids: [app-gallery-ui, app-design-api]
source_url: null
license_note: Hướng dẫn mới do nhóm biên soạn từ mã nguồn ứng dụng
author: Home AI content draft
reviewer: pending
version: 1
reviewed_at: null
status: draft
---

# Xem dự án trong Gallery

## Hướng dẫn
Tab Gallery hiển thị các dự án thiết kế thuộc người dùng hiện tại. Khi vừa tạo ảnh mà dự án còn xử lý, có thể tải lại để xem trạng thái mới. Màn Gallery có nút thử lại khi tải thất bại và nút tải thêm khi còn dự án. Nút Delete có hộp xác nhận; ở backend đây là xóa mềm khỏi lịch sử hiển thị, không phải bảo đảm ảnh và mọi dữ liệu đã bị xóa vĩnh viễn khỏi các dịch vụ liên quan.

Nếu Gallery trống, trước hết phân biệt “chưa có dự án” với “không kết nối được máy chủ”. Kiểm tra lại server trong My Profile, sau đó thử tải lại. Dự án không xuất hiện cũng có thể liên quan đến định danh người dùng; không nên thay user-id thủ công để tìm dự án của người khác.

## Cần hỏi thêm
Bạn thấy màn trống hay thông báo lỗi, vừa tạo ảnh xong hay đã có dự án cũ, và kết nối server hiện có hoạt động không?

## Giới hạn
Chatbot không tự khôi phục dự án đã xóa hoặc đọc dự án riêng của người khác. Nếu cần khôi phục/xóa vĩnh viễn, phải có quy trình riêng được xác nhận.

## Nguồn
- app-gallery-ui: trạng thái rỗng/lỗi, tải lại, xóa và tải thêm.
- app-design-api: danh sách và xóa mềm dự án.
