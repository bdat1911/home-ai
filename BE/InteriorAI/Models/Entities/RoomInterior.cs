namespace InteriorAI.Domain.Entities
{
    public class RoomInterior
    {
        public int RoomID { get; set; }
        public string RoomType { get; set; } = string.Empty; 
        public int StyleID { get; set; }     // Khóa ngoại (Foreign Key)
        public string FocalFurnitureOptions { get; set; } = string.Empty;
        public string DecorOptions { get; set; } = string.Empty;

        // Thuộc tính điều hướng: 1 phòng chỉ thuộc 1 phong cách (trong bản ghi này)
        public virtual StyleAesthetic Style { get; set; } = null!;
    }
}
