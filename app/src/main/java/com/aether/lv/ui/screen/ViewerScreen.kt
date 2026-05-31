package com.aether.lv.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.lv.util.ParsedLine
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    fileUri             : Uri?,
    onBack              : () -> Unit,
    onSettings          : () -> Unit,
    onRequestPermission : () -> Unit = {},
    vm                  : ViewerViewModel = viewModel()
) {
    val state     by vm.state.collectAsStateWithLifecycle()
    val context    = LocalContext.current
    val listState  = rememberLazyListState()
    val scope      = rememberCoroutineScope()

    var searchExpanded  by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    val fileName = remember(fileUri) {
        if (fileUri == null) return@remember "file.log"
        try {
            context.contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (_: Exception) { null }
            ?: fileUri.lastPathSegment?.substringAfterLast('/') ?: "file.log"
    }

    // Teruskan Activity context (LocalContext.current) — BUKAN application context
    LaunchedEffect(fileUri) {
        vm.loadFile(fileUri, fileName, activityContext = context)
    }

    LaunchedEffect(state.jumpToEnd) {
        if (state.jumpToEnd && state.filteredLines.isNotEmpty()) {
            listState.animateScrollToItem(state.filteredLines.size - 1)
            vm.consumeJump()
        }
    }

    Scaffold(
        topBar = {
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
                                state.fileName.ifBlank { fileName },
                                style    = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!state.isLoading) {
                                val lineInfo = if (state.searchQuery.isBlank())
                                    "${state.totalLines} baris"
                                else
                                    "${state.filteredLines.size} / ${state.totalLines} baris"
                                val display = if (state.isGzipped) "GZ  ·  $lineInfo" else lineInfo
                                Text(
                                    display,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { searchExpanded = !searchExpanded }) {
                            Icon(
                                if (searchExpanded) Icons.Outlined.SearchOff else Icons.Outlined.Search,
                                "Cari"
                            )
                        }
                        IconButton(onClick = {
                            fileUri?.let { uri ->
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Bagikan File Log"))
                            }
                        }) { Icon(Icons.Outlined.Share, "Bagikan") }

                        Box {
                            IconButton(onClick = { showOptionsMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, "Opsi")
                            }
                            DropdownMenu(
                                expanded         = showOptionsMenu,
                                onDismissRequest = { showOptionsMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text        = { Text(if (state.wrapLines) "Nonaktifkan Wrap" else "Aktifkan Wrap") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.WrapText, null) },
                                    onClick     = { vm.toggleWrap(!state.wrapLines); showOptionsMenu = false }
                                )
                                DropdownMenuItem(
                                    text        = { Text(if (state.showLineNums) "Sembunyikan No. Baris" else "Tampilkan No. Baris") },
                                    leadingIcon = { Icon(Icons.Outlined.Tag, null) },
                                    onClick     = { vm.toggleLineNums(!state.showLineNums); showOptionsMenu = false }
                                )
                                DropdownMenuItem(
                                    text        = { Text(if (state.applyColors) "Nonaktifkan Warna Log" else "Aktifkan Warna Log") },
                                    leadingIcon = { Icon(Icons.Outlined.Palette, null) },
                                    onClick     = { vm.toggleColors(!state.applyColors); showOptionsMenu = false }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text        = { Text("Ke Baris Pertama") },
                                    leadingIcon = { Icon(Icons.Outlined.VerticalAlignTop, null) },
                                    onClick     = { scope.launch { listState.scrollToItem(0) }; showOptionsMenu = false }
                                )
                                DropdownMenuItem(
                                    text        = { Text("Ke Baris Terakhir") },
                                    leadingIcon = { Icon(Icons.Outlined.VerticalAlignBottom, null) },
                                    onClick     = { vm.jumpToEnd(); showOptionsMenu = false }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text        = { Text("Salin Semua Teks") },
                                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                                    onClick     = {
                                        val clip = state.filteredLines.joinToString("\n") { it.raw }
                                        val cm   = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("log", clip))
                                        showOptionsMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text        = { Text("Pengaturan") },
                                    leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                                    onClick     = { showOptionsMenu = false; onSettings() }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                AnimatedVisibility(
                    visible = searchExpanded,
                    enter   = expandVertically() + fadeIn(),
                    exit    = shrinkVertically() + fadeOut()
                ) {
                    SearchBar(
                        query    = state.searchQuery,
                        onQuery  = vm::onSearch,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                HorizontalDivider(thickness = 0.5.dp)
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                state.isLoading -> {
                    Column(
                        modifier            = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            "Membaca file…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                state.error != null -> {
                    ErrorState(
                        message             = state.error!!,
                        errorType           = state.errorType,
                        onRequestPermission = onRequestPermission,
                        // Teruskan context ke retryLoad agar permission tetap valid
                        onRetry             = { vm.retryLoad(activityContext = context) },
                        modifier            = Modifier.align(Alignment.Center)
                    )
                }
                state.filteredLines.isEmpty() && state.searchQuery.isNotBlank() -> {
                    Text(
                        "Tidak ada baris yang cocok dengan \"${state.searchQuery}\"",
                        modifier  = Modifier.align(Alignment.Center).padding(24.dp),
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                state.filteredLines.isEmpty() -> {
                    Text(
                        "File kosong",
                        modifier = Modifier.align(Alignment.Center),
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    LogContent(
                        lines        = state.filteredLines,
                        wrapLines    = state.wrapLines,
                        showLineNums = state.showLineNums,
                        searchQuery  = state.searchQuery,
                        listState    = listState
                    )
                }
            }

            if (!state.isLoading && state.filteredLines.size > 50) {
                SmallFloatingActionButton(
                    onClick        = { vm.jumpToEnd() },
                    modifier       = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Icon(Icons.Outlined.KeyboardArrowDown, "Ke Bawah")
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query   : String,
    onQuery : (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value         = query,
        onValueChange = onQuery,
        modifier      = modifier.fillMaxWidth(),
        placeholder   = { Text("Cari dalam log…") },
        leadingIcon   = { Icon(Icons.Outlined.Search, null) },
        trailingIcon  = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) {
                    Icon(Icons.Outlined.Clear, "Hapus pencarian")
                }
            }
        },
        singleLine = true,
        shape      = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun LogContent(
    lines       : List<ParsedLine>,
    wrapLines   : Boolean,
    showLineNums: Boolean,
    searchQuery  : String,
    listState   : LazyListState,
    modifier    : Modifier = Modifier
) {
    SelectionContainer {
        if (wrapLines) {
            LazyColumn(
                state          = listState,
                modifier       = modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                itemsIndexed(items = lines, key = { index, _ -> index }) { index, pl ->
                    LogLine(index + 1, pl, true, showLineNums, searchQuery)
                }
            }
        } else {
            val hScroll = rememberScrollState()
            LazyColumn(
                state          = listState,
                modifier       = modifier.fillMaxSize().horizontalScroll(hScroll),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                itemsIndexed(items = lines, key = { index, _ -> index }) { index, pl ->
                    LogLine(index + 1, pl, false, showLineNums, searchQuery)
                }
            }
        }
    }
}

@Composable
private fun LogLine(
    lineNumber : Int,
    parsed     : ParsedLine,
    wrapLines  : Boolean,
    showLineNum: Boolean,
    searchQuery : String
) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (showLineNum) {
            Text(
                text       = "%5d".format(lineNumber),
                style      = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                fontFamily = FontFamily.Monospace,
                modifier   = Modifier.width(40.dp).padding(end = 6.dp)
            )
        }

        val annotated = remember(parsed.raw, parsed.color, searchQuery) {
            buildAnnotatedString {
                if (searchQuery.isNotBlank()) {
                    val raw   = parsed.raw
                    var start = 0
                    val lower = raw.lowercase()
                    val qLow  = searchQuery.lowercase()
                    while (true) {
                        val idx = lower.indexOf(qLow, start)
                        if (idx < 0) {
                            withStyle(SpanStyle(color = parsed.color)) { append(raw.substring(start)) }
                            break
                        }
                        withStyle(SpanStyle(color = parsed.color)) { append(raw.substring(start, idx)) }
                        withStyle(SpanStyle(
                            background = androidx.compose.ui.graphics.Color(0x4D6200EE),
                            color      = parsed.color
                        )) { append(raw.substring(idx, idx + searchQuery.length)) }
                        start = idx + searchQuery.length
                    }
                } else {
                    withStyle(SpanStyle(color = parsed.color)) { append(parsed.raw) }
                }
            }
        }

        Text(
            text       = annotated,
            style      = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines   = if (wrapLines) Int.MAX_VALUE else 1,
            overflow   = if (wrapLines) TextOverflow.Clip else TextOverflow.Visible
        )
    }
}

@Composable
private fun ErrorState(
    message             : String,
    errorType           : FileErrorType? = null,
    onRequestPermission : () -> Unit = {},
    onRetry             : () -> Unit = {},
    modifier            : Modifier = Modifier
) {
    val isPermissionError = errorType == FileErrorType.PERMISSION

    Column(
        modifier            = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            if (isPermissionError) Icons.Outlined.LockOpen else Icons.Outlined.ErrorOutline,
            null,
            modifier = Modifier.size(48.dp),
            tint     = if (isPermissionError) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (isPermissionError) "Izin Akses Diperlukan" else "Gagal Membaca File",
            style = MaterialTheme.typography.titleMedium,
            color = if (isPermissionError) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style     = MaterialTheme.typography.bodySmall,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        if (isPermissionError) {
            Button(onClick = onRequestPermission) {
                Icon(Icons.Outlined.LockOpen, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Izinkan Akses Storage")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRetry) {
                Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Coba Lagi")
            }
        } else {
            OutlinedButton(onClick = onRetry) {
                Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Coba Lagi")
            }
        }
    }
}
