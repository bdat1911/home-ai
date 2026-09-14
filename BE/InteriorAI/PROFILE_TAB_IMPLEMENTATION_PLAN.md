# Kế hoạch tách My Profile thành tab riêng

## 1. Mục tiêu

Tách Profile khỏi màn Gallery và đưa thành tab chính thứ năm trên thanh điều hướng dưới.

Thứ tự điều hướng mới:

```text
Interior → Exterior → Chat → Gallery → My Profile
```

Mục tiêu UX:

- Gallery chỉ dùng để xem, mở chi tiết và xóa lịch sử thiết kế.
- My Profile là nơi quản lý avatar, tên hiển thị, thông tin tài khoản và thống kê dự án.
- Profile là tab ngang hàng, không phải màn Settings mở từ Gallery.

## 2. Hiện trạng

Hiện tại:

- `StudioTab` chỉ có `Interior`, `Exterior`, `Chat`, `Gallery`.
- `HistoryScreen` có avatar/icon Profile ở góc phải để mở `SettingsScreen`.
- `SettingsScreen` đã có UI đổi avatar, đổi tên và copy User ID.
- Backend đã có `User` với các trường `Id`, `Name`, `AvatarUrl`, `CreatedAt`, `UpdatedAt`.
- Backend đã có API profile và avatar, nên không cần migration chỉ để thêm tab Profile.

Các file chính chịu ảnh hưởng:

```text
FE/ReRoom/app/src/main/java/com/example/revroom/
├── core/ui/RevRoomScaffold.kt
├── navigation/AppNavigation.kt
├── features/history/ui/HistoryScreen.kt
├── features/auth/ui/SettingsScreen.kt
└── features/auth/viewmodel/AuthViewModel.kt

BE/InteriorAI/
├── Controllers/AuthController.cs
├── Services/AuthManager.cs
└── Models/DTOs/AuthDTOs.cs
```

## 3. Thiết kế UI đề xuất

### Bottom navigation

Thanh điều hướng có năm item:

| Vị trí | Tab | Icon đề xuất |
|---:|---|---|
| 1 | Interior | Home |
| 2 | Exterior | Landscape |
| 3 | Chat | ChatBubbleOutline |
| 4 | Gallery | Image |
| 5 | Profile | Person |

Nên hiển thị nhãn **Profile** ở bottom bar để vừa màn hình nhỏ. Tiêu đề màn hình vẫn là **My Profile**.

Nếu bắt buộc dùng nhãn `My Profile` ở bottom bar, cần cho phép text hai dòng hoặc giảm font size để tránh ellipsis.

### Màn My Profile

Nội dung giai đoạn đầu:

```text
My Profile
├── Avatar
├── Display name
├── User ID + Copy
├── Save changes
├── App version
└── (Tùy chọn) Project statistics
    ├── Total projects
    ├── Completed
    └── Processing
```

Không cần nút Back vì đây là tab chính. Android Back nên theo hành vi navigation chung của ứng dụng.

## 4. Thay đổi frontend

### 4.1. Cập nhật `StudioTab`

File: `core/ui/RevRoomScaffold.kt`

```kotlin
enum class StudioTab {
    Interior,
    Exterior,
    Chat,
    Gallery,
    Profile
}
```

### 4.2. Cập nhật bottom bar

Thêm callback `onProfile` vào `StudioScaffold` và `StudioBottomBar`.

```kotlin
BottomBarItem(
    label = "Profile",
    icon = Icons.Outlined.Person,
    selected = selectedTab == StudioTab.Profile,
    onClick = onProfile,
    modifier = Modifier.weight(1f)
)
```

Đặt item này sau Gallery.

Lưu ý: Có thể đặt `onProfile: () -> Unit = {}` trong `StudioScaffold` để không buộc sửa ngay tất cả Preview hoặc màn flow cũ. Tuy nhiên `AppNavigation` phải luôn truyền callback điều hướng thật.

### 4.3. Thêm route Profile

File: `navigation/AppNavigation.kt`

Thêm hàm:

```kotlin
fun goProfile() = go(Route.PROFILE)
```

Thêm route:

```kotlin
const val PROFILE = "profile"
```

Thêm destination:

```kotlin
composable(Route.PROFILE) {
    ProfileScreen(userManager = UserManager(LocalContext.current))
}
```

Cập nhật mapping tab:

```kotlin
private fun String?.toStudioTab(): StudioTab? = when (this) {
    Route.INTERIOR_HOME -> StudioTab.Interior
    Route.EXTERIOR_HOME -> StudioTab.Exterior
    Route.CHAT -> StudioTab.Chat
    Route.GALLERY -> StudioTab.Gallery
    Route.PROFILE -> StudioTab.Profile
    else -> null
}
```

Cập nhật animation index:

```kotlin
Route.PROFILE -> 14
```

Truyền `onProfile = ::goProfile` cho `StudioBottomBar` toàn cục.

### 4.4. Bỏ Profile khỏi Gallery

File: `features/history/ui/HistoryScreen.kt`

- Bỏ tham số `onProfileClick`.
- Bỏ avatar/icon Profile ở `TopTitleBar`.
- Bỏ import icon Person nếu không còn dùng.
- Gallery chỉ giữ tiêu đề `My projects` và nội dung lịch sử.

Điều này tránh có hai lối vào cùng một màn Profile.

### 4.5. Đổi Settings thành Profile

Khuyến nghị đổi tên:

```text
SettingsScreen.kt  → ProfileScreen.kt
SettingsScreen     → ProfileScreen
```

Có thể giữ package `features.auth.ui` ở giai đoạn đầu, nhưng tên màn hình và title cần đổi từ `Settings` thành `My Profile`.

Nên bổ sung state rõ ràng:

```text
isLoading
isSaving
errorMessage
saveSuccess
profile
```

Không chỉ ghi lỗi vào Log; UI cần hiển thị Snackbar hoặc trạng thái Retry.

### 4.6. Chừa khoảng trống cho bottom bar

`AppNavigation` đang đặt bottom bar toàn cục dạng overlay. Khi Profile trở thành tab chính, nút Save hoặc phần App version có thể bị che.

Thêm khoảng chừa đáy cho ProfileScreen:

```kotlin
Modifier
    .fillMaxSize()
    .systemBarsPadding()
    .padding(bottom = 96.dp)
```

Về lâu dài, nên truyền chiều cao bottom bar thật từ `AppNavigation` xuống màn chính thay vì dùng hằng số.

### 4.7. Sửa luồng Save name + avatar

Hiện tại, nếu người dùng đổi cả avatar lẫn tên, UI chỉ gọi upload avatar; tên mới không được lưu.

Tạo một hàm duy nhất trong ViewModel:

```kotlin
suspend fun saveProfile(name: String, avatarUri: Uri?)
```

Luồng tạm thời:

```text
Nếu có avatar mới
    → upload avatar
Nếu tên thay đổi
    → update profile name
Refresh profile
Hiển thị success hoặc error
```

Giải pháp tốt hơn là để backend nhận name và avatar trong cùng một request atomic.

## 5. Backend: phạm vi tối thiểu

Chỉ để thêm tab My Profile, backend hiện tại đã đủ:

```text
POST /api/auth/register-device
GET  /api/auth/profile/{userId}
PUT  /api/auth/profile/{userId}
POST /api/auth/upload-avatar
```

Không cần migration vì entity `User` đã có:

```text
Id
Name
AvatarUrl
CreatedAt
UpdatedAt
```

Tuy nhiên, cần sửa một số điểm trước khi đưa tính năng Profile ra môi trường public.

## 6. Backend: kiến trúc chuẩn đề xuất

Tách `ProfileController` và dùng API của user đang đăng nhập:

```text
GET    /api/v1/me
PATCH  /api/v1/me
POST   /api/v1/me/avatar
DELETE /api/v1/me/avatar
GET    /api/v1/me/summary
```

Ví dụ response profile:

```json
{
  "id": "user-id-from-token",
  "displayName": "Nguyen Van A",
  "avatarUrl": "/media/avatars/avatar_abc.jpg",
  "createdAt": "2026-09-14T00:00:00Z",
  "updatedAt": "2026-09-14T01:00:00Z"
}
```

Ví dụ response summary tùy chọn:

```json
{
  "projectCount": 12,
  "completedDesignCount": 9,
  "pendingDesignCount": 2
}
```

`summary` không cần thêm cột database; có thể query từ `DesignResults` theo user hiện tại.

## 7. Bảo mật và upload avatar

API profile hiện nhận `userId` từ path hoặc form do client gửi. Đây không phải cơ chế an toàn; ai biết ID có thể đọc/sửa profile hoặc upload avatar của người khác.

Khi làm kiến trúc chuẩn:

- Dùng JWT, Firebase Auth hoặc provider tương tự.
- Lấy user ID từ token claim, không lấy từ path/body/header.
- Thêm `[Authorize]` cho profile, avatar, design và history.
- `PATCH /me` chỉ đổi display name; không cho client tự truyền `avatarUrl`.
- Validate display name từ 1 đến 50 ký tự.
- Validate avatar: dung lượng, MIME type, magic bytes, pixel dimension.
- Re-encode ảnh server-side; không dùng `file.FileName` trực tiếp.
- Xóa avatar cũ sau khi lưu avatar mới thành công.
- Trả URL chuẩn từ backend, không để Android tự ghép `BASE_URL + uploads + filename`.
- Với production, dùng private storage hoặc signed URL thay vì public toàn bộ `wwwroot/uploads`.

Cần sửa thêm `RegisterOrGetUserAsync`: `Substring(0, 4)` có thể lỗi nếu user ID ngắn hơn bốn ký tự.

## 8. Lộ trình triển khai

### Giai đoạn 1 — Tách UI tab Profile

Thời gian: 0.5–1 ngày.

1. Thêm `StudioTab.Profile`.
2. Thêm item Profile sau Gallery.
3. Thêm `Route.PROFILE` và destination.
4. Bỏ Profile icon khỏi Gallery.
5. Đổi title Settings thành My Profile.
6. Thêm bottom padding cho ProfileScreen.

**Acceptance criteria:**

- Có năm tab theo đúng thứ tự.
- Nhấn Profile mở màn My Profile và tab được highlight.
- Gallery không còn icon Profile.
- Nút Save không bị bottom bar che.

### Giai đoạn 2 — Hoàn thiện lưu profile

Thời gian: 1–2 ngày.

1. Refactor `AuthViewModel` thành `ProfileViewModel` hoặc giữ tên cũ tạm thời.
2. Thêm loading, saving, success, error state.
3. Tạo `saveProfile()` để lưu cả name và avatar.
4. Refresh dữ liệu profile khi mở tab hoặc sau khi lưu.
5. Hiển thị Snackbar/Retry khi API lỗi.

**Acceptance criteria:**

- Đổi tên thành công.
- Đổi avatar thành công.
- Đổi cả tên và avatar trong một lần Save thành công.
- Mở lại tab vẫn thấy dữ liệu mới.

### Giai đoạn 3 — Bổ sung profile summary

Thời gian: 1 ngày.

1. Tạo `GET /api/v1/me/summary` hoặc endpoint tương đương.
2. Hiển thị total projects, completed và processing trong My Profile.
3. Thêm loading/error state cho summary.

**Acceptance criteria:**

- Số liệu trong Profile đúng với Gallery của user.

### Giai đoạn 4 — Chuyển sang profile API an toàn

Thời gian: 1 sprint.

1. Thêm authentication/authorization.
2. Chuyển API từ `/profile/{userId}` sang `/me`.
3. Chuyển ownership sang token claim.
4. Bảo vệ upload avatar và storage.
5. Giữ endpoint cũ tạm thời, sau đó deprecate.

**Acceptance criteria:**

- User A không thể đọc hoặc sửa Profile của user B.
- Client không thể tự thay `userId` để truy cập dữ liệu khác.
- Avatar không hợp lệ bị từ chối.

## 9. Thứ tự khuyến nghị

```text
Tách tab Profile
    ↓
Sửa Save name + avatar
    ↓
Thêm Profile summary
    ↓
Chuyển API /me + JWT + storage an toàn
```

Điểm kết thúc giai đoạn đầu: Profile là tab độc lập, Gallery chỉ còn quản lý dự án, và user có thể đổi tên/avatar đáng tin cậy mà không bị che UI hoặc mất thay đổi.
