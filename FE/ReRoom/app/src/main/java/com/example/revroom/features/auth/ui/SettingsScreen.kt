package com.example.revroom.features.auth.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.revroom.data.local.UserManager
import com.example.revroom.features.auth.viewmodel.AuthViewModel

@Composable
fun SettingsScreen(
    userManager: UserManager,
    authViewModel: AuthViewModel = viewModel(),
) {
    val context = LocalContext.current
    val userId = remember(userManager) { userManager.getDeviceId() }
    val clipboardManager = LocalClipboardManager.current

    // State quản lý
    var userNameInput by remember { mutableStateOf("") }
    val currentNameFromServer = authViewModel.userName

    // Đăng ký Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        authViewModel.onImageSelected(uri)
    }

    // Tự động load profile
    LaunchedEffect(key1 = userId) {
        authViewModel.loadUserProfile(userId)
    }

    // Đồng bộ tên từ server về UI
    LaunchedEffect(key1 = currentNameFromServer) {
        currentNameFromServer?.let {
            userNameInput = it
        }
    }

    val isNameChanged = userNameInput != (currentNameFromServer ?: "")
    val shouldShowSaveButton = authViewModel.selectedImageUri != null || isNameChanged

    // --- MÀU SẮC CHUẨN ---
    val gradientColors = listOf(Color(0xFF8A2BE2), Color(0xFFFF4081)) // Tím đậm -> Hồng nhạt
    val backgroundColor = Color(0xFFF7F8FA) // Màu nền trắng xám bên dưới

    // ROOT LAYOUT: Tách thẳng thành 2 nửa 40/60 thay vì xếp chồng Layer
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // ==========================================
        // PHẦN TRÊN: 40% (BACKGROUND GRADIENT)
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.4f)
                .background(brush = Brush.verticalGradient(gradientColors))
                .padding(top = 60.dp, bottom = 16.dp), // Margin an toàn cho system bar
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Tiêu đề
            Text(
                text = "Settings",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            // Dùng weight(1f) để tự động căn giữa Avatar vào phần không gian còn lại
            Spacer(modifier = Modifier.weight(1f))

            // 2. KHU VỰC AVATAR
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(4.dp) // Viền trắng
                    .clip(CircleShape)
                    .background(Color.LightGray)
                    .clickable {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val selectedUri = authViewModel.selectedImageUri
                val currentAvatarUrl = authViewModel.avatarUrl

                if (selectedUri != null) {
                    AsyncImage(
                        model = selectedUri,
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (!currentAvatarUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = currentAvatarUrl,
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Default Avatar",
                        modifier = Modifier.size(60.dp),
                        tint = Color.Gray
                    )
                }
            }

            // Khoảng trống linh hoạt phía dưới Avatar
            Spacer(modifier = Modifier.weight(1f))
        }

        // ==========================================
        // PHẦN DƯỚI: 60% (BACKGROUND TRẮNG XÁM)
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.6f)
                .background(backgroundColor)
                .padding(horizontal = 24.dp)
        ) {

            // Khoảng cách cố định từ mép phân cách (40/60) đẩy Form xuống
            Spacer(modifier = Modifier.height(32.dp))

            // Nhập Tên
            Text(text = "USER PROFILE", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = userNameInput,
                onValueChange = { userNameInput = it },
                placeholder = { Text("Enter your name") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF8A2BE2),
                    unfocusedBorderColor = Color.LightGray,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Copy ID
            Text(text = "USER SETTINGS", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = Color.Gray)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = if (userId.length > 18) userId.take(18) + "..." else userId,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.DarkGray
                        )
                    }
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(userId))
                            Toast.makeText(context, "ID Copied!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy ID", tint = Color.Gray)
                    }
                }
            }

            // Nút Lưu thay đổi
            if (shouldShowSaveButton) {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (authViewModel.selectedImageUri != null) {
                            authViewModel.uploadImage(context, userId)
                        } else {
                            authViewModel.updateUserProfile(userId, userNameInput)
                        }
                    },
                    enabled = !authViewModel.isUploading,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8A2BE2)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (authViewModel.isUploading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Text("SAVE CHANGES", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // --- APP VERSION (Dùng weight để tự động đẩy nó xuống sát mép dưới màn hình)
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "App Version i100.1.2 AA",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
            }
            Spacer(modifier = Modifier.height(50.dp)) // Khoảng hở với viền dưới điện thoại
        }
    }
}