package com.example.revroom.data.repository

import android.content.Context
import com.example.revroom.core.network.ApiClient
import com.example.revroom.core.network.ServerEndpoint
import com.example.revroom.core.utils.ImageCompressor
import com.example.revroom.core.utils.SelectedImageException
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
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
    private var registeredApiBaseUrl: String? = null
    private val registrationMutex = Mutex()

    suspend fun uploadDesign(request: DesignRequest): Result<DesignResult> {
        return ServerEndpoint.withStableEndpoint {
            runApiCall {
                val imageBytes = ImageCompressor.readAndCompressIfNeeded(context, request.imageUri)
                val imagePart = createImagePart("image", imageBytes)
                val userId = ensureRegisteredUser()
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

    private suspend fun ensureRegisteredUser(): String = registrationMutex.withLock {
        val apiBaseUrl = ApiClient.baseUrl
        if (registeredApiBaseUrl == apiBaseUrl) {
            registeredUserId?.let { return@withLock it }
        }
        val deviceId = userIdProvider.getOrCreateUserId()
        val profileResponse = authApi.getProfile(deviceId)
        val profile = when {
            profileResponse.isSuccessful -> profileResponse.body()
            profileResponse.code() == 404 -> {
                profileResponse.errorBody()?.close()
                val registrationResponse = authApi.registerDevice(
                    RegisterDeviceRequest(userId = deviceId, name = "Revroom User"),
                )
                if (!registrationResponse.isSuccessful) throw HttpException(registrationResponse)
                registrationResponse.body()
            }
            else -> throw HttpException(profileResponse)
        } ?: throw DesignRepositoryException("Máy chủ trả về hồ sơ trống. Vui lòng thử lại.")

        val confirmedUserId = profile.userId.takeIf { it.isNotBlank() }
            ?: throw DesignRepositoryException("Máy chủ không trả về mã người dùng hợp lệ.")
        userIdProvider.saveUserId(confirmedUserId)
        registeredUserId = confirmedUserId
        registeredApiBaseUrl = apiBaseUrl
        confirmedUserId
    }

    suspend fun getRegisteredUserId(): String = ensureRegisteredUser()

    suspend fun readImagePart(imageUri: android.net.Uri): MultipartBody.Part =
        createImagePart("image", ImageCompressor.readAndCompressIfNeeded(context, imageUri))

    private fun createImagePart(partName: String, imageBytes: ByteArray): MultipartBody.Part {
        val requestBody = imageBytes.toRequestBody(ImageCompressor.OUTPUT_MIME_TYPE.toMediaType())
        return MultipartBody.Part.createFormData(partName, "room-upload.jpg", requestBody)
    }

    private suspend fun <T> runApiCall(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (error: CancellationException) {
            // Leaving a screen stops its coroutine; it is not an API failure or a server cancellation.
            throw error
        } catch (error: SelectedImageException) {
            Result.failure(DesignRepositoryException(error.message ?: "Không thể đọc ảnh đã chọn.", cause = error))
        } catch (error: HttpException) {
            Result.failure(DesignRepositoryException(parseHttpError(error), httpStatus = error.code(), cause = error))
        } catch (error: IOException) {
            Result.failure(DesignRepositoryException("Không thể kết nối máy chủ. Vui lòng kiểm tra mạng.", cause = error))
        } catch (error: DesignRepositoryException) {
            Result.failure(error)
        } catch (error: Exception) {
            Result.failure(DesignRepositoryException(error.message ?: "Đã có lỗi xảy ra.", cause = error))
        }
    }

    private fun parseHttpError(error: HttpException): String {
        // Server error bodies may contain provider details. Do not display or log them.
        if (error.code() >= 500) {
            error.response()?.errorBody()?.close()
            return "Máy chủ tạm thời gặp lỗi (${error.code()}). Vui lòng thử lại sau."
        }
        val errorBody = try {
            error.response()?.errorBody()?.use { it.string() }
        } catch (_: IOException) {
            null
        }
        if (!errorBody.isNullOrBlank()) {
            val message = runCatching { JSONObject(errorBody).optString("message") }.getOrNull()
            if (!message.isNullOrBlank()) {
                return message.take(300)
            }
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

class DesignRepositoryException(
    message: String,
    val httpStatus: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    val isInputRejection: Boolean
        get() = cause is SelectedImageException || httpStatus?.let { it in 400..499 } == true
}
