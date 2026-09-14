using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace InteriorAI.Migrations
{
    /// <inheritdoc />
    public partial class InitialCreate : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "StyleAesthetics",
                columns: table => new
                {
                    StyleID = table.Column<int>(type: "int", nullable: false)
                        .Annotation("SqlServer:Identity", "1, 1"),
                    StyleName = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    CoreAesthetic = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    BaseStructuralPrompt = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    LightingOptions = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    MaterialOptions = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    ColorRuleOptions = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    AtmosphereOptions = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    SpecificNegative = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    TechnicalSpecs = table.Column<string>(type: "nvarchar(max)", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_StyleAesthetics", x => x.StyleID);
                });

            migrationBuilder.CreateTable(
                name: "Users",
                columns: table => new
                {
                    Id = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    Name = table.Column<string>(type: "nvarchar(max)", nullable: true),
                    AvatarUrl = table.Column<string>(type: "nvarchar(max)", nullable: true),
                    CreatedAt = table.Column<DateTime>(type: "datetime2", nullable: false),
                    UpdatedAt = table.Column<DateTime>(type: "datetime2", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_Users", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "RoomInteriors",
                columns: table => new
                {
                    RoomID = table.Column<int>(type: "int", nullable: false)
                        .Annotation("SqlServer:Identity", "1, 1"),
                    RoomType = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    StyleID = table.Column<int>(type: "int", nullable: false),
                    FocalFurnitureOptions = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    DecorOptions = table.Column<string>(type: "nvarchar(max)", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_RoomInteriors", x => x.RoomID);
                    table.ForeignKey(
                        name: "FK_RoomInteriors_StyleAesthetics_StyleID",
                        column: x => x.StyleID,
                        principalTable: "StyleAesthetics",
                        principalColumn: "StyleID",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "RoomStylePrompts",
                columns: table => new
                {
                    Id = table.Column<int>(type: "int", nullable: false)
                        .Annotation("SqlServer:Identity", "1, 1"),
                    StyleId = table.Column<int>(type: "int", nullable: false),
                    RoomTypeKey = table.Column<string>(type: "nvarchar(64)", maxLength: 64, nullable: false),
                    RoomTypeName = table.Column<string>(type: "nvarchar(128)", maxLength: 128, nullable: false),
                    Variant = table.Column<string>(type: "nvarchar(64)", maxLength: 64, nullable: false),
                    Lighting = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    Material = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    Color = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    Furniture = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    Atmosphere = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    CreatedAt = table.Column<DateTime>(type: "datetime2", nullable: false),
                    UpdatedAt = table.Column<DateTime>(type: "datetime2", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_RoomStylePrompts", x => x.Id);
                    table.ForeignKey(
                        name: "FK_RoomStylePrompts_StyleAesthetics_StyleId",
                        column: x => x.StyleId,
                        principalTable: "StyleAesthetics",
                        principalColumn: "StyleID",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "DesignResults",
                columns: table => new
                {
                    Id = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    UserId = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    OriginalImageUrl = table.Column<string>(type: "nvarchar(2048)", maxLength: 2048, nullable: false),
                    DesignedImageUrl = table.Column<string>(type: "nvarchar(2048)", maxLength: 2048, nullable: true),
                    DesignPrompt = table.Column<string>(type: "nvarchar(max)", nullable: true),
                    Status = table.Column<string>(type: "nvarchar(32)", maxLength: 32, nullable: false),
                    ErrorMessage = table.Column<string>(type: "nvarchar(2000)", maxLength: 2000, nullable: true),
                    IsDeleted = table.Column<bool>(type: "bit", nullable: false),
                    CreatedAt = table.Column<DateTime>(type: "datetime2", nullable: false),
                    UpdatedAt = table.Column<DateTime>(type: "datetime2", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_DesignResults", x => x.Id);
                    table.ForeignKey(
                        name: "FK_DesignResults_Users_UserId",
                        column: x => x.UserId,
                        principalTable: "Users",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Restrict);
                });

            migrationBuilder.CreateIndex(
                name: "IX_DesignResults_CreatedAt",
                table: "DesignResults",
                column: "CreatedAt");

            migrationBuilder.CreateIndex(
                name: "IX_DesignResults_Status",
                table: "DesignResults",
                column: "Status");

            migrationBuilder.CreateIndex(
                name: "IX_DesignResults_UserId",
                table: "DesignResults",
                column: "UserId");

            migrationBuilder.CreateIndex(
                name: "IX_DesignResults_UserId_IsDeleted_CreatedAt",
                table: "DesignResults",
                columns: new[] { "UserId", "IsDeleted", "CreatedAt" });

            migrationBuilder.CreateIndex(
                name: "IX_RoomInteriors_StyleID",
                table: "RoomInteriors",
                column: "StyleID");

            migrationBuilder.CreateIndex(
                name: "IX_RoomStylePrompts_RoomTypeKey",
                table: "RoomStylePrompts",
                column: "RoomTypeKey");

            migrationBuilder.CreateIndex(
                name: "IX_RoomStylePrompts_StyleId_RoomTypeKey",
                table: "RoomStylePrompts",
                columns: new[] { "StyleId", "RoomTypeKey" },
                unique: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "DesignResults");

            migrationBuilder.DropTable(
                name: "RoomInteriors");

            migrationBuilder.DropTable(
                name: "RoomStylePrompts");

            migrationBuilder.DropTable(
                name: "Users");

            migrationBuilder.DropTable(
                name: "StyleAesthetics");
        }
    }
}
