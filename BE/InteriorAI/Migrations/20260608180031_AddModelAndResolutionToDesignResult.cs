using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace InteriorAI.Migrations
{
    /// <inheritdoc />
    public partial class AddModelAndResolutionToDesignResult : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "Model",
                table: "DesignResults",
                type: "nvarchar(64)",
                maxLength: 64,
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "Resolution",
                table: "DesignResults",
                type: "nvarchar(64)",
                maxLength: 64,
                nullable: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "Model",
                table: "DesignResults");

            migrationBuilder.DropColumn(
                name: "Resolution",
                table: "DesignResults");
        }
    }
}
