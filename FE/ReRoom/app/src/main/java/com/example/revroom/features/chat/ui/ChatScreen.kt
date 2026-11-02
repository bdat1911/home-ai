package com.example.revroom.features.chat.ui

import androidx.compose.runtime.Composable

@Composable
fun ChatScreen(
    onInterior: () -> Unit,
    onExterior: () -> Unit,
    onChat: () -> Unit,
    onGallery: () -> Unit,
    showBottomBar: Boolean = true
) {
    AdviceChatScreen(
        onInterior = onInterior,
        onExterior = onExterior,
        onChat = onChat,
        onGallery = onGallery,
        showBottomBar = showBottomBar
    )
}
