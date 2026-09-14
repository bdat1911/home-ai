package com.example.revroom.data.local

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.core.content.edit

class LocalUserIdProvider(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getOrCreateUserId(): String {
        // 1. Nếu đã có ID ổn định thì dùng luôn
        val existingUserId = preferences.getString(KEY_USER_ID, null)
        if (!existingUserId.isNullOrBlank() && !existingUserId.contains("-")) {
            return existingUserId
        }
2
        // 2. Lấy Android ID
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        // 3. Tạo mã định danh từ phần cứng (Hardware Fingerprint)

        val hardwareInfo = android.os.Build.MODEL + android.os.Build.BOARD + 
                          android.os.Build.BRAND + android.os.Build.DEVICE
        val hardwareId = "RevRoom_${Math.abs(hardwareInfo.hashCode())}"

        val userId = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            androidId
        } else {
            Log.w(TAG, "Dùng Hardware ID ổn định cho máy ảo: $hardwareId")
            hardwareId
        }

        saveUserId(userId)
        return userId
    }

    fun saveUserId(userId: String) {
        preferences.edit(commit = true) {
            putString(KEY_USER_ID, userId)
        }
    }

    private companion object {
        const val TAG = "LocalUserIdProvider"
        const val PREFS_NAME = "InteriorAIPrefs"
        const val KEY_USER_ID = "DEVICE_ID"
    }
}
