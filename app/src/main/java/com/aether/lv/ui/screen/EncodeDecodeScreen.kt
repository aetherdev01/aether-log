package com.aether.lv.ui.screen

import android.util.Base64
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// State
// ─────────────────────────────────────────────────────────────────────────────

private enum class EncodeMode { ENCODE, DECODE }

private enum class CharsetOption(val label: String) {
    UTF8("UTF-8"),
    ISO_8859("ISO-8859-1"),
    ASCII("ASCII");
    val charsetObj get() = when (this) {
        UTF8      -> Charsets.UTF_8
        ISO_8859  -> Charsets.ISO_8859_1
        ASCII     -> Charsets.US_ASCII
    }
}

private data class EncodeDecodeState(
    val mode       : EncodeMode    = EncodeMode.ENCODE,
    val input      : String        = "",
    val output     : String        = "",
    val error      : String?       = null,
    val useChunks  : Boolean       = false,
    val chunkSize  : Int           = 512,
    val charset    : CharsetOption = CharsetOption.UTF8,
    val urlSafe    : Boolean       = false,
)

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncodeDecodeScreen() {
    var s by remember { mutableStateOf(EncodeDecodeState()) }
    val clipboard      = LocalClipboardManager.current
    val snackHost      = remember { SnackbarHostState() }
    val scope          = rememberCoroutineScope()
    val scrollState    = rememberScrollState()

    fun snack(msg: String) { scope.launch { snackHost.showSnackbar(msg, duration = SnackbarDuration.Short) } }

    fun process() {
        val input = s.input.trim()
        if (input.isBlank()) { s = s.copy(error = "Input tidak boleh kosong"); return }
        try {
            val result = when (s.mode) {
                EncodeMode.ENCODE -> {
                    val bytes = input.toByteArray(s.charset.charsetObj)
                    val flags = if (s.urlSafe) Base64.URL_SAFE or Base64.NO_WRAP else Base64.NO_WRAP
                    if (s.useChunks) {
                        bytes.toList().chunked(s.chunkSize)
                            .joinToString("\n---\n") { chunk ->
                                Base64.encodeToString(chunk.toByteArray(), flags)
                            }
                    } else {
                        Base64.encodeToString(bytes, flags)
                    }
                }
                EncodeMode.DECODE -> {
                    val flags = Base64.NO_WRAP or Base64.URL_SAFE  // selalu terima keduanya saat decode
                    if (s.useChunks) {
                        input.split(Regex("\\n?---\\n?"))
                            .joinToString("") { chunk ->
                                String(Base64.decode(chunk.trim(), flags), s.charset.charsetObj)
                            }
                    } else {
                        String(Base64.decode(input, flags), s.charset.charsetObj)
                    }
                }
            }
            s = s.copy(output = result, error = null)
        } catch (e: Exception) {
            val msg = if (s.mode == EncodeMode.DECODE)
                "Gagal decode: bukan Base64 yang valid"
            else
                "Gagal encode: ${e.message}"
            s = s.copy(output = "", error = msg)
        }
    }

    fun swap() {
        if (s.output.isBlank()) return
        s = s.copy(
            input  = s.output,
            output = "",
            error  = null,
            mode   = if (s.mode == EncodeMode.ENCODE) EncodeMode.DECODE else EncodeMode.ENCODE,
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackHost) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape    = RoundedCornerShape(10.dp),
                                color    = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Outlined.LockOpen, null,
                                        modifier = Modifier.size(17.dp),
                                        tint     = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Encode / Decode",
                                style      = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
                HorizontalDivider(thickness = 0.5.dp)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── Mode selector ──────────────────────────────────────────────
            ModeSelector(
                mode     = s.mode,
                onChange = { s = s.copy(mode = it, output = "", error = null) }
            )

            // ── Input field ────────────────────────────────────────────────
            SectionCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(
                        if (s.mode == EncodeMode.ENCODE) "Teks Asli" else "String Base64",
                        style      = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.primary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Paste dari clipboard
                        TooltipIconButton(
                            icon    = Icons.Outlined.ContentPaste,
                            tooltip = "Paste dari clipboard",
                            onClick = {
                                val text = clipboard.getText()?.text ?: ""
                                s = s.copy(input = text, output = "", error = null)
                            }
                        )
                        // Hapus input
                        if (s.input.isNotEmpty()) {
                            TooltipIconButton(
                                icon    = Icons.Outlined.Clear,
                                tooltip = "Hapus",
                                onClick = { s = s.copy(input = "", output = "", error = null) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value         = s.input,
                    onValueChange = { s = s.copy(input = it, output = "", error = null) },
                    modifier      = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 240.dp),
                    placeholder   = {
                        Text(
                            if (s.mode == EncodeMode.ENCODE) "Ketik atau paste teks untuk dienkode…"
                            else "Paste string Base64 untuk didekode…",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    maxLines      = 12,
                    textStyle     = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 13.sp,
                    ),
                    shape         = RoundedCornerShape(10.dp),
                )
                if (s.input.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${s.input.length} karakter",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }

            // ── Opsi ──────────────────────────────────────────────────────
            OptionsCard(
                state    = s,
                onChange = { s = it }
            )

            // ── Tombol proses ──────────────────────────────────────────────
            Button(
                onClick  = ::process,
                enabled  = s.input.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape    = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = if (s.mode == EncodeMode.ENCODE) Icons.Outlined.Lock
                    else Icons.Outlined.LockOpen,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (s.mode == EncodeMode.ENCODE) "Encode ke Base64"
                    else "Decode dari Base64",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // ── Error ──────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = s.error != null,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut(),
            ) {
                Surface(
                    color    = MaterialTheme.colorScheme.errorContainer,
                    shape    = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Outlined.ErrorOutline, null,
                            tint     = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            s.error ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            // ── Output ─────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = s.output.isNotEmpty(),
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut(),
            ) {
                SectionCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // Badge sukses
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(
                                    if (s.mode == EncodeMode.ENCODE) "ENCODED" else "DECODED",
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            Text(
                                "${s.output.length} karakter",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Aksi output
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TooltipIconButton(
                                icon    = Icons.Outlined.ContentCopy,
                                tooltip = "Salin hasil",
                                onClick = {
                                    clipboard.setText(AnnotatedString(s.output))
                                    snack("Hasil disalin ke clipboard")
                                }
                            )
                            TooltipIconButton(
                                icon    = Icons.Outlined.SwapVert,
                                tooltip = "Gunakan sebagai input",
                                onClick = ::swap,
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Output field read-only
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 280.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(12.dp),
                    ) {
                        val outScroll = rememberScrollState()
                        val hOutScroll = rememberScrollState()
                        Text(
                            text = s.output,
                            modifier = Modifier
                                .verticalScroll(outScroll)
                                .horizontalScroll(hOutScroll),
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 12.sp,
                            color      = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp,
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Salin tombol besar
                    OutlinedButton(
                        onClick  = {
                            clipboard.setText(AnnotatedString(s.output))
                            snack("Hasil disalin ke clipboard")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Salin Hasil")
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mode selector — Encode / Decode tabs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ModeSelector(mode: EncodeMode, onChange: (EncodeMode) -> Unit) {
    Surface(
        color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape    = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EncodeMode.entries.forEach { m ->
                val selected = mode == m
                Surface(
                    onClick  = { onChange(m) },
                    modifier = Modifier.weight(1f),
                    color    = if (selected) MaterialTheme.colorScheme.primaryContainer
                               else Color.Transparent,
                    shape    = RoundedCornerShape(10.dp),
                ) {
                    Row(
                        modifier              = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (m == EncodeMode.ENCODE) Icons.Outlined.Lock
                                         else Icons.Outlined.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint     = if (selected) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (m == EncodeMode.ENCODE) "Encode" else "Decode",
                            style      = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color      = if (selected) MaterialTheme.colorScheme.primary
                                         else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Options card
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionsCard(
    state    : EncodeDecodeState,
    onChange : (EncodeDecodeState) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    SectionCard {
        // Header opsi — collapsible
        Surface(
            onClick = { expanded = !expanded },
            color   = Color.Transparent,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Outlined.Tune, null,
                        modifier = Modifier.size(16.dp),
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Opsi Tambahan",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Badge aktif
                    val activeCount = listOf(state.useChunks, state.urlSafe, state.charset != CharsetOption.UTF8)
                        .count { it }
                    if (activeCount > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(99.dp),
                        ) {
                            Text(
                                "$activeCount",
                                style    = MaterialTheme.typography.labelSmall,
                                color    = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    null,
                    modifier = Modifier.size(18.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HorizontalDivider(thickness = 0.5.dp)

                // ── Charset ────────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Charset",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CharsetOption.entries.forEach { opt ->
                            FilterChip(
                                selected = state.charset == opt,
                                onClick  = { onChange(state.copy(charset = opt, output = "", error = null)) },
                                label    = { Text(opt.label, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }

                // ── URL-safe ───────────────────────────────────────────────
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("URL-Safe Base64", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Gunakan - dan _ ganti + dan /",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked         = state.urlSafe,
                        onCheckedChange = { onChange(state.copy(urlSafe = it, output = "", error = null)) },
                    )
                }

                // ── Chunk mode ─────────────────────────────────────────────
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Mode Chunk", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Proses per blok, dipisah dengan ---",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked         = state.useChunks,
                        onCheckedChange = { onChange(state.copy(useChunks = it, output = "", error = null)) },
                    )
                }

                // ── Chunk size slider (hanya jika chunk aktif) ─────────────
                AnimatedVisibility(visible = state.useChunks) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Ukuran Chunk", style = MaterialTheme.typography.labelSmall)
                            Text(
                                "${state.chunkSize} byte",
                                style      = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color      = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Slider(
                            value         = state.chunkSize.toFloat(),
                            onValueChange = {
                                onChange(state.copy(chunkSize = it.toInt(), output = "", error = null))
                            },
                            valueRange    = 64f..4096f,
                            steps         = 62,
                            modifier      = Modifier.fillMaxWidth(),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("64", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("4096", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color          = MaterialTheme.colorScheme.surfaceContainerLow,
        shape          = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp,
        modifier       = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content  = content,
        )
    }
}

@Composable
private fun TooltipIconButton(
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    tooltip : String,
    onClick : () -> Unit,
) {
    IconButton(
        onClick  = onClick,
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            icon, tooltip,
            modifier = Modifier.size(18.dp),
            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
