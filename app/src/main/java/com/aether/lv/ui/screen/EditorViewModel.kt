package com.aether.lv.ui.screen

import android.app.Activity
import android.app.Application
import android.net.Uri
import android.os.FileObserver
import android.provider.OpenableColumns
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.lv.LogLogApplication
import com.aether.lv.data.repository.FileRepository
import com.aether.lv.util.SyntaxHighlighter
import com.aether.lv.util.SyntaxType
import com.aether.lv.util.syntaxTypeOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter

// ── Undo/Redo snapshot ────────────────────────────────────────────────────────
private data class TextSnapshot(val text: String, val selection: TextRange)

// ── Find & Replace state ──────────────────────────────────────────────────────
data class FindState(
    val query        : String         = "",
    val replaceWith  : String         = "",
    val matchCase    : Boolean        = false,
    val useRegex     : Boolean        = false,
    val matches      : List<IntRange> = emptyList(),
    val currentMatch : Int            = -1,
)

data class EditorUiState(
    val textField        : TextFieldValue   = TextFieldValue(),
    val fileName         : String           = "untitled.txt",
    val isLoading        : Boolean          = false,
    val isSaving         : Boolean          = false,
    val isDirty          : Boolean          = false,
    val error            : String?          = null,

    // Syntax highlighting
    // PENTING: highlightedText dipisah dari textField — tidak boleh
    // di-wrap ulang ke TextFieldValue saat IME aktif karena merusak composition.
    val highlightedText  : AnnotatedString? = null,
    val syntaxType       : SyntaxType       = SyntaxType.NONE,
    val syntaxEnabled    : Boolean          = true,

    // Undo / Redo
    val canUndo          : Boolean          = false,
    val canRedo          : Boolean          = false,

    // Cursor / statistik
    val cursorLine       : Int              = 1,
    val cursorCol        : Int              = 1,
    val totalLines       : Int              = 1,
    val totalChars       : Int              = 0,
    val selectedChars    : Int              = 0,

    // Find & Replace
    val findVisible      : Boolean          = false,
    val replaceVisible   : Boolean          = false,
    val findState        : FindState        = FindState(),

    // Tampilan
    val fontSize         : Float            = 13f,
    val showLineNumbers  : Boolean          = true,
    val wordWrap         : Boolean          = true,

    // Go To Line
    val goToLineVisible  : Boolean          = false,

    // Snackbar
    val snackMessage     : String?          = null,

    // File watcher
    val fileChangedOnDisk: Boolean          = false,
)

private const val UNDO_DEBOUNCE_MS      = 400L
private const val UNDO_HISTORY_MAX      = 200
private const val HIGHLIGHT_DEBOUNCE_MS = 350L

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = FileRepository(
        context = application,
        dao     = (application as LogLogApplication).database.recentFileDao()
    )

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val undoStack       = ArrayDeque<TextSnapshot>(UNDO_HISTORY_MAX)
    private val redoStack       = ArrayDeque<TextSnapshot>()
    private var lastPushedText  = ""
    private var undoDebounceJob : Job? = null
    private var highlightJob    : Job? = null

    // URI & context — disimpan permanent saat loadFile
    private var currentUri     : Uri? = null
    private var appContext     : android.content.Context = application  // Application context, always available

    // Jalur fisik file (untuk FileObserver)
    private var fileObserver   : FileObserver? = null
    private var observedPath   : String? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Load
    // ─────────────────────────────────────────────────────────────────────────

    fun loadFile(uri: Uri?, fileName: String, activityContext: android.content.Context? = null) {
        if (uri == null) { newFile(); return }

        currentUri = uri
        // Simpan application context — tidak leak Activity
        // activityContext hanya dipakai untuk query nama file
        val ctx = activityContext ?: appContext

        viewModelScope.launch {
            val syntax = syntaxTypeOf(fileName)
            _state.update { it.copy(isLoading = true, error = null, fileName = fileName, syntaxType = syntax) }

            repo.readLines(uri, maxLines = 100_000, activityContext = ctx)
                .onSuccess { lines ->
                    val fullText = lines.joinToString("\n")
                    undoStack.clear(); redoStack.clear()
                    lastPushedText = fullText
                    _state.update { s ->
                        s.copy(
                            isLoading  = false,
                            textField  = TextFieldValue(fullText, TextRange(0)),
                            isDirty    = false,
                            canUndo    = false,
                            canRedo    = false,
                            fileChangedOnDisk = false,
                        ).withStats()
                    }
                    scheduleHighlight(fullText, syntax)
                    startWatchingUri(uri, ctx)
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun newFile() {
        currentUri = null
        stopWatching()
        undoStack.clear(); redoStack.clear()
        lastPushedText = ""
        _state.update { EditorUiState(textField = TextFieldValue("", TextRange(0)), fileName = "untitled.txt").withStats() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // File Watcher — deteksi perubahan file di disk
    // ─────────────────────────────────────────────────────────────────────────

    private fun startWatchingUri(uri: Uri, ctx: android.content.Context) {
        stopWatching()
        // Resolve jalur fisik dari URI content://
        val path = resolveRealPath(uri, ctx) ?: return
        observedPath = path
        @Suppress("DEPRECATION")
        fileObserver = object : FileObserver(path, CLOSE_WRITE or MODIFY) {
            override fun onEvent(event: Int, path: String?) {
                // Hanya notify jika file berubah dari luar (bukan hasil save kita)
                if (_state.value.isSaving) return
                viewModelScope.launch(Dispatchers.Main) {
                    _state.update { it.copy(fileChangedOnDisk = true) }
                }
            }
        }.also { it.startWatching() }
    }

    private fun stopWatching() {
        fileObserver?.stopWatching()
        fileObserver = null
        observedPath = null
    }

    /** Reload konten dari disk (dipanggil saat user konfirmasi file berubah) */
    fun reloadFromDisk() {
        val uri = currentUri ?: return
        _state.update { it.copy(fileChangedOnDisk = false) }
        loadFile(uri, _state.value.fileName)
    }

    fun dismissFileChanged() {
        _state.update { it.copy(fileChangedOnDisk = false) }
    }

    private fun resolveRealPath(uri: Uri, ctx: android.content.Context): String? {
        // Untuk file:// langsung ambil path
        if (uri.scheme == "file") return uri.path
        // Untuk content:// coba via cursor
        return try {
            ctx.contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { c ->
                val col = c.getColumnIndex("_data")
                if (col >= 0 && c.moveToFirst()) c.getString(col) else null
            }
        } catch (_: Exception) { null }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Text change — dipanggil dari TextField
    // ─────────────────────────────────────────────────────────────────────────

    fun onTextChange(new: TextFieldValue) {
        val prev = _state.value.textField
        val textChanged = new.text != prev.text

        if (textChanged) {
            scheduleUndoPush(prev)
            redoStack.clear()
        }

        _state.update { s ->
            s.copy(
                textField = new,
                isDirty   = textChanged || s.isDirty,
                canRedo   = redoStack.isNotEmpty(),
                canUndo   = undoStack.isNotEmpty(),
                // PENTING: saat teks berubah, JANGAN langsung nullkan highlightedText.
                // Biarkan highlighted lama tetap ada (tidak terlihat karena BasicTextField
                // pakai plain mode saat highlighted.text != new.text) — ini mencegah
                // re-compose yang merusak IME composition.
            ).withStats()
        }

        if (textChanged) {
            val syntax = _state.value.syntaxType
            if (syntax != SyntaxType.NONE && _state.value.syntaxEnabled) {
                scheduleHighlight(new.text, syntax)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Undo / Redo
    // ─────────────────────────────────────────────────────────────────────────

    private fun scheduleUndoPush(snapshot: TextFieldValue) {
        undoDebounceJob?.cancel()
        undoDebounceJob = viewModelScope.launch {
            delay(UNDO_DEBOUNCE_MS)
            if (snapshot.text != lastPushedText) {
                if (undoStack.size >= UNDO_HISTORY_MAX) undoStack.removeFirst()
                undoStack.addLast(TextSnapshot(snapshot.text, snapshot.selection))
                lastPushedText = snapshot.text
                _state.update { it.copy(canUndo = true) }
            }
        }
    }

    fun undo() {
        undoDebounceJob?.cancel()
        val prev = undoStack.removeLastOrNull() ?: return
        val cur  = _state.value.textField
        redoStack.addLast(TextSnapshot(cur.text, cur.selection))
        lastPushedText = prev.text
        _state.update { s ->
            s.copy(
                textField = TextFieldValue(prev.text, prev.selection),
                canUndo   = undoStack.isNotEmpty(),
                canRedo   = true,
                isDirty   = true,
            ).withStats()
        }
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        val cur  = _state.value.textField
        undoStack.addLast(TextSnapshot(cur.text, cur.selection))
        lastPushedText = next.text
        _state.update { s ->
            s.copy(
                textField = TextFieldValue(next.text, next.selection),
                canUndo   = true,
                canRedo   = redoStack.isNotEmpty(),
                isDirty   = true,
            ).withStats()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Syntax Highlighting
    // ─────────────────────────────────────────────────────────────────────────

    private fun scheduleHighlight(text: String, type: SyntaxType) {
        if (!_state.value.syntaxEnabled || type == SyntaxType.NONE) return
        highlightJob?.cancel()
        highlightJob = viewModelScope.launch {
            delay(HIGHLIGHT_DEBOUNCE_MS)
            val annotated = withContext(Dispatchers.Default) {
                SyntaxHighlighter.highlight(text, type)
            }
            // Hanya update jika teks masih sama saat highlight selesai
            // (user mungkin sudah mengetik lagi saat delay berlangsung)
            if (_state.value.textField.text == text) {
                _state.update { it.copy(highlightedText = annotated) }
            }
        }
    }

    fun toggleSyntaxHighlight() {
        val enabled = !_state.value.syntaxEnabled
        _state.update { it.copy(syntaxEnabled = enabled, highlightedText = null) }
        if (enabled) scheduleHighlight(_state.value.textField.text, _state.value.syntaxType)
        snack(if (enabled) "Syntax highlight aktif" else "Syntax highlight nonaktif")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Edit helpers
    // ─────────────────────────────────────────────────────────────────────────

    fun selectAll() {
        val text = _state.value.textField.text
        _state.update { s -> s.copy(textField = s.textField.copy(selection = TextRange(0, text.length))).withStats() }
    }

    fun cutSelected(): String {
        val s   = _state.value.textField
        val sel = s.selection.takeIf { it.min != it.max } ?: return ""
        val cut = s.text.substring(sel.min, sel.max)
        onTextChange(TextFieldValue(s.text.removeRange(sel.min, sel.max), TextRange(sel.min)))
        return cut
    }

    fun duplicateLine() {
        val s     = _state.value.textField
        val pos   = s.selection.min
        val start = s.text.lastIndexOf('\n', pos - 1) + 1
        val end   = s.text.indexOf('\n', pos).let { if (it < 0) s.text.length else it }
        val line  = s.text.substring(start, end)
        val newText = s.text.substring(0, end) + "\n" + line + s.text.substring(end)
        onTextChange(TextFieldValue(newText, TextRange(end + 1 + (pos - start))))
    }

    fun deleteLine() {
        val s     = _state.value.textField
        val pos   = s.selection.min
        val start = s.text.lastIndexOf('\n', pos - 1) + 1
        val end   = s.text.indexOf('\n', pos).let { if (it < 0) s.text.length else it + 1 }
        val newText = s.text.removeRange(start, end)
        onTextChange(TextFieldValue(newText, TextRange(start.coerceAtMost(newText.length))))
    }

    fun indentLines()   = shiftLines(add = true)
    fun unindentLines() = shiftLines(add = false)

    private fun shiftLines(add: Boolean) {
        val s   = _state.value.textField
        val sel = s.selection
        val blockStart = s.text.lastIndexOf('\n', sel.min - 1) + 1
        val rawEnd     = s.text.indexOf('\n', if (sel.min == sel.max) sel.min else sel.max - 1)
        val blockEnd   = if (rawEnd < 0) s.text.length else rawEnd
        val block      = s.text.substring(blockStart, blockEnd)
        val shifted    = if (add) block.replace(Regex("(?m)^"), "    ")
                         else     block.replace(Regex("(?m)^    "), "")
        val newText    = s.text.substring(0, blockStart) + shifted + s.text.substring(blockEnd)
        onTextChange(TextFieldValue(newText, TextRange(blockStart, blockStart + shifted.length)))
    }

    fun toggleComment() {
        val s   = _state.value.textField
        val sel = s.selection
        val blockStart = s.text.lastIndexOf('\n', sel.min - 1) + 1
        val rawEnd     = s.text.indexOf('\n', if (sel.min == sel.max) sel.min else sel.max - 1)
        val blockEnd   = if (rawEnd < 0) s.text.length else rawEnd
        val block      = s.text.substring(blockStart, blockEnd)
        val allCommented = block.lines().all { it.trimStart().startsWith("//") }
        val toggled = if (allCommented)
            block.lines().joinToString("\n") { it.replaceFirst(Regex("^(\\s*)//\\s?"), "$1") }
        else
            block.lines().joinToString("\n") { if (it.isBlank()) it else "// $it" }
        val newText = s.text.substring(0, blockStart) + toggled + s.text.substring(blockEnd)
        onTextChange(TextFieldValue(newText, TextRange(blockStart, blockStart + toggled.length)))
    }

    fun toUpperCase()   = transformSelection { it.uppercase() }
    fun toLowerCase()   = transformSelection { it.lowercase() }

    private fun transformSelection(transform: (String) -> String) {
        val s   = _state.value.textField
        val sel = s.selection
        if (sel.min == sel.max) return
        val newText = s.text.substring(0, sel.min) + transform(s.text.substring(sel.min, sel.max)) + s.text.substring(sel.max)
        onTextChange(TextFieldValue(newText, sel))
    }

    fun trimWhitespace() {
        val trimmed = _state.value.textField.text.lines().joinToString("\n") { it.trimEnd() }.trimEnd()
        onTextChange(TextFieldValue(trimmed, TextRange(trimmed.length.coerceAtMost(_state.value.textField.selection.min))))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Go To Line
    // ─────────────────────────────────────────────────────────────────────────

    fun showGoToLine() { _state.update { it.copy(goToLineVisible = true) } }
    fun hideGoToLine() { _state.update { it.copy(goToLineVisible = false) } }

    fun goToLine(lineNumber: Int) {
        val text   = _state.value.textField.text
        val lines  = text.split('\n')
        val target = lineNumber.coerceIn(1, lines.size)
        val offset = lines.take(target - 1).sumOf { it.length + 1 }
        _state.update { s ->
            s.copy(
                textField       = s.textField.copy(selection = TextRange(offset.coerceAtMost(text.length))),
                goToLineVisible = false,
            ).withStats()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Find & Replace
    // ─────────────────────────────────────────────────────────────────────────

    fun showFind(withReplace: Boolean = false) {
        _state.update { it.copy(findVisible = true, replaceVisible = withReplace) }
    }

    fun hideFind() {
        _state.update { it.copy(findVisible = false, replaceVisible = false, findState = FindState()) }
    }

    fun toggleReplacePanel() { _state.update { it.copy(replaceVisible = !it.replaceVisible) } }

    fun onFindQueryChange(q: String) {
        _state.update { s -> s.copy(findState = rebuildMatches(s.findState.copy(query = q), s.textField.text)) }
    }

    fun onReplaceChange(r: String) {
        _state.update { s -> s.copy(findState = s.findState.copy(replaceWith = r)) }
    }

    fun toggleMatchCase() {
        _state.update { s -> s.copy(findState = rebuildMatches(s.findState.copy(matchCase = !s.findState.matchCase), s.textField.text)) }
    }

    fun toggleRegex() {
        _state.update { s -> s.copy(findState = rebuildMatches(s.findState.copy(useRegex = !s.findState.useRegex), s.textField.text)) }
    }

    fun findNext() {
        _state.update { s ->
            val fs = s.findState
            if (fs.matches.isEmpty()) return@update s
            val next  = (fs.currentMatch + 1) % fs.matches.size
            val range = fs.matches[next]
            s.copy(
                textField = s.textField.copy(selection = TextRange(range.first, range.last + 1)),
                findState = fs.copy(currentMatch = next),
            )
        }
    }

    fun findPrev() {
        _state.update { s ->
            val fs = s.findState
            if (fs.matches.isEmpty()) return@update s
            val prev  = if (fs.currentMatch <= 0) fs.matches.size - 1 else fs.currentMatch - 1
            val range = fs.matches[prev]
            s.copy(
                textField = s.textField.copy(selection = TextRange(range.first, range.last + 1)),
                findState = fs.copy(currentMatch = prev),
            )
        }
    }

    fun replaceOne() {
        val s  = _state.value
        val fs = s.findState
        if (fs.matches.isEmpty() || fs.currentMatch < 0) { findNext(); return }
        val range   = fs.matches[fs.currentMatch]
        val newText = s.textField.text.substring(0, range.first) + fs.replaceWith + s.textField.text.substring(range.last + 1)
        onTextChange(TextFieldValue(newText, TextRange(range.first + fs.replaceWith.length)))
        _state.update { st -> st.copy(findState = rebuildMatches(st.findState, newText)) }
    }

    fun replaceAll() {
        val s  = _state.value
        val fs = s.findState
        if (fs.query.isBlank()) return
        val newText = try {
            if (fs.useRegex) {
                val opts = if (fs.matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
                s.textField.text.replace(Regex(fs.query, opts), fs.replaceWith)
            } else {
                s.textField.text.replace(fs.query, fs.replaceWith, ignoreCase = !fs.matchCase)
            }
        } catch (_: Exception) { s.textField.text }
        val count = fs.matches.size
        onTextChange(TextFieldValue(newText, TextRange(newText.length)))
        snack("Diganti $count kejadian")
    }

    private fun rebuildMatches(fs: FindState, text: String): FindState {
        if (fs.query.isBlank()) return fs.copy(matches = emptyList(), currentMatch = -1)
        val ranges = try {
            if (fs.useRegex) {
                val opts = if (fs.matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
                Regex(fs.query, opts).findAll(text).map { it.range }.toList()
            } else {
                val lText  = if (fs.matchCase) text       else text.lowercase()
                val lQuery = if (fs.matchCase) fs.query   else fs.query.lowercase()
                buildList {
                    var idx = lText.indexOf(lQuery)
                    while (idx >= 0) { add(idx until idx + lQuery.length); idx = lText.indexOf(lQuery, idx + 1) }
                }
            }
        } catch (_: Exception) { emptyList() }
        return fs.copy(matches = ranges, currentMatch = if (ranges.isEmpty()) -1 else 0)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Save — menggunakan appContext (Application), tidak perlu Activity
    // ─────────────────────────────────────────────────────────────────────────

    fun saveFile(activityContext: android.content.Context? = null) {
        val uri = currentUri ?: run { snack("Tidak ada file yang dibuka"); return }
        // Application context cukup untuk contentResolver.openOutputStream
        val ctx = activityContext ?: appContext
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val text = _state.value.textField.text
            withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                        BufferedWriter(OutputStreamWriter(os, Charsets.UTF_8)).use { w -> w.write(text) }
                    } ?: throw Exception("Tidak bisa membuka output stream")
                    _state.update { it.copy(isSaving = false, isDirty = false) }
                    snack("File tersimpan")
                } catch (e: Exception) {
                    _state.update { it.copy(isSaving = false) }
                    snack("Gagal menyimpan: ${e.message}")
                }
            }
        }
    }

    fun saveAsNew(uri: Uri, activityContext: android.content.Context? = null) {
        val ctx = activityContext ?: appContext
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val text = _state.value.textField.text
            withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                        BufferedWriter(OutputStreamWriter(os, Charsets.UTF_8)).use { w -> w.write(text) }
                    } ?: throw Exception("Tidak bisa membuka output stream")
                    currentUri = uri
                    val name = queryFileName(uri, ctx)
                    _state.update { it.copy(isSaving = false, isDirty = false, fileName = name) }
                    snack("Disimpan sebagai $name")
                    startWatchingUri(uri, ctx)
                } catch (e: Exception) {
                    _state.update { it.copy(isSaving = false) }
                    snack("Gagal menyimpan: ${e.message}")
                }
            }
        }
    }

    private fun queryFileName(uri: Uri, ctx: android.content.Context): String =
        runCatching {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        }.getOrNull()
            ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file.txt"

    // ─────────────────────────────────────────────────────────────────────────
    // Display options
    // ─────────────────────────────────────────────────────────────────────────

    fun setFontSize(sz: Float)  { _state.update { it.copy(fontSize = sz.coerceIn(8f, 32f)) } }
    fun toggleWordWrap()        { _state.update { it.copy(wordWrap = !it.wordWrap) } }
    fun toggleLineNumbers()     { _state.update { it.copy(showLineNumbers = !it.showLineNumbers) } }

    // ─────────────────────────────────────────────────────────────────────────
    // Snackbar
    // ─────────────────────────────────────────────────────────────────────────

    fun snack(msg: String) { _state.update { it.copy(snackMessage = msg) } }
    fun clearSnack()       { _state.update { it.copy(snackMessage = null) } }

    // ─────────────────────────────────────────────────────────────────────────
    // Stats helper
    // ─────────────────────────────────────────────────────────────────────────

    private fun EditorUiState.withStats(): EditorUiState {
        val text  = textField.text
        val pos   = textField.selection.min
        val sel   = textField.selection
        val lines = text.split('\n')
        var charCount = 0; var lineIdx = 0; var colIdx = 1
        for ((i, line) in lines.withIndex()) {
            if (charCount + line.length >= pos) { lineIdx = i + 1; colIdx = pos - charCount + 1; break }
            charCount += line.length + 1
            if (charCount > pos) { lineIdx = i + 2; colIdx = 1; break }
        }
        if (lineIdx == 0) { lineIdx = lines.size; colIdx = (lines.lastOrNull()?.length ?: 0) + 1 }
        return copy(
            cursorLine    = lineIdx,
            cursorCol     = colIdx,
            totalLines    = lines.size,
            totalChars    = text.length,
            selectedChars = if (sel.min == sel.max) 0 else sel.max - sel.min,
        )
    }

    override fun onCleared() {
        super.onCleared()
        stopWatching()
    }
}
