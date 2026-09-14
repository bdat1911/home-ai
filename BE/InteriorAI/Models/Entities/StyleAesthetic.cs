namespace InteriorAI.Domain.Entities
{
    public class StyleAesthetic
    {
        public int StyleID { get; set; }
        public string StyleName { get; set; } = string.Empty;
        public string CoreAesthetic { get; set; } = string.Empty;

        public string TechnicalSpecs { get; set; } = string.Empty;

        // Thuộc tính điều hướng: 1 phong cách có thể áp dụng cho nhiều phòng
        public virtual ICollection<RoomInterior> RoomInteriors { get; set; }
            = new List<RoomInterior>();

        public virtual ICollection<RoomStylePrompt> RoomStylePrompts { get; set; }
            = new List<RoomStylePrompt>();
    }
}
