package com.example.revroom.features.history.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.revroom.core.network.ApiClient
import com.example.revroom.data.local.LocalUserIdProvider
import com.example.revroom.features.history.model.ProjectModel
import kotlinx.coroutines.launch

class HistoryViewModel(private val context: Context) : ViewModel() {

    private val _projects = MutableLiveData<List<ProjectModel>>()
    val projects: LiveData<List<ProjectModel>> = _projects

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _isDeleting = MutableLiveData<Boolean>()
    val isDeleting: LiveData<Boolean> = _isDeleting

    private val _deleteError = MutableLiveData<String?>()
    val deleteError: LiveData<String?> = _deleteError

    private val _deleteSuccess = MutableLiveData<Boolean>()
    val deleteSuccess: LiveData<Boolean> = _deleteSuccess

    private val userIdProvider = LocalUserIdProvider(context)
    private var isLastPage = false
    var currentPage = 1

    fun fetchProjects(page: Int = 1) {
        if (_isLoading.value == true) return
        
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val userId = userIdProvider.getOrCreateUserId()
                val response = ApiClient.designApi.getUserDesigns(userId, page, 10)
                
                if (response.isSuccessful) {
                    val newProjects = response.body()?.data ?: emptyList()
                    if (page == 1) {
                        _projects.value = newProjects
                    } else {
                        val currentList = _projects.value ?: emptyList()
                        _projects.value = currentList + newProjects
                    }
                    isLastPage = newProjects.size < 10
                    currentPage = page
                    _error.value = null
                } else {
                    _error.value = "Failed to load projects: ${response.code()}"
                }
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshProjects() {
        currentPage = 1
        isLastPage = false
        fetchProjects(1)
    }

    fun deleteProject(designId: String) {
        _isDeleting.value = true
        _deleteError.value = null
        viewModelScope.launch {
            try {
                val userId = userIdProvider.getOrCreateUserId()
                val response = ApiClient.designApi.deleteDesign(userId, designId)
                if (response.isSuccessful) {
                    val currentList = _projects.value?.toMutableList() ?: mutableListOf<ProjectModel>()
                    currentList.removeAll { it.designId == designId }
                    _projects.value = currentList
                    _deleteSuccess.value = true
                } else {
                    _deleteError.value = "Delete failed: ${response.code()}"
                }
            } catch (e: Exception) {
                _deleteError.value = "Delete error: ${e.message}"
            } finally {
                _isDeleting.value = false
            }
        }
    }

    fun hasMore() = !isLastPage

    fun removeProjectFromList(id: String) {
        val currentList = _projects.value?.toMutableList() ?: return
        if (currentList.removeAll { it.designId == id }) {
            _projects.value = currentList
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(context) as T
        }
    }
}
