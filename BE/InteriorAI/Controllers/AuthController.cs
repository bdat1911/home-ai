using InteriorAI.Data;
using InteriorAI.Domain.Entities;
using InteriorAI.Models.DTOs;
using InteriorAI.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using System.IO;

namespace InteriorAI.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class AuthController : ControllerBase
    {
        private const long MaxAvatarSizeBytes = 5 * 1024 * 1024;

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

            return Ok(ToProfileResponse(user));
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

            if (file.Length > MaxAvatarSizeBytes)
            {
                return BadRequest("Ảnh đại diện không được vượt quá 5 MB.");
            }

            if (!IsSupportedAvatarContentType(file.ContentType))
            {
                return BadRequest("Chỉ hỗ trợ ảnh JPG, PNG hoặc WEBP.");
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

            // 4. Đặt tên file do server tạo để không tin trực tiếp tên file từ client.
            var uniqueFileName = $"{Guid.NewGuid():N}{GetAvatarExtension(file.ContentType)}";
            var filePath = Path.Combine(uploadsFolder, uniqueFileName);

            // 5. Lưu file ảnh vào ổ cứng máy tính
            using (var stream = new FileStream(filePath, FileMode.Create))
            {
                await file.CopyToAsync(stream);
            }

            // Chỉ lưu tên file; URL công khai được xây dựng ở một nơi trên client.
            var relativePath = uniqueFileName;

            // 7. Lưu cái link đó vào Database của thằng User này
            user.AvatarUrl = relativePath;
            user.UpdatedAt = DateTime.UtcNow;
            
            await _context.SaveChangesAsync();

            return Ok(ToProfileResponse(user));
        }

        // GET /api/auth/profile/{userId}
        [HttpGet("profile/{userId}")]
        public async Task<IActionResult> GetProfile(string userId)
        {
            var user = await _authManager.GetUserAsync(userId);

            // Check nếu không tìm thấy user trong Database
            if (user == null)
                return NotFound(new { message = "Không tìm thấy người dùng" });

            return Ok(ToProfileResponse(user));
        }

        // GET /api/auth/profile/{userId}/summary
        [HttpGet("profile/{userId}/summary")]
        public async Task<IActionResult> GetProfileSummary(string userId)
        {
            var userExists = await _context.Users
                .AsNoTracking()
                .AnyAsync(user => user.Id == userId);
            if (!userExists)
            {
                return NotFound(new { message = "Không tìm thấy người dùng" });
            }

            var activeProjects = _context.DesignResults
                .AsNoTracking()
                .Where(design => design.UserId == userId && !design.IsDeleted);

            return Ok(new ProfileSummaryResponse
            {
                ProjectCount = await activeProjects.CountAsync(),
                CompletedDesignCount = await activeProjects.CountAsync(design => design.Status == DesignStatuses.Completed),
                PendingDesignCount = await activeProjects.CountAsync(design => design.Status == DesignStatuses.Pending)
            });
        }

        // PUT /api/auth/profile/{userId}
        [HttpPut("profile/{userId}")]
        public async Task<IActionResult> UpdateProfile(string userId, [FromBody] UpdateProfileRequest request)
        {
            var displayName = request.Name?.Trim();
            if (string.IsNullOrWhiteSpace(displayName) || displayName.Length > 50)
            {
                return BadRequest(new { message = "Tên hiển thị phải có từ 1 đến 50 ký tự." });
            }

            var user = await _authManager.UpdateUserProfileAsync(userId, displayName, avatarUrl: null);

            // Check nếu không tìm thấy user để cập nhật
            if (user == null)
                return NotFound(new { message = "Không tìm thấy người dùng để cập nhật" });

            return Ok(ToProfileResponse(user));
        }

        private static UserProfileResponse ToProfileResponse(User user)
        {
            return new UserProfileResponse
            {
                UserId = user.Id,
                Name = user.Name,
                AvatarUrl = user.AvatarUrl,
                CreatedAt = user.CreatedAt,
                UpdatedAt = user.UpdatedAt
            };
        }

        private static bool IsSupportedAvatarContentType(string? contentType)
        {
            return contentType?.ToLowerInvariant() is "image/jpeg" or "image/png" or "image/webp";
        }

        private static string GetAvatarExtension(string? contentType)
        {
            return contentType?.ToLowerInvariant() switch
            {
                "image/png" => ".png",
                "image/webp" => ".webp",
                _ => ".jpg"
            };
        }
    }
}
