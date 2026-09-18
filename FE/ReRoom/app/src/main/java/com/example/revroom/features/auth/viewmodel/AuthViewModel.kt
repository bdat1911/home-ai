package com.example.revroom.features.auth.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.revroom.BuildConfig
import com.example.revroom.core.network.ApiClient
import com.example.revroom.core.utils.FileUtils
import com.example.revroom.data.local.UserManager
import com.example.revroom.data.remote.ProfileSummaryResponse
import com.example.revroom.data.remote.RegisterDeviceRequest
import com.example.revroom.data.remote.UpdateProfileRequest
import com.example.revroom.data.remote.UserProfileResponse
import java.io.IOException
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class AuthViewModel : ViewModel() {

    var selectedImageUri by mutableStateOf<Uri?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isSaving by mutableStateOf(false)
        private set

    var avatarUrl by mutableStateOf<String?>(null)
        private set

    var userName by mutableStateOf<String?>(null)
        private set

    var profileSummary by mutableStateOf<ProfileSummaryResponse?>(null)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var successMessage by mutableStateOf<String?>(null)
        private set

    fun onImageSelected(uri: Uri?) {
        selectedImageUri = uri
        errorMessage = null
        successMessage = null
    }

    fun sendIdToServer(userManager: UserManager, userId: String) {
        viewModelScope.launch {
            try {
                val response = ApiClient.authApi.registerDevice(RegisterDeviceRequest(userId))
                if (response.isSuccessful) {
                    userManager.isRegisteredOnServer = true
                } else {
                    Log.e(TAG, "Device registration failed: HTTP ${response.code()}")
                }
            } catch (error: Exception) {
                Log.e(TAG, "Device registration failed", error)
            }
        }
    }

    fun loadUserProfile(userId: String) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            successMessage = null

            try {
                val response = ApiClient.authApi.getProfile(userId)
                val profile = when {
                    response.isSuccessful -> response.body()
                    response.code() == 404 -> registerMissingProfile(userId)
                    else -> throw IOException("Unable to load profile (HTTP ${response.code()}).")
                } ?: throw IOException("The profile response was empty.")

                applyProfile(profile)
                loadProfileSummary(userId)
            } catch (error: Exception) {
                Log.e(TAG, "Unable to load profile", error)
                errorMessage = error.toUserMessage("Unable to load profile")
            } finally {
                isLoading = false
            }
        }
    }

    fun saveProfile(context: Context, userId: String, displayName: String) {
        val normalizedName = displayName.trim()
        if (normalizedName.length !in MIN_DISPLAY_NAME_LENGTH..MAX_DISPLAY_NAME_LENGTH) {
            errorMessage = "Display name must contain 1–50 characters."
            successMessage = null
            return
        }

        val shouldUpdateName = normalizedName != userName.orEmpty().trim()

        viewModelScope.launch {
            isSaving = true
            errorMessage = null
            successMessage = null
            var avatarWasSaved = false

            try {
                selectedImageUri?.let { avatarUri ->
                    val avatarProfile = uploadAvatar(context, userId, avatarUri)
                    applyProfile(avatarProfile)
                    selectedImageUri = null
                    avatarWasSaved = true
                }

                if (shouldUpdateName) {
                    val response = ApiClient.authApi.updateProfile(
                        userId = userId,
                        request = UpdateProfileRequest(name = normalizedName),
                    )
                    if (!response.isSuccessful) {
                        throw IOException("Unable to save profile (HTTP ${response.code()}).")
                    }

                    val updatedProfile = response.body()
                        ?: throw IOException("The updated profile response was empty.")
                    applyProfile(updatedProfile)
                }
                loadProfileSummary(userId)
                successMessage = "Profile saved successfully."
            } catch (error: Exception) {
                Log.e(TAG, "Unable to save profile", error)
                errorMessage = if (avatarWasSaved) {
                    "Avatar was saved, but the display name could not be updated. Please try saving again."
                } else {
                    error.toUserMessage("Unable to save profile")
                }
            } finally {
                isSaving = false
            }
        }
    }

    private suspend fun registerMissingProfile(userId: String): UserProfileResponse? {
        val response = ApiClient.authApi.registerDevice(RegisterDeviceRequest(userId))
        if (!response.isSuccessful) {
            throw IOException("Unable to create profile (HTTP ${response.code()}).")
        }
        return response.body()
    }

    private suspend fun uploadAvatar(
        context: Context,
        userId: String,
        uri: Uri,
    ): UserProfileResponse {
        val file = FileUtils.uriToFile(context, uri)
            ?: throw IOException("Unable to read the selected image.")
        try {
            val contentType = context.contentResolver.getType(uri)?.toMediaTypeOrNull()
                ?: "image/jpeg".toMediaType()
            val imagePart = MultipartBody.Part.createFormData(
                "file",
                file.name,
                file.asRequestBody(contentType),
            )
            val userIdPart = userId.toRequestBody("text/plain".toMediaType())
            val response = ApiClient.authApi.uploadAvatar(userIdPart, imagePart)

            if (!response.isSuccessful) {
                throw IOException("Unable to upload avatar (HTTP ${response.code()}).")
            }
            return response.body() ?: throw IOException("The avatar response was empty.")
        } finally {
            if (file.exists() && !file.delete()) {
                Log.w(TAG, "Unable to delete temporary avatar file.")
            }
        }
    }

    private suspend fun loadProfileSummary(userId: String) {
        val response = ApiClient.authApi.getProfileSummary(userId)
        if (response.isSuccessful) {
            profileSummary = response.body()
        } else {
            Log.w(TAG, "Unable to load profile summary: HTTP ${response.code()}")
            profileSummary = null
        }
    }

    private fun applyProfile(profile: UserProfileResponse) {
        userName = profile.name.orEmpty()
        avatarUrl = profile.avatarUrl.toAvatarUrl()
    }

    private fun String?.toAvatarUrl(): String? {
        val value = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) {
            return value
        }

        val normalizedPath = value.trimStart('/')
        val relativePath = if (normalizedPath.startsWith("uploads/")) {
            normalizedPath
        } else {
            "uploads/$normalizedPath"
        }
        return "${BuildConfig.API_BASE_URL.trimEnd('/')}/$relativePath"
    }

    private fun Throwable.toUserMessage(fallback: String): String {
        return message?.takeIf { it.isNotBlank() } ?: fallback
    }

    private companion object {
        const val TAG = "ProfileViewModel"
        const val MIN_DISPLAY_NAME_LENGTH = 1
        const val MAX_DISPLAY_NAME_LENGTH = 50
    }
}
