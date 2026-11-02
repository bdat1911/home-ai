package com.example.revroom.features.design_studio.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.revroom.R
import com.example.revroom.core.network.ApiClient
import com.example.revroom.core.network.ServerEndpoint
import com.example.revroom.data.repository.DesignRepository
import com.example.revroom.data.repository.DesignRepositoryException
import com.example.revroom.features.design_studio.model.DesignJobStatus
import com.example.revroom.features.design_studio.model.DesignMode
import com.example.revroom.features.design_studio.model.DesignPhase
import com.example.revroom.features.design_studio.model.DesignRequest
import com.example.revroom.features.design_studio.model.DesignResult
import com.example.revroom.features.design_studio.model.DesignStyle
import com.example.revroom.features.design_studio.model.DesignUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class DesignViewModel internal constructor(
    private val uploadDesign: suspend (DesignRequest) -> Result<DesignResult>,
    private val getDesignStatus: suspend (String) -> Result<DesignResult>,
    private val getDesignStyles: suspend () -> Result<List<DesignStyle>>,
    private val apiBaseUrl: () -> String,
    endpointChanges: Flow<String>,
    initialState: DesignUiState = DesignUiState(),
) : ViewModel() {
    constructor(repository: DesignRepository) : this(
        uploadDesign = repository::uploadDesign,
        getDesignStatus = repository::getDesignStatus,
        getDesignStyles = repository::getDesignStyles,
        apiBaseUrl = { ApiClient.baseUrl },
        endpointChanges = ServerEndpoint.baseUrl
    )

    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<DesignUiState> = _uiState.asStateFlow()

    private val _designStyles = MutableStateFlow<List<SelectionItem>>(emptyList())
    val designStyles: StateFlow<List<SelectionItem>> = _designStyles.asStateFlow()

    private val _isStylesLoading = MutableStateFlow(false)
    val isStylesLoading: StateFlow<Boolean> = _isStylesLoading.asStateFlow()

    private val _stylesLoadError = MutableStateFlow<String?>(null)
    val stylesLoadError: StateFlow<String?> = _stylesLoadError.asStateFlow()

    private var pollingJob: Job? = null
    private var submissionJob: Job? = null
    private var stylesJob: Job? = null
    private var stylesRequestVersion = 0

    data class DesignFeatureItem(
        val id: String,
        val title: String,
        val subtitle: String,
        val colors: List<Color>,
        val beforeImageRes: Int? = null,
        val afterImageRes: Int? = null
    )

    data class SelectionItem(
        val id: String,
        val label: String,
        val colors: List<Color>,
        val description: String = "",
        val lighting: String = "",
        val material: String = "",
        val color: String = "",
        val atmosphere: String = "",
        val imageRes: Int? = null
    )

    val interiorFeatures = listOf(
        DesignFeatureItem(
            "interior_design",
            "Interior Design",
            "Redesign your interior space",
            listOf(Color(0xFFE8E2D8), Color(0xFF75675B), Color(0xFF181818)),
            beforeImageRes = R.drawable.feature_interior_before,
            afterImageRes = R.drawable.feature_interior_after
        ),
        DesignFeatureItem(
            "furnish_empty_room",
            "Furnish Empty Room",
            "Transform empty space into furnished room",
            listOf(Color(0xFFEDE8DE), Color(0xFFB7A58C), Color(0xFF1F1A15)),
            beforeImageRes = R.drawable.feature_furnish_before,
            afterImageRes = R.drawable.feature_furnish_after
        ),
        DesignFeatureItem(
            "remove_furniture",
            "Remove Furniture",
            "Clear and empty your room",
            listOf(Color(0xFFECE7DD), Color(0xFF9F8F7C), Color(0xFF171717)),
            beforeImageRes = R.drawable.feature_remove_before,
            afterImageRes = R.drawable.feature_remove_after
        )
    )

    val exteriorFeatures = listOf(
        DesignFeatureItem(
            "exterior_design",
            "Exterior Design",
            "Redesign your exterior space",
            listOf(Color(0xFFC6D8A8), Color(0xFF4D744B), Color(0xFF182512))
        ),
        DesignFeatureItem(
            "facade",
            "Facade",
            "Building exterior & materials",
            listOf(Color(0xFFD8D2C8), Color(0xFF56626B), Color(0xFF15191D))
        ),
        DesignFeatureItem(
            "landscaping",
            "Landscaping",
            "Beautiful outdoor spaces",
            listOf(Color(0xFFD5E3C4), Color(0xFF50705A), Color(0xFF132016))
        )
    )

    val roomTypes = listOf(
        SelectionItem("living_room", "Living Room", listOf(Color(0xFFD8C3A5), Color(0xFF735F4D)), imageRes = R.drawable.room_living_room),
        SelectionItem("master_bedroom", "Master Bedroom", listOf(Color(0xFFE6D8CC), Color(0xFF78909C)), imageRes = R.drawable.room_master_bedroom),
        SelectionItem("kitchen", "Kitchen", listOf(Color(0xFFDCE8E4), Color(0xFF7E8D85)), imageRes = R.drawable.room_kitchen),
        SelectionItem("dining_room", "Dining Room", listOf(Color(0xFFECE4D7), Color(0xFFB48B58)), imageRes = R.drawable.room_dining_room),
        SelectionItem("bathroom", "Bathroom", listOf(Color(0xFFE7E1D4), Color(0xFFA79F93)), imageRes = R.drawable.room_bathroom),
        SelectionItem("study_room", "Study Room", listOf(Color(0xFFE8E2DB), Color(0xFFB9A18D)), imageRes = R.drawable.room_study_room),
        SelectionItem("kids_room", "Kids Room", listOf(Color(0xFFE9F0EF), Color(0xFFA7C7C5)), imageRes = R.drawable.room_kids_room),
        SelectionItem("walk_in_closet", "Walk-in Closet", listOf(Color(0xFFE5D7C6), Color(0xFF9E8065)), imageRes = R.drawable.room_walk_in_closet),
    )

    private val stylePalettes = listOf(
        listOf(Color(0xFFE4E0D9), Color(0xFF7D7269)),
        listOf(Color(0xFFF4F2EE), Color(0xFFC9C4B9)),
        listOf(Color(0xFFF0D7BE), Color(0xFFB87555)),
        listOf(Color(0xFFD8EBF2), Color(0xFF7DA5B2)),
        listOf(Color(0xFFE1B686), Color(0xFF7A5135)),
    )

    init {
        viewModelScope.launch {
            endpointChanges.collect {
                _designStyles.value = emptyList()
                if (!_uiState.value.submissionAttempted) {
                    _uiState.value = _uiState.value.copy(selectedStyle = null)
                }
                loadDesignStyles(force = true)
            }
        }
    }

    fun startDesign(mode: DesignMode, featureId: String): Boolean {
        if (submissionJob?.isActive == true) return false
        pollingJob?.cancel()
        _uiState.value = DesignUiState(
            designMode = mode,
            selectedFeature = featureId,
            selectedRoomType = if (mode == DesignMode.Exterior) "exterior" else null
        )
        return true
    }

    fun selectImage(uri: Uri?) {
        _uiState.value = _uiState.value.copy(selectedImageUri = uri, errorMessage = null)
    }

    fun selectRoomType(roomType: String) {
        _uiState.value = _uiState.value.copy(selectedRoomType = roomType, errorMessage = null)
    }

    fun selectStyle(style: String) {
        _uiState.value = _uiState.value.copy(selectedStyle = style, errorMessage = null)
    }

    fun selectModel(model: String) {
        _uiState.value = _uiState.value.copy(selectedModel = model)
    }

    fun selectResolution(resolution: String) {
        _uiState.value = _uiState.value.copy(selectedResolution = resolution)
    }

    fun createDesign() {
        val currentState = _uiState.value
        if (submissionJob?.isActive == true || pollingJob?.isActive == true) return
        if (currentState.designId != null) {
            retry()
            return
        }
        // A lost POST response does not tell us whether the server created a job.
        // Only an explicit new design flow may submit another generation.
        if (currentState.submissionAttempted) return
        val imageUri = currentState.selectedImageUri
        val styleId = currentState.selectedStyle?.toIntOrNull()
        val isRemoveFurniture = currentState.selectedFeature == "remove_furniture"

        if (imageUri == null) {
            _uiState.value = currentState.copy(
                phase = DesignPhase.Failed,
                errorMessage = "Please choose a photo."
            )
            return
        }

        if (!isRemoveFurniture && (styleId == null || (currentState.designMode == DesignMode.Interior && currentState.selectedRoomType == null))) {
            _uiState.value = currentState.copy(
                phase = DesignPhase.Failed,
                errorMessage = "Please choose a photo, room type, and style."
            )
            return
        }

        pollingJob?.cancel()
        val submittedApiBaseUrl = apiBaseUrl()
        _uiState.value = currentState.copy(
            phase = DesignPhase.Uploading,
            errorMessage = null,
            submissionAttempted = true,
            submittedApiBaseUrl = submittedApiBaseUrl
        )

        submissionJob = viewModelScope.launch {
            uploadDesign(
                DesignRequest(
                    imageUri = imageUri,
                    styleId = if (isRemoveFurniture) null else styleId,
                    roomType = if (isRemoveFurniture) null else currentState.selectedRoomType,
                    featureId = if (isRemoveFurniture) "remove_furniture" else currentState.selectedFeature ?: "interior_design",
                    model = currentState.selectedModel,
                    resolution = currentState.selectedResolution
                )
            )
                .onSuccess { response ->
                    currentCoroutineContext().ensureActive()
                    _uiState.value = _uiState.value.copy(
                        phase = DesignPhase.Processing,
                        designId = response.designId,
                        originalImageUrl = response.originalImageUrl,
                        errorMessage = null
                    )
                    startPolling(response.designId)
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    currentCoroutineContext().ensureActive()
                    if (error is DesignRepositoryException && error.isInputRejection) {
                        _uiState.value = _uiState.value.copy(
                            phase = DesignPhase.Failed,
                            submissionAttempted = false,
                            errorMessage = error.message ?: "Please check the selected image and options."
                        )
                        return@onFailure
                    }
                    _uiState.value = _uiState.value.copy(
                        phase = DesignPhase.SubmissionUncertain,
                        errorMessage = "We could not confirm the submission. It may already be processing. " +
                            "Check Gallery on the original server before starting a new design."
                    )
                }
        }
    }

    fun retry() {
        val designId = _uiState.value.designId ?: return
        if (submissionJob?.isActive == true || pollingJob?.isActive == true) return
        startPolling(designId)
    }

    fun leaveDesignScreen() {
        pollingJob?.cancel()
        if (_uiState.value.phase == DesignPhase.Processing) {
            pauseStatusChecks("Status checks were paused when you left this screen. Continue checking or view the design in Gallery.")
        }
    }

    fun retryLoadDesignStyles() {
        loadDesignStyles()
    }

    private fun loadDesignStyles(force: Boolean = false) {
        if (_isStylesLoading.value && !force) return
        val requestVersion = ++stylesRequestVersion
        val requestedApiBaseUrl = apiBaseUrl()
        stylesJob?.cancel()
        _isStylesLoading.value = true
        _stylesLoadError.value = null
        stylesJob = viewModelScope.launch {
            try {
                val result = getDesignStyles()
                currentCoroutineContext().ensureActive()
                if (requestVersion != stylesRequestVersion || requestedApiBaseUrl != apiBaseUrl()) return@launch
                result.onSuccess { styles ->
                        _designStyles.value = styles.mapIndexed { index, style ->
                            SelectionItem(
                                id = style.styleId.toString(),
                                label = style.styleName,
                                colors = stylePalettes[index % stylePalettes.size],
                                description = style.coreAesthetic,
                                lighting = style.lighting,
                                material = style.material,
                                color = style.color,
                                atmosphere = style.atmosphere
                            )
                        }
                    }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        _stylesLoadError.value = error.message ?: "Unable to load design styles."
                    }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (requestVersion == stylesRequestVersion && requestedApiBaseUrl == apiBaseUrl()) {
                    _stylesLoadError.value = error.message ?: "Unable to load design styles."
                }
            } finally {
                if (requestVersion == stylesRequestVersion) _isStylesLoading.value = false
            }
        }
    }

    private fun startPolling(designId: String) {
        if (pollingJob?.isActive == true) return
        if (apiBaseUrl() != _uiState.value.submittedApiBaseUrl) {
            pauseStatusChecks("The API address changed. Restore the original address to check this design, or open Gallery on that server.")
            return
        }
        _uiState.value = _uiState.value.copy(phase = DesignPhase.Processing, errorMessage = null)
        pollingJob = viewModelScope.launch {
            val finished = withTimeoutOrNull(POLL_WINDOW_MS) {
                repeat(MAX_POLL_ATTEMPTS) { attempt ->
                    if (attempt > 0) delay(POLL_INTERVAL_MS)
                    if (apiBaseUrl() != _uiState.value.submittedApiBaseUrl) {
                        pauseStatusChecks("The API address changed. Restore the original address to check this design.")
                        return@withTimeoutOrNull true
                    }
                    val result = getDesignStatus(designId)
                    currentCoroutineContext().ensureActive()
                    if (apiBaseUrl() != _uiState.value.submittedApiBaseUrl) {
                        pauseStatusChecks("The API address changed. Restore the original address to check this design.")
                        return@withTimeoutOrNull true
                    }
                    val error = result.exceptionOrNull()
                    if (error != null) {
                        if (error is CancellationException) throw error
                        pauseStatusChecks("Unable to check the design status. The server may still be processing it. Continue checking or open Gallery.")
                        return@withTimeoutOrNull true
                    }
                    val status = result.getOrThrow()
                        when (status.status) {
                            DesignJobStatus.Completed -> {
                                _uiState.value = _uiState.value.copy(
                                    phase = DesignPhase.Completed,
                                    originalImageUrl = status.originalImageUrl,
                                    designedImageUrl = status.designedImageUrl,
                                    errorMessage = null
                                )
                                return@withTimeoutOrNull true
                            }

                            DesignJobStatus.Failed -> {
                                _uiState.value = _uiState.value.copy(
                                    phase = DesignPhase.Failed,
                                    errorMessage = status.errorMessage ?: "Design generation failed."
                                )
                                return@withTimeoutOrNull true
                            }

                            else -> {
                                _uiState.value = _uiState.value.copy(
                                    phase = DesignPhase.Processing,
                                    originalImageUrl = status.originalImageUrl
                                )
                            }
                        }
                }
                false
            }
            if (finished != true) {
                pauseStatusChecks("This design is taking longer than expected. Continue checking the same design or return later through Gallery.")
            }
        }
    }

    private fun pauseStatusChecks(message: String) {
        _uiState.value = _uiState.value.copy(phase = DesignPhase.StatusUnavailable, errorMessage = message)
    }

    override fun onCleared() {
        pollingJob?.cancel()
        submissionJob?.cancel()
        super.onCleared()
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DesignViewModel(DesignRepository(appContext)) as T
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 2_000L
        const val MAX_POLL_ATTEMPTS = 150
        const val POLL_WINDOW_MS = 300_000L
    }
}
