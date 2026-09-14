package com.example.revroom.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface DesignApi {
    @Multipart
    @POST("api/design/analyze")
    suspend fun analyzeDesign(
        @Header("user-id") userId: String,
        @Part image: MultipartBody.Part,
        @Part("styleId") styleId: RequestBody?,
        @Part("roomType") roomType: RequestBody?,
        @Part("featureId") featureId: RequestBody?,
        @Part("model") model: RequestBody?,
        @Part("resolution") resolution: RequestBody?
    ): DesignResponse

    @GET("api/design/styles")
    suspend fun getDesignStyles(): List<DesignStyleResponse>

    @GET("api/design/status/{designId}")
    suspend fun getDesignStatus(
        @Header("user-id") userId: String,
        @Path("designId") designId: String
    ): DesignStatusResponse

    @GET("api/design/projects")
    suspend fun getUserDesigns(
        @Header("user-id") userId: String,
        @retrofit2.http.Query("page") page: Int,
        @retrofit2.http.Query("pageSize") pageSize: Int
    ): retrofit2.Response<ProjectHistoryResponse>

    @retrofit2.http.DELETE("api/design/{designId}")
    suspend fun deleteDesign(
        @Header("user-id") userId: String,
        @Path("designId") designId: String
    ): retrofit2.Response<Unit>

    @Multipart
    @POST("api/design/chat")
    suspend fun chat(
        @Header("user-id") userId: String,
        @Part("Prompt") message: String,
        @Part image: MultipartBody.Part? = null,
        @Part("model") model: RequestBody? = null,
        @Part("resolution") resolution: RequestBody? = null
    ): ChatResponse
}

data class ChatResponse(
    val message: String? = null,
    val designId: String? = null,
    val imageUrl: String? = null
)

data class ProjectHistoryResponse(
    val data: List<com.example.revroom.features.history.model.ProjectModel>,
    val totalCount: Int,
    val page: Int,
    val totalPages: Int
)

data class DesignResponse(
    val designId: String,
    val originalImageUrl: String,
    val status: String
)

data class DesignStyleResponse(
    val styleId: Int,
    val styleName: String,
    val coreAesthetic: String? = null,
    val lighting: String? = null,
    val material: String? = null,
    val color: String? = null,
    val atmosphere: String? = null
)

data class DesignStatusResponse(
    val designId: String,
    val status: String,
    val originalImageUrl: String,
    val designedImageUrl: String?,
    val errorMessage: String?
)
