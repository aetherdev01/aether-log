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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
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
    fileUri : Uri?,
    onBack  : () -> Unit,
    vm      : EditorViewModel = viewModel()
) {
    val state     by vm.state.collectAsStateWithLifecycle()
    val context    = LocalContext.current
    val snackHost  = remember { SnackbarHostState() }
    val scope      = rememberCoroutineScope()

    // Resolve Activity — dipakai HANYA untuk saveAsLauncher, bukan untuk save biasa
    val activity = remember(context) {
        var ctx: Context = context
        while (ctx is ContextWrapper) { if (ctx is Activity) return@remember ctx; ctx = ctx.baseContext }
        null
    }

    val saveAsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let { vm.saveAsNew(it, activity) } }

    // Load file saat pertama masuk
    LaunchedEffect(fileUri) {
        val name = resolveFileName(fileUri, activity)
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
                // saveFile sekarang pakai appContext internal — tidak perlu Activity
                onSave   = { vm.saveFile() },
                onSaveAs = { saveAsLauncher.launch(state.fileName) },
                onUndo   = vm::undo,
                onRedo   = vm::redo,
                onFind   = { vm.showFind(false) },
                onReplace = { vm.showFind(true) },
                vm       = vm,
            )
        },
        bottomBar = { EditorStatusBar(state) }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                Column(Modifier.fillMaxSize()) {

                    // ── Banner file berubah di disk ──────────────────────
                    AnimatedVisibility(
                        visible = state.fileChangedOnDisk,
                        enter   = expandVertically() + fadeIn(),
                        exit    = shrinkVertically() + fadeOut(),
                    ) {
                        Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.SyncProblem, null,
                                    modifier = Modifier.size(16.dp),
                                    tint     = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Text(
                                    "File berubah di luar editor",
                                    style    = MaterialTheme.typography.labelMedium,
                                    color    = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick        = { vm.reloadFromDisk() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                ) {
                                    Text(
                                        "Muat Ulang",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                                IconButton(
                                    onClick  = { vm.dismissFileChanged() },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Close, null,
                                        modifier = Modifier.size(14.dp),
                                        tint     = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                }
                            }
                        }
                    }

                    // ── Find/Replace bar ─────────────────────────────────
                    AnimatedVisibility(
                        visible = state.findVisible,
                        enter   = expandVertically() + fadeIn(),
                        exit    = shrinkVertically() + fadeOut(),
                    ) {
                        FindReplaceBar(
                            state           = state.findState,
                            showReplace     = state.replaceVisible,
                            onQueryChange   = vm::onFindQueryChange,
                            onReplaceChange = vm::onReplaceChange,
                            onNext          = vm::findNext,
                            onPrev          = vm::findPrev,
                            onReplaceOne    = vm::replaceOne,
                            onReplaceAll    = vm::replaceAll,
                            onToggleCase    = vm::toggleMatchCase,
                            onToggleRegex   = vm::toggleRegex,
                            onToggleReplace = vm::toggleReplacePanel,
                            onClose         = vm::hideFind,
                        )
                    }
                    HorizontalDivider(thickness = 0.5.dp)

                    // ── Editor body ──────────────────────────────────────
                    EditorBody(
                        state    = state,
                        onChange = vm::onTextChange,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    if (state.goToLineVisible) {
        GoToLineDialog(
            totalLines = state.totalLines,
            onConfirm  = vm::goToLine,
            onDismiss  = vm::hideGoToLine,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top App Bar
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(
    state    : EditorUiState,
    onBack   : () -> Unit,
    onSave   : () -> Unit,
    onSaveAs : () -> Unit,
    onUndo   : () -> Unit,
    onRedo   : () -> Unit,
    onFind   : () -> Unit,
    onReplace: () -> Unit,
    vm       : EditorViewModel,
) {
    var showMenu by remember { mutableStateOf(false) }

    Column {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Kembali") }
            },
            title = {
                Text(
                    buildString { if (state.isDirty) append("● "); append(state.fileName) },
                    style    = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            actions = {
                IconButton(onClick = onUndo, enabled = state.canUndo) {
                    Icon(Icons.AutoMirrored.Outlined.Undo, "Undo")
                }
                IconButton(onClick = onRedo, enabled = state.canRedo) {
                    Icon(Icons.AutoMirrored.Outlined.Redo, "Redo")
                }
                IconButton(onClick = onSave, enabled = state.isDirty && !state.isSaving) {
                    if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Save, "Simpan")
                }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Outlined.MoreVert, "Lainnya") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Cari") },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            onClick = { showMenu = false; onFind() })
                        DropdownMenuItem(text = { Text("Cari & Ganti") },
                            leadingIcon = { Icon(Icons.Outlined.FindReplace, null) },
                            onClick = { showMenu = false; onReplace() })
                        DropdownMenuItem(text = { Text("Ke Baris…") },
                            leadingIcon = { Icon(Icons.Outlined.Tag, null) },
                            onClick = { showMenu = false; vm.showGoToLine() })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Pilih Semua") },
                            leadingIcon = { Icon(Icons.Outlined.SelectAll, null) },
                            onClick = { showMenu = false; vm.selectAll() })
                        DropdownMenuItem(text = { Text("Duplikasi Baris") },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                            onClick = { showMenu = false; vm.duplicateLine() })
                        DropdownMenuItem(text = { Text("Hapus Baris") },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
                            onClick = { showMenu = false; vm.deleteLine() })
                        DropdownMenuItem(text = { Text("Toggle Komentar //") },
                            leadingIcon = { Icon(Icons.Outlined.Code, null) },
                            onClick = { showMenu = false; vm.toggleComment() })
                        DropdownMenuItem(text = { Text("HURUF BESAR") },
                            leadingIcon = { Icon(Icons.Outlined.TextFields, null) },
                            onClick = { showMenu = false; vm.toUpperCase() })
                        DropdownMenuItem(text = { Text("huruf kecil") },
                            leadingIcon = { Icon(Icons.Outlined.TextFields, null) },
                            onClick = { showMenu = false; vm.toLowerCase() })
                        DropdownMenuItem(text = { Text("Trim Spasi Akhir") },
                            leadingIcon = { Icon(Icons.Outlined.CleaningServices, null) },
                            onClick = { showMenu = false; vm.trimWhitespace() })
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(if (state.wordWrap) "Nonaktifkan Word Wrap" else "Aktifkan Word Wrap") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.WrapText, null) },
                            onClick = { showMenu = false; vm.toggleWordWrap() })
                        DropdownMenuItem(
                            text = { Text(if (state.showLineNumbers) "Sembunyikan No. Baris" else "Tampilkan No. Baris") },
                            leadingIcon = { Icon(Icons.Outlined.Tag, null) },
                            onClick = { showMenu = false; vm.toggleLineNumbers() })
                        DropdownMenuItem(
                            text = { Text(if (state.syntaxEnabled) "Nonaktifkan Syntax Highlight" else "Aktifkan Syntax Highlight") },
                            leadingIcon = { Icon(Icons.Outlined.ColorLens, null) },
                            onClick = { showMenu = false; vm.toggleSyntaxHighlight() })
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
                            onClick = { }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Simpan Sebagai…") },
                            leadingIcon = { Icon(Icons.Outlined.SaveAs, null) },
                            onClick = { showMenu = false; onSaveAs() })
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )
        HorizontalDivider(thickness = 0.5.dp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Editor Body
//
// FIX BUG MENGETIK:
// BasicTextField harus SELALU menerima TextFieldValue yang teksnya identik
// dengan apa yang user ketik. Jika kita inject AnnotatedString dari highlight
// yang berbeda dari teks aktual, IME akan bingung dan close keyboard.
//
// Solusi:
// - Mode highlight: buat AnnotatedString baru dengan teks SAMA persis dari
//   state.textField.text, hanya ambil spans dari highlightedText.
// - Cek ketat: hanya pakai highlight jika panjang teks sama.
// - Composition (IME internal state) tidak disentuh sama sekali.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditorBody(
    state    : EditorUiState,
    onChange : (TextFieldValue) -> Unit,
    modifier : Modifier = Modifier,
) {
    val hScroll     = rememberScrollState()
    val vScroll     = rememberScrollState()
    val focusReq    = remember { FocusRequester() }
    val lineColor   = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val gutterBg    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val textColor   = MaterialTheme.colorScheme.onSurface
    val cursorColor = MaterialTheme.colorScheme.primary
    val fontSizeSp  = state.fontSize.sp
    val lineHeightSp = (state.fontSize * 1.5f).sp

    val lineNumbers = remember(state.totalLines) {
        (1..state.totalLines).joinToString("\n") { it.toString() }
    }

    LaunchedEffect(Unit) {
        runCatching { focusReq.requestFocus() }
    }

    // Bangun TextFieldValue yang aman untuk BasicTextField.
    // Kunci: teks harus IDENTIK dengan state.textField.text.
    // Spans highlight hanya ditempelkan jika panjang cocok.
    val safeTfv = remember(state.textField, state.highlightedText, state.syntaxEnabled) {
        val currentText = state.textField.text
        val highlighted = state.highlightedText

        val canUseHighlight = state.syntaxEnabled &&
            highlighted != null &&
            highlighted.text == currentText   // cek EXACT match, bukan hanya panjang

        if (canUseHighlight && highlighted != null) {
            // Buat AnnotatedString baru dari teks aktual + spans highlight lama
            // Ini aman karena teks dijamin identik
            TextFieldValue(
                annotatedString = highlighted,
                selection       = state.textField.selection,
                // composition TIDAK diset — biarkan IME manage sendiri
            )
        } else {
            // Plain mode: tidak ada highlight, teks murni
            state.textField
        }
    }

    Row(modifier = modifier.fillMaxSize()) {
        // ── Gutter ───────────────────────────────────────────────────────────
        if (state.showLineNumbers) {
            Box(
                Modifier
                    .width(44.dp)
                    .fillMaxHeight()
                    .background(gutterBg)
                    .drawBehind {
                        drawLine(lineColor, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
                    }
                    .verticalScroll(vScroll)
                    .padding(end = 4.dp),
                contentAlignment = Alignment.TopEnd,
            ) {
                Text(
                    text     = lineNumbers,
                    style    = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize   = fontSizeSp,
                        lineHeight = lineHeightSp,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 6.dp),
                )
            }
        }

        // ── BasicTextField ────────────────────────────────────────────────────
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

        BasicTextField(
            value         = safeTfv,
            onValueChange = { new ->
                // Forward selalu sebagai plain TextFieldValue
                // — AnnotatedString dikembalikan ke plain agar ViewModel tidak
                //   perlu tahu apakah user mengetik di highlight mode atau tidak
                onChange(
                    if (new.annotatedString.spanStyles.isNotEmpty()) {
                        TextFieldValue(new.text, new.selection, new.composition)
                    } else {
                        new
                    }
                )
            },
            modifier      = fieldModifier
                .focusRequester(focusReq)
                .fillMaxWidth(),
            textStyle     = TextStyle(
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
            decorationBox = { inner -> inner() },
        )
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
    LaunchedEffect(Unit) { runCatching { focusReq.requestFocus() } }

    Surface(
        color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onToggleReplace, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (showReplace) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        null, modifier = Modifier.size(18.dp),
                    )
                }
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
                    shape  = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor   = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                FilterChipSmall("Aa", state.matchCase, onToggleCase)
                FilterChipSmall(".*", state.useRegex,  onToggleRegex)
                if (state.matches.isNotEmpty()) {
                    Text(
                        "${if (state.currentMatch >= 0) state.currentMatch + 1 else 0}/${state.matches.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (state.query.isNotEmpty()) {
                    Text("0/0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = onPrev, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.KeyboardArrowUp, null, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.KeyboardArrowDown, null, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Close, null, modifier = Modifier.size(18.dp))
                }
            }
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
                        ),
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
private fun GoToLineDialog(totalLines: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Batal") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { num?.let(onConfirm) }, enabled = num != null && num in 1..totalLines) { Text("Pergi") }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Status bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditorStatusBar(state: EditorUiState) {
    Surface(
        color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                "Baris ${state.cursorLine}, Kolom ${state.cursorCol}" +
                    if (state.selectedChars > 0) "  |  ${state.selectedChars} dipilih" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${state.totalLines} baris  ·  ${state.totalChars} karakter",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.syntaxType != SyntaxType.NONE) {
                Surface(
                    color = if (state.syntaxEnabled)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        state.syntaxType.name,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = if (state.syntaxEnabled) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun resolveFileName(uri: Uri?, activity: Activity?): String {
    if (uri == null) return "untitled.txt"
    return try {
        activity?.contentResolver?.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    } catch (_: Exception) { null }
        ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file.txt"
}
