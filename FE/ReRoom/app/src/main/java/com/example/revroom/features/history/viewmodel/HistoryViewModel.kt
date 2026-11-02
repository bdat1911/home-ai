package com.example.revroom.features.history.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.revroom.core.network.ApiClient
import com.example.revroom.core.network.ServerEndpoint
import com.example.revroom.data.local.LocalUserIdProvider
import com.example.revroom.features.history.model.ProjectModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data object Empty : HistoryUiState
    data class Error(val message: String) : HistoryUiState
    data class Content(
        val projects: List<ProjectModel>,
        val isRefreshing: Boolean,
        val isLoadingMore: Boolean,
        val hasMore: Boolean,
        val error: String?
    ) : HistoryUiState
}

class HistoryViewModel(context: Context) : ViewModel() {

    private val _uiState = MutableLiveData<HistoryUiState>(HistoryUiState.Loading)
    val uiState: LiveData<HistoryUiState> = _uiState

    // Retained for existing callers, including the project detail activity.
    private val _projects = MutableLiveData<List<ProjectModel>>(emptyList())
    val projects: LiveData<List<ProjectModel>> = _projects

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _isDeleting = MutableLiveData(false)
    val isDeleting: LiveData<Boolean> = _isDeleting

    private val _deleteError = MutableLiveData<String?>(null)
    val deleteError: LiveData<String?> = _deleteError

    private val _deleteSuccess = MutableLiveData(false)
    val deleteSuccess: LiveData<Boolean> = _deleteSuccess

    private val userIdProvider = LocalUserIdProvider(context.applicationContext)
    private val deletedIds = mutableSetOf<String>()
    private var isLastPage = false
    private var hasLoaded = false
    private var loadingPage: Int? = null
    private var failedPage: Int? = null
    private var refreshAfterCurrentRequest = false
    private var paginationInvalidated = false
    private var contentBaseUrl = ApiClient.baseUrl
    var currentPage = 1
        private set

    fun loadProjectsIfNeeded() {
        if (contentBaseUrl != ApiClient.baseUrl || (!hasLoaded && _error.value == null)) fetchProjects()
    }

    fun fetchProjects(page: Int = 1) {
        if (_isLoading.value == true || page < 1) return
        val requestBaseUrl = ApiClient.baseUrl
        val endpointChanged = contentBaseUrl != requestBaseUrl
        if (endpointChanged) resetForEndpoint(requestBaseUrl)
        val requestedPage = if (endpointChanged || paginationInvalidated) 1 else page
        if (requestedPage > 1 && (!hasLoaded || isLastPage || requestedPage != currentPage + 1)) return

        loadingPage = requestedPage
        _isLoading.value = true
        _error.value = null
        failedPage = null
        publishState()
        viewModelScope.launch {
            var cancelled = false
            try {
                val userId = userIdProvider.getOrCreateUserId()
                val response = ApiClient.designApi.getUserDesigns(userId, requestedPage, PAGE_SIZE)
                if (requestBaseUrl != ApiClient.baseUrl) return@launch
                val body = response.body()

                if (response.isSuccessful && body != null) {
                    val newProjects = body.data.filterNot { it.designId in deletedIds }
                    _projects.value = mergeProjectPages(
                        current = if (requestedPage == 1) emptyList() else _projects.value.orEmpty(),
                        incoming = newProjects
                    )
                    isLastPage = body.data.isEmpty() ||
                        (if (body.totalPages > 0) requestedPage >= body.totalPages else body.data.size < PAGE_SIZE)
                    currentPage = requestedPage
                    hasLoaded = true
                    if (requestedPage == 1 && !refreshAfterCurrentRequest) paginationInvalidated = false
                } else {
                    failedPage = requestedPage
                    _error.value = if (response.isSuccessful) {
                        "The server returned an empty response. Please try again."
                    } else {
                        "Couldn't load projects (${response.code()}). Please try again."
                    }
                }
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            } catch (e: Exception) {
                if (requestBaseUrl != ApiClient.baseUrl) return@launch
                failedPage = requestedPage
                _error.value = if (e is IOException) {
                    "Couldn't connect. Check your connection and try again."
                } else {
                    "Couldn't load projects. Please try again."
                }
            } finally {
                if (!cancelled && requestBaseUrl != ApiClient.baseUrl) {
                    resetForEndpoint(ApiClient.baseUrl)
                    refreshAfterCurrentRequest = true
                }
                loadingPage = null
                _isLoading.value = false
                publishState()
                if (!cancelled && refreshAfterCurrentRequest) {
                    refreshAfterCurrentRequest = false
                    refreshProjects()
                }
            }
        }
    }

    fun refreshProjects() {
        // Reset pagination only after success so a failed refresh preserves it.
        fetchProjects(1)
    }

    fun loadMoreProjects() {
        fetchProjects(currentPage + 1)
    }

    fun retryProjects() {
        fetchProjects(failedPage ?: 1)
    }

    fun deleteProject(designId: String) {
        if (_isDeleting.value == true || designId.isBlank()) return
        val requestBaseUrl = ApiClient.baseUrl
        if (contentBaseUrl != requestBaseUrl) {
            _deleteError.value = "The server changed. Refresh projects before deleting."
            refreshProjects()
            return
        }
        _isDeleting.value = true
        _deleteError.value = null
        _deleteSuccess.value = false
        viewModelScope.launch {
            try {
                val userId = userIdProvider.getOrCreateUserId()
                val response = ServerEndpoint.withStableEndpoint {
                    if (requestBaseUrl != ApiClient.baseUrl) {
                        _deleteError.value = "The server changed. Refresh projects before deleting."
                        return@withStableEndpoint null
                    }
                    ApiClient.designApi.deleteDesign(userId, designId)
                } ?: return@launch
                if (requestBaseUrl != ApiClient.baseUrl) {
                    _deleteError.value = "The server changed. Refresh projects to check the deletion result."
                    return@launch
                }
                if (response.isSuccessful) {
                    removeProjectFromList(designId)
                    _deleteSuccess.value = true
                } else {
                    _deleteError.value = "Couldn't delete project (${response.code()}). Please try again."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _deleteError.value = "Couldn't delete project. Check your connection and try again."
            } finally {
                _isDeleting.value = false
            }
        }
    }

    fun clearDeleteError() {
        _deleteError.value = null
    }

    fun hasMore() = !isLastPage

    fun removeProjectFromList(id: String) {
        if (contentBaseUrl != ApiClient.baseUrl) {
            refreshProjects()
            return
        }
        deletedIds += id
        paginationInvalidated = true
        _projects.value = _projects.value.orEmpty().filterNot { it.designId == id }
        publishState()
        // Deletion changes server page offsets. Reload page one, and filter
        // stale in-flight responses using deletedIds.
        if (_isLoading.value == true) {
            refreshAfterCurrentRequest = true
        } else if (hasLoaded) {
            refreshProjects()
        }
    }

    private fun resetForEndpoint(baseUrl: String) {
        contentBaseUrl = baseUrl
        _projects.value = emptyList()
        _error.value = null
        deletedIds.clear()
        currentPage = 1
        isLastPage = false
        hasLoaded = false
        failedPage = null
        paginationInvalidated = false
    }

    private fun publishState() {
        val currentProjects = _projects.value.orEmpty()
        val loading = _isLoading.value == true
        val message = _error.value
        _uiState.value = when {
            currentProjects.isNotEmpty() -> HistoryUiState.Content(
                projects = currentProjects,
                isRefreshing = loading && loadingPage == 1,
                isLoadingMore = loading && loadingPage != 1,
                hasMore = !isLastPage,
                error = message
            )
            loading || (!hasLoaded && message == null) -> HistoryUiState.Loading
            message != null -> HistoryUiState.Error(message)
            else -> HistoryUiState.Empty
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HistoryViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(context) as T
        }
    }

    private companion object {
        const val PAGE_SIZE = 10
    }
}

/** Keep the API's newest-first order while updating overlapping project records. */
internal fun mergeProjectPages(
    current: List<ProjectModel>,
    incoming: List<ProjectModel>
): List<ProjectModel> {
    val merged = mutableListOf<ProjectModel>()
    val positions = mutableMapOf<String, Int>()
    for (project in current + incoming) {
        val id = project.designId?.takeIf { it.isNotBlank() }
        val existingIndex = id?.let(positions::get)
        if (existingIndex != null) {
            merged[existingIndex] = project
        } else {
            if (id != null) positions[id] = merged.size
            merged += project
        }
    }
    return merged
}
