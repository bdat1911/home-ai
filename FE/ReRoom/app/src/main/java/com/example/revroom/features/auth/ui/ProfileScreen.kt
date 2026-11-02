package com.example.revroom.features.auth.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.revroom.BuildConfig
import com.example.revroom.core.ui.StudioBackground
import com.example.revroom.core.ui.StudioGradient
import com.example.revroom.core.ui.StudioMuted
import com.example.revroom.core.ui.StudioPink
import com.example.revroom.core.ui.StudioPurple
import com.example.revroom.core.ui.StudioText
import com.example.revroom.core.ui.TopTitleBar
import com.example.revroom.data.local.UserManager
import com.example.revroom.data.remote.ProfileSummaryResponse
import com.example.revroom.features.auth.viewmodel.AuthViewModel

@Composable
fun ProfileScreen(
    userManager: UserManager,
    authViewModel: AuthViewModel = viewModel(),
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val userId = remember(userManager) { userManager.getDeviceId() }
    val serverName = authViewModel.userName.orEmpty()

    var displayName by rememberSaveable(userId) { mutableStateOf("") }
    var nameTouched by rememberSaveable(userId) { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        authViewModel.onImageSelected(uri)
    }

    LaunchedEffect(userId) {
        authViewModel.loadUserProfile(userId)
    }

    LaunchedEffect(serverName) {
        if (!nameTouched && serverName.isNotBlank()) {
            displayName = serverName
        }
    }

    LaunchedEffect(authViewModel.successMessage) {
        if (authViewModel.successMessage != null) {
            nameTouched = false
            displayName = authViewModel.userName.orEmpty()
        }
    }

    val hasChanges = authViewModel.selectedImageUri != null || displayName.trim() != serverName

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackground)
            .systemBarsPadding()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TopTitleBar(title = "My Profile")
        }

        if (BuildConfig.DEBUG) {
            item {
                ServerConnectionCard(
                    enabled = !authViewModel.isSaving && !authViewModel.isLoading,
                    canSave = { !authViewModel.isSaving && !authViewModel.isLoading },
                    onSaved = { authViewModel.loadUserProfile(userId) },
                )
            }
        }

        item {
            ProfileHero(
                selectedAvatar = authViewModel.selectedImageUri,
                avatarUrl = authViewModel.avatarUrl,
                displayName = displayName.ifBlank { "Your profile" },
                onAvatarClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )
        }

        if (authViewModel.isLoading) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = StudioPurple,
                    trackColor = Color(0xFFE9DDFE),
                )
            }
        }

        authViewModel.errorMessage?.let { error ->
            item {
                StatusCard(
                    message = error,
                    color = Color(0xFFB3261E),
                    actionLabel = "Retry",
                    onAction = {
                        authViewModel.loadUserProfile(userId)
                    },
                )
            }
        }

        authViewModel.successMessage?.let { message ->
            item {
                StatusCard(
                    message = message,
                    color = Color(0xFF1B7F4B),
                )
            }
        }

        item {
            SectionTitle("PROFILE DETAILS")
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Display name",
                        style = MaterialTheme.typography.labelLarge,
                        color = StudioText,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = {
                            displayName = it
                            nameTouched = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Enter your name") },
                        supportingText = { Text("Use 1–50 characters") },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StudioPurple,
                            unfocusedBorderColor = Color(0xFFD8DCE2),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                        ),
                    )
                }
            }
        }

        item {
            SectionTitle("PROJECT ACTIVITY")
            Spacer(modifier = Modifier.height(8.dp))
            ProfileSummary(summary = authViewModel.profileSummary)
        }

        item {
            SectionTitle("ACCOUNT")
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF0E7FF)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = StudioPurple)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("User ID", color = StudioMuted, fontSize = 12.sp)
                        Text(
                            text = userId,
                            color = StudioText,
                            fontSize = 13.sp,
                            maxLines = 1,
                        )
                    }
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(userId))
                            Toast.makeText(context, "User ID copied", Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy user ID", tint = StudioMuted)
                    }
                }
            }
        }

        item {
            Button(
                onClick = { authViewModel.saveProfile(context, userId, displayName) },
                enabled = hasChanges && !authViewModel.isSaving && !authViewModel.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioPurple,
                    disabledContainerColor = Color(0xFFE2D4F9),
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (authViewModel.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = if (hasChanges) "Save changes" else "Profile is up to date",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { authViewModel.loadUserProfile(userId) },
                enabled = !authViewModel.isLoading && !authViewModel.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Refresh profile")
            }
        }

        item {
            Text(
                text = "Home AI · Version ${BuildConfig.VERSION_NAME}",
                modifier = Modifier.fillMaxWidth(),
                color = StudioMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ProfileHero(
    selectedAvatar: Any?,
    avatarUrl: String?,
    displayName: String,
    onAvatarClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .border(3.dp, StudioPurple, CircleShape)
                    .clickable(onClick = onAvatarClick),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    selectedAvatar != null -> AsyncImage(
                        model = selectedAvatar,
                        contentDescription = "Selected profile photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )

                    !avatarUrl.isNullOrBlank() -> AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Profile photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )

                    else -> Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Default profile photo",
                        modifier = Modifier.size(58.dp),
                        tint = StudioMuted,
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(StudioGradient)
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Change profile photo",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(displayName, color = StudioText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Tap the photo to change your avatar", color = StudioMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ProfileSummary(summary: ProfileSummaryResponse?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ProfileStat("Projects", summary?.projectCount?.toString() ?: "–")
            ProfileStat("Completed", summary?.completedDesignCount?.toString() ?: "–")
            ProfileStat("Processing", summary?.pendingDesignCount?.toString() ?: "–")
        }
    }
}

@Composable
private fun ProfileStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = StudioText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, color = StudioMuted, fontSize = 11.sp)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, color = StudioMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.8.sp)
}

@Composable
private fun StatusCard(
    message: String,
    color: Color,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, modifier = Modifier.weight(1f), color = color, fontSize = 13.sp)
            if (actionLabel != null && onAction != null) {
                Text(
                    text = actionLabel,
                    modifier = Modifier.clickable(onClick = onAction).padding(start = 12.dp),
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
