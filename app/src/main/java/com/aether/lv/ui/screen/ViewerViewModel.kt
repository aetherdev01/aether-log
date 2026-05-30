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

/**
 * Tipe error yang diketahui — membantu UI menentukan action yang tepat.
 */
enum class FileErrorType {
    PERMISSION,   // SecurityException — butuh minta ulang izin
    NOT_FOUND,    // FileNotFoundException — file hilang/dipindah
    IO,           // IOException — error baca
    UNKNOWN
}

data class ViewerUiState(
    val isLoading    : Boolean          = true,
    val lines        : List<ParsedLine> = emptyList(),
    val filteredLines: List<ParsedLine> = emptyList(),
    val error        : String?          = null,
    val errorType    : FileErrorType?   = null,   // ← baru: tipe error eksplisit
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

    private var loadedUri: Uri? = null

    init {
        viewModelScope.launch {
            combine(
                themePrefs.isWrapLines,
                themePrefs.showLineNumbers,
                themePrefs.showLogColors
            ) { wrap, nums, colors ->
                Triple(wrap, nums, colors)
            }.collect { (wrap, nums, colors) ->
                _state.update { s ->
                    val newLines = if (s.lines.isNotEmpty() && colors != s.applyColors) {
                        s.lines.map { LogLineParser.parse(it.raw, applyColors = colors) }
                    } else {
                        s.lines
                    }
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
     * [callerContext] WAJIB diisi dengan Activity context (LocalContext.current dari Compose).
     * Ini adalah satu-satunya context yang memiliki grant URI permission dari SAF maupun ACTION_VIEW.
     * Menggunakan Application context akan menyebabkan SecurityException meski URI valid.
     */
    fun loadFile(uri: Uri?, fileName: String, callerContext: android.content.Context? = null) {
        if (uri == null) {
            _state.update { it.copy(isLoading = false, error = "File tidak ditemukan", errorType = FileErrorType.NOT_FOUND) }
            return
        }
        // Guard rekompose — tapi izinkan retry jika sebelumnya error
        if (uri == loadedUri && _state.value.error == null && !_state.value.isLoading) return
        loadedUri = uri

        viewModelScope.launch {
            val isGzipped   = fileName.endsWith(".gz", ignoreCase = true)
            val displayName = if (isGzipped) GzipUtil.stripGzSuffix(fileName) else fileName

            _state.update { it.copy(isLoading = true, error = null, errorType = null, fileName = displayName, isGzipped = isGzipped) }

            // Selalu teruskan callerContext — FileRepository akan memprioritaskannya
            repo.readLines(uri, callerContext = callerContext).onSuccess { rawLines ->
                val colors = _state.value.applyColors
                val parsed = rawLines.map { line ->
                    LogLineParser.parse(line, applyColors = colors)
                }
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
                repo.saveRecent(uri, lineCount = rawLines.size, callerContext = callerContext)
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
                    else -> Pair(
                        e.message ?: "Gagal membaca file",
                        FileErrorType.UNKNOWN
                    )
                }
                _state.update { it.copy(isLoading = false, error = message, errorType = errorType) }
                loadedUri = null  // reset agar bisa retry
            }
        }
    }

    /** Retry load dengan URI yang sama — dipanggil setelah user grant permission */
    fun retryLoad(callerContext: android.content.Context? = null) {
        val uri = loadedUri ?: return
        val name = _state.value.fileName
        loadedUri = null  // reset guard agar loadFile mau jalan
        loadFile(uri, name, callerContext)
    }

    fun onSearch(query: String) {
        _state.update { s ->
            s.copy(
                searchQuery   = query,
                filteredLines = applyFilter(s.lines, query)
            )
        }
    }

    fun toggleColors(enabled: Boolean) {
        viewModelScope.launch { themePrefs.setShowLogColors(enabled) }
    }

    fun toggleWrap(enabled: Boolean) {
        viewModelScope.launch { themePrefs.setWrapLines(enabled) }
    }

    fun toggleLineNums(enabled: Boolean) {
        viewModelScope.launch { themePrefs.setShowLineNumbers(enabled) }
    }

    fun jumpToEnd()   { _state.update { it.copy(jumpToEnd = true)  } }
    fun consumeJump() { _state.update { it.copy(jumpToEnd = false) } }

    private fun applyFilter(lines: List<ParsedLine>, query: String): List<ParsedLine> =
        if (query.isBlank()) lines
        else lines.filter { it.raw.contains(query, ignoreCase = true) }
}
