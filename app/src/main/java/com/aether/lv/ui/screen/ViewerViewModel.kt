package com.aether.lv.ui.screen

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.lv.LogLogApplication
import com.aether.lv.data.preferences.ThemePreferences
import com.aether.lv.data.repository.FileRepository
import com.aether.lv.util.GzipUtil
import com.aether.lv.util.LogLineParser
import com.aether.lv.util.ParsedLine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class FileErrorType {
    PERMISSION,
    NOT_FOUND,
    IO,
    UNKNOWN
}

data class ViewerUiState(
    val isLoading    : Boolean          = true,
    val lines        : List<ParsedLine> = emptyList(),
    val filteredLines: List<ParsedLine> = emptyList(),
    val error        : String?          = null,
    val errorType    : FileErrorType?   = null,
    val fileName     : String           = "",
    val totalLines   : Int              = 0,
    val searchQuery  : String           = "",
    val applyColors  : Boolean          = true,
    val wrapLines    : Boolean          = false,
    val showLineNums  : Boolean         = true,
    val jumpToEnd    : Boolean          = false,
    val isGzipped    : Boolean          = false,
)

class ViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = FileRepository(
        context = application,
        dao     = (application as LogLogApplication).database.recentFileDao()
    )

    private val themePrefs = ThemePreferences(application)

    private val _state = MutableStateFlow(ViewerUiState())
    val state: StateFlow<ViewerUiState> = _state.asStateFlow()

    private var loadedUri  : Uri?    = null
    // Simpan activityContext agar retryLoad bisa pakai context yang sama
    private var savedActivityCtx: android.content.Context? = null

    init {
        viewModelScope.launch {
            combine(
                themePrefs.isWrapLines,
                themePrefs.showLineNumbers,
                themePrefs.showLogColors
            ) { wrap, nums, colors -> Triple(wrap, nums, colors) }
            .collect { (wrap, nums, colors) ->
                _state.update { s ->
                    val newLines = if (s.lines.isNotEmpty() && colors != s.applyColors)
                        s.lines.map { LogLineParser.parse(it.raw, applyColors = colors) }
                    else s.lines
                    s.copy(
                        wrapLines     = wrap,
                        showLineNums  = nums,
                        applyColors   = colors,
                        lines         = newLines,
                        filteredLines = applyFilter(newLines, s.searchQuery)
                    )
                }
            }
        }
    }

    /**
     * Load file dari URI.
     *
     * [activityContext] WAJIB diisi dengan Activity context (LocalContext.current dari Compose).
     * Application context tidak memiliki grant URI permission dari SAF / ACTION_VIEW.
     */
    fun loadFile(
        uri            : Uri?,
        fileName       : String,
        activityContext: android.content.Context? = null
    ) {
        if (uri == null) {
            _state.update { it.copy(isLoading = false, error = "File tidak ditemukan", errorType = FileErrorType.NOT_FOUND) }
            return
        }
        if (uri == loadedUri && _state.value.error == null && !_state.value.isLoading) return
        loadedUri = uri
        // Simpan activity context untuk retry
        if (activityContext != null) savedActivityCtx = activityContext

        viewModelScope.launch {
            val isGzipped   = fileName.endsWith(".gz", ignoreCase = true)
            val displayName = if (isGzipped) GzipUtil.stripGzSuffix(fileName) else fileName

            _state.update { it.copy(
                isLoading = true, error = null, errorType = null,
                fileName  = displayName, isGzipped = isGzipped
            )}

            repo.readLines(uri, activityContext = activityContext).onSuccess { rawLines ->
                val colors = _state.value.applyColors
                val parsed = rawLines.map { LogLineParser.parse(it, applyColors = colors) }
                _state.update { s ->
                    s.copy(
                        isLoading     = false,
                        lines         = parsed,
                        filteredLines = applyFilter(parsed, s.searchQuery),
                        totalLines    = rawLines.size,
                        error         = null,
                        errorType     = null,
                    )
                }
                // Teruskan activityContext agar takePersistableUriPermission berhasil
                repo.saveRecent(uri, lineCount = rawLines.size, activityContext = activityContext)
            }.onFailure { e ->
                val (message, errorType) = when (e) {
                    is SecurityException -> Pair(
                        "Izin akses file ditolak.\n\nSilakan buka file kembali melalui tombol \"Buka File\" di halaman utama.",
                        FileErrorType.PERMISSION
                    )
                    is java.io.FileNotFoundException -> Pair(
                        "File tidak ditemukan. Mungkin sudah dipindah atau dihapus.",
                        FileErrorType.NOT_FOUND
                    )
                    is java.io.IOException -> Pair(
                        "Gagal membaca file: ${e.message ?: "Error I/O tidak diketahui"}",
                        FileErrorType.IO
                    )
                    else -> Pair(e.message ?: "Gagal membaca file", FileErrorType.UNKNOWN)
                }
                _state.update { it.copy(isLoading = false, error = message, errorType = errorType) }
                loadedUri = null
            }
        }
    }

    /** Retry — pakai savedActivityCtx agar permission masih valid */
    fun retryLoad(activityContext: android.content.Context? = null) {
        val uri  = loadedUri ?: return
        val name = _state.value.fileName
        loadedUri = null
        loadFile(uri, name, activityContext ?: savedActivityCtx)
    }

    fun onSearch(query: String) {
        _state.update { s ->
            s.copy(searchQuery = query, filteredLines = applyFilter(s.lines, query))
        }
    }

    fun toggleColors(enabled: Boolean)   { viewModelScope.launch { themePrefs.setShowLogColors(enabled) } }
    fun toggleWrap(enabled: Boolean)     { viewModelScope.launch { themePrefs.setWrapLines(enabled) } }
    fun toggleLineNums(enabled: Boolean) { viewModelScope.launch { themePrefs.setShowLineNumbers(enabled) } }

    fun jumpToEnd()   { _state.update { it.copy(jumpToEnd = true)  } }
    fun consumeJump() { _state.update { it.copy(jumpToEnd = false) } }

    private fun applyFilter(lines: List<ParsedLine>, query: String): List<ParsedLine> =
        if (query.isBlank()) lines
        else lines.filter { it.raw.contains(query, ignoreCase = true) }
}
