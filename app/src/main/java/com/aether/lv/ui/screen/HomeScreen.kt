package com.aether.lv.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.lv.data.model.RecentFile
import com.aether.lv.update.UpdateDialog
import com.aether.lv.util.FileTypeUtil
import com.aether.lv.util.FormatUtil
import com.aether.lv.ui.component.FileTypeChip
import com.aether.lv.ui.component.FileTypeIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenFile : (Uri) -> Unit,
    onSettings : () -> Unit,
    onAbout    : () -> Unit,
    vm         : HomeViewModel = viewModel()
) {
    val recentFiles  by vm.recentFiles.collectAsStateWithLifecycle()
    val updateState  by vm.updateVm.state.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    // File picker — izinkan semua tipe file agar user bisa pilih file apapun
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { onOpenFile(it) } }

    // ── Update dialog ─────────────────────────────────────────────────────────
    if (updateState.showDialog) {
        UpdateDialog(
            state     = updateState,
            onDismiss = { vm.updateVm.dismissDialog() },
            onDownload= { vm.updateVm.startDownload() },
            onInstall = { vm.updateVm.install() },
            onRetry   = { vm.updateVm.retryDownload() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("LogLog", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Log Viewer",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Badge update bila ada versi baru
                    if (updateState.updateInfo?.isNewVersion == true) {
                        BadgedBox(
                            badge = {
                                Badge(containerColor = MaterialTheme.colorScheme.error)
                            }
                        ) {
                            IconButton(onClick = { vm.updateVm.showUpdateDialog() }) {
                                Icon(Icons.Outlined.SystemUpdate, contentDescription = "Update tersedia")
                            }
                        }
                    }
                    IconButton(onClick = onAbout) {
                        Icon(Icons.Outlined.Info, contentDescription = "Tentang")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Pengaturan")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    // Gunakan */* agar semua file bisa dipilih, tidak hanya yang punya MIME terdaftar
                    filePicker.launch(arrayOf("*/*"))
                },
                icon = { Icon(Icons.Rounded.FolderOpen, "Buka File") },
                text = { Text("Buka File") }
            )
        }
    ) { innerPadding ->
        if (recentFiles.isEmpty()) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onPickFile = {
                    filePicker.launch(arrayOf("*/*"))
                }
            )
        } else {
            LazyColumn(
                modifier            = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            "Riwayat (${recentFiles.size})",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Outlined.DeleteSweep, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Hapus Semua")
                        }
                    }
                }

                items(
                    items = recentFiles,
                    key   = { it.path }
                ) { file ->
                    RecentFileCard(
                        file    = file,
                        onClick = { onOpenFile(Uri.parse(file.path)) },
                        onDelete= { vm.removeRecent(file.path) }
                    )
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    // Dialog konfirmasi hapus semua
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon    = { Icon(Icons.Outlined.DeleteForever, null) },
            title   = { Text("Hapus Semua Riwayat?") },
            text    = { Text("Semua riwayat file yang pernah dibuka akan dihapus.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearHistory()
                    showClearDialog = false
                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun RecentFileCard(
    file    : RecentFile,
    onClick : () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val ext = file.fileType

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick   = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape    = RoundedCornerShape(12.dp),
                color    = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    FileTypeIcon(iconType = FileTypeUtil.iconKey(ext), modifier = Modifier.size(26.dp))
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = file.displayName,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    FileTypeChip(label = FileTypeUtil.label(ext))
                    Text("•", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(FormatUtil.formatSize(file.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (file.lineCount > 0) {
                        Text("•", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${file.lineCount} baris",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    FormatUtil.formatRelativeTime(file.lastOpenedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Outlined.MoreVert, null, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text        = { Text("Hapus dari Riwayat") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick     = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    modifier   : Modifier = Modifier,
    onPickFile : () -> Unit
) {
    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector        = Icons.Outlined.FolderOpen,
            contentDescription = null,
            modifier           = Modifier.size(80.dp),
            tint               = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Belum Ada Riwayat",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Buka file log, txt, json, xml, yaml, err, atau out",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onPickFile) {
            Icon(Icons.Outlined.FileOpen, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Pilih File")
        }
    }
}
