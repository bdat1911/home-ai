package com.example.revroom.features.history.model

import com.google.gson.annotations.SerializedName

data class ProjectModel(
    @SerializedName("designId", alternate = ["id"])
    val designId: String?,
    @SerializedName("originalImageUrl")
    val originalImageUrl: String?,
    @SerializedName("designedImageUrl")
    val designedImageUrl: String?,
    @SerializedName("status")
    val status: String?,
    @SerializedName("createdAt")
    val createdAt: String?
)
