package com.aether.lv.update

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aether.lv.util.FormatUtil

@Composable
fun UpdateDialog(
    state   : UpdateUiState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onRetry  : () -> Unit
) {
    val info = state.updateInfo ?: return

    Dialog(
        onDismissRequest = {
            // Tidak bisa dismiss saat download berlangsung
            if (state.downloadState != DownloadState.DOWNLOADING) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress    = state.downloadState != DownloadState.DOWNLOADING,
            dismissOnClickOutside = state.downloadState != DownloadState.DOWNLOADING,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier  = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape     = RoundedCornerShape(28.dp),
            color     = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {

                // ── Header ───────────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.SystemUpdate,
                                contentDescription = null,
                                tint     = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            "Update Tersedia",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Versi ${info.latestVersion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    // Ukuran APK
                    if (info.apkSizeBytes > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                FormatUtil.formatSize(info.apkSizeBytes),
                                style    = MaterialTheme.typography.labelSmall,
                                color    = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Changelog ────────────────────────────────────────────
                if (info.changelog.isNotBlank()) {
                    Text(
                        "Changelog",
                        style      = MaterialTheme.typography.labelMedium,
                        color      = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            // Parse markdown sederhana: baris "- item" jadi bullet
                            info.changelog.lines().forEach { line ->
                                val trimmed = line.trim()
                                when {
                                    trimmed.startsWith("## ") -> {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            trimmed.removePrefix("## "),
                                            style      = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color      = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.padding(vertical = 1.dp)
                                        ) {
                                            Text(
                                                "•",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                trimmed.removePrefix("- ").removePrefix("* "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    trimmed.isNotEmpty() -> {
                                        Text(
                                            trimmed,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // ── Progress area ─────────────────────────────────────────
                AnimatedVisibility(
                    visible = state.downloadState == DownloadState.DOWNLOADING ||
                              state.downloadState == DownloadState.DONE
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                when (state.downloadState) {
                                    DownloadState.DOWNLOADING -> "Mengunduh…"
                                    DownloadState.DONE        -> "Selesai diunduh"
                                    else                      -> ""
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${state.downloadPct}%",
                                style      = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color      = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress          = { state.downloadPct / 100f },
                            modifier          = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            trackColor        = MaterialTheme.colorScheme.surfaceVariant,
                            color             = if (state.downloadState == DownloadState.DONE)
                                MaterialTheme.colorScheme.tertiary
                            else
                                MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }

                // ── Error ─────────────────────────────────────────────────
                AnimatedVisibility(visible = state.downloadState == DownloadState.ERROR) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.ErrorOutline, null,
                            tint     = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            state.errorMsg ?: "Download gagal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // ── Tombol aksi ───────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Tombol kiri: Nanti / Batal
                    if (state.downloadState != DownloadState.DOWNLOADING) {
                        OutlinedButton(
                            onClick  = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Nanti")
                        }
                    }

                    // Tombol kanan: Download / Install / Coba Lagi
                    when (state.downloadState) {
                        DownloadState.IDLE -> {
                            Button(
                                onClick  = onDownload,
                                modifier = Modifier.weight(2f)
                            ) {
                                Icon(Icons.Outlined.Download, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Download")
                            }
                        }

                        DownloadState.DOWNLOADING -> {
                            Button(
                                onClick  = { /* tidak bisa cancel */ },
                                enabled  = false,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator(
                                    modifier  = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color     = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Mengunduh ${state.downloadPct}%")
                            }
                        }

                        DownloadState.DONE -> {
                            Button(
                                onClick  = onInstall,
                                modifier = Modifier.weight(2f),
                                colors   = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary
                                )
                            ) {
                                Icon(Icons.Outlined.InstallMobile, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Install Sekarang")
                            }
                        }

                        DownloadState.ERROR -> {
                            Button(
                                onClick  = onRetry,
                                modifier = Modifier.weight(2f),
                                colors   = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Coba Lagi")
                            }
                        }
                    }
                }
            }
        }
    }
}
