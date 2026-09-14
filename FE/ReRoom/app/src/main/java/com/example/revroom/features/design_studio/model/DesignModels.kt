package com.example.revroom.features.design_studio.model

import android.net.Uri

data class DesignRequest(
    val imageUri: Uri,
    val styleId: Int?,
    val roomType: String? = null,
    val featureId: String? = null,
    val model: String? = "generate-pro",
    val resolution: String? = "2K"
)

data class DesignStyle(
    val styleId: Int,
    val styleName: String,
    val coreAesthetic: String = "",
    val lighting: String = "",
    val material: String = "",
    val color: String = "",
    val atmosphere: String = ""
)

enum class DesignMode {
    Interior,
    Exterior
}

val DesignUiState.selectedFeatureTitle: String
    get() = designFeatureTitle(selectedFeature)

val DesignUiState.selectedFeatureUploadSubtitle: String
    get() = when (normalizeDesignFeatureId(selectedFeature)) {
        "furnish_empty_room" -> "Upload an empty room photo"
        "remove_furniture" -> "Upload a room photo to clear"
        else -> "Upload your room photo"
    }

val DesignUiState.selectedFeatureRoomSubtitle: String
    get() = when (normalizeDesignFeatureId(selectedFeature)) {
        "furnish_empty_room" -> "What empty room is this?"
        "remove_furniture" -> "What room should be cleared?"
        else -> "What room is this?"
    }

val DesignUiState.selectedFeatureStyleSubtitle: String
    get() = when (normalizeDesignFeatureId(selectedFeature)) {
        "furnish_empty_room" -> "Choose furnishing style"
        "remove_furniture" -> "Choose cleanup style"
        else -> "Choose your style"
    }

val DesignUiState.selectedFeatureProcessingSubtitle: String
    get() = when (normalizeDesignFeatureId(selectedFeature)) {
        "furnish_empty_room" -> "Furnishing your room"
        "remove_furniture" -> "Removing furniture"
        else -> "Generating your design"
    }

val DesignUiState.selectedFeatureProcessingTitle: String
    get() = when (normalizeDesignFeatureId(selectedFeature)) {
        "furnish_empty_room" -> "Furnishing your room..."
        "remove_furniture" -> "Removing furniture..."
        else -> "Creating your redesign..."
    }

val DesignUiState.selectedFeatureResultTitle: String
    get() = when (normalizeDesignFeatureId(selectedFeature)) {
        "furnish_empty_room" -> "Your furnished room is ready"
        "remove_furniture" -> "Your cleared room is ready"
        else -> "Your redesign is ready"
    }

val DesignUiState.designFlowTotalSteps: Int
    get() = if (isRemoveFurnitureFeature) 2 else 4

val DesignUiState.designFlowProcessingStep: Int
    get() = designFlowTotalSteps

private fun designFeatureTitle(featureId: String?): String {
    return when (normalizeDesignFeatureId(featureId)) {
        "furnish_empty_room" -> "Furnish Empty Room"
        "remove_furniture" -> "Remove Furniture"
        else -> "Interior Design"
    }
}

private fun normalizeDesignFeatureId(featureId: String?): String {
    return when (featureId?.trim()?.lowercase()?.replace("-", "_")?.replace(" ", "_")) {
        "furnish_empty_room" -> "furnish_empty_room"
        "remove_furniture" -> "remove_furniture"
        else -> "interior_design"
    }
}

private val DesignUiState.isRemoveFurnitureFeature: Boolean
    get() = normalizeDesignFeatureId(selectedFeature) == "remove_furniture"

data class DesignResult(
    val designId: String,
    val status: DesignJobStatus,
    val originalImageUrl: String,
    val designedImageUrl: String?,
    val errorMessage: String?
)

enum class DesignJobStatus {
    Pending,
    Completed,
    Failed,
    Unknown;

    companion object {
        fun fromApiValue(value: String): DesignJobStatus {
            return when (value.lowercase()) {
                "pending" -> Pending
                "completed" -> Completed
                "failed" -> Failed
                else -> Unknown
            }
        }
    }
}
