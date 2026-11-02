package com.example.revroom.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface ChatApi {
    @POST("api/v1/chat/conversations")
    suspend fun createConversation(
        @Header("user-id") userId: String,
        @Body request: CreateConversationRequest
    ): ConversationResponse

    @GET("api/v1/chat/conversations")
    suspend fun getConversations(@Header("user-id") userId: String): List<ConversationResponse>

    @GET("api/v1/chat/conversations/{conversationId}/messages")
    suspend fun getMessages(
        @Header("user-id") userId: String,
        @Path("conversationId") conversationId: String
    ): List<ChatMessageResponse>

    @POST("api/v1/chat/conversations/{conversationId}/messages")
    suspend fun sendMessage(
        @Header("user-id") userId: String,
        @Path("conversationId") conversationId: String,
        @Body request: SendChatMessageRequest
    ): SendChatMessageResponse

    @GET("api/v1/chat/conversations/{conversationId}/brief")
    suspend fun getBrief(
        @Header("user-id") userId: String,
        @Path("conversationId") conversationId: String
    ): DesignBriefResponse

    @PATCH("api/v1/chat/conversations/{conversationId}/brief")
    suspend fun updateBrief(
        @Header("user-id") userId: String,
        @Path("conversationId") conversationId: String,
        @Body request: UpdateDesignBriefRequest
    ): DesignBriefResponse

    @Multipart
    @POST("api/v1/chat/conversations/{conversationId}/brief/confirm")
    suspend fun confirmBrief(
        @Header("user-id") userId: String,
        @Path("conversationId") conversationId: String,
        @Part image: MultipartBody.Part,
        @Part("briefVersion") briefVersion: RequestBody,
        @Part("clientRequestId") clientRequestId: RequestBody
    ): ConfirmDesignBriefResponse
}

data class CreateConversationRequest(val clientConversationId: String)
data class SendChatMessageRequest(val clientMessageId: String, val content: String)
data class ConversationResponse(
    val id: String,
    val title: String,
    val createdAt: String,
    val updatedAt: String
)
data class ChatCitationResponse(
    val chunkId: String,
    val documentId: String,
    val title: String,
    val sourceLabel: String,
    val sourceUrl: String? = null
)
data class ChatMessageResponse(
    val id: String,
    val role: String,
    val content: String,
    val createdAt: String,
    val kind: String? = null,
    val designId: String? = null,
    val citations: List<ChatCitationResponse>? = null
)
data class DesignBriefResponse(
    val conversationId: String,
    val version: Int,
    val roomType: String? = null,
    val styleId: Int? = null,
    val styleName: String? = null,
    val featureId: String = "interior_design",
    val colorPreference: String? = null,
    val mustKeep: String? = null,
    val mustAvoid: String? = null,
    val status: String = "draft",
    val missingFields: List<String>? = null
)
data class UpdateDesignBriefRequest(
    val expectedVersion: Int,
    val roomType: String? = null,
    val styleId: Int? = null,
    val featureId: String? = null,
    val colorPreference: String? = null,
    val mustKeep: String? = null,
    val mustAvoid: String? = null
)
data class SendChatMessageResponse(
    val userMessage: ChatMessageResponse,
    val assistantMessage: ChatMessageResponse,
    val brief: DesignBriefResponse? = null
)
data class ConfirmDesignBriefResponse(
    val designId: String,
    val status: String,
    val message: ChatMessageResponse,
    val brief: DesignBriefResponse
)
