using System.Text.Json;
using InteriorAI.Domain.Entities;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.ChangeTracking;

namespace InteriorAI.Data
{
    public class AppDbContext : DbContext
    {
        public AppDbContext(DbContextOptions<AppDbContext> options) : base(options) { }
        public DbSet<User> Users { get; set; } = default!;
        public DbSet<DesignResult> DesignResults { get; set; } = default!;
        public DbSet<RoomInterior> RoomInteriors { get; set; } = default!;
        public DbSet<RoomStylePrompt> RoomStylePrompts { get; set; } = default!;
        public DbSet<StyleAesthetic> StyleAesthetics { get; set; } = default!;
        public DbSet<ChatConversation> ChatConversations { get; set; } = default!;
        public DbSet<ChatMessage> ChatMessages { get; set; } = default!;
        public DbSet<ChatMessageCitation> ChatMessageCitations { get; set; } = default!;
        public DbSet<DesignBrief> DesignBriefs { get; set; } = default!;
        public DbSet<KnowledgeDocument> KnowledgeDocuments { get; set; } = default!;
        public DbSet<KnowledgeChunk> KnowledgeChunks { get; set; } = default!;

        protected override void OnModelCreating(ModelBuilder modelBuilder)
        {
            base.OnModelCreating(modelBuilder);

            modelBuilder.Entity<StyleAesthetic>(entity =>
            {
                entity.HasKey(e => e.StyleID);
            });

            
            // 2. CẤU HÌNH BẢNG RoomInteriors
            
            modelBuilder.Entity<RoomInterior>(entity =>
            {
                entity.HasKey(e => e.RoomID);

                entity.HasOne(r => r.Style)
                      .WithMany(s => s.RoomInteriors)
                      .HasForeignKey(r => r.StyleID)
                      .OnDelete(DeleteBehavior.Cascade);
            });

            modelBuilder.Entity<RoomStylePrompt>(entity =>
            {
                entity.HasKey(e => e.Id);

                entity.Property(e => e.RoomTypeKey)
                    .HasMaxLength(64)
                    .IsRequired();

                entity.Property(e => e.RoomTypeName)
                    .HasMaxLength(128)
                    .IsRequired();

                entity.Property(e => e.Variant)
                    .HasMaxLength(64)
                    .IsRequired();

                entity.Property(e => e.Lighting)
                    .IsRequired();

                entity.Property(e => e.Material)
                    .IsRequired();

                entity.Property(e => e.Color)
                    .IsRequired();

                entity.Property(e => e.Furniture)
                    .IsRequired();

                entity.Property(e => e.Atmosphere)
                    .IsRequired();

                entity.HasIndex(e => e.RoomTypeKey);
                entity.HasIndex(e => new { e.StyleId, e.RoomTypeKey })
                    .IsUnique();

                entity.HasOne(e => e.StyleAesthetic)
                    .WithMany(style => style.RoomStylePrompts)
                    .HasForeignKey(e => e.StyleId)
                    .OnDelete(DeleteBehavior.Cascade);
            });

            modelBuilder.Entity<DesignResult>(entity =>
            {
                entity.HasOne(design => design.User)
                    .WithMany()
                    .HasForeignKey(design => design.UserId)
                    .OnDelete(DeleteBehavior.Restrict);

                entity.HasIndex(design => design.UserId);
                entity.HasIndex(design => design.CreatedAt);
                entity.HasIndex(design => design.Status);
                entity.HasIndex(design => new { design.UserId, design.IsDeleted, design.CreatedAt });
                entity.HasIndex(design => new { design.ConversationId, design.ClientRequestId })
                    .IsUnique()
                    .HasFilter("[ClientRequestId] IS NOT NULL");
            });

            modelBuilder.Entity<DesignBrief>(entity =>
            {
                entity.HasOne(brief => brief.Conversation)
                    .WithOne()
                    .HasForeignKey<DesignBrief>(brief => brief.ConversationId)
                    .OnDelete(DeleteBehavior.Cascade);
            });

            modelBuilder.Entity<ChatMessageCitation>(entity =>
            {
                entity.HasOne(citation => citation.Message)
                    .WithMany(message => message.Citations)
                    .HasForeignKey(citation => citation.MessageId)
                    .OnDelete(DeleteBehavior.Cascade);
                entity.HasIndex(citation => new { citation.MessageId, citation.ChunkId }).IsUnique();
            });

            modelBuilder.Entity<KnowledgeChunk>(entity =>
            {
                entity.HasOne(chunk => chunk.Document)
                    .WithMany(document => document.Chunks)
                    .HasForeignKey(chunk => chunk.DocumentId)
                    .OnDelete(DeleteBehavior.Cascade);
                entity.HasIndex(chunk => new { chunk.DocumentId, chunk.Ordinal });
            });

            modelBuilder.Entity<ChatConversation>(entity =>
            {
                entity.HasOne(conversation => conversation.User)
                    .WithMany()
                    .HasForeignKey(conversation => conversation.UserId)
                    .OnDelete(DeleteBehavior.Restrict);
                entity.HasIndex(conversation => new { conversation.UserId, conversation.UpdatedAt });
            });

            modelBuilder.Entity<ChatMessage>(entity =>
            {
                entity.HasOne(message => message.Conversation)
                    .WithMany(conversation => conversation.Messages)
                    .HasForeignKey(message => message.ConversationId)
                    .OnDelete(DeleteBehavior.Cascade);
                entity.HasIndex(message => new { message.ConversationId, message.CreatedAt });
                entity.HasIndex(message => new { message.ConversationId, message.ClientMessageId })
                    .IsUnique()
                    .HasFilter("[ClientMessageId] IS NOT NULL");
                entity.HasIndex(message => message.ReplyToMessageId);
                entity.HasIndex(message => new { message.ConversationId, message.DesignId })
                    .IsUnique()
                    .HasFilter("[DesignId] IS NOT NULL");
            });
        }
    }
}
