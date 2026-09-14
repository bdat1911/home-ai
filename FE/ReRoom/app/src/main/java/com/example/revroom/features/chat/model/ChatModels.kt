package com.example.revroom.features.chat.model

import android.net.Uri

data class ChatMessage(
    val id: String,
    val text: String,
    val imageUrl: String? = null,
    val localImageUri: android.net.Uri? = null,
    val isFromUser: Boolean,
    val designId: String? = null,
    val isProcessing: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val selectedImageUri: Uri? = null,
    val selectedModel: String? = null,
    val selectedResolution: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)
