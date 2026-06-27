package com.aether.lv.ui.screen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.lv.util.SyntaxType
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    fileUri  : Uri?,
    onBack   : () -> Unit,
    vm       : EditorViewModel = viewModel()
) {
    val state   by vm.state.collectAsStateWithLifecycle()
    val context  = LocalContext.current
    val activity = remember(context) {
        var ctx = context
        while (ctx is ContextWrapper) { if (ctx is Activity) return@remember ctx; ctx = ctx.baseContext }
        null
    }

    val snackHost = remember { SnackbarHostState() }
    val scope     = rememberCoroutineScope()

    // Launcher untuk "Simpan Sebagai"
    val saveAsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let { vm.saveAsNew(it, activity) } }

    // Load file saat masuk layar
    LaunchedEffect(fileUri) {
        val name = if (fileUri == null) "untitled.txt" else {
            try {
                activity?.contentResolver?.query(fileUri, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
                }
            } catch (_: Exception) { null }
                ?: fileUri.lastPathSegment?.substringAfterLast('/') ?: "file.txt"
        }
        vm.loadFile(fileUri, name, activity)
    }

    // Snackbar
    LaunchedEffect(state.snackMessage) {
        state.snackMessage?.let { msg ->
            scope.launch { snackHost.showSnackbar(msg, duration = SnackbarDuration.Short) }
            vm.clearSnack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackHost) },
        topBar = {
            EditorTopBar(
                state    = state,
                onBack   = onBack,
                onSave   = { vm.saveFile(activity) },
                onSaveAs = { saveAsLauncher.launch(state.fileName) },
                onUndo   = vm::undo,
                onRedo   = vm::redo,
                onFind   = { vm.showFind(false) },
                onReplace    = { vm.showFind(true) },
                onMoreAction = {},   // handled inside bar
                vm       = vm,
                activity = activity,
            )
        },
        bottomBar = {
            EditorStatusBar(state)
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                Column(Modifier.fillMaxSize()) {
                    // ── Find/Replace bar ────────────────────────────────
                    AnimatedVisibility(
                        visible = state.findVisible,
                        enter   = expandVertically() + fadeIn(),
                        exit    = shrinkVertically() + fadeOut()
                    ) {
                        FindReplaceBar(
                            state   = state.findState,
                            showReplace    = state.replaceVisible,
                            onQueryChange  = vm::onFindQueryChange,
                            onReplaceChange = vm::onReplaceChange,
                            onNext         = vm::findNext,
                            onPrev         = vm::findPrev,
                            onReplaceOne   = vm::replaceOne,
                            onReplaceAll   = vm::replaceAll,
                            onToggleCase   = vm::toggleMatchCase,
                            onToggleRegex  = vm::toggleRegex,
                            onToggleReplace = vm::toggleReplacePanel,
                            onClose        = vm::hideFind,
                        )
                    }
                    HorizontalDivider(thickness = 0.5.dp)

                    // ── Editor body ─────────────────────────────────────
                    EditorBody(
                        state    = state,
                        onChange = vm::onTextChange,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    // ── Go To Line dialog ───────────────────────────────────────────────────
    if (state.goToLineVisible) {
        GoToLineDialog(
            totalLines = state.totalLines,
            onConfirm  = vm::goToLine,
            onDismiss  = vm::hideGoToLine,
        )
    }

    // ── Base64 dialog ───────────────────────────────────────────────────────
    if (state.base64DialogVisible) {
        Base64Dialog(
            state     = state,
            onDismiss = vm::hideBase64Dialog,
            onModeChange   = vm::setBase64Mode,
            onInputChange  = vm::setBase64Input,
            onProcess      = vm::processBase64,
            onApply        = vm::applyBase64Output,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top App Bar
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(
    state       : EditorUiState,
    onBack      : () -> Unit,
    onSave      : () -> Unit,
    onSaveAs    : () -> Unit,
    onUndo      : () -> Unit,
    onRedo      : () -> Unit,
    onFind      : () -> Unit,
    onReplace   : () -> Unit,
    onMoreAction: () -> Unit,
    vm          : EditorViewModel,
    activity    : Activity?,
) {
    var showMenu by remember { mutableStateOf(false) }

    Column {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, "Kembali")
                }
            },
            title = {
                Column {
                    Text(
                        buildString { if (state.isDirty) append("● "); append(state.fileName) },
                        style    = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            actions = {
                // Undo
                IconButton(onClick = onUndo, enabled = state.canUndo) {
                    Icon(Icons.AutoMirrored.Outlined.Undo, "Undo")
                }
                // Redo
                IconButton(onClick = onRedo, enabled = state.canRedo) {
                    Icon(Icons.AutoMirrored.Outlined.Redo, "Redo")
                }
                // Save
                IconButton(onClick = onSave, enabled = state.isDirty && !state.isSaving) {
                    if (state.isSaving) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Save, "Simpan")
                    }
                }
                // Overflow menu
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Outlined.MoreVert, "Lainnya")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        // Cari & Ganti
                        DropdownMenuItem(
                            text = { Text("Cari") },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            onClick = { showMenu = false; onFind() }
                        )
                        DropdownMenuItem(
                            text = { Text("Cari & Ganti") },
                            leadingIcon = { Icon(Icons.Outlined.FindReplace, null) },
                            onClick = { showMenu = false; onReplace() }
                        )
                        DropdownMenuItem(
                            text = { Text("Ke Baris…") },
                            leadingIcon = { Icon(Icons.Outlined.Tag, null) },
                            onClick = { showMenu = false; vm.showGoToLine() }
                        )
                        HorizontalDivider()
                        // Edit actions
                        DropdownMenuItem(
                            text = { Text("Pilih Semua") },
                            leadingIcon = { Icon(Icons.Outlined.SelectAll, null) },
                            onClick = { showMenu = false; vm.selectAll() }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplikasi Baris") },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                            onClick = { showMenu = false; vm.duplicateLine() }
                        )
                        DropdownMenuItem(
                            text = { Text("Hapus Baris") },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
                            onClick = { showMenu = false; vm.deleteLine() }
                        )
                        DropdownMenuItem(
                            text = { Text("Toggle Komentar //") },
                            leadingIcon = { Icon(Icons.Outlined.Code, null) },
                            onClick = { showMenu = false; vm.toggleComment() }
                        )
                        DropdownMenuItem(
                            text = { Text("HURUF BESAR") },
                            leadingIcon = { Icon(Icons.Outlined.TextFields, null) },
                            onClick = { showMenu = false; vm.toUpperCase() }
                        )
                        DropdownMenuItem(
                            text = { Text("huruf kecil") },
                            leadingIcon = { Icon(Icons.Outlined.TextFields, null) },
                            onClick = { showMenu = false; vm.toLowerCase() }
                        )
                        DropdownMenuItem(
                            text = { Text("Trim Spasi Akhir") },
                            leadingIcon = { Icon(Icons.Outlined.CleaningServices, null) },
                            onClick = { showMenu = false; vm.trimWhitespace() }
                        )
                        HorizontalDivider()
                        // View options
                        DropdownMenuItem(
                            text = { Text(if (state.wordWrap) "Nonaktifkan Word Wrap" else "Aktifkan Word Wrap") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.WrapText, null) },
                            onClick = { showMenu = false; vm.toggleWordWrap() }
                        )
                        DropdownMenuItem(
                            text = { Text(if (state.showLineNumbers) "Sembunyikan No. Baris" else "Tampilkan No. Baris") },
                            leadingIcon = { Icon(Icons.Outlined.Tag, null) },
                            onClick = { showMenu = false; vm.toggleLineNumbers() }
                        )
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        if (state.syntaxEnabled) "Nonaktifkan Syntax Highlight"
                                        else "Aktifkan Syntax Highlight"
                                    )
                                    if (state.syntaxType.name != "NONE") {
                                        Text(
                                            "Format: ${state.syntaxType.name}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            leadingIcon = { Icon(Icons.Outlined.ColorLens, null) },
                            onClick = { showMenu = false; vm.toggleSyntaxHighlight() }
                        )
                        // Font size submenu (inline slider approach)
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("Ukuran Font: ${state.fontSize.toInt()}sp")
                                    Slider(
                                        value         = state.fontSize,
                                        onValueChange = { vm.setFontSize(it) },
                                        valueRange    = 8f..32f,
                                        steps         = 23,
                                        modifier      = Modifier.width(200.dp)
                                    )
                                }
                            },
                            onClick = { /* tidak dismiss */ {}() }
                        )
                        HorizontalDivider()
                        // Simpan Sebagai
                        DropdownMenuItem(
                            text = { Text("Simpan Sebagai…") },
                            leadingIcon = { Icon(Icons.Outlined.SaveAs, null) },
                            onClick = { showMenu = false; onSaveAs() }
                        )
                        HorizontalDivider()
                        // Base64
                        DropdownMenuItem(
                            text = { Text("Base64 Encode") },
                            leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                            onClick = { showMenu = false; vm.showBase64Dialog(Base64Mode.ENCODE) }
                        )
                        DropdownMenuItem(
                            text = { Text("Base64 Decode") },
                            leadingIcon = { Icon(Icons.Outlined.LockOpen, null) },
                            onClick = { showMenu = false; vm.showBase64Dialog(Base64Mode.DECODE) }
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )
        HorizontalDivider(thickness = 0.5.dp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Editor Body with optional line numbers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditorBody(
    state    : EditorUiState,
    onChange : (TextFieldValue) -> Unit,
    modifier : Modifier = Modifier,
) {
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val focusReq = remember { FocusRequester() }

    val lineColor  = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val gutterBg   = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val textColor  = MaterialTheme.colorScheme.onSurface
    val cursorColor= MaterialTheme.colorScheme.primary

    val fontSizeSp = state.fontSize.sp
    val lineHeightSp = (state.fontSize * 1.5f).sp

    val gutterWidth = if (state.showLineNumbers) 44.dp else 0.dp

    // Hitung gutter line numbers teks satu kali saat totalLines berubah
    val lineNumbers = remember(state.totalLines) {
        (1..state.totalLines).joinToString("\n") { it.toString() }
    }

    LaunchedEffect(Unit) { focusReq.requestFocus() }

    Row(modifier = modifier.fillMaxSize()) {
        // ── Gutter ─────────────────────────────────────────────────────────
        if (state.showLineNumbers) {
            Box(
                Modifier
                    .width(gutterWidth)
                    .fillMaxHeight()
                    .background(gutterBg)
                    .drawBehind {
                        drawLine(
                            color       = lineColor,
                            start       = Offset(size.width, 0f),
                            end         = Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    .verticalScroll(vScroll)
                    .padding(end = 4.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Text(
                    text       = lineNumbers,
                    style      = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize   = fontSizeSp,
                        lineHeight = lineHeightSp,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 6.dp),
                )
            }
        }

        // ── Text field ──────────────────────────────────────────────────────
        val fieldModifier = if (state.wordWrap) {
            Modifier
                .fillMaxSize()
                .verticalScroll(vScroll)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        } else {
            Modifier
                .fillMaxSize()
                .horizontalScroll(hScroll)
                .verticalScroll(vScroll)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        }

        // Jika ada highlighted text dan panjang cocok → pakai AnnotatedString
        val highlighted = state.highlightedText
        val useHighlight = highlighted != null &&
            highlighted.text.length == state.textField.text.length &&
            state.syntaxEnabled

        if (useHighlight && highlighted != null) {
            // Mode highlight: TextFieldValue dibuat dari AnnotatedString
            val highlightedTfv = remember(highlighted, state.textField.selection, state.textField.composition) {
                TextFieldValue(
                    annotatedString = highlighted,
                    selection       = state.textField.selection,
                )
            }
            BasicTextField(
                value         = highlightedTfv,
                onValueChange = { new ->
                    // Forward ke ViewModel dengan plain TextFieldValue (teks saja)
                    onChange(TextFieldValue(new.text, new.selection, new.composition))
                },
                modifier      = fieldModifier
                    .focusRequester(focusReq)
                    .fillMaxWidth(),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize   = fontSizeSp,
                    lineHeight = lineHeightSp,
                    color      = textColor,
                ),
                cursorBrush   = SolidColor(cursorColor),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect    = false,
                    keyboardType   = KeyboardType.Ascii,
                ),
                decorationBox = { inner -> inner() }
            )
        } else {
            // Mode plain (highlight belum tersedia / disabled / terlalu besar)
            BasicTextField(
                value         = state.textField,
                onValueChange = onChange,
                modifier      = fieldModifier
                    .focusRequester(focusReq)
                    .fillMaxWidth(),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize   = fontSizeSp,
                    lineHeight = lineHeightSp,
                    color      = textColor,
                ),
                cursorBrush   = SolidColor(cursorColor),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect    = false,
                    keyboardType   = KeyboardType.Ascii,
                ),
                decorationBox = { inner -> inner() }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Find / Replace bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FindReplaceBar(
    state           : FindState,
    showReplace     : Boolean,
    onQueryChange   : (String) -> Unit,
    onReplaceChange : (String) -> Unit,
    onNext          : () -> Unit,
    onPrev          : () -> Unit,
    onReplaceOne    : () -> Unit,
    onReplaceAll    : () -> Unit,
    onToggleCase    : () -> Unit,
    onToggleRegex   : () -> Unit,
    onToggleReplace : () -> Unit,
    onClose         : () -> Unit,
) {
    val focusReq = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusReq.requestFocus() }

    Surface(
        color     = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier  = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // ── Query row ─────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Chevron toggle replace
                IconButton(onClick = onToggleReplace, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (showReplace) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        "Toggle replace", modifier = Modifier.size(18.dp)
                    )
                }

                // Query input
                OutlinedTextField(
                    value         = state.query,
                    onValueChange = onQueryChange,
                    modifier      = Modifier.weight(1f).focusRequester(focusReq),
                    placeholder   = { Text("Cari…", style = MaterialTheme.typography.bodySmall) },
                    singleLine    = true,
                    textStyle     = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    trailingIcon  = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Outlined.Clear, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onNext() }),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor   = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    )
                )

                // Opsi match case / regex
                FilterChipSmall("Aa", state.matchCase, onToggleCase)
                FilterChipSmall(".*", state.useRegex,  onToggleRegex)

                // Counter
                if (state.matches.isNotEmpty()) {
                    Text(
                        "${if (state.currentMatch >= 0) state.currentMatch + 1 else 0}/${state.matches.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (state.query.isNotEmpty()) {
                    Text("0/0", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }

                // Navigasi
                IconButton(onClick = onPrev, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.KeyboardArrowUp, "Sebelumnya", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.KeyboardArrowDown, "Berikutnya", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Close, "Tutup", modifier = Modifier.size(18.dp))
                }
            }

            // ── Replace row ───────────────────────────────────────────────
            AnimatedVisibility(showReplace) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Spacer(Modifier.width(32.dp))
                    OutlinedTextField(
                        value         = state.replaceWith,
                        onValueChange = onReplaceChange,
                        modifier      = Modifier.weight(1f),
                        placeholder   = { Text("Ganti dengan…", style = MaterialTheme.typography.bodySmall) },
                        singleLine    = true,
                        textStyle     = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        shape         = RoundedCornerShape(8.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor   = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        )
                    )
                    OutlinedButton(onClick = onReplaceOne, modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp)) {
                        Text("Ganti", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(onClick = onReplaceAll, modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp)) {
                        Text("Ganti Semua", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipSmall(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick  = onClick,
        label    = { Text(label, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace) },
        modifier = Modifier.height(28.dp),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Go To Line dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GoToLineDialog(
    totalLines : Int,
    onConfirm  : (Int) -> Unit,
    onDismiss  : () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val num = input.toIntOrNull()

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Ke Baris", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value         = input,
                    onValueChange = { if (it.length <= 7) input = it.filter { c -> c.isDigit() } },
                    label         = { Text("Nomor baris (1–$totalLines)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { num?.let(onConfirm) }),
                    singleLine    = true,
                    isError       = num != null && num !in 1..totalLines,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Batal") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick  = { num?.let(onConfirm) },
                        enabled  = num != null && num in 1..totalLines
                    ) { Text("Pergi") }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Status bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Base64Dialog(
    state         : EditorUiState,
    onDismiss     : () -> Unit,
    onModeChange  : (Base64Mode) -> Unit,
    onInputChange : (String) -> Unit,
    onProcess     : (Boolean) -> Unit,
    onApply       : () -> Unit,
) {
    var useChunks by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape         = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            modifier      = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── Judul ───────────────────────────────────────────────────
                Text("Base64 Encode / Decode", style = MaterialTheme.typography.titleMedium)

                // ── Toggle Mode ─────────────────────────────────────────────
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilterChip(
                        selected = state.base64Mode == Base64Mode.ENCODE,
                        onClick  = { onModeChange(Base64Mode.ENCODE) },
                        label    = { Text("Encode") },
                        leadingIcon = { Icon(Icons.Outlined.Lock, null, Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = state.base64Mode == Base64Mode.DECODE,
                        onClick  = { onModeChange(Base64Mode.DECODE) },
                        label    = { Text("Decode") },
                        leadingIcon = { Icon(Icons.Outlined.LockOpen, null, Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f),
                    )
                }

                // ── Chunks toggle ────────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Checkbox(
                        checked         = useChunks,
                        onCheckedChange = { useChunks = it },
                    )
                    Column(Modifier.weight(1f)) {
                        Text("Mode 512-byte chunk", style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (state.base64Mode == Base64Mode.ENCODE)
                                "Encode per blok 512 byte, dipisah ---"
                            else
                                "Decode tiap blok yang dipisah ---",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ── Input ───────────────────────────────────────────────────
                OutlinedTextField(
                    value         = state.base64Input,
                    onValueChange = onInputChange,
                    label         = {
                        Text(if (state.base64Mode == Base64Mode.ENCODE) "Teks asli" else "String Base64")
                    },
                    placeholder   = {
                        Text(
                            if (state.base64Mode == Base64Mode.ENCODE) "Ketik atau paste teks…"
                            else "Paste Base64 di sini…",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    modifier      = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp, max = 150.dp),
                    maxLines      = 8,
                    textStyle     = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                )

                // ── Proses button ────────────────────────────────────────────
                Button(
                    onClick  = { onProcess(useChunks) },
                    enabled  = state.base64Input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        if (state.base64Mode == Base64Mode.ENCODE) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                        null,
                        Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.base64Mode == Base64Mode.ENCODE) "Encode" else "Decode")
                }

                // ── Error ────────────────────────────────────────────────────
                if (state.base64Error != null) {
                    Surface(
                        color  = MaterialTheme.colorScheme.errorContainer,
                        shape  = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text     = state.base64Error,
                            color    = MaterialTheme.colorScheme.onErrorContainer,
                            style    = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }

                // ── Output ───────────────────────────────────────────────────
                if (state.base64Output.isNotEmpty()) {
                    OutlinedTextField(
                        value         = state.base64Output,
                        onValueChange = {},
                        readOnly      = true,
                        label         = {
                            Text(if (state.base64Mode == Base64Mode.ENCODE) "Hasil Base64" else "Teks terdecode")
                        },
                        modifier      = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp, max = 150.dp),
                        maxLines      = 8,
                        textStyle     = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        trailingIcon  = {
                            // Info karakter
                            Text(
                                "${state.base64Output.length} char",
                                style    = MaterialTheme.typography.labelSmall,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        },
                    )
                }

                // ── Action buttons ───────────────────────────────────────────
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("Tutup") }
                    if (state.base64Output.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onApply) {
                            Icon(Icons.Outlined.ContentPaste, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Tempel ke Editor")
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Status bar (original)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditorStatusBar(state: EditorUiState) {
    Surface(
        color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 0.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Kiri: posisi kursor
            Text(
                "Baris ${state.cursorLine}, Kolom ${state.cursorCol}" +
                        if (state.selectedChars > 0) "  |  ${state.selectedChars} dipilih" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Kanan: statistik
            Text(
                "${state.totalLines} baris  ·  ${state.totalChars} karakter",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Badge format syntax
            if (state.syntaxType != SyntaxType.NONE) {
                val badgeColor = if (state.syntaxEnabled)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
                val textColor = if (state.syntaxEnabled)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                Surface(
                    color  = badgeColor,
                    shape  = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text     = state.syntaxType.name,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = textColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}
