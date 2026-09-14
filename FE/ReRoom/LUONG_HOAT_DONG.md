# Luồng Hoạt Động Dự Án ReRoom

Tài liệu này mô tả các luồng nghiệp vụ chính trong ứng dụng ReRoom (FE).

## 1. Luồng Thiết Kế Nội Thất (Interior Design Flow)
Đây là luồng chính cho phép người dùng thay đổi thiết kế bên trong căn phòng.
1. **Màn hình chính (Interior Home):** Người dùng chọn một trong các tính năng:
    - Interior Design (Thiết kế tổng thể)
    - Furnish Empty Room (Thêm nội thất vào phòng trống)
    - Remove Furniture (Xóa nội thất hiện có)
2. **Tải ảnh (Upload Photo):** Người dùng chọn ảnh từ thư viện hoặc chụp ảnh căn phòng cần thiết kế.
3. **Chọn loại phòng (Room Type):** (Chỉ áp dụng cho nội thất) Người dùng chọn loại phòng (Phòng khách, phòng ngủ, bếp, v.v.).
4. **Chọn phong cách (Style Selection):** Người dùng chọn phong cách thiết kế mong muốn (Hiện đại, tối giản, cổ điển, v.v.).
5. **Xử lý AI (Processing):** Ứng dụng gửi dữ liệu lên Server qua `DesignViewModel.createDesign()`. Trong quá trình này, ứng dụng thực hiện polling để kiểm tra trạng thái xử lý của AI.
6. **Kết quả (Processing Result):** Hiển thị ảnh sau khi đã được AI xử lý. Người dùng có thể:
    - Lưu vào lịch sử.
    - Thử lại (Retry).
    - Tạo thiết kế mới.

## 2. Luồng Thiết Kế Ngoại Thất (Exterior Design Flow)
Tương tự như nội thất nhưng tập trung vào kiến trúc bên ngoài.
1. **Màn hình Exterior Home:** Chọn các tính năng như Facade, Landscaping, Greenery.
2. **Tải ảnh:** Tải ảnh kiến trúc bên ngoài ngôi nhà.
3. **Chọn phong cách:** Chọn phong cách kiến trúc ngoại thất.
4. **Xử lý & Kết quả:** Quy trình xử lý AI tương tự luồng nội thất.

## 3. Luồng Quản Lý Lịch Sử & Bộ Sưu Tập (History/Gallery Flow)
1. **Màn hình Gallery:** Hiển thị danh sách các dự án (Project) người dùng đã thực hiện thành công.
2. **Chi tiết dự án (Project Detail):** Khi nhấn vào một dự án, người dùng có thể xem:
    - Ảnh gốc (Before).
    - Ảnh kết quả (After).
    - Thông tin chi tiết (Phong cách, loại phòng, thời gian tạo).

## 4. Luồng Trò Chuyện (Chat Flow)
- Người dùng có thể truy cập tab **Chat** từ thanh điều hướng dưới (Bottom Bar).
- Cho phép gửi nhận tin nhắn (có thể là hỗ trợ khách hàng hoặc tư vấn thiết kế).

## 5. Luồng Tài Khoản & Cài Đặt (Settings Flow)
- Truy cập từ biểu tượng Profile trong màn hình Gallery.
- Quản lý thông tin cá nhân thông qua `UserManager`.
- Thay đổi các cài đặt ứng dụng.

## Kiến Trúc Kỹ Thuật (Technical Overview)
- **Navigation:** Sử dụng Jetpack Compose Navigation với `AppNavigation.kt`.
- **State Management:** Sử dụng `ViewModel` và `StateFlow` để quản lý trạng thái UI.
- **Network:** Sử dụng `Retrofit` để giao tiếp với Backend (API Base URL: `http://10.0.2.2:5207/`).
- **Image Loading:** Sử dụng thư viện `Coil` và `Glide`.
- **Dependency Injection:** Sử dụng Factory pattern cho ViewModel.

---
*Tài liệu được tạo tự động dựa trên cấu trúc mã nguồn hiện tại.*
