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
                var userIdPrefix = userId.Length <= 4 ? userId : userId[..4];
                user = new User
                {
                    Id = userId,
                    Name = defaultName ?? $"Khách_{userIdPrefix}",
                    CreatedAt = DateTime.UtcNow,
                    UpdatedAt = DateTime.UtcNow
                };
                _context.Users.Add(user);
                try
                {
                    await _context.SaveChangesAsync();
                }
                catch (DbUpdateException)
                {
                    // The app registers a device during startup and Profile can also
                    // register after a 404. If those requests arrive together, use
                    // the user created by the other request instead of returning 500.
                    _context.ChangeTracker.Clear();
                    var existingUser = await _context.Users
                        .AsNoTracking()
                        .FirstOrDefaultAsync(u => u.Id == userId);

                    if (existingUser == null)
                    {
                        throw;
                    }

                    return existingUser;
                }
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
