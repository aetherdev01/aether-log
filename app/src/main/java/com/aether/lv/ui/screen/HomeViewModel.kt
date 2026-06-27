package com.aether.lv.ui.screen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.lv.LogLogApplication
import com.aether.lv.data.model.RecentFile
import com.aether.lv.data.repository.FileRepository
import com.aether.lv.license.LicenseViewModel
import com.aether.lv.update.UpdateViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = FileRepository(
        context = application,
        dao     = (application as LogLogApplication).database.recentFileDao()
    )

    val updateVm  = UpdateViewModel(application)
    val licenseVm = LicenseViewModel(application)

    val recentFiles: StateFlow<List<RecentFile>> =
        repo.recentFiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            repo.pruneInaccessibleRecents()
        }
        // Auto-check update dihapus — cek update hanya dari Settings (manual).
        // Ini mencegah dialog update muncul spam setiap buka HomeScreen.
    }

    fun removeRecent(path: String) = viewModelScope.launch { repo.removeRecent(path) }
    fun clearHistory()             = viewModelScope.launch { repo.clearHistory()     }
}
