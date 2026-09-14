package com.example.revroom.features.design_studio.model

import android.net.Uri

data class DesignUiState(
    val designMode: DesignMode = DesignMode.Interior,
    val selectedFeature: String? = null,
    val selectedImageUri: Uri? = null,
    val selectedRoomType: String? = null,
    val selectedStyle: String? = null,
    val selectedModel: String? = null,
    val selectedResolution: String? = null,
    val phase: DesignPhase = DesignPhase.Idle,
    val designId: String? = null,
    val originalImageUrl: String? = null,
    val designedImageUrl: String? = null,
    val errorMessage: String? = null
)

enum class DesignPhase {
    Idle,
    Uploading,
    Processing,
    Completed,
    Failed
}
