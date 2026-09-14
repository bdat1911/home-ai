package com.example.revroom.features.design_studio.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.revroom.core.ui.GradientButton
import com.example.revroom.core.ui.StepProgress
import com.example.revroom.core.ui.StudioMuted
import com.example.revroom.core.ui.StudioScaffold
import com.example.revroom.core.ui.StudioTab
import com.example.revroom.core.ui.StudioText
import com.example.revroom.core.ui.TopTitleBar
import com.example.revroom.features.design_studio.components.BeforeAfterSlider
import com.example.revroom.features.design_studio.components.FanSpinnerAnimation
import com.example.revroom.features.design_studio.model.DesignMode
import com.example.revroom.features.design_studio.model.DesignPhase
import com.example.revroom.features.design_studio.model.DesignUiState
import com.example.revroom.features.design_studio.model.selectedFeatureProcessingSubtitle
import com.example.revroom.features.design_studio.model.selectedFeatureProcessingTitle
import com.example.revroom.features.design_studio.model.selectedFeatureResultTitle
import com.example.revroom.features.design_studio.model.selectedFeatureTitle
import com.example.revroom.core.theme.RevroomTheme

@Composable
fun ProcessingResultScreen(
    uiState: DesignUiState,
    onRetry: () -> Unit,
    onSaveToHistory: () -> Unit,
    onCreateAnother: () -> Unit,
    onBack: () -> Unit,
    onInterior: () -> Unit,
    onExterior: () -> Unit,
    onChat: () -> Unit,
    onGallery: () -> Unit,
    currentStep: Int = 4,
    totalSteps: Int = 4,
    showBottomBar: Boolean = true
) {
    var showExitDialog by remember { mutableStateOf(false) }
    val isProcessing = uiState.phase == DesignPhase.Uploading || uiState.phase == DesignPhase.Processing
    val selectedTab = if (uiState.designMode == DesignMode.Exterior) StudioTab.Exterior else StudioTab.Interior
    val title = if (uiState.designMode == DesignMode.Exterior) "Exterior Design" else uiState.selectedFeatureTitle
    val processingCaption = if (uiState.designMode == DesignMode.Exterior) {
        "Generating your design"
    } else {
        uiState.selectedFeatureProcessingSubtitle
    }
    val resultTitle = if (uiState.designMode == DesignMode.Exterior) {
        "Your design is ready"
    } else {
        uiState.selectedFeatureResultTitle
    }
    val processingTitle = if (uiState.designMode == DesignMode.Exterior) {
        "Creating your redesign..."
    } else {
        uiState.selectedFeatureProcessingTitle
    }

    BackHandler(enabled = isProcessing) {
        showExitDialog = true
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Cancel design?") },
            text = { Text("Do you want to cancel this generation?") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    onBack()
                }) {
                    Text("Cancel")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Keep waiting")
                }
            }
        )
    }

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
                currentStep = currentStep,
                caption = if (uiState.phase == DesignPhase.Completed) resultTitle else processingCaption,
                modifier = Modifier.padding(top = 6.dp),
                totalSteps = totalSteps
            )

            Box(modifier = Modifier.weight(1f)) {
                when (uiState.phase) {
                    DesignPhase.Uploading,
                    DesignPhase.Processing,
                    DesignPhase.Idle -> ProcessingContent(title = processingTitle)

                    DesignPhase.Completed -> CompletedContent(
                        title = resultTitle,
                        originalImageUrl = uiState.originalImageUrl,
                        designedImageUrl = uiState.designedImageUrl,
                        onSaveToHistory = onSaveToHistory,
                        onCreateAnother = onCreateAnother
                    )

                    DesignPhase.Failed -> FailedContent(
                        errorMessage = uiState.errorMessage,
                        onRetry = onRetry,
                        onBack = onBack
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessingContent(title: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        FanSpinnerAnimation(modifier = Modifier.size(76.dp))

        Text(
            text = title,
            modifier = Modifier.padding(top = 28.dp),
            color = StudioText,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Please wait while we redesign your space. This can take 1-3 minutes.",
            modifier = Modifier.padding(top = 8.dp),
            color = StudioMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CompletedContent(
    title: String,
    originalImageUrl: String?,
    designedImageUrl: String?,
    onSaveToHistory: () -> Unit,
    onCreateAnother: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = title,
            color = StudioText,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        if (originalImageUrl != null && designedImageUrl != null) {
            BeforeAfterSlider(
                beforeImageUrl = originalImageUrl,
                afterImageUrl = designedImageUrl,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = "No result image was returned yet.",
                color = StudioMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        GradientButton(
            text = "Save to gallery",
            icon = Icons.Outlined.Save,
            modifier = Modifier.fillMaxWidth(),
            onClick = onSaveToHistory
        )

        OutlinedButton(
            onClick = onCreateAnother,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(imageVector = Icons.Outlined.AddPhotoAlternate, contentDescription = null)
            Text(text = "Create another", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun FailedContent(
    errorMessage: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(60.dp)
        )

        Text(
            text = "Unable to create design",
            modifier = Modifier.padding(top = 16.dp),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = errorMessage ?: "Something went wrong.",
            modifier = Modifier.padding(top = 8.dp),
            color = StudioMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "Back")
            }
            GradientButton(
                text = "Retry",
                icon = Icons.Outlined.Refresh,
                modifier = Modifier.weight(1f),
                onClick = onRetry
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProcessingResultScreenProcessingPreview() {
    RevroomTheme {
        ProcessingResultScreen(
            uiState = DesignUiState(
                phase = DesignPhase.Processing,
                designMode = DesignMode.Interior
            ),
            onRetry = {},
            onSaveToHistory = {},
            onCreateAnother = {},
            onBack = {},
            onInterior = {},
            onExterior = {},
            onChat = {},
            onGallery = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ProcessingResultScreenCompletedPreview() {
    RevroomTheme {
        ProcessingResultScreen(
            uiState = DesignUiState(
                phase = DesignPhase.Completed,
                designMode = DesignMode.Interior,
                originalImageUrl = "https://example.com/original.jpg",
                designedImageUrl = "https://example.com/designed.jpg"
            ),
            onRetry = {},
            onSaveToHistory = {},
            onCreateAnother = {},
            onBack = {},
            onInterior = {},
            onExterior = {},
            onChat = {},
            onGallery = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ProcessingResultScreenFailedPreview() {
    RevroomTheme {
        ProcessingResultScreen(
            uiState = DesignUiState(
                phase = DesignPhase.Failed,
                errorMessage = "Failed to connect to server. Please check your internet connection and try again."
            ),
            onRetry = {},
            onSaveToHistory = {},
            onCreateAnother = {},
            onBack = {},
            onInterior = {},
            onExterior = {},
            onChat = {},
            onGallery = {}
        )
    }
}
