using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace InteriorAI.Migrations
{
    /// <inheritdoc />
    public partial class AddRagCitationsAndDesignBrief : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<int>(
                name: "BriefVersion",
                table: "DesignResults",
                type: "int",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "ClientRequestId",
                table: "DesignResults",
                type: "nvarchar(36)",
                maxLength: 36,
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "ConversationId",
                table: "DesignResults",
                type: "nvarchar(36)",
                maxLength: 36,
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "AnswerMode",
                table: "ChatMessages",
                type: "nvarchar(16)",
                maxLength: 16,
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "DesignId",
                table: "ChatMessages",
                type: "nvarchar(36)",
                maxLength: 36,
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "Kind",
                table: "ChatMessages",
                type: "nvarchar(16)",
                maxLength: 16,
                nullable: false,
                defaultValue: "text");

            migrationBuilder.CreateTable(
                name: "ChatMessageCitations",
                columns: table => new
                {
                    Id = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    MessageId = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    ChunkId = table.Column<string>(type: "nvarchar(240)", maxLength: 240, nullable: false),
                    DocumentId = table.Column<string>(type: "nvarchar(120)", maxLength: 120, nullable: false),
                    DocumentVersion = table.Column<string>(type: "nvarchar(20)", maxLength: 20, nullable: true),
                    Title = table.Column<string>(type: "nvarchar(300)", maxLength: 300, nullable: false),
                    SourceLabel = table.Column<string>(type: "nvarchar(300)", maxLength: 300, nullable: false),
                    SourceUrl = table.Column<string>(type: "nvarchar(2048)", maxLength: 2048, nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_ChatMessageCitations", x => x.Id);
                    table.ForeignKey(
                        name: "FK_ChatMessageCitations_ChatMessages_MessageId",
                        column: x => x.MessageId,
                        principalTable: "ChatMessages",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "DesignBriefs",
                columns: table => new
                {
                    ConversationId = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    Version = table.Column<int>(type: "int", nullable: false),
                    RoomType = table.Column<string>(type: "nvarchar(64)", maxLength: 64, nullable: true),
                    StyleId = table.Column<int>(type: "int", nullable: true),
                    FeatureId = table.Column<string>(type: "nvarchar(64)", maxLength: 64, nullable: false),
                    ColorPreference = table.Column<string>(type: "nvarchar(300)", maxLength: 300, nullable: true),
                    MustKeep = table.Column<string>(type: "nvarchar(300)", maxLength: 300, nullable: true),
                    MustAvoid = table.Column<string>(type: "nvarchar(300)", maxLength: 300, nullable: true),
                    Status = table.Column<string>(type: "nvarchar(16)", maxLength: 16, nullable: false),
                    CreatedAt = table.Column<DateTime>(type: "datetime2", nullable: false),
                    UpdatedAt = table.Column<DateTime>(type: "datetime2", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_DesignBriefs", x => x.ConversationId);
                    table.ForeignKey(
                        name: "FK_DesignBriefs_ChatConversations_ConversationId",
                        column: x => x.ConversationId,
                        principalTable: "ChatConversations",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "KnowledgeDocuments",
                columns: table => new
                {
                    Id = table.Column<string>(type: "nvarchar(120)", maxLength: 120, nullable: false),
                    Title = table.Column<string>(type: "nvarchar(300)", maxLength: 300, nullable: false),
                    DocumentType = table.Column<string>(type: "nvarchar(40)", maxLength: 40, nullable: false),
                    Status = table.Column<string>(type: "nvarchar(40)", maxLength: 40, nullable: false),
                    Version = table.Column<string>(type: "nvarchar(20)", maxLength: 20, nullable: true),
                    IsActive = table.Column<bool>(type: "bit", nullable: false),
                    UpdatedAt = table.Column<DateTime>(type: "datetime2", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_KnowledgeDocuments", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "KnowledgeChunks",
                columns: table => new
                {
                    Id = table.Column<string>(type: "nvarchar(240)", maxLength: 240, nullable: false),
                    DocumentId = table.Column<string>(type: "nvarchar(120)", maxLength: 120, nullable: false),
                    Ordinal = table.Column<int>(type: "int", nullable: false),
                    Text = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    ContentHash = table.Column<string>(type: "nvarchar(64)", maxLength: 64, nullable: false),
                    Embedding = table.Column<byte[]>(type: "varbinary(max)", nullable: false),
                    EmbeddingModel = table.Column<string>(type: "nvarchar(80)", maxLength: 80, nullable: false),
                    IsActive = table.Column<bool>(type: "bit", nullable: false),
                    UpdatedAt = table.Column<DateTime>(type: "datetime2", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_KnowledgeChunks", x => x.Id);
                    table.ForeignKey(
                        name: "FK_KnowledgeChunks_KnowledgeDocuments_DocumentId",
                        column: x => x.DocumentId,
                        principalTable: "KnowledgeDocuments",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_DesignResults_ConversationId_ClientRequestId",
                table: "DesignResults",
                columns: new[] { "ConversationId", "ClientRequestId" },
                unique: true,
                filter: "[ClientRequestId] IS NOT NULL");

            migrationBuilder.CreateIndex(
                name: "IX_ChatMessages_ConversationId_DesignId",
                table: "ChatMessages",
                columns: new[] { "ConversationId", "DesignId" },
                unique: true,
                filter: "[DesignId] IS NOT NULL");

            migrationBuilder.CreateIndex(
                name: "IX_ChatMessageCitations_MessageId_ChunkId",
                table: "ChatMessageCitations",
                columns: new[] { "MessageId", "ChunkId" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_KnowledgeChunks_DocumentId_Ordinal",
                table: "KnowledgeChunks",
                columns: new[] { "DocumentId", "Ordinal" });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "ChatMessageCitations");

            migrationBuilder.DropTable(
                name: "DesignBriefs");

            migrationBuilder.DropTable(
                name: "KnowledgeChunks");

            migrationBuilder.DropTable(
                name: "KnowledgeDocuments");

            migrationBuilder.DropIndex(
                name: "IX_DesignResults_ConversationId_ClientRequestId",
                table: "DesignResults");

            migrationBuilder.DropIndex(
                name: "IX_ChatMessages_ConversationId_DesignId",
                table: "ChatMessages");

            migrationBuilder.DropColumn(
                name: "BriefVersion",
                table: "DesignResults");

            migrationBuilder.DropColumn(
                name: "ClientRequestId",
                table: "DesignResults");

            migrationBuilder.DropColumn(
                name: "ConversationId",
                table: "DesignResults");

            migrationBuilder.DropColumn(
                name: "AnswerMode",
                table: "ChatMessages");

            migrationBuilder.DropColumn(
                name: "DesignId",
                table: "ChatMessages");

            migrationBuilder.DropColumn(
                name: "Kind",
                table: "ChatMessages");
        }
    }
}
