package com.aether.lv.ui.screen

import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
        UTF8     -> Charsets.UTF_8
        ISO_8859 -> Charsets.ISO_8859_1
        ASCII    -> Charsets.US_ASCII
    }
}

private data class EncodeDecodeState(
    val mode      : EncodeMode    = EncodeMode.ENCODE,
    val input     : String        = "",
    val output    : String        = "",
    val error     : String?       = null,
    val useChunks : Boolean       = false,
    val chunkSize : Int           = 512,
    val charset   : CharsetOption = CharsetOption.UTF8,
    val urlSafe   : Boolean       = false,
)

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Layar Encode/Decode Base64.
 *
 * Catatan rework:
 * - Tidak punya Scaffold/TopAppBar sendiri lagi — top bar sudah disediakan
 *   oleh HomeScreen di tingkat lebih atas, sebelumnya dobel.
 * - Input → opsi → tombol proses digabung jadi satu kartu supaya terasa satu
 *   alur kerja, bukan kartu-kartu lepas dengan spacing tidak konsisten.
 * - Output tidak lagi memakai scroll vertikal + horizontal sekaligus (yang
 *   bikin gesture rebutan); teks Base64 sekarang melipat (wrap) rapi dan
 *   hanya discroll vertikal bila hasilnya panjang.
 */
@Composable
fun EncodeDecodeScreen() {
    var s by remember { mutableStateOf(EncodeDecodeState()) }
    val clipboard   = LocalClipboardManager.current
    val snackHost   = remember { SnackbarHostState() }
    val scope       = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    fun snack(msg: String) {
        scope.launch { snackHost.showSnackbar(msg, duration = SnackbarDuration.Short) }
    }

    fun process() {
        val input = s.input.trim()
        if (input.isBlank()) {
            s = s.copy(error = "Input tidak boleh kosong")
            return
        }
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
                    val flags = Base64.NO_WRAP or Base64.URL_SAFE // selalu terima keduanya saat decode
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

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // ── Satu kartu utama: mode → input → opsi → aksi ─────────────────
            Surface(
                color          = MaterialTheme.colorScheme.surfaceContainerLow,
                shape          = RoundedCornerShape(20.dp),
                tonalElevation = 0.dp,
                modifier       = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier            = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ModeSelector(
                        mode     = s.mode,
                        onChange = { s = s.copy(mode = it, output = "", error = null) },
                    )

                    InputSection(
                        state    = s,
                        onChange = { s = it },
                    )

                    OptionsSection(
                        state    = s,
                        onChange = { s = it },
                    )

                    Button(
                        onClick  = ::process,
                        enabled  = s.input.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape    = RoundedCornerShape(14.dp),
                    ) {
                        Icon(
                            imageVector        = if (s.mode == EncodeMode.ENCODE) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                            contentDescription = null,
                            modifier           = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text       = if (s.mode == EncodeMode.ENCODE) "Encode ke Base64" else "Decode dari Base64",
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // ── Error ─────────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = s.error != null,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut(),
            ) {
                ErrorBanner(message = s.error ?: "")
            }

            // ── Output ────────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = s.output.isNotEmpty(),
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut(),
            ) {
                OutputSection(
                    state  = s,
                    onCopy = {
                        clipboard.setText(AnnotatedString(s.output))
                        snack("Hasil disalin ke clipboard")
                    },
                    onSwap = ::swap,
                )
            }

            // Ruang ekstra agar konten terakhir tidak tertutup pill nav mengambang.
            Spacer(Modifier.height(96.dp))
        }

        SnackbarHost(
            hostState = snackHost,
            modifier  = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mode selector — Encode / Decode segmented control
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ModeSelector(mode: EncodeMode, onChange: (EncodeMode) -> Unit) {
    Surface(
        color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape    = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EncodeMode.entries.forEach { m ->
                val selected = mode == m
                Surface(
                    onClick  = { onChange(m) },
                    modifier = Modifier.weight(1f),
                    color    = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    shape    = RoundedCornerShape(10.dp),
                ) {
                    Row(
                        modifier              = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector        = if (m == EncodeMode.ENCODE) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                            contentDescription = null,
                            modifier           = Modifier.size(16.dp),
                            tint               = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text       = if (m == EncodeMode.ENCODE) "Encode" else "Decode",
                            style      = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color      = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Input section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InputSection(
    state    : EncodeDecodeState,
    onChange : (EncodeDecodeState) -> Unit,
) {
    val clipboard = LocalClipboardManager.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text       = if (state.mode == EncodeMode.ENCODE) "Teks Asli" else "String Base64",
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.primary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TooltipIconButton(
                    icon    = Icons.Outlined.ContentPaste,
                    tooltip = "Paste dari clipboard",
                    onClick = {
                        val text = clipboard.getText()?.text ?: ""
                        onChange(state.copy(input = text, output = "", error = null))
                    },
                )
                if (state.input.isNotEmpty()) {
                    TooltipIconButton(
                        icon    = Icons.Outlined.Clear,
                        tooltip = "Hapus",
                        onClick = { onChange(state.copy(input = "", output = "", error = null)) },
                    )
                }
            }
        }

        OutlinedTextField(
            value         = state.input,
            onValueChange = { onChange(state.copy(input = it, output = "", error = null)) },
            modifier      = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 220.dp),
            placeholder   = {
                Text(
                    text  = if (state.mode == EncodeMode.ENCODE)
                        "Ketik atau paste teks untuk dienkode…"
                    else
                        "Paste string Base64 untuk didekode…",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            textStyle     = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace,
                fontSize   = 13.sp,
            ),
            shape         = RoundedCornerShape(12.dp),
        )

        if (state.input.isNotEmpty()) {
            Text(
                text  = "${state.input.length} karakter",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Options section — collapsible, opsi tambahan (charset, url-safe, chunk)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OptionsSection(
    state    : EncodeDecodeState,
    onChange : (EncodeDecodeState) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val activeCount = listOf(
        state.useChunks,
        state.urlSafe,
        state.charset != CharsetOption.UTF8,
    ).count { it }

    Column {
        Surface(
            onClick  = { expanded = !expanded },
            color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape    = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector        = Icons.Outlined.Tune,
                        contentDescription = null,
                        modifier           = Modifier.size(16.dp),
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text  = "Opsi Tambahan",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (activeCount > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(99.dp),
                        ) {
                            Text(
                                text       = "$activeCount",
                                style      = MaterialTheme.typography.labelSmall,
                                color      = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.SemiBold,
                                modifier   = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                Icon(
                    imageVector        = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier           = Modifier.size(18.dp),
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier            = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ── Charset ──────────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text  = "Charset",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CharsetOption.entries.forEach { opt ->
                            FilterChip(
                                selected = state.charset == opt,
                                onClick  = { onChange(state.copy(charset = opt, output = "", error = null)) },
                                label    = { Text(opt.label, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }

                OptionToggleRow(
                    title           = "URL-Safe Base64",
                    subtitle        = "Gunakan - dan _ ganti + dan /",
                    checked         = state.urlSafe,
                    onCheckedChange = { onChange(state.copy(urlSafe = it, output = "", error = null)) },
                )

                OptionToggleRow(
                    title           = "Mode Chunk",
                    subtitle        = "Proses per blok, dipisah dengan ---",
                    checked         = state.useChunks,
                    onCheckedChange = { onChange(state.copy(useChunks = it, output = "", error = null)) },
                )

                AnimatedVisibility(visible = state.useChunks) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Ukuran Chunk", style = MaterialTheme.typography.labelSmall)
                            Text(
                                text       = "${state.chunkSize} byte",
                                style      = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color      = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Slider(
                            value         = state.chunkSize.toFloat(),
                            onValueChange = { onChange(state.copy(chunkSize = it.toInt(), output = "", error = null)) },
                            valueRange    = 64f..4096f,
                            steps         = 62,
                            modifier      = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("64", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("4096", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionToggleRow(
    title           : String,
    subtitle        : String,
    checked         : Boolean,
    onCheckedChange : (Boolean) -> Unit,
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Column(
            modifier            = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Output section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OutputSection(
    state  : EncodeDecodeState,
    onCopy : () -> Unit,
    onSwap : () -> Unit,
) {
    Surface(
        color          = MaterialTheme.colorScheme.surfaceContainerLow,
        shape          = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
        modifier       = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text       = if (state.mode == EncodeMode.ENCODE) "ENCODED" else "DECODED",
                            style      = MaterialTheme.typography.labelSmall,
                            color      = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier   = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                    Text(
                        text  = "${state.output.length} karakter",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TooltipIconButton(
                        icon    = Icons.Outlined.SwapVert,
                        tooltip = "Gunakan sebagai input",
                        onClick = onSwap,
                    )
                    TooltipIconButton(
                        icon    = Icons.Outlined.ContentCopy,
                        tooltip = "Salin hasil",
                        onClick = onCopy,
                    )
                }
            }

            // Output di-wrap (tanpa scroll horizontal) supaya tidak ada gesture
            // ganda yang rebutan dengan scroll halaman — hanya scroll vertikal
            // jika hasilnya panjang.
            SelectionContainer {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp, max = 260.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    Text(
                        text       = state.output,
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 12.sp,
                        lineHeight = 18.sp,
                        color      = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            OutlinedButton(
                onClick  = onCopy,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Salin Hasil")
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        color    = MaterialTheme.colorScheme.errorContainer,
        shape    = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier              = Modifier.padding(14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector        = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.error,
                modifier           = Modifier.size(18.dp),
            )
            Text(
                text  = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Small helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TooltipIconButton(
    icon    : ImageVector,
    tooltip : String,
    onClick : () -> Unit,
) {
    IconButton(
        onClick  = onClick,
        modifier = Modifier.size(34.dp),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = tooltip,
            modifier           = Modifier.size(18.dp),
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
