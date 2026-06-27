package com.aether.lv.ui.screen

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.lv.LogLogApplication
import com.aether.lv.data.repository.FileRepository
import com.aether.lv.util.GzipUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter

// ── Undo/Redo snapshot ────────────────────────────────────────────────────────
private data class TextSnapshot(
    val text      : String,
    val selection : TextRange
)

// ── Find & Replace state ──────────────────────────────────────────────────────
data class FindState(
    val query          : String    = "",
    val replaceWith    : String    = "",
    val matchCase      : Boolean   = false,
    val useRegex       : Boolean   = false,
    val matches        : List<IntRange> = emptyList(),
    val currentMatch   : Int       = -1,
)

data class EditorUiState(
    // Konten editor
    val textField        : TextFieldValue = TextFieldValue(),
    val fileName         : String         = "untitled.txt",
    val isLoading        : Boolean        = false,
    val isSaving         : Boolean        = false,
    val isDirty          : Boolean        = false,   // ada perubahan belum disimpan
    val error            : String?        = null,

    // Undo / Redo
    val canUndo          : Boolean        = false,
    val canRedo          : Boolean        = false,

    // Cursor / statistik
    val cursorLine       : Int            = 1,
    val cursorCol        : Int            = 1,
    val totalLines       : Int            = 1,
    val totalChars       : Int            = 0,
    val selectedChars    : Int            = 0,

    // Find & Replace
    val findVisible      : Boolean        = false,
    val replaceVisible   : Boolean        = false,
    val findState        : FindState      = FindState(),

    // Opsi tampilan
    val fontSize         : Float          = 13f,
    val showLineNumbers  : Boolean        = true,
    val wordWrap         : Boolean        = true,

    // Go to line dialog
    val goToLineVisible  : Boolean        = false,

    // Snackbar
    val snackMessage     : String?        = null,
)

private const val UNDO_DEBOUNCE_MS  = 400L
private const val UNDO_HISTORY_MAX  = 200

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = FileRepository(
        context = application,
        dao     = (application as LogLogApplication).database.recentFileDao()
    )

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    // ── Undo / Redo stacks ────────────────────────────────────────────────────
    private val undoStack = ArrayDeque<TextSnapshot>()
    private val redoStack = ArrayDeque<TextSnapshot>()
    private var lastPushedText = ""
    private var undoDebounceJob: Job? = null

    // ── URI yang dibuka ───────────────────────────────────────────────────────
    private var currentUri: Uri? = null
    private var savedActivityCtx: android.content.Context? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Load
    // ─────────────────────────────────────────────────────────────────────────

    fun loadFile(uri: Uri?, fileName: String, activityContext: android.content.Context? = null) {
        if (uri == null) {
            newFile()
            return
        }
        currentUri = uri
        if (activityContext != null) savedActivityCtx = activityContext

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, fileName = fileName) }

            repo.readLines(uri, maxLines = 100_000, activityContext = activityContext)
                .onSuccess { lines ->
                    val fullText = lines.joinToString("\n")
                    val tfv = TextFieldValue(fullText, TextRange(0))
                    undoStack.clear(); redoStack.clear()
                    lastPushedText = fullText
                    _state.update { s ->
                        s.copy(
                            isLoading   = false,
                            textField   = tfv,
                            isDirty     = false,
                            canUndo     = false,
                            canRedo     = false,
                        ).withStats()
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun newFile() {
        currentUri = null
        val tfv = TextFieldValue("", TextRange(0))
        undoStack.clear(); redoStack.clear()
        lastPushedText = ""
        _state.update {
            EditorUiState(textField = tfv, fileName = "untitled.txt").withStats()
        }
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
            ).withStats()
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
                pushUndo(TextSnapshot(snapshot.text, snapshot.selection))
                lastPushedText = snapshot.text
            }
        }
    }

    private fun pushUndo(snap: TextSnapshot) {
        if (undoStack.size >= UNDO_HISTORY_MAX) undoStack.removeFirst()
        undoStack.addLast(snap)
        _state.update { it.copy(canUndo = true) }
    }

    fun undo() {
        undoDebounceJob?.cancel()
        val prev = undoStack.removeLastOrNull() ?: return
        val current = _state.value.textField
        redoStack.addLast(TextSnapshot(current.text, current.selection))
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
        val current = _state.value.textField
        undoStack.addLast(TextSnapshot(current.text, current.selection))
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
    // Edit helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Pilih semua teks */
    fun selectAll() {
        val text = _state.value.textField.text
        _state.update { s ->
            s.copy(textField = s.textField.copy(selection = TextRange(0, text.length))).withStats()
        }
    }

    /** Cut — hapus teks terpilih, kembalikan string yang di-cut */
    fun cutSelected(): String {
        val s     = _state.value.textField
        val sel   = s.selection.let { if (it.min == it.max) null else it } ?: return ""
        val cut   = s.text.substring(sel.min, sel.max)
        val newText = s.text.removeRange(sel.min, sel.max)
        onTextChange(TextFieldValue(newText, TextRange(sel.min)))
        return cut
    }

    /** Duplicate baris yang sedang di-cursor */
    fun duplicateLine() {
        val s     = _state.value.textField
        val text  = s.text
        val pos   = s.selection.min
        val start = text.lastIndexOf('\n', pos - 1) + 1
        val end   = text.indexOf('\n', pos).let { if (it < 0) text.length else it }
        val line  = text.substring(start, end)
        val newText = text.substring(0, end) + "\n" + line + text.substring(end)
        onTextChange(TextFieldValue(newText, TextRange(end + 1 + (pos - start))))
    }

    /** Hapus baris saat ini */
    fun deleteLine() {
        val s     = _state.value.textField
        val text  = s.text
        val pos   = s.selection.min
        val start = text.lastIndexOf('\n', pos - 1) + 1
        val rawEnd = text.indexOf('\n', pos)
        val end   = if (rawEnd < 0) text.length else rawEnd + 1
        val newText = text.removeRange(start, end)
        onTextChange(TextFieldValue(newText, TextRange(start.coerceAtMost(newText.length))))
    }

    /** Indentasi baris terpilih (Tab) */
    fun indentLines() = shiftLines(add = true)

    /** Un-indent baris terpilih (Shift+Tab) */
    fun unindentLines() = shiftLines(add = false)

    private fun shiftLines(add: Boolean) {
        val s = _state.value.textField
        val text = s.text
        val sel  = s.selection
        val blockStart = text.lastIndexOf('\n', sel.min - 1) + 1
        val rawEnd = text.indexOf('\n', if (sel.min == sel.max) sel.min else sel.max - 1)
        val blockEnd = if (rawEnd < 0) text.length else rawEnd

        val block = text.substring(blockStart, blockEnd)
        val shifted = if (add) {
            block.replace(Regex("(?m)^"), "    ")
        } else {
            block.replace(Regex("(?m)^    "), "")
        }
        val newText = text.substring(0, blockStart) + shifted + text.substring(blockEnd)
        onTextChange(TextFieldValue(newText, TextRange(blockStart, blockStart + shifted.length)))
    }

    /** Toggle komentar // pada baris terpilih */
    fun toggleComment() {
        val s = _state.value.textField
        val text = s.text
        val sel  = s.selection
        val blockStart = text.lastIndexOf('\n', sel.min - 1) + 1
        val rawEnd = text.indexOf('\n', if (sel.min == sel.max) sel.min else sel.max - 1)
        val blockEnd = if (rawEnd < 0) text.length else rawEnd

        val block = text.substring(blockStart, blockEnd)
        val allCommented = block.lines().all { it.trimStart().startsWith("//") }
        val toggled = if (allCommented) {
            block.lines().joinToString("\n") { it.replaceFirst(Regex("^(\\s*)//\\s?"), "$1") }
        } else {
            block.lines().joinToString("\n") { if (it.isBlank()) it else "// $it" }
        }
        val newText = text.substring(0, blockStart) + toggled + text.substring(blockEnd)
        onTextChange(TextFieldValue(newText, TextRange(blockStart, blockStart + toggled.length)))
    }

    /** Ubah baris terpilih ke UPPER CASE */
    fun toUpperCase() = transformSelection { it.uppercase() }

    /** Ubah baris terpilih ke lower case */
    fun toLowerCase() = transformSelection { it.lowercase() }

    private fun transformSelection(transform: (String) -> String) {
        val s   = _state.value.textField
        val sel = s.selection
        if (sel.min == sel.max) return
        val selected    = s.text.substring(sel.min, sel.max)
        val transformed = transform(selected)
        val newText = s.text.substring(0, sel.min) + transformed + s.text.substring(sel.max)
        onTextChange(TextFieldValue(newText, sel))
    }

    /** Trim whitespace tiap baris */
    fun trimWhitespace() {
        val text = _state.value.textField.text
        val trimmed = text.lines().joinToString("\n") { it.trimEnd() }.trimEnd()
        onTextChange(TextFieldValue(trimmed, TextRange(trimmed.length.coerceAtMost(_state.value.textField.selection.min))))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Go to Line
    // ─────────────────────────────────────────────────────────────────────────

    fun showGoToLine()  { _state.update { it.copy(goToLineVisible = true) } }
    fun hideGoToLine()  { _state.update { it.copy(goToLineVisible = false) } }

    fun goToLine(lineNumber: Int) {
        val text  = _state.value.textField.text
        val lines = text.split('\n')
        val target = lineNumber.coerceIn(1, lines.size)
        val offset = lines.take(target - 1).sumOf { it.length + 1 }
        _state.update { s ->
            s.copy(
                textField      = s.textField.copy(selection = TextRange(offset.coerceAtMost(text.length))),
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

    fun toggleReplacePanel() {
        _state.update { it.copy(replaceVisible = !it.replaceVisible) }
    }

    fun onFindQueryChange(q: String) {
        _state.update { s ->
            val fs = s.findState.copy(query = q)
            s.copy(findState = rebuildMatches(fs, s.textField.text))
        }
    }

    fun onReplaceChange(r: String) {
        _state.update { s -> s.copy(findState = s.findState.copy(replaceWith = r)) }
    }

    fun toggleMatchCase() {
        _state.update { s ->
            val fs = s.findState.copy(matchCase = !s.findState.matchCase)
            s.copy(findState = rebuildMatches(fs, s.textField.text))
        }
    }

    fun toggleRegex() {
        _state.update { s ->
            val fs = s.findState.copy(useRegex = !s.findState.useRegex)
            s.copy(findState = rebuildMatches(fs, s.textField.text))
        }
    }

    /** Loncat ke hasil berikutnya */
    fun findNext() {
        _state.update { s ->
            val fs = s.findState
            if (fs.matches.isEmpty()) return@update s
            val next = (fs.currentMatch + 1) % fs.matches.size
            val range = fs.matches[next]
            s.copy(
                textField = s.textField.copy(selection = TextRange(range.first, range.last + 1)),
                findState = fs.copy(currentMatch = next),
            )
        }
    }

    /** Loncat ke hasil sebelumnya */
    fun findPrev() {
        _state.update { s ->
            val fs = s.findState
            if (fs.matches.isEmpty()) return@update s
            val prev = if (fs.currentMatch <= 0) fs.matches.size - 1 else fs.currentMatch - 1
            val range = fs.matches[prev]
            s.copy(
                textField = s.textField.copy(selection = TextRange(range.first, range.last + 1)),
                findState = fs.copy(currentMatch = prev),
            )
        }
    }

    /** Ganti satu kejadian (di currentMatch) */
    fun replaceOne() {
        val s  = _state.value
        val fs = s.findState
        if (fs.matches.isEmpty() || fs.currentMatch < 0) { findNext(); return }
        val range   = fs.matches[fs.currentMatch]
        val text    = s.textField.text
        val newText = text.substring(0, range.first) + fs.replaceWith + text.substring(range.last + 1)
        val tfv     = TextFieldValue(newText, TextRange(range.first + fs.replaceWith.length))
        onTextChange(tfv)
        // rebuild matches pada teks baru
        _state.update { st ->
            st.copy(findState = rebuildMatches(st.findState, newText))
        }
    }

    /** Ganti semua kejadian */
    fun replaceAll() {
        val s  = _state.value
        val fs = s.findState
        if (fs.query.isBlank()) return
        val text = s.textField.text
        val newText = try {
            if (fs.useRegex) {
                val opts = if (fs.matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
                text.replace(Regex(fs.query, opts), fs.replaceWith)
            } else {
                if (fs.matchCase) text.replace(fs.query, fs.replaceWith)
                else text.replace(fs.query, fs.replaceWith, ignoreCase = true)
            }
        } catch (_: Exception) { text }
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
                val lText = if (fs.matchCase) text else text.lowercase()
                val lQuery = if (fs.matchCase) fs.query else fs.query.lowercase()
                buildList {
                    var idx = lText.indexOf(lQuery)
                    while (idx >= 0) {
                        add(idx until idx + lQuery.length)
                        idx = lText.indexOf(lQuery, idx + 1)
                    }
                }
            }
        } catch (_: Exception) { emptyList() }
        val cur = if (ranges.isEmpty()) -1 else 0
        return fs.copy(matches = ranges, currentMatch = cur)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Save
    // ─────────────────────────────────────────────────────────────────────────

    fun saveFile(activityContext: android.content.Context? = null) {
        val uri = currentUri ?: return
        val ctx = activityContext ?: savedActivityCtx ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val text = _state.value.textField.text
            withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                        BufferedWriter(OutputStreamWriter(os, Charsets.UTF_8)).use { it.write(text) }
                    }
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
        val ctx = activityContext ?: savedActivityCtx ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val text = _state.value.textField.text
            withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                        BufferedWriter(OutputStreamWriter(os, Charsets.UTF_8)).use { it.write(text) }
                    }
                    currentUri = uri
                    val name = queryFileName(uri, ctx)
                    _state.update { it.copy(isSaving = false, isDirty = false, fileName = name) }
                    snack("Disimpan sebagai $name")
                } catch (e: Exception) {
                    _state.update { it.copy(isSaving = false) }
                    snack("Gagal menyimpan: ${e.message}")
                }
            }
        }
    }

    private fun queryFileName(uri: Uri, ctx: android.content.Context): String {
        return try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        } catch (_: Exception) { null }
            ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file.txt"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Display options
    // ─────────────────────────────────────────────────────────────────────────

    fun setFontSize(sz: Float)        { _state.update { it.copy(fontSize = sz.coerceIn(8f, 32f)) } }
    fun toggleWordWrap()              { _state.update { it.copy(wordWrap = !it.wordWrap) } }
    fun toggleLineNumbers()           { _state.update { it.copy(showLineNumbers = !it.showLineNumbers) } }

    // ─────────────────────────────────────────────────────────────────────────
    // Snackbar
    // ─────────────────────────────────────────────────────────────────────────

    fun snack(msg: String) { _state.update { it.copy(snackMessage = msg) } }
    fun clearSnack()       { _state.update { it.copy(snackMessage = null) } }

    // ─────────────────────────────────────────────────────────────────────────
    // Stats helper (extension)
    // ─────────────────────────────────────────────────────────────────────────

    private fun EditorUiState.withStats(): EditorUiState {
        val text  = textField.text
        val pos   = textField.selection.min
        val sel   = textField.selection
        val lines = text.split('\n')
        // hitung baris & kolom kursor
        var charCount = 0
        var lineIdx   = 0
        var colIdx    = 1
        for ((i, line) in lines.withIndex()) {
            if (charCount + line.length >= pos) {
                lineIdx = i + 1
                colIdx  = pos - charCount + 1
                break
            }
            charCount += line.length + 1
            if (charCount > pos) { lineIdx = i + 2; colIdx = 1; break }
        }
        if (lineIdx == 0) { lineIdx = lines.size; colIdx = (lines.lastOrNull()?.length ?: 0) + 1 }
        return copy(
            cursorLine   = lineIdx,
            cursorCol    = colIdx,
            totalLines   = lines.size,
            totalChars   = text.length,
            selectedChars = if (sel.min == sel.max) 0 else sel.max - sel.min,
        )
    }
}
