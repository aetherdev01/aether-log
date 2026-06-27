package com.aether.lv.license

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─── UI state ─────────────────────────────────────────────────────────────────
sealed class LicenseUiState {
    object Idle    : LicenseUiState()
    object Loading : LicenseUiState()
    data class Success(val message: String) : LicenseUiState()
    data class Error(val message: String)   : LicenseUiState()
}

class LicenseViewModel(app: Application) : AndroidViewModel(app) {

    val repository = LicenseRepository(app)

    // State lisensi aktif (dari DataStore)
    val licenseState: StateFlow<LicenseState> = repository.licenseState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LicenseState())

    // UI state untuk form aktivasi
    private val _uiState = MutableStateFlow<LicenseUiState>(LicenseUiState.Idle)
    val uiState: StateFlow<LicenseUiState> = _uiState.asStateFlow()

    // Input field
    private val _inputKey = MutableStateFlow("")
    val inputKey: StateFlow<String> = _inputKey.asStateFlow()

    // Apakah input key ditampilkan / disembunyikan
    private val _keyVisible = MutableStateFlow(false)
    val keyVisible: StateFlow<Boolean> = _keyVisible.asStateFlow()

    init {
        // Background verify saat ViewModel dibuat
        viewModelScope.launch {
            repository.verifyIfNeeded()
        }
    }

    fun onKeyInput(value: String) {
        _inputKey.value = value.uppercase().take(64)
        if (_uiState.value is LicenseUiState.Error || _uiState.value is LicenseUiState.Success) {
            _uiState.value = LicenseUiState.Idle
        }
    }

    fun toggleKeyVisibility() { _keyVisible.value = !_keyVisible.value }

    fun activate() {
        val key = _inputKey.value.trim()
        if (key.isBlank()) {
            _uiState.value = LicenseUiState.Error("Masukkan kode lisensi terlebih dahulu")
            return
        }
        viewModelScope.launch {
            _uiState.value = LicenseUiState.Loading
            when (val result = repository.activate(key)) {
                is ActivateResult.Success -> {
                    _uiState.value = LicenseUiState.Success("Lisensi berhasil diaktifkan! Iklan dinonaktifkan.")
                    _inputKey.value = ""
                }
                is ActivateResult.Error -> {
                    _uiState.value = LicenseUiState.Error(result.message)
                }
            }
        }
    }

    fun revoke() {
        viewModelScope.launch {
            repository.revoke()
            _uiState.value = LicenseUiState.Idle
            _inputKey.value = ""
        }
    }

    fun resetUiState() { _uiState.value = LicenseUiState.Idle }
}
