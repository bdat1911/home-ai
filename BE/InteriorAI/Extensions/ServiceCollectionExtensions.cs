using InteriorAI.Services;

namespace InteriorAI.Extensions;

public static class ServiceCollectionExtensions
{
    /// <summary>Chat and knowledge services, shared by the API and the ingest and evaluation tools.</summary>
    public static IServiceCollection AddChatbot(this IServiceCollection services)
    {
        services.AddSingleton<KnowledgeIndex>();
        services.AddScoped<KnowledgeIngestService>();
        services.AddScoped<LexicalRetriever>();
        services.AddScoped<HybridRetriever>();
        services.AddScoped<IChatReplyService, ChatReplyService>();
        services.AddHttpClient<IChatAnswerGenerator, GeminiChatAnswerGenerator>();
        services.AddHttpClient<IEmbeddingService, GeminiEmbeddingService>();
        return services;
    }
}
