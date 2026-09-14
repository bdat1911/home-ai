package com.example.revroom.features.chat.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.revroom.data.repository.DesignRepository
import com.example.revroom.features.chat.model.ChatMessage
import com.example.revroom.features.chat.model.ChatUiState
import com.example.revroom.features.design_studio.model.DesignJobStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(
    private val repository: DesignRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun onImageSelected(uri: Uri?) {
        _uiState.update { it.copy(selectedImageUri = uri) }
    }

    fun selectModel(model: String?) {
        _uiState.update { it.copy(selectedModel = model) }
    }

    fun selectResolution(resolution: String?) {
        _uiState.update { it.copy(selectedResolution = resolution) }
    }

    fun sendMessage() {
        val currentState = _uiState.value
        val text = currentState.inputText
        val imageUri = currentState.selectedImageUri

        if (text.isBlank() && imageUri == null) return

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = text,
            localImageUri = imageUri,
            isFromUser = true
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                inputText = "",
                selectedImageUri = null,
                isLoading = true,
                error = null
            )
        }

        viewModelScope.launch {
            Log.d("ChatViewModel", "Sending message: '$text', hasImage: ${imageUri != null}")
            repository.sendMessage(text, imageUri, currentState.selectedModel, currentState.selectedResolution)
                .onSuccess { response ->
                    val assistantMessageId = UUID.randomUUID().toString()
                    val assistantMessage = ChatMessage(
                        id = assistantMessageId,
                        text = response.message ?: "Đang xử lý thiết kế của bạn...",
                        imageUrl = response.imageUrl,
                        isFromUser = false,
                        designId = response.designId,
                        isProcessing = response.designId != null && response.imageUrl == null
                    )
                    _uiState.update {
                        it.copy(
                            messages = it.messages + assistantMessage,
                            isLoading = false
                        )
                    }

                    // Nếu có designId mà chưa có imageUrl, bắt đầu polling để lấy kết quả
                    if (response.designId != null && response.imageUrl == null) {
                        startPollingStatus(assistantMessageId, response.designId)
                    }
                }
                .onFailure { error ->
                    Log.e("ChatViewModel", "Server response failure: ${error.message}", error)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to send message"
                        )
                    }
                }
        }
    }

    private fun startPollingStatus(messageId: String, designId: String) {
        viewModelScope.launch {
            var isFinished = false
            var retryCount = 0
            val maxRetries = 30 // Khoảng 2 phút (30 * 4s)

            while (!isFinished && retryCount < maxRetries) {
                delay(4000) // Đợi 4 giây mỗi lần check
                retryCount++

                repository.getDesignStatus(designId)
                    .onSuccess { result ->
                        Log.d("ChatViewModel", "Polling status for $designId: ${result.status}")
                        when (result.status) {
                            DesignJobStatus.Completed -> {
                                updateMessageStatus(messageId, result.designedImageUrl, null)
                                isFinished = true
                            }
                            DesignJobStatus.Failed -> {
                                val errorMsg = result.errorMessage ?: "Thiết kế thất bại. Có thể do hết phí API hoặc ảnh không phù hợp."
                                updateMessageStatus(messageId, null, errorMsg)
                                isFinished = true
                            }
                            else -> { /* Tiếp tục đợi Pending */ }
                        }
                    }
                    .onFailure { error ->
                        Log.e("ChatViewModel", "Polling failed for $designId: ${error.message}")
                        // Nếu lỗi kết nối thì có thể thử lại, nhưng nếu lỗi 402/400 (hết phí) thì nên dừng
                        if (error.message?.contains("402") == true || error.message?.contains("credit") == true) {
                            updateMessageStatus(messageId, null, "Tài khoản của bạn đã hết lượt sử dụng (Out of credits).")
                            isFinished = true
                        }
                    }
            }
            
            if (!isFinished && retryCount >= maxRetries) {
                updateMessageStatus(messageId, null, "Thời gian xử lý quá lâu. Vui lòng kiểm tra lại sau.")
            }
        }
    }

    private fun updateMessageStatus(messageId: String, imageUrl: String?, errorText: String?) {
        _uiState.update { state ->
            val updatedMessages = state.messages.map { msg ->
                if (msg.id == messageId) {
                    val newText = when {
                        errorText != null -> errorText
                        imageUrl != null -> "Thiết kế của bạn đã hoàn thành!"
                        else -> msg.text
                    }
                    msg.copy(
                        text = newText,
                        imageUrl = imageUrl ?: msg.imageUrl,
                        isProcessing = false
                    )
                } else msg
            }
            state.copy(messages = updatedMessages)
        }
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(DesignRepository(appContext)) as T
        }
    }
}
