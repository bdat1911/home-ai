---
id: app-profile-connection-01
title: My Profile và kết nối máy chủ khi thử ứng dụng
language: vi
document_type: app_guide
styles: []
room_types: []
topics: [app, profile, server, connection, usb]
source_type: internal_derived
source_ids: [app-profile-ui, app-connection-ui, app-chat-api]
source_url: null
license_note: Hướng dẫn mới do nhóm biên soạn từ mã nguồn ứng dụng
author: Home AI content draft
reviewer: pending
version: 1
reviewed_at: null
status: draft
---

# My Profile và kết nối máy chủ khi thử ứng dụng

## Hướng dẫn
My Profile cho phép xem/sửa tên hiển thị, ảnh đại diện và thống kê dự án. Trong bản debug có thẻ Server connection để kiểm tra và lưu địa chỉ API. Khi thử bằng điện thoại USB, chạy backend ở cổng 5207, chạy adb reverse tcp:5207 tcp:5207, rồi dùng nút Use USB localhost (test & save) để đặt http://127.0.0.1:5207/. Địa chỉ 127.0.0.1 trên điện thoại chỉ đến máy tính sau khi adb reverse thành công; nếu rút cáp hoặc ADB mất kết nối, cần thiết lập lại.

Đổi địa chỉ sang IP Wi-Fi chỉ có tác dụng khi điện thoại và máy tính liên lạc được trong cùng mạng hoặc có hạ tầng truy cập phù hợp. API Chat thử nghiệm hiện còn giới hạn loopback, nên IP LAN có thể mở các API khác nhưng Chat trả 404.

## Cần hỏi thêm
Bạn dùng USB hay Wi-Fi, ứng dụng debug hay release, thẻ Test connection báo gì và backend có đang chạy không?

## Giới hạn
Không yêu cầu người dùng gửi mật khẩu, chuỗi kết nối database, API key hay ảnh hồ sơ riêng để chẩn đoán. Bản release cần backend HTTPS và xác thực an toàn trước khi dùng ở ngoài máy phát triển.

## Nguồn
- app-profile-ui: nội dung My Profile.
- app-connection-ui: điều khiển địa chỉ server.
- app-chat-api: giới hạn Chat ở Development/loopback.
