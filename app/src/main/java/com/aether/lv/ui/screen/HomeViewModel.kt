package com.aether.lv.ui.screen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.lv.LogLogApplication
import com.aether.lv.data.model.RecentFile
import com.aether.lv.data.repository.FileRepository
import com.aether.lv.update.UpdateViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = FileRepository(
        context = application,
        dao     = (application as LogLogApplication).database.recentFileDao()
    )

    // Update checker — pakai shared instance via ViewModelProvider di HomeScreen
    val updateVm = UpdateViewModel(application)

    val recentFiles: StateFlow<List<RecentFile>> =
        repo.recentFiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Cek update dengan sedikit delay agar UI tidak blocked saat launch
        viewModelScope.launch {
            delay(1_500)
            updateVm.checkForUpdate()
        }
    }

    fun removeRecent(path: String) = viewModelScope.launch { repo.removeRecent(path) }
    fun clearHistory()             = viewModelScope.launch { repo.clearHistory()     }
}
