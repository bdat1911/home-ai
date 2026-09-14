package com.example.revroom.data.local

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.core.content.edit

class UserManager(private val context: Context) {
    // Sử dụng chung file với LocalUserIdProvider để đồng bộ
    private val prefs = context.getSharedPreferences("InteriorAIPrefs", Context.MODE_PRIVATE)
    
    var isNewUser = false
    
    fun getDeviceId(): String {
        val existingId = prefs.getString("DEVICE_ID", null)
        
        // Nếu đã có ID trong máy và nó KHÔNG phải là UUID (không có dấu gạch ngang)
        // thì dùng luôn. Nếu là UUID cũ, ta sẽ ép tạo lại theo Hardware ID.
        if (!existingId.isNullOrBlank() && !existingId.contains("-")) {
            return existingId
        }

        // 1. Lấy Android ID
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        // 2. Tạo mã Fingerprint từ phần cứng (Cực kỳ ổn định trên máy ảo)
        val hardwareInfo = android.os.Build.MODEL + android.os.Build.BOARD + 
                          android.os.Build.BRAND + android.os.Build.DEVICE
        val fingerprint = Math.abs(hardwareInfo.hashCode()).toString()
        val hardwareId = "RevRoom_$fingerprint"

        // 3. Quyết định ID: Ưu tiên Android ID xịn, nếu không thì dùng Hardware ID
        val finalId = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            androidId
        } else {
            hardwareId
        }

        Log.d("STABLE_ID", "Đã xác định ID ổn định cho thiết bị: $finalId")
        
        prefs.edit(commit = true) {
            putString("DEVICE_ID", finalId)
        }
        
        return finalId
    }

    var isRegisteredOnServer: Boolean
        get() = prefs.getBoolean("IS_REGISTERED", false)
        set(value) = prefs.edit { putBoolean("IS_REGISTERED", value) }
}
