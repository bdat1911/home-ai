package com.example.revroom.features.design_studio.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.revroom.core.ui.GradientButton
import com.example.revroom.core.ui.StepProgress
import com.example.revroom.core.ui.StudioMuted
import com.example.revroom.core.ui.StudioScaffold
import com.example.revroom.core.ui.StudioTab
import com.example.revroom.core.ui.StudioText
import com.example.revroom.core.ui.TopTitleBar

@Composable
fun UploadPhotoScreen(
    title: String,
    stepCaption: String = "Upload your room photo",
    selectedTab: StudioTab,
    selectedImageUri: Uri?,
    onImageSelected: (Uri?) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onInterior: () -> Unit,
    onExterior: () -> Unit,
    onChat: () -> Unit,
    onGallery: () -> Unit,
    totalSteps: Int = 4,
    showBottomBar: Boolean = true
) {
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = onImageSelected
    )
    var selectedImageAspectRatio by remember(selectedImageUri) { mutableStateOf<Float?>(null) }
    val previewAspectRatio = (selectedImageAspectRatio ?: 1.35f).coerceIn(0.75f, 1.8f)

    StudioScaffold(
        selectedTab = selectedTab,
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        onInterior = onInterior,
        onExterior = onExterior,
        onChat = onChat,
        onGallery = onGallery,
        showBottomBar = showBottomBar
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopTitleBar(
                title = title,
                modifier = Modifier.padding(top = 16.dp),
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = StudioText
                        )
                    }
                }
            )

            StepProgress(
                currentStep = 1,
                caption = stepCaption,
                modifier = Modifier.padding(top = 10.dp),
                totalSteps = totalSteps
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .aspectRatio(previewAspectRatio)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0xFFDADDE3), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedImageUri != null) {
                            AsyncImage(
                                model = selectedImageUri,
                                contentDescription = "Selected room photo",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Fit,
                                onSuccess = { state ->
                                    state.painter.intrinsicSize.toImageAspectRatio()?.let { aspectRatio ->
                                        selectedImageAspectRatio = aspectRatio
                                    }
                                }
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.CameraAlt,
                                    contentDescription = null,
                                    tint = StudioMuted,
                                    modifier = Modifier.size(38.dp)
                                )
                                Text(
                                    text = "Upload new photo",
                                    modifier = Modifier.padding(top = 12.dp),
                                    color = StudioText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Take or choose a photo of your room",
                                    modifier = Modifier.padding(top = 7.dp),
                                    color = StudioMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    GradientButton(
                        text = if (selectedImageUri == null) "Choose photo" else "Next",
                        icon = if (selectedImageUri == null) Icons.Outlined.Image else Icons.AutoMirrored.Filled.ArrowForward,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        onClick = {
                            if (selectedImageUri == null) {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            } else {
                                onNext()
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun Size.toImageAspectRatio(): Float? {
    return if (width.isFinite() && height.isFinite() && width > 0f && height > 0f) {
        width / height
    } else {
        null
    }
}
