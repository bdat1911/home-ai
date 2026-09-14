using System.ComponentModel.DataAnnotations;
using Microsoft.AspNetCore.Http;

namespace InteriorAI.Models.DTOs
{
    public class ChatDesignRequest
    {
        [Required]
        public string Prompt { get; set; } = string.Empty;

        [Required]
        public IFormFile Image { get; set; } = null!;

        public string? Model { get; set; }

        public string? Resolution { get; set; }
    }
}
