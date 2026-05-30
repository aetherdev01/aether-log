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

data class ViewerUiState(
    val isLoading    : Boolean          = true,
    val lines        : List<ParsedLine> = emptyList(),
    val filteredLines: List<ParsedLine> = emptyList(),
    val error        : String?          = null,
    val fileName     : String           = "",
    val totalLines   : Int              = 0,
    val searchQuery  : String           = "",
    val applyColors  : Boolean          = true,
    val wrapLines    : Boolean          = false,
    val showLineNums  : Boolean         = true,
    val jumpToEnd    : Boolean          = false,
    val isGzipped    : Boolean          = false,   // true jika file aslinya .gz
)

class ViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = FileRepository(
        context = application,
        dao     = (application as LogLogApplication).database.recentFileDao()
    )

    private val themePrefs = ThemePreferences(application)

    private val _state = MutableStateFlow(ViewerUiState())
    val state: StateFlow<ViewerUiState> = _state.asStateFlow()

    // URI yang sudah di-load — guard supaya tidak load ulang saat recompose
    private var loadedUri: Uri? = null

    init {
        // Sinkron settings dari DataStore ke state awal
        viewModelScope.launch {
            combine(
                themePrefs.isWrapLines,
                themePrefs.showLineNumbers,
                themePrefs.showLogColors
            ) { wrap, nums, colors ->
                Triple(wrap, nums, colors)
            }.collect { (wrap, nums, colors) ->
                _state.update { s ->
                    // Re-parse bila setting warna berubah dan ada baris yang sudah di-load
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

    fun loadFile(uri: Uri?, fileName: String) {
        if (uri == null) {
            _state.update { it.copy(isLoading = false, error = "File tidak ditemukan") }
            return
        }
        // Hindari reload saat recompose — tapi izinkan reload jika ada error sebelumnya
        if (uri == loadedUri && _state.value.error == null && !_state.value.isLoading) return
        loadedUri = uri

        viewModelScope.launch {
            // Strip .gz suffix untuk nama tampilan yang lebih bersih
            val isGzipped   = fileName.endsWith(".gz", ignoreCase = true)
            val displayName = if (isGzipped) GzipUtil.stripGzSuffix(fileName) else fileName

            _state.update { it.copy(isLoading = true, error = null, fileName = displayName, isGzipped = isGzipped) }
            repo.readLines(uri).onSuccess { rawLines ->
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
                    )
                }
                repo.saveRecent(uri, lineCount = rawLines.size)
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message ?: "Gagal membaca file") }
                // Reset loadedUri agar bisa di-retry
                loadedUri = null
            }
        }
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
        viewModelScope.launch {
            themePrefs.setShowLogColors(enabled)
            // State diupdate otomatis via collect di init
        }
    }

    fun toggleWrap(enabled: Boolean) {
        viewModelScope.launch { themePrefs.setWrapLines(enabled) }
    }

    fun toggleLineNums(enabled: Boolean) {
        viewModelScope.launch { themePrefs.setShowLineNumbers(enabled) }
    }

    fun jumpToEnd()    { _state.update { it.copy(jumpToEnd = true)  } }
    fun consumeJump()  { _state.update { it.copy(jumpToEnd = false) } }

    private fun applyFilter(lines: List<ParsedLine>, query: String): List<ParsedLine> =
        if (query.isBlank()) lines
        else lines.filter { it.raw.contains(query, ignoreCase = true) }
}
