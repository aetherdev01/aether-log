package com.aether.lv.update

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.lv.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class DownloadState { IDLE, DOWNLOADING, DONE, ERROR }

data class UpdateUiState(
    val isChecking     : Boolean       = false,
    val updateInfo     : UpdateInfo?   = null,
    val showDialog     : Boolean       = false,
    val downloadState  : DownloadState = DownloadState.IDLE,
    val downloadPct    : Int           = 0,
    val downloadedFile : File?         = null,
    val errorMsg       : String?       = null
)

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private val ctx: Context get() = getApplication()

    // ── Cek update (dipanggil dari HomeViewModel saat app launch) ────────────
    fun checkForUpdate(force: Boolean = false) {
        if (_state.value.isChecking) return
        if (force) UpdateChecker.clearCache(ctx)

        viewModelScope.launch {
            _state.update { it.copy(isChecking = true) }
            val info = UpdateChecker.check(ctx, BuildConfig.VERSION_NAME)
            _state.update { s ->
                s.copy(
                    isChecking = false,
                    updateInfo = info,
                    showDialog = info?.isNewVersion == true
                )
            }
        }
    }

    fun dismissDialog() {
        _state.update { it.copy(showDialog = false) }
    }

    fun showUpdateDialog() {
        if (_state.value.updateInfo?.isNewVersion == true) {
            _state.update { it.copy(showDialog = true) }
        }
    }

    // ── Download + install ────────────────────────────────────────────────────
    fun startDownload() {
        val info = _state.value.updateInfo ?: return
        if (_state.value.downloadState == DownloadState.DOWNLOADING) return

        viewModelScope.launch {
            _state.update { it.copy(downloadState = DownloadState.DOWNLOADING, downloadPct = 0, errorMsg = null) }

            val file = ApkInstaller.download(
                context    = ctx,
                url        = info.downloadUrl,
                totalBytes = info.apkSizeBytes,
                onProgress = { pct ->
                    _state.update { it.copy(downloadPct = pct) }
                }
            )

            if (file != null) {
                _state.update { it.copy(downloadState = DownloadState.DONE, downloadedFile = file) }
            } else {
                _state.update { it.copy(downloadState = DownloadState.ERROR, errorMsg = "Download gagal. Cek koneksi internet.") }
            }
        }
    }

    fun install() {
        val file = _state.value.downloadedFile ?: return
        ApkInstaller.install(ctx, file)
    }

    fun retryDownload() {
        _state.update { it.copy(downloadState = DownloadState.IDLE, downloadPct = 0, errorMsg = null) }
        startDownload()
    }

    fun cleanUp() {
        ApkInstaller.cleanUp(ctx)
        _state.update { it.copy(downloadState = DownloadState.IDLE, downloadPct = 0, downloadedFile = null) }
    }
}
