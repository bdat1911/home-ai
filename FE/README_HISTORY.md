# HƯỚNG DẪN CHI TIẾT & TÀI LIỆU TÍNH NĂNG: HISTORY + DETAIL + SOFT DELETE

Tài liệu này giải thích chi tiết toàn bộ cấu trúc mã nguồn, các lớp đối tượng, tệp giao diện XML và cấu hình hệ thống đã được triển khai cho tính năng **Lịch sử dự án (Project History)**, **Chi tiết dự án (Project Detail)** và **Xóa mềm (Soft Delete)** trong ứng dụng **ReRoom (AI Interior Design)**.

---

## 📂 1. Cấu trúc thư mục & các tệp được thêm mới/chỉnh sửa

```
ReRoom/
├── app/
│   ├── src/main/java/com/example/revroom/
│   │   ├── core/
│   │   │   ├── network/
│   │   │   │   └── ApiClient.kt (Chỉnh sửa: Expose retrofitInstance để Java dùng chung)
│   │   │   └── utils/
│   │   │       └── DateFormatter.java [MỚI] (Hàm tiện ích định dạng ngày tháng)
│   │   ├── data/remote/
│   │   │   ├── ApiService.java [MỚI] (Interface Retrofit quản lý API lịch sử và xóa)
│   │   │   ├── ProjectHistoryResponse.java [MỚI] (DTO phản hồi danh sách phân trang)
│   │   │   └── DeleteResponse.java [MỚI] (DTO phản hồi trạng thái xóa)
│   │   └── features/history/
│   │       ├── model/
│   │       │   └── ProjectModel.java [MỚI] (Model thực thể lưu trữ thông tin dự án)
│   │       ├── viewmodel/
│   │       │   └── HistoryViewModel.java [MỚI] (ViewModel Java quản lý trạng thái & API)
│   │       ├── adapters/
│   │       │   └── ProjectAdapter.java [MỚI] (Adapter RecyclerView quản lý danh sách)
│   │       └── ui/
│   │           ├── HistoryActivity.kt [MỚI] (Giao diện hiển thị danh sách cuộn vô hạn)
│   │           ├── ProjectDetailActivity.kt [MỚI] (Giao diện chi tiết dự án & xác nhận xóa)
│   │           └── ResultActivity.kt [MỚI] (Giao diện xem lại ảnh kết quả)
│   │
│   ├── src/main/res/
│   │   ├── drawable/
│   │   │   ├── bg_status_completed.xml [MỚI] (Màu nền xanh lá cho trạng thái Hoàn thành)
│   │   │   ├── bg_status_pending.xml [MỚI] (Màu nền cam cho trạng thái Đang xử lý)
│   │   │   ├── bg_status_failed.xml [MỚI] (Màu nền đỏ cho trạng thái Thất bại)
│   │   │   ├── ic_gallery_empty.xml [MỚI] (Hình vẽ vector biểu diễn danh sách rỗng)
│   │   │   └── ic_back_arrow.xml [MỚI] (Hình vẽ vector nút quay lại)
│   │   └── layout/
│   │       ├── activity_history.xml [MỚI] (Layout XML màn hình danh sách lịch sử)
│   │       ├── activity_project_detail.xml [MỚI] (Layout XML màn hình chi tiết dự án)
│   │       ├── activity_result.xml [MỚI] (Layout XML màn hình xem trước kết quả)
│   │       └── item_project.xml [MỚI] (Layout XML cho từng tấm thẻ CardView dự án)
│   │
│   ├── AndroidManifest.xml (Chỉnh sửa: Đăng ký các Activity mới)
│   └── build.gradle.kts (Chỉnh sửa: Kích hoạt ViewBinding và thêm thư viện bổ trợ)
└── local.properties [MỚI] (Cấu hình cục bộ đường dẫn Android SDK)
```

---

## 🛠️ 2. Giải thích chi tiết các thành phần hệ thống

### ⚙️ A. CẤU HÌNH HỆ THỐNG & LIÊN KẾT MẠNG

#### 1. Kích hoạt ViewBinding & Thư viện ([build.gradle.kts](file:///d:/nhom5/FE/ReRoom/app/build.gradle.kts))
Để xây dựng giao diện bằng XML cổ điển theo phong cách Senior Android và không ảnh hưởng tới Jetpack Compose hiện tại, tôi đã kích hoạt ViewBinding trong khối `buildFeatures`:
```kotlin
buildFeatures {
    compose = true
    buildConfig = true
    viewBinding = true // Cho phép tự động tạo các lớp Binding từ XML layouts
}
```
Đồng thời, thêm các thư viện bổ trợ cần thiết cho View truyền thống:
- `androidx.recyclerview:recyclerview`: Để hiển thị danh sách cuộn tối ưu.
- `androidx.swiperefreshlayout:swiperefreshlayout`: Hỗ trợ thao tác vuốt từ trên xuống để refresh danh sách.
- `com.github.bumptech.glide:glide`: Thư viện tải và cache ảnh hàng đầu thế giới dành cho XML-Views.
- `com.google.android.material:material`: Cung cấp các CardView bo góc, hiệu ứng chạm Ripple, Dialogs hiện đại.

#### 2. Cầu nối Retrofit Kotlin-Java ([ApiClient.kt](file:///d:/nhom5/FE/ReRoom/app/src/main/java/com/example/revroom/core/network/ApiClient.kt))
Do `ApiClient` ban đầu được viết bằng Kotlin Object và che giấu đi đối tượng `retrofit` (Private), tôi đã viết thêm một Getter công khai để cầu nối từ Java gọi sang một cách dễ dàng:
```kotlin
val retrofitInstance: Retrofit
    get() = retrofit // Cho phép lớp Java gọi: ApiClient.INSTANCE.getRetrofitInstance()
```
Điều này đảm bảo toàn bộ ứng dụng dùng chung **duy nhất một OkHttpClient**, giúp tiết kiệm tài nguyên bộ nhớ, chia sẻ cấu hình timeout, logging interceptor và địa chỉ Base URL.

#### 3. Các API Endpoints ([ApiService.java](file:///d:/nhom5/FE/ReRoom/app/src/main/java/com/example/revroom/data/remote/ApiService.java))
Thiết lập Interface Retrofit cho Java chứa hai API chính kết nối trực tiếp với backend C# ASP.NET Core:
- **Tải danh sách**: `GET api/design/projects?page={page}&pageSize=10`
- **Xóa dự án**: `DELETE api/design/{designId}`
Cả 2 API này đều bắt buộc phải truyền mã định danh thiết bị độc bản lên thông qua Header `@Header("user-id") String userId` để backend xác thực quyền sở hữu dự án.

---

### 📦 B. LỚP TIỆN ÍCH & DỮ LIỆU (MODELS / DTOS)

#### 1. Lớp Định dạng ngày tháng ([DateFormatter.java](file:///d:/nhom5/FE/ReRoom/app/src/main/java/com/example/revroom/core/utils/DateFormatter.java))
Backend trả về thời gian dạng ISO 8601 UTC như `"2026-05-23T07:38:46Z"`. Lớp này sẽ phân tích và chuyển đổi thành dạng chuỗi ngày tháng Việt Nam hiển thị trên thẻ dự án:
```
"2026-05-23T07:38:46Z"  --->  "23/05/2026"
```
Được trang bị cơ chế bắt lỗi ngoại lệ thông minh, nếu quá trình định dạng thông qua `SimpleDateFormat` thất bại, nó sẽ kích hoạt bộ cắt chuỗi con (substring) để lấy chính xác các phần tử ngày-tháng-năm, đảm bảo **an toàn tuyệt đối không bao giờ gây crash app**.

#### 2. Dữ liệu thực thể dự án ([ProjectModel.java](file:///d:/nhom5/FE/ReRoom/app/src/main/java/com/example/revroom/features/history/model/ProjectModel.java))
Khớp hoàn toàn các thuộc tính cơ sở dữ liệu từ backend C#: `designId`, `originalImageUrl`, `designedImageUrl`, `status`, `createdAt`.
Đặc biệt sử dụng kỹ thuật cấu hình Gson thông minh hỗ trợ ánh xạ kép nhằm xử lý tình huống tên thuộc tính không đồng nhất trên các endpoint khác nhau:
```java
@SerializedName(value = "id", alternate = {"designId"})
private String designId; // Chấp nhận cả trường "id" và "designId" trong JSON phản hồi
```

---

### 🧠 C. KIẾN TRÚC LOGIC MVVM (VIEWMODEL & ADAPTER)

#### 1. Quản lý luồng xử lý và dữ liệu ([HistoryViewModel.java](file:///d:/nhom5/FE/ReRoom/app/src/main/java/com/example/revroom/features/history/viewmodel/HistoryViewModel.java))
ViewModel là bộ não xử lý toàn bộ hoạt động bất đồng bộ:
- **Tải trang lịch sử (`fetchProjects`)**: Gọi API Retrofit. Khi có dữ liệu trả về, nó sẽ tính toán số trang hiện tại so với `totalPages` để cập nhật trạng thái `hasMore` (quyết định có cho phép cuộn tải tiếp hay dừng).
- **Lọc trùng lặp dữ liệu**: Sử dụng thuật toán so sánh ID để loại bỏ triệt để các dự án bị lặp thẻ khi người dùng liên tục cuộn lên xuống trong điều kiện mạng lag.
- **Xóa mềm (`deleteProject`)**: Gửi request DELETE lên server.
- **Xóa nhanh tại chỗ (`removeProjectFromList`)**: Cung cấp một phương thức cho phép View xóa trực tiếp một phần tử ra khỏi LiveData danh sách trong bộ nhớ tạm để UI đồng bộ ngay tức khắc mà không cần reload tốn băng thông mạng.

#### 2. Bộ nạp danh sách mượt mà ([ProjectAdapter.java](file:///d:/nhom5/FE/ReRoom/app/src/main/java/com/example/revroom/features/history/adapters/ProjectAdapter.java))
- **DiffUtil**: Tích hợp lớp so sánh danh sách động. Khi có phần tử bị xóa hoặc thêm mới, nó chỉ vẽ lại và dịch chuyển duy nhất phần tử bị tác động đó thay vì vẽ lại toàn bộ danh sách, đem lại cảm giác cuộn mượt mà như các ứng dụng cao cấp.
- **Glide Smart Image**: Ưu tiên tải ảnh đã được AI thiết kế (`designedImageUrl`). Nếu thiết kế chưa hoàn thành (đang xử lý hoặc lỗi), nó sẽ tự động lấy ảnh gốc làm ảnh đại diện để giao diện không bị khoảng trống.
- **Dynamic Badges**: Gắn các nền màu phân biệt trạng thái của dự án (Xanh lá: Đã xong, Cam: Đang xử lý, Đỏ: Lỗi).

---

### 🎨 D. GIAO DIỆN & HOẠT ĐỘNG (ACTIVITIES & LAYOUTS XML)

#### 1. Màn hình Lịch sử ([HistoryActivity.kt](file:///d:/nhom5/FE/ReRoom/app/src/main/java/com/example/revroom/features/history/ui/HistoryActivity.kt))
- Sử dụng cấu hình hiển thị dạng lưới **Grid 2 cột** rất bắt mắt và trực quan cho các app xem ảnh thiết kế.
- **Bộ lắng nghe cuộn vô tận (Endless Scroll Listener)**:
  ```kotlin
  if ((visibleItemCount + pastVisibleItems) >= totalItemCount - 2) {
      val nextPage = viewModel.currentPage + 1
      viewModel.fetchProjects(nextPage) // Tải trang kế tiếp khi cuộn gần tới đáy
  }
  ```
- Quan sát trạng thái LiveData để ẩn hiện các layout thích hợp:
  - Khi đang tải trang đầu tiên -> Hiện vòng tròn xoay `ProgressBar`.
  - Khi danh sách trống -> Hiện layout Vector `layoutEmpty` vẽ minh họa đẹp mắt "No projects yet".
  - Khi mất kết nối hoặc API lỗi -> Hiện layout cảnh báo `layoutError` màu đỏ kèm nút "Retry" lớn để người dùng bấm tải lại dễ dàng.

#### 2. Màn hình Chi tiết dự án ([ProjectDetailActivity.kt](file:///d:/nhom5/FE/ReRoom/app/src/main/java/com/example/revroom/features/history/ui/ProjectDetailActivity.kt))
- Hiển thị hai bức ảnh lớn xếp dọc giúp người dùng dễ dàng so sánh kết quả Trước (Before) và Sau (After).
- **Hộp thoại cảnh báo an toàn**: Hiện Dialog Material xác nhận chắc chắn trước khi tiến hành xóa dự án.
- **Màn hình khóa thông minh (Delete Loading Overlay)**: Khi tiến trình xóa đang chạy, một Layout tối màu bao phủ toàn màn hình có độ ưu tiên cao (`elevation="99dp"`) sẽ xuất hiện. Layout này chặn mọi hành động click chuột hoặc ấn phím của người dùng, hiển thị dòng chữ "Deleting project..." kèm xoay tròn tiến trình, giúp hệ thống hoạt động ổn định không bị lỗi xung đột lệnh.
- **Cơ chế phản hồi kết quả ngược (Activity Results)**:
  Khi xóa dự án thành công trên database, màn hình chi tiết sẽ gửi tín hiệu kết quả quay lại màn hình lịch sử:
  ```kotlin
  val resultIntent = Intent().apply { putExtra(EXTRA_DELETED_ID, designId) }
  setResult(Activity.RESULT_OK, resultIntent)
  finish() // Đóng màn hình chi tiết
  ```
  Tại màn hình Lịch sử, bộ đón nhận sẽ lập tức bắt được ID này và xóa thẻ đó khỏi danh sách hiển thị với hiệu ứng slide cực đẹp mắt.

#### 3. Màn hình Xem lại kết quả ([ResultActivity.kt](file:///d:/nhom5/FE/ReRoom/app/src/main/java/com/example/revroom/features/history/ui/ResultActivity.kt))
- Hỗ trợ hiển thị ảnh thiết kế ở chế độ toàn màn hình trên nền tối (Dark Theme), giúp tối ưu hóa thị giác để người dùng có thể thưởng thức tác phẩm thiết kế nội thất tốt nhất.

---

## 🚀 3. Hướng dẫn tích hợp vào dự án hiện tại của bạn

Vì ứng dụng chính của bạn hiện tại đang sử dụng cấu trúc điều hướng bằng **Jetpack Compose** (`MainActivity` mở `AppNavigation`), để mở màn hình Lịch sử truyền thống bằng XML này từ bất kỳ nút bấm Compose nào (Ví dụ: nút bấm Avatar trong `HistoryScreen.kt` hoặc biểu tượng danh mục):

Bạn chỉ cần gọi mã khởi chạy Intent như sau:

```kotlin
// 1. Lấy context hiện tại của màn hình Composable
val context = LocalContext.current

// 2. Khởi tạo Intent trỏ tới HistoryActivity dạng Native
val intent = Intent(context, HistoryActivity::class.java)

// 3. Khởi chạy Activity
context.startActivity(intent)
```

Ví dụ, tích hợp vào sự kiện nhấn nút của Jetpack Compose:
```kotlin
Button(
    onClick = {
        val intent = Intent(context, HistoryActivity::class.java)
        context.startActivity(intent)
    }
) {
    Text("Xem lịch sử thiết kế")
}
```

---

## 🧪 4. Hướng dẫn kiểm thử tính năng (Manual Testing)

1. **Kiểm tra Tải trang đầu**: Mở màn hình lịch sử. Xem vòng quay load dữ liệu xuất hiện.
2. **Kiểm tra Phân trang**: Cuộn nhanh xuống đáy danh sách. Xác nhận dữ liệu trang 2, 3 được tải thêm mượt mà và không lặp lại thẻ.
3. **Kiểm tra Làm mới**: Vuốt màn hình từ trên xuống. Xác nhận danh sách được làm mới, số trang reset về trang 1.
4. **Kiểm tra Trạng thái Mạng lỗi**: Tắt mạng (Wifi/Cellular) rồi bấm làm mới. Xác nhận màn hình chuyển sang giao diện lỗi màu đỏ kèm nút "Thử lại". Bật lại mạng và nhấn "Thử lại" để xem dữ liệu hiển thị lại.
5. **Kiểm tra Xem ảnh lớn**: Bấm vào một dự án bất kỳ, xem màn hình chi tiết mở ra đầy đủ 2 ảnh Trước/Sau. Bấm **View Again** để xem ảnh lớn toàn màn hình trên nền tối.
6. **Kiểm tra Xóa mềm**:
   - Bấm **Delete Project**. Chọn "Cancel" -> Xác nhận dự án không bị xóa.
   - Bấm **Delete Project** lần nữa. Chọn "Yes, Delete" -> Màn hình khóa tiến trình xuất hiện -> Nhận thông báo xóa thành công bằng Toast -> Quay trở về danh sách -> Xem tấm thẻ dự án tự động biến mất mượt mà.
