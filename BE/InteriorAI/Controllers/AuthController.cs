using InteriorAI.Data;
using InteriorAI.Models.DTOs;
using InteriorAI.Services;
using Microsoft.AspNetCore.Mvc;
using System.IO;

namespace InteriorAI.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class AuthController : ControllerBase
    {
       // 1. Khai báo 2 thằng cần dùng ở đây
        private readonly AppDbContext _context;
        private readonly AuthManager _authManager;

        // 2. Gộp chung vào 1 cửa (Constructor) duy nhất đón cả 2 thằng
        public AuthController(AppDbContext context, AuthManager authManager)
        {
            _context = context;
            _authManager = authManager;
        }

        // POST /api/auth/register-device
        [HttpPost("register-device")]
        public async Task<IActionResult> RegisterDevice([FromBody] RegisterDeviceRequest request)
        {
            if (string.IsNullOrEmpty(request.UserId))
                return BadRequest("Thiếu UserId");

            var user = await _authManager.RegisterOrGetUserAsync(request.UserId, request.Name);

            return Ok(new UserProfileResponse
            {
                UserId = user.Id,
                Name = user.Name,
                AvatarUrl = user.AvatarUrl,
                CreatedAt = user.CreatedAt
            });
        }

        // HÀM MỚI: HỨNG ẢNH TỪ ANDROID
        [HttpPost("upload-avatar")]
        public async Task<IActionResult> UploadAvatar([FromForm] UploadAvatarRequest request)
        {
            var userId = request.UserId;
            var file = request.File;

            // 1. Kiểm tra đầu vào xem có ảnh không
            if (file == null || file.Length == 0)
            {
                return BadRequest("Không tìm thấy file ảnh!");
            }

            // 2. Tìm thằng User đang cần đổi avatar trong Database
            var user = _context.Users.FirstOrDefault(u => u.Id == userId);
            if (user == null)
            {
                return NotFound("Không tìm thấy User này trong Database!");
            }

            // 3. Tạo thư mục wwwroot/uploads (Nếu chưa có thì code tự đẻ ra)
            var uploadsFolder = Path.Combine(Directory.GetCurrentDirectory(), "wwwroot", "uploads");
            if (!Directory.Exists(uploadsFolder))
            {
                Directory.CreateDirectory(uploadsFolder);
            }

            // 4. Đổi tên file cho khỏi trùng (Thêm dải mã ngẫu nhiên Guid vào trước tên ảnh)
            var uniqueFileName = Guid.NewGuid().ToString() + "_" + file.FileName;
            var filePath = Path.Combine(uploadsFolder, uniqueFileName);

            // 5. Lưu file ảnh vào ổ cứng máy tính
            using (var stream = new FileStream(filePath, FileMode.Create))
            {
                await file.CopyToAsync(stream);
            }

           // Chỉ lưu đường dẫn thư mục và tên file
var relativePath = $"{uniqueFileName}";

            // 7. Lưu cái link đó vào Database của thằng User này
            user.AvatarUrl = relativePath;
            user.UpdatedAt = DateTime.UtcNow;
            
            await _context.SaveChangesAsync();

            // Trả về thành công cho Android
            return Ok(new { message = "Lưu ảnh thành công!", url = relativePath });
        }

        // GET /api/auth/profile/{userId}
        [HttpGet("profile/{userId}")]
        public async Task<IActionResult> GetProfile(string userId)
        {
            var user = await _authManager.GetUserAsync(userId);

            // Check nếu không tìm thấy user trong Database
            if (user == null)
                return NotFound(new { message = "Không tìm thấy người dùng" });

            return Ok(new UserProfileResponse
            {
                UserId = user.Id,
                Name = user.Name,
                AvatarUrl = user.AvatarUrl,
                CreatedAt = user.CreatedAt
            });
        }

        // PUT /api/auth/profile/{userId}
        [HttpPut("profile/{userId}")]
        public async Task<IActionResult> UpdateProfile(string userId, [FromBody] UpdateProfileRequest request)
        {
            var user = await _authManager.UpdateUserProfileAsync(userId, request.Name, request.AvatarUrl);

            // Check nếu không tìm thấy user để cập nhật
            if (user == null)
                return NotFound(new { message = "Không tìm thấy người dùng để cập nhật" });

            return Ok(new UserProfileResponse
            {
                UserId = user.Id,
                Name = user.Name,
                AvatarUrl = user.AvatarUrl,
                CreatedAt = user.CreatedAt
            });
        }
    }
}