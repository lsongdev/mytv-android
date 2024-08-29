package me.lsong.mytv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.lsong.mytv.providers.MyTvProviderManager


// MainViewModel.kt
class MainViewModel : ViewModel() {
    private val providerManager = MyTvProviderManager()
    private val _uiState = MutableStateFlow<LeanbackMainUiState>(LeanbackMainUiState.Loading())
    val uiState: StateFlow<LeanbackMainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            loadData()
        }
    }

    private suspend fun loadData() {
        try {
            _uiState.value = LeanbackMainUiState.Loading("Initializing providers...")
            providerManager.load()
            _uiState.value = LeanbackMainUiState.Ready(providerManager)
        } catch (error: Exception) {
            _uiState.value = LeanbackMainUiState.Error(error.message)
        }
    }
}

sealed interface LeanbackMainUiState {
    data class Loading(val message: String? = null) : LeanbackMainUiState
    data class Error(val message: String? = null) : LeanbackMainUiState
    data class Ready(
        val providerManager: MyTvProviderManager,
    ) : LeanbackMainUiState
}
