using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace InteriorAI.Migrations
{
    /// <inheritdoc />
    public partial class AddPromptTemplateToRoomStylePrompt : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "BaseStructuralPrompt",
                table: "StyleAesthetics");

            migrationBuilder.DropColumn(
                name: "SpecificNegative",
                table: "StyleAesthetics");

            migrationBuilder.AddColumn<string>(
                name: "BaseStructuralPrompt",
                table: "RoomStylePrompts",
                type: "nvarchar(max)",
                nullable: false,
                defaultValue: "");

            migrationBuilder.AddColumn<string>(
                name: "PromptTemplate",
                table: "RoomStylePrompts",
                type: "nvarchar(max)",
                nullable: false,
                defaultValue: "");

            migrationBuilder.AddColumn<string>(
                name: "SpecificNegative",
                table: "RoomStylePrompts",
                type: "nvarchar(max)",
                nullable: false,
                defaultValue: "");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "BaseStructuralPrompt",
                table: "RoomStylePrompts");

            migrationBuilder.DropColumn(
                name: "PromptTemplate",
                table: "RoomStylePrompts");

            migrationBuilder.DropColumn(
                name: "SpecificNegative",
                table: "RoomStylePrompts");

            migrationBuilder.AddColumn<string>(
                name: "BaseStructuralPrompt",
                table: "StyleAesthetics",
                type: "nvarchar(max)",
                nullable: false,
                defaultValue: "");

            migrationBuilder.AddColumn<string>(
                name: "SpecificNegative",
                table: "StyleAesthetics",
                type: "nvarchar(max)",
                nullable: false,
                defaultValue: "");
        }
    }
}
