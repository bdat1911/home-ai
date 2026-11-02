package com.example.revroom.features.chat.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.revroom.core.network.ApiClient
import com.example.revroom.data.remote.ChatMessageResponse
import com.example.revroom.data.remote.ConversationResponse
import com.example.revroom.data.remote.DesignBriefResponse
import com.example.revroom.data.remote.UpdateDesignBriefRequest
import com.example.revroom.data.repository.ChatGateway
import com.example.revroom.data.repository.ChatRepository
import com.example.revroom.data.repository.ChatRequestRejectedException
import com.example.revroom.features.design_studio.model.DesignJobStatus
import com.example.revroom.features.design_studio.model.DesignStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class DesignJobUi(
    val status: DesignJobStatus = DesignJobStatus.Pending,
    val originalImageUrl: String? = null,
    val designedImageUrl: String? = null,
    val isChecking: Boolean = false,
    val needsManualCheck: Boolean = false
)

data class AdviceChatUiState(
    val conversations: List<ConversationResponse> = emptyList(),
    val conversationId: String? = null,
    val messages: List<ChatMessageResponse> = emptyList(),
    val draft: String = "",
    val isBusy: Boolean = false,
    val error: String? = null,
    val designStyles: List<DesignStyle> = emptyList(),
    val brief: DesignBriefResponse? = null,
    val selectedImageUri: Uri? = null,
    val designJobs: Map<String, DesignJobUi> = emptyMap(),
    val isConfirming: Boolean = false,
    val designError: String? = null
) {
    val hasBriefContent: Boolean
        get() = brief?.roomType != null || brief?.styleId != null || selectedImageUri != null

    val canConfirm: Boolean
        get() = selectedImageUri != null && !isConfirming &&
            brief?.roomType != null && (brief.styleId != null || brief.featureId == REMOVE_FURNITURE)
}

const val REMOVE_FURNITURE = "remove_furniture"

class AdviceChatViewModel(
    private val repository: ChatGateway,
    private val serverUrl: () -> String = { ApiClient.baseUrl }
) : ViewModel() {
    private val _uiState = MutableStateFlow(AdviceChatUiState())
    val uiState: StateFlow<AdviceChatUiState> = _uiState.asStateFlow()
    private var loadedServer = serverUrl()
    private var pendingConversationId: String? = null
    private var pendingMessageId: String? = null
    private var pendingConfirmId: String? = null
    private var newChatRequested = false
    private val pollingJobs = mutableMapOf<String, Job>()

    init {
        refresh()
        loadDesignStyles()
    }

    fun onDraftChanged(text: String) {
        pendingMessageId = null
        _uiState.update { it.copy(draft = text, error = null) }
    }

    fun newChat() {
        if (_uiState.value.isBusy || _uiState.value.isConfirming) return
        stopTracking()
        pendingConversationId = null
        pendingMessageId = null
        pendingConfirmId = null
        newChatRequested = true
        _uiState.update { state ->
            state.copy(conversationId = null, messages = emptyList(), draft = "", error = null,
                brief = null, selectedImageUri = null, designJobs = emptyMap(), designError = null)
        }
    }

    fun refresh() {
        if (_uiState.value.isBusy || _uiState.value.isConfirming) return
        if (loadedServer != serverUrl()) {
            stopTracking()
            loadedServer = serverUrl()
            pendingConversationId = null
            pendingMessageId = null
            pendingConfirmId = null
            newChatRequested = false
            _uiState.update { it.copy(conversationId = null, conversations = emptyList(), messages = emptyList(),
                designStyles = emptyList(), brief = null, selectedImageUri = null,
                designJobs = emptyMap(), designError = null) }
            loadDesignStyles()
        }
        _uiState.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            try {
                repository.conversations().fold(
                    onSuccess = { conversations ->
                        val selected = _uiState.value.conversationId
                            ?.takeIf { id -> conversations.any { it.id == id } }
                            ?: conversations.firstOrNull()?.id?.takeUnless { newChatRequested }
                        _uiState.update { it.copy(conversations = conversations, conversationId = selected) }
                        if (selected != null) loadConversation(selected)
                        else _uiState.update { it.copy(messages = emptyList(), brief = null) }
                    },
                    onFailure = { error -> _uiState.update { it.copy(error = error.message) } }
                )
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun selectConversation(id: String) {
        if (_uiState.value.isBusy || _uiState.value.isConfirming ||
            id == _uiState.value.conversationId) return
        stopTracking()
        pendingConversationId = null
        pendingMessageId = null
        pendingConfirmId = null
        newChatRequested = false
        _uiState.update { it.copy(conversationId = id, messages = emptyList(), isBusy = true, error = null,
            brief = null, selectedImageUri = null, designJobs = emptyMap(), designError = null) }
        viewModelScope.launch {
            try { loadConversation(id) }
            finally { _uiState.update { it.copy(isBusy = false) } }
        }
    }

    fun send() {
        val current = _uiState.value
        val content = current.draft.trim()
        if (current.isBusy || content.isEmpty()) return
        if (loadedServer != serverUrl()) {
            _uiState.update { it.copy(error = "Máy chủ đã đổi. Hãy tải lại hội thoại trước khi gửi.") }
            return
        }
        _uiState.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            try {
                val conversationId = ensureConversation() ?: return@launch
                val messageId = pendingMessageId ?: UUID.randomUUID().toString().also {
                    pendingMessageId = it
                }
                repository.sendMessage(conversationId, messageId, content).fold(
                    onSuccess = { response ->
                        pendingMessageId = null
                        _uiState.update {
                            it.copy(
                                messages = (it.messages + response.userMessage + response.assistantMessage)
                                    .distinctBy { message -> message.id },
                                draft = "",
                                error = null,
                                brief = response.brief ?: it.brief
                            )
                        }
                        // A changed brief no longer matches an earlier confirm attempt.
                        if (response.brief?.version != current.brief?.version) pendingConfirmId = null
                        repository.conversations().onSuccess { conversations ->
                            _uiState.update { it.copy(conversations = conversations) }
                        }
                    },
                    onFailure = { error -> _uiState.update { it.copy(error = error.message) } }
                )
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    private suspend fun ensureConversation(): String? {
        _uiState.value.conversationId?.let { return it }
        val clientId = pendingConversationId ?: UUID.randomUUID().toString().also {
            pendingConversationId = it
        }
        val conversation = repository.createConversation(clientId).getOrElse { error ->
            _uiState.update { it.copy(error = error.message, designError = error.message) }
            return null
        }
        pendingConversationId = null
        newChatRequested = false
        _uiState.update { it.copy(conversationId = conversation.id) }
        return conversation.id
    }

    private suspend fun loadConversation(conversationId: String) {
        repository.messages(conversationId).fold(
            onSuccess = { messages ->
                if (_uiState.value.conversationId == conversationId) {
                    _uiState.update { it.copy(messages = messages, error = null) }
                    messages.mapNotNull { it.designId }.forEach(::trackDesign)
                }
            },
            onFailure = { error -> _uiState.update { it.copy(error = error.message) } }
        )
        repository.brief(conversationId).onSuccess { brief ->
            if (_uiState.value.conversationId == conversationId) {
                _uiState.update { it.copy(brief = brief) }
            }
        }
    }

    fun loadDesignStyles() {
        val requestedServer = serverUrl()
        viewModelScope.launch {
            repository.designStyles().fold(
                onSuccess = { styles ->
                    if (serverUrl() == requestedServer) {
                        _uiState.update { it.copy(designStyles = styles) }
                    }
                },
                onFailure = {
                    if (serverUrl() == requestedServer) {
                        _uiState.update { it.copy(designError = "Chưa tải được danh sách phong cách. Hãy thử lại.") }
                    }
                }
            )
        }
    }

    fun selectStyle(styleId: Int) = changeBrief { UpdateDesignBriefRequest(it, styleId = styleId) }

    fun selectRoom(roomType: String) = changeBrief { UpdateDesignBriefRequest(it, roomType = roomType) }

    fun selectFeature(featureId: String) = changeBrief { UpdateDesignBriefRequest(it, featureId = featureId) }

    private fun changeBrief(request: (version: Int) -> UpdateDesignBriefRequest) {
        if (_uiState.value.isConfirming) return
        viewModelScope.launch {
            val conversationId = ensureConversation() ?: return@launch
            applyBriefUpdate(conversationId, request(_uiState.value.brief?.version ?: 0))
        }
    }

    private suspend fun applyBriefUpdate(conversationId: String, request: UpdateDesignBriefRequest): Boolean =
        repository.updateBrief(conversationId, request).fold(
            onSuccess = { brief ->
                if (brief.version != _uiState.value.brief?.version) pendingConfirmId = null
                _uiState.update { it.copy(brief = brief, designError = null) }
                true
            },
            onFailure = { error ->
                _uiState.update { it.copy(designError = error.message) }
                // The usual cause is a brief changed elsewhere, so show the user its current values.
                repository.brief(conversationId).onSuccess { brief ->
                    _uiState.update { it.copy(brief = brief) }
                }
                false
            }
        )

    fun selectImage(uri: Uri?) {
        if (_uiState.value.isConfirming) return
        pendingConfirmId = null
        _uiState.update { it.copy(selectedImageUri = uri, designError = null) }
    }

    /** Saves the edited text, then starts the one action that can create an image job. */
    fun confirmDesign(colorPreference: String, mustKeep: String, mustAvoid: String) {
        val current = _uiState.value
        if (!current.canConfirm) {
            _uiState.update { it.copy(designError = "Hãy chọn ảnh phòng, loại phòng và phong cách trước khi tạo thiết kế.") }
            return
        }
        if (loadedServer != serverUrl()) {
            _uiState.update { it.copy(designError = "Máy chủ đã đổi. Hãy tải lại cuộc trò chuyện trước khi gửi ảnh.") }
            return
        }
        val image = current.selectedImageUri ?: return
        _uiState.update { it.copy(isConfirming = true, designError = null) }
        viewModelScope.launch {
            try {
                val conversationId = ensureConversation() ?: return@launch
                val brief = current.brief ?: return@launch
                if (colorPreference != brief.colorPreference.orEmpty() ||
                    mustKeep != brief.mustKeep.orEmpty() || mustAvoid != brief.mustAvoid.orEmpty()) {
                    val saved = applyBriefUpdate(conversationId, UpdateDesignBriefRequest(
                        brief.version, colorPreference = colorPreference,
                        mustKeep = mustKeep, mustAvoid = mustAvoid))
                    if (!saved) return@launch
                }

                val version = _uiState.value.brief?.version ?: return@launch
                val requestId = pendingConfirmId ?: UUID.randomUUID().toString().also { pendingConfirmId = it }
                repository.confirmBrief(conversationId, version, requestId, image).fold(
                    onSuccess = { response ->
                        pendingConfirmId = null
                        _uiState.update {
                            it.copy(messages = (it.messages + response.message).distinctBy { message -> message.id },
                                brief = response.brief, selectedImageUri = null, designError = null)
                        }
                        trackDesign(response.designId)
                    },
                    onFailure = { error ->
                        if (error is ChatRequestRejectedException) {
                            pendingConfirmId = null
                            _uiState.update { it.copy(designError = error.message) }
                        } else {
                            // Keeping the same request ID makes a retry safe if the job was in fact created.
                            _uiState.update { it.copy(designError =
                                "Chưa xác nhận được yêu cầu đã gửi. Bấm xác nhận lại sẽ không tạo trùng thiết kế.") }
                        }
                    }
                )
            } finally {
                _uiState.update { it.copy(isConfirming = false) }
            }
        }
    }

    fun checkDesignStatus(designId: String) = trackDesign(designId)

    private fun trackDesign(designId: String) {
        if (pollingJobs[designId]?.isActive == true) return
        if (_uiState.value.designJobs[designId]?.status.isFinished()) return
        val server = serverUrl()
        updateJob(designId) { it.copy(isChecking = true, needsManualCheck = false) }
        pollingJobs[designId] = viewModelScope.launch {
            try {
                repeat(MAX_STATUS_CHECKS) { attempt ->
                    if (attempt > 0) delay(STATUS_INTERVAL_MILLIS)
                    if (serverUrl() != server) return@launch
                    val status = repository.designStatus(designId).getOrNull()
                    if (status == null) {
                        updateJob(designId) { it.copy(needsManualCheck = true) }
                        return@launch
                    }
                    updateJob(designId) {
                        it.copy(status = status.status, originalImageUrl = status.originalImageUrl,
                            designedImageUrl = status.designedImageUrl)
                    }
                    if (status.status.isFinished()) return@launch
                }
                updateJob(designId) { it.copy(needsManualCheck = true) }
            } finally {
                updateJob(designId) { it.copy(isChecking = false) }
                pollingJobs.remove(designId)
            }
        }
    }

    private fun updateJob(designId: String, change: (DesignJobUi) -> DesignJobUi) {
        _uiState.update { state ->
            val job = state.designJobs[designId] ?: DesignJobUi()
            state.copy(designJobs = state.designJobs + (designId to change(job)))
        }
    }

    private fun stopTracking() {
        pollingJobs.values.toList().forEach { it.cancel() }
        pollingJobs.clear()
    }

    private fun DesignJobStatus?.isFinished() =
        this == DesignJobStatus.Completed || this == DesignJobStatus.Failed

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AdviceChatViewModel(ChatRepository(appContext)) as T
    }

    private companion object {
        const val MAX_STATUS_CHECKS = 75
        const val STATUS_INTERVAL_MILLIS = 4_000L
    }
}
