using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace InteriorAI.Migrations
{
    /// <inheritdoc />
    public partial class RemoveOptionsColumnsFromStyleAesthetic : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "AtmosphereOptions",
                table: "StyleAesthetics");

            migrationBuilder.DropColumn(
                name: "ColorRuleOptions",
                table: "StyleAesthetics");

            migrationBuilder.DropColumn(
                name: "LightingOptions",
                table: "StyleAesthetics");

            migrationBuilder.DropColumn(
                name: "MaterialOptions",
                table: "StyleAesthetics");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "AtmosphereOptions",
                table: "StyleAesthetics",
                type: "nvarchar(max)",
                nullable: false,
                defaultValue: "");

            migrationBuilder.AddColumn<string>(
                name: "ColorRuleOptions",
                table: "StyleAesthetics",
                type: "nvarchar(max)",
                nullable: false,
                defaultValue: "");

            migrationBuilder.AddColumn<string>(
                name: "LightingOptions",
                table: "StyleAesthetics",
                type: "nvarchar(max)",
                nullable: false,
                defaultValue: "");

            migrationBuilder.AddColumn<string>(
                name: "MaterialOptions",
                table: "StyleAesthetics",
                type: "nvarchar(max)",
                nullable: false,
                defaultValue: "");
        }
    }
}
