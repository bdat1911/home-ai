using InteriorAI.Data;
using InteriorAI.Domain.Entities;
using Microsoft.EntityFrameworkCore;

namespace InteriorAI.Services
{
    public class AuthManager
    {
        private readonly AppDbContext _context;

        public AuthManager(AppDbContext context)
        {
            _context = context;
        }

        public async Task<User> RegisterOrGetUserAsync(string userId, string? defaultName)
        {
            var user = await _context.Users.FirstOrDefaultAsync(u => u.Id == userId);

            if (user == null)
            {
                user = new User
                {
                    Id = userId,
                    Name = defaultName ?? $"Khách_{userId.Substring(0, 4)}",
                    CreatedAt = DateTime.UtcNow,
                    UpdatedAt = DateTime.UtcNow
                };
                _context.Users.Add(user);
                await _context.SaveChangesAsync();
            }
            return user;
        }

        public async Task<User?> GetUserAsync(string userId)
        {
            return await _context.Users.FirstOrDefaultAsync(u => u.Id == userId);
        }

        public async Task<User?> UpdateUserProfileAsync(string userId, string? name, string? avatarUrl)
        {
            var user = await _context.Users.FirstOrDefaultAsync(u => u.Id == userId);
            if (user == null) return null;

            if (!string.IsNullOrEmpty(name)) user.Name = name;
            if (!string.IsNullOrEmpty(avatarUrl)) user.AvatarUrl = avatarUrl;

            user.UpdatedAt = DateTime.UtcNow;
            await _context.SaveChangesAsync();

            return user;
        }
    }
}
