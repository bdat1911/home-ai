package com.example.revroom.data.repository

import android.content.Context
import android.util.Log
import android.webkit.MimeTypeMap
import com.example.revroom.core.network.ApiClient
import com.example.revroom.core.utils.ImageCompressor
import com.example.revroom.data.local.LocalUserIdProvider
import com.example.revroom.data.remote.AuthApi
import com.example.revroom.data.remote.DesignApi
import com.example.revroom.data.remote.DesignResponse
import com.example.revroom.data.remote.DesignStatusResponse
import com.example.revroom.data.remote.RegisterDeviceRequest
import com.example.revroom.features.design_studio.model.DesignJobStatus
import com.example.revroom.features.design_studio.model.DesignRequest
import com.example.revroom.features.design_studio.model.DesignResult
import com.example.revroom.features.design_studio.model.DesignStyle
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException

class DesignRepository(
    private val context: Context,
    private val designApi: DesignApi = ApiClient.designApi,
    private val authApi: AuthApi = ApiClient.authApi,
    private val userIdProvider: LocalUserIdProvider = LocalUserIdProvider(context),
) {
    private var registeredUserId: String? = null

    suspend fun uploadDesign(request: DesignRequest): Result<DesignResult> {
        return runApiCall {
            val userId = ensureRegisteredUser()
            val imageBytes = ImageCompressor.readAndCompressIfNeeded(context, request.imageUri)
            val contentType = context.contentResolver.getType(request.imageUri) ?: "image/jpeg"
            val imagePart = createImagePart("image", imageBytes, contentType)
            val styleIdPart = request.styleId
                ?.toString()
                ?.toRequestBody("text/plain".toMediaType())
            val roomTypePart = request.roomType
                ?.takeIf { it.isNotBlank() }
                ?.toRequestBody("text/plain".toMediaType())
            val featureIdPart = request.featureId
                ?.takeIf { it.isNotBlank() }
                ?.toRequestBody("text/plain".toMediaType())
            val modelPart = request.model
                ?.takeIf { it.isNotBlank() }
                ?.toRequestBody("text/plain".toMediaType())
            val resolutionPart = request.resolution
                ?.takeIf { it.isNotBlank() }
                ?.toRequestBody("text/plain".toMediaType())

            designApi.analyzeDesign(userId, imagePart, styleIdPart, roomTypePart, featureIdPart, modelPart, resolutionPart).toDesignResult()
        }
    }

    suspend fun getDesignStyles(): Result<List<DesignStyle>> {
        return runApiCall {
            designApi.getDesignStyles().map { style ->
                DesignStyle(
                    styleId = style.styleId,
                    styleName = style.styleName,
                    coreAesthetic = style.coreAesthetic ?: "",
                    lighting = style.lighting ?: "",
                    material = style.material ?: "",
                    color = style.color ?: "",
                    atmosphere = style.atmosphere ?: ""
                )
            }
        }
    }

    suspend fun getDesignStatus(designId: String): Result<DesignResult> {
        return runApiCall {
            val userId = ensureRegisteredUser()
            designApi.getDesignStatus(userId, designId).toDesignResult()
        }
    }

    suspend fun sendMessage(
        message: String,
        imageUri: android.net.Uri?,
        model: String? = null,
        resolution: String? = null
    ): Result<com.example.revroom.data.remote.ChatResponse> {
        return runApiCall {
            val userId = ensureRegisteredUser()
            val imagePart = imageUri?.let { uri ->
                val imageBytes = ImageCompressor.readAndCompressIfNeeded(context, uri)
                val contentType = context.contentResolver.getType(uri) ?: "image/jpeg"
                createImagePart("Image", imageBytes, contentType)
            } ?: throw DesignRepositoryException("Vui lòng chọn ảnh để bắt đầu tư vấn thiết kế.")

            val modelPart = model?.toRequestBody("text/plain".toMediaType())
            val resolutionPart = resolution?.toRequestBody("text/plain".toMediaType())
            
            designApi.chat(userId, message, imagePart, modelPart, resolutionPart)
        }
    }

    private suspend fun ensureRegisteredUser(): String {
        // Nếu đã có trong session hiện tại thì dùng luôn
        registeredUserId?.let { return it }

        // 1. Lấy Android ID của thiết bị (định danh không đổi)
        val deviceId = userIdProvider.getOrCreateUserId()
        
        try {
            // 2. Kiểm tra Server xem có "điện thoại cũ" này chưa
            val profileResponse = authApi.getProfile(deviceId)
            if (profileResponse.isSuccessful) {
                val profile = profileResponse.body()
                if (profile != null) {
                    // QUAN TRỌNG: Server đã có rồi! 
                    // Ta dùng profile.userId (lấy từ server) để đảm bảo đồng bộ hoàn toàn
                    registeredUserId = profile.userId
                    
                    // Lưu lại vào Local để các lần sau không cần check server nữa (cho đến khi xóa app)
                    userIdProvider.saveUserId(profile.userId)
                    
                    Log.d("DesignRepository", "Đã nhận diện thiết bị cũ. Tên: ${profile.name}, ID: ${profile.userId}")
                    return profile.userId
                }
            }
        } catch (e: Exception) {
            Log.d("DesignRepository", "Không tìm thấy thông tin cũ, sẽ đăng ký mới")
        }

        // 3. Nếu Server chưa có thông tin, mới tiến hành đăng ký
        try {
            val registerResponse = authApi.registerDevice(
                RegisterDeviceRequest(userId = deviceId, name = "Revroom User")
            )
            if (registerResponse.isSuccessful) {
                registeredUserId = deviceId
                userIdProvider.saveUserId(deviceId)
                Log.d("DesignRepository", "Đăng ký thiết bị mới: $deviceId")
            }
        } catch (e: Exception) {
            Log.e("DesignRepository", "Lỗi đăng ký: ${e.message}")
        }

        return registeredUserId ?: deviceId
    }

    private fun createImagePart(partName: String, imageBytes: ByteArray, contentType: String): MultipartBody.Part {
        val requestBody = imageBytes.toRequestBody(contentType.toMediaTypeOrNull())
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(contentType) ?: "jpg"
        return MultipartBody.Part.createFormData(partName, "room-upload.$extension", requestBody)
    }

    private suspend fun <T> runApiCall(block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (error: HttpException) {
            Result.failure(DesignRepositoryException(parseHttpError(error)))
        } catch (_: IOException) {
            Result.failure(DesignRepositoryException("Không thể kết nối máy chủ."))
        } catch (error: Exception) {
            Result.failure(DesignRepositoryException(error.message ?: "Đã có lỗi xảy ra."))
        }
    }

    private fun parseHttpError(error: HttpException): String {
        val errorBody = error.response()?.errorBody()?.string()
        if (!errorBody.isNullOrBlank()) {
            val message = runCatching { JSONObject(errorBody).optString("message") }.getOrNull()
            if (!message.isNullOrBlank()) {
                return message
            }
            // Log raw error body if message not found
            android.util.Log.e("DesignRepository", "Raw error body: $errorBody")
        }

        return "Yêu cầu thất bại (${error.code()})."
    }

    private fun DesignResponse.toDesignResult(): DesignResult {
        return DesignResult(
            designId = designId,
            status = DesignJobStatus.fromApiValue(status),
            originalImageUrl = originalImageUrl,
            designedImageUrl = null,
            errorMessage = null
        )
    }

    private fun DesignStatusResponse.toDesignResult(): DesignResult {
        return DesignResult(
            designId = designId,
            status = DesignJobStatus.fromApiValue(status),
            originalImageUrl = originalImageUrl,
            designedImageUrl = designedImageUrl,
            errorMessage = errorMessage
        )
    }
}

class DesignRepositoryException(message: String) : Exception(message)
