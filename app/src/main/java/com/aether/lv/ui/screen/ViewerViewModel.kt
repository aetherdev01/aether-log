package com.aether.lv.ui.screen

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.lv.LogLogApplication
import com.aether.lv.data.repository.FileRepository
import com.aether.lv.util.LogLineParser
import com.aether.lv.util.ParsedLine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ViewerUiState(
    val isLoading    : Boolean       = true,
    val lines        : List<ParsedLine> = emptyList(),
    val filteredLines: List<ParsedLine> = emptyList(),
    val error        : String?       = null,
    val fileName     : String        = "",
    val totalLines   : Int           = 0,
    val searchQuery  : String        = "",
    val applyColors  : Boolean       = true,
    val wrapLines    : Boolean       = false,
    val showLineNums  : Boolean      = true,
    val jumpToEnd    : Boolean       = false,    // trigger scroll ke akhir
)

class ViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = FileRepository(
        context = application,
        dao     = (application as LogLogApplication).database.recentFileDao()
    )

    private val _state = MutableStateFlow(ViewerUiState())
    val state: StateFlow<ViewerUiState> = _state.asStateFlow()

    fun loadFile(uri: Uri?, fileName: String) {
        if (uri == null) {
            _state.update { it.copy(isLoading = false, error = "File tidak ditemukan") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, fileName = fileName) }
            repo.readLines(uri).onSuccess { rawLines ->
                val parsed = rawLines.map { line ->
                    LogLineParser.parse(line, applyColors = _state.value.applyColors)
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
                // Simpan ke riwayat
                repo.saveRecent(uri, lineCount = rawLines.size)
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message ?: "Gagal membaca file") }
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
            val reparsed = _state.value.lines.map { pl ->
                LogLineParser.parse(pl.raw, applyColors = enabled)
            }
            _state.update { s ->
                s.copy(
                    applyColors   = enabled,
                    lines         = reparsed,
                    filteredLines = applyFilter(reparsed, s.searchQuery)
                )
            }
        }
    }

    fun toggleWrap(enabled: Boolean)       { _state.update { it.copy(wrapLines    = enabled) } }
    fun toggleLineNums(enabled: Boolean)   { _state.update { it.copy(showLineNums  = enabled) } }
    fun jumpToEnd()                        { _state.update { it.copy(jumpToEnd     = true)   } }
    fun jumpToStart()                      { _state.update { it.copy(jumpToEnd     = false)  } }
    fun consumeJump()                      { _state.update { it.copy(jumpToEnd     = false)  } }

    private fun applyFilter(lines: List<ParsedLine>, query: String): List<ParsedLine> =
        if (query.isBlank()) lines
        else lines.filter { it.raw.contains(query, ignoreCase = true) }
}
