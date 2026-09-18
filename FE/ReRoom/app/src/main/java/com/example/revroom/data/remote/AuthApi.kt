package com.example.revroom.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

// --- CÁC CLASS ĐÓNG GÓI DỮ LIỆU ---

data class RegisterDeviceRequest(
    val userId: String,
    val name: String? = null,
)

data class UserProfileResponse(
    val userId: String,
    val name: String?,
    val avatarUrl: String?,
    val createdAt: String?,
)

data class UpdateProfileRequest(
    val name: String,
)

data class ProfileSummaryResponse(
    val projectCount: Int,
    val completedDesignCount: Int,
    val pendingDesignCount: Int,
)

// --- INTERFACE GỌI API ---

interface AuthApi {
    @POST("api/Auth/register-device")
    suspend fun registerDevice(@Body request: RegisterDeviceRequest): Response<UserProfileResponse>

    @Multipart
    @POST("api/Auth/upload-avatar")
    suspend fun uploadAvatar(
        @Part("userId") userId: RequestBody,
        @Part file: MultipartBody.Part,
    ): Response<UserProfileResponse>

    @GET("api/Auth/profile/{userId}")
    suspend fun getProfile(@Path("userId") userId: String): Response<UserProfileResponse>

    @GET("api/Auth/profile/{userId}/summary")
    suspend fun getProfileSummary(
        @Path("userId") userId: String,
    ): Response<ProfileSummaryResponse>

    @PUT("api/Auth/profile/{userId}")
    suspend fun updateProfile(
        @Path("userId") userId: String,
        @Body request: UpdateProfileRequest
    ): Response<UserProfileResponse>
}
