namespace InteriorAI.Domain.Entities
{
    public class RoomStylePrompt
    {
        public int Id { get; set; }
        public int StyleId { get; set; }
        public string RoomTypeKey { get; set; } = string.Empty;
        public string RoomTypeName { get; set; } = string.Empty;
        public string Variant { get; set; } = string.Empty;
        public string Lighting { get; set; } = string.Empty;
        public string Material { get; set; } = string.Empty;
        public string Color { get; set; } = string.Empty;
        public string Furniture { get; set; } = string.Empty;
        public string Atmosphere { get; set; } = string.Empty;
        public string BaseStructuralPrompt { get; set; } = string.Empty;
        public string PromptTemplate { get; set; } = string.Empty;
        public string SpecificNegative { get; set; } = string.Empty;
        public DateTime CreatedAt { get; set; }
        public DateTime UpdatedAt { get; set; }

        public virtual StyleAesthetic StyleAesthetic { get; set; } = null!;
    }
}
