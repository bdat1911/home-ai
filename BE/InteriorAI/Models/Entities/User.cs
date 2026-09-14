﻿using System.ComponentModel.DataAnnotations;

namespace InteriorAI.Domain.Entities
{
    public class User
    {
        [Key]
        public string Id { get; set; } = string.Empty; // Sẽ chứa UUID do app Java gửi lên

        public string? Name { get; set; }

        public string? AvatarUrl { get; set; }

        public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

        public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
    }
}