package com.example.revroom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.revroom.core.theme.RevroomTheme
import com.example.revroom.data.local.UserManager
import com.example.revroom.features.auth.viewmodel.AuthViewModel
import com.example.revroom.navigation.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Khởi tạo đồ nghề Backend
        val userManager = UserManager(this)
        val authViewModel = AuthViewModel()
        val userId = userManager.getDeviceId()

        // 2. Logic kiểm tra nếu chưa đăng ký thành công trên server thì gửi lại
        if (!userManager.isRegisteredOnServer) {
            authViewModel.sendIdToServer(userManager, userId)
        }

        // 3. Khởi chạy Giao diện
        enableEdgeToEdge()
        setContent {
            RevroomTheme {
                // Trả lại sân khấu cho AppNavigation của team chạy.
                AppNavigation()
            }
        }
    }
}
