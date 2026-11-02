# Home AI — chạy và kiểm tra bản cải tiến

## Chạy trên Android Emulator (mặc định)

1. Từ thư mục gốc, chạy `powershell -NoProfile -ExecutionPolicy Bypass -File .\run-emulator-dev.ps1`. Lệnh này mở AVD `Pixel_9` nếu chưa chạy, chờ khởi động xong rồi chạy backend nếu cần. Giữ cửa sổ backend mở. Dùng AVD khác bằng `-AvdName <tên>`.
2. Trong Android Studio, chọn thiết bị `Pixel_9` rồi bấm Run, hoặc chạy `.\gradlew.bat :app:installDebug` trong `FE\ReRoom`.
3. App dùng sẵn `http://10.0.2.2:5207/`, không cần `adb reverse`. Nếu app từng lưu địa chỉ khác, vào **My Profile → Server connection → Use emulator (test & save)**.

Chat thử nghiệm hoạt động trên emulator vì kết nối qua `10.0.2.2` đi vào backend như loopback.

## Kết nối trên điện thoại thật

Với điện thoại USB, chạy `powershell -NoProfile -ExecutionPolicy Bypass -File .\run-usb-dev.ps1` từ thư mục gốc. Lệnh này kiểm tra điện thoại, thiết lập `adb reverse tcp:5207 tcp:5207` rồi chạy backend nếu cần. Giữ cửa sổ backend mở; chạy lại lệnh sau khi rút/cắm USB hoặc khởi động lại ADB. Trong app debug, chọn **My Profile → Server connection → Use USB localhost (test & save)**. Không dùng IP Wi-Fi cũ cho chế độ USB.

1. Dùng USB: chạy script ở trên (nó chạy cả ADB reverse và backend). Nếu dùng LAN, chạy backend trong `BE/InteriorAI`: `dotnet run --launch-profile http`.
2. Cài APK debug mới một lần. Với LAN, điện thoại và máy tính phải ở mạng cho phép chúng liên lạc; với USB, điện thoại phải hiện trạng thái `device` trong `adb devices`.
3. Mở **My Profile → Server connection · Debug**.
4. USB: bấm **Use USB localhost (test & save)** để lưu `http://127.0.0.1:5207/`. LAN: nhập IP hiện tại của máy tính, ví dụ `http://192.168.1.10:5207/`. Không thêm `/api` hoặc `/api/design`.
5. Với địa chỉ nhập tay, bấm **Test & save** để thử và lưu khi thành công. **Test connection** chỉ kiểm tra, không đổi địa chỉ đang dùng. URL đã lưu được sử dụng ngay và giữ lại sau khi đóng app.

Khi sang Wi-Fi khác ở chế độ LAN, kiểm tra IPv4 máy tính bằng `ipconfig` rồi nhập địa chỉ mới trong app. Không cần sửa code hay build lại APK. Chat thử nghiệm hiện chỉ hoạt động trên emulator hoặc qua USB localhost, không qua LAN. Hãy kết thúc upload trước khi chuyển máy chủ; job của máy chủ cũ không được tiếp tục kiểm tra trên máy chủ mới.

`127.0.0.1` trên điện thoại là điện thoại: chỉ dùng với `adb reverse tcp:5207 tcp:5207`. `10.0.2.2` dành cho Android Emulator; `0.0.0.0` là địa chỉ bind, không phải địa chỉ để nhập vào app.

Nếu Test thất bại, kiểm tra backend đã chạy, IP/cổng, firewall TCP 5207 và việc Wi-Fi có chặn thiết bị liên lạc với nhau không. Endpoint test là `GET /api/webhook/health`: kết quả healthy chỉ xác nhận API truy cập được, không bảo đảm database hoặc nhà cung cấp AI đang hoạt động.

**Đổi URL không nối được hai mạng riêng biệt.** Muốn sử dụng từ Wi-Fi/4G ở nhiều nơi cần backend có địa chỉ truy cập phù hợp, thường là một dịch vụ HTTPS đã triển khai. Backend local vẫn cần Internet để gọi AI và lưu ảnh qua dịch vụ bên ngoài; hotspot không biến tạo ảnh AI thành chức năng offline.

## Hành vi Retry và lịch sử

- Gallery phân biệt đang tải, danh sách rỗng, lỗi và có dữ liệu; có Refresh/Retry/Load more. Lỗi refresh trên cùng server giữ lại dữ liệu đã tải.
- Khi đã có mã thiết kế, **Check status again** chỉ đọc trạng thái tác vụ đó; không tạo ảnh mới.
- Nếu mất phản hồi khi gửi yêu cầu, app không biết chắc backend đã nhận hay chưa. Kiểm tra Gallery trước khi chủ động tạo lại.
- **Leave screen** chỉ rời màn hình; không khẳng định tác vụ hoặc chi phí phía provider đã bị hủy.
- Bản debug có chatbot tư vấn văn bản thử nghiệm và lưu lịch sử; API chat chỉ mở trong Development qua emulator, localhost hoặc USB reverse, chưa phải RAG. Chế độ tạo thiết kế từ ảnh + mô tả là luồng riêng.
- Theo dõi tác vụ vẫn có giới hạn: chưa có phục hồi đầy đủ sau process death hay hàng đợi backend bền vững. Có thể kiểm tra lại trong Gallery khi mở app.

## Cấu hình và build

Trong `FE/ReRoom`, sao chép cấu hình mẫu vào `local.properties` của riêng máy và giữ `sdk.dir` Android Studio đã tạo. File này không được commit.

URL debug mặc định lấy theo thứ tự: `-PhomeAiApiUrl=...` → `HOME_AI_API_URL` → `homeAiApiUrl` trong local.properties → `http://10.0.2.2:5207/`. URL được lưu trong app sẽ ưu tiên hơn mặc định debug này.

```powershell
cd FE\ReRoom
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :app:installDebug
```

Bản release không có giao diện đổi máy chủ và không cho HTTP cleartext. Cần cấu hình `homeAiReleaseApiUrl` trong local.properties, `-PhomeAiReleaseApiUrl=...` hoặc biến môi trường `HOME_AI_RELEASE_API_URL`. Giá trị phải là URL HTTPS. Build release sẽ báo lỗi nếu chưa cấu hình, tránh tạo APK với placeholder không hoạt động. Cấu hình ký APK vẫn phải được chuẩn bị riêng khi phát hành.

Trước khi công khai backend, cần hoàn thiện xác thực/ownership, giới hạn request/chi phí và quản lý secret. Các cải tiến kết nối trong đợt này không thay thế những phần đó.

## Checklist kiểm thử trên điện thoại

- [ ] Backend tắt: Styles/Profile/Gallery báo lỗi và có đường thử lại.
- [ ] Nhập sai IP: Test thất bại và không ghi đè URL đang dùng.
- [ ] Lưu IP đúng: tải được Styles/Profile/Gallery mà không cài APK lại.
- [ ] Đóng/mở app: địa chỉ đã lưu vẫn được sử dụng.
- [ ] Gallery rỗng khác với Gallery lỗi; tải thêm không nhân đôi dự án.
- [ ] Mất mạng khi đang tạo ảnh: kiểm tra trạng thái lại không tạo thêm yêu cầu POST.
- [ ] Rời màn hình: giao diện giải thích tác vụ vẫn có thể tiếp tục.
- [ ] Chat nhập chữ không ảnh: không gửi và không xóa bản nháp.
- [ ] Ảnh điện thoại lớn, xoay EXIF hoặc PNG: upload có đúng chiều và đúng MIME; ảnh vượt giới hạn có thông báo.

Các kiểm thử build/unit không thay thế checklist này và không xác minh chất lượng đầu ra của dịch vụ AI.
