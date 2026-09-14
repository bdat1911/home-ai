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
import com.example.revroom.data.remote.RegisterDeviceRequest
import com.example.revroom.data.remote.UpdateProfileRequest
import com.example.revroom.data.local.UserManager
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class AuthViewModel : ViewModel() {

    // --- QUẢN LÝ TRẠNG THÁI UI ---
    var selectedImageUri by mutableStateOf<Uri?>(null)
        private set

    var isUploading by mutableStateOf(value = false)
        private set

    var avatarUrl by mutableStateOf<String?>(null)
        private set

    var userName by mutableStateOf<String?>(null)
        private set

    // --- CÁC HÀM XỬ LÝ LOGIC ---

    fun onImageSelected(uri: Uri?) {
        selectedImageUri = uri
    }

    fun sendIdToServer(userManager: UserManager, userId: String) {
        viewModelScope.launch {
            try {
                val response = ApiClient.authApi.registerDevice(RegisterDeviceRequest(userId))
                if (response.isSuccessful) {
                    Log.d("API_TEST", "Thành công! Đã bắn ID: $userId lên C#")
                    userManager.isRegisteredOnServer = true
                } else {
                    Log.e("API_TEST", "Lỗi từ C#: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("API_TEST", "Mất mạng hoặc Backend chưa bật: ${e.message}")
            }
        }
    }

    fun loadUserProfile(userId: String) {
        viewModelScope.launch {
            try {
                val response = ApiClient.authApi.getProfile(userId)
                if (response.isSuccessful) {
                    val profile = response.body()
                    if (profile != null) {
                        userName = profile.name
                        val rawFileName = profile.avatarUrl
                        if (!rawFileName.isNullOrEmpty()) {
                            avatarUrl = "${BuildConfig.API_BASE_URL}uploads/$rawFileName"
                            Log.d("API_TEST", "Lấy ảnh thành công: $avatarUrl")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("API_TEST", "Lỗi kéo Profile: ${e.message}")
            }
        }
    }

    // --- ĐÃ FIX API ĐỔI TÊN CHUẨN THEO BACKEND ---
    fun updateUserProfile(userId: String, name: String) {
        viewModelScope.launch {
            isUploading = true
            try {
                Log.d("API_TEST", "Đang gọi Backend đổi tên thành: $name")
                // Gọi PUT /api/Auth/profile/{userId} với Body chứa UpdateProfileRequest
                val response = ApiClient.authApi.updateProfile(userId, UpdateProfileRequest(name))
                if (response.isSuccessful) {
                    Log.d("API_TEST", "Thành công! C# đã nhận tên mới.")
                    loadUserProfile(userId)
                } else {
                    Log.e("API_TEST", "Lỗi từ C#: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("API_TEST", "Lỗi update (Mất mạng): ${e.message}")
            } finally {
                isUploading = false
            }
        }
    }

    fun uploadImage(context: Context, userId: String) {
        val uri = selectedImageUri ?: return

        viewModelScope.launch {
            isUploading = true
            try {
                val file = FileUtils.uriToFile(context, uri)
                if (file == null) {
                    Log.e("API_TEST", "Lỗi: Không đọc được file ảnh!")
                    return@launch
                }

                val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                val userIdBody = userId.toRequestBody("text/plain".toMediaTypeOrNull())

                val response = ApiClient.authApi.uploadAvatar(userIdBody, body)

                if (response.isSuccessful) {
                    Log.d("API_TEST", "Thành công! Đã đẩy ảnh lên C#")
                    selectedImageUri = null
                    loadUserProfile(userId)
                } else {
                    Log.e("API_TEST", "Lỗi C# trả về: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("API_TEST", "Sập mạng: ${e.message}")
            } finally {
                isUploading = false
            }
        }
    }
}
