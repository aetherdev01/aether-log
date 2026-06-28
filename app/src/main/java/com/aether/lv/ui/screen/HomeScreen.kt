package com.aether.lv.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.lv.ads.AdsManager
import com.aether.lv.ads.RewardedNoAdsManager
import com.aether.lv.data.model.RecentFile
import com.aether.lv.update.UpdateDialog
import com.aether.lv.util.FileTypeUtil
import com.aether.lv.util.FormatUtil
import kotlinx.coroutines.launch

// ── Tab destination ───────────────────────────────────────────────────────────
private enum class HomeTab { FILES, ENCODE_DECODE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenFile         : (Uri) -> Unit,
    onSettings         : () -> Unit,
    onAbout            : () -> Unit,
    onShowInterstitial : (() -> Unit) -> Unit = { it() },
    vm                 : HomeViewModel = viewModel()
) {
    val updateState by vm.updateVm.state.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(HomeTab.FILES) }

    if (updateState.showDialog) {
        UpdateDialog(
            state      = updateState,
            onDismiss  = { vm.updateVm.dismissDialog() },
            onDownload = { vm.updateVm.startDownload() },
            onInstall  = { vm.updateVm.install() },
            onRetry    = { vm.updateVm.retryDownload() }
        )
    }

    Scaffold(
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
                                        Icons.Outlined.Article, null,
                                        modifier = Modifier.size(18.dp),
                                        tint     = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "LogLog",
                                style      = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    actions = {
                        AnimatedVisibility(
                            visible = updateState.updateInfo?.isNewVersion == true,
                            enter   = scaleIn() + fadeIn(),
                            exit    = scaleOut() + fadeOut()
                        ) {
                            BadgedBox(badge = {
                                Badge(containerColor = MaterialTheme.colorScheme.error)
                            }) {
                                IconButton(onClick = { vm.updateVm.showUpdateDialog() }) {
                                    Icon(Icons.Outlined.SystemUpdate, "Update tersedia")
                                }
                            }
                        }
                        IconButton(onClick = onAbout) {
                            Icon(Icons.Outlined.Info, "Tentang")
                        }
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Outlined.Settings, "Pengaturan")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                HorizontalDivider(thickness = 0.5.dp)
            }
        },
        bottomBar = {
            Column {
                HorizontalDivider(thickness = 0.5.dp)
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    NavigationBarItem(
                        selected = selectedTab == HomeTab.FILES,
                        onClick  = { selectedTab = HomeTab.FILES },
                        icon = {
                            Icon(
                                if (selectedTab == HomeTab.FILES) Icons.Rounded.FolderOpen
                                else Icons.Outlined.FolderOpen,
                                contentDescription = null
                            )
                        },
                        label = { Text("File") },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == HomeTab.ENCODE_DECODE,
                        onClick  = { selectedTab = HomeTab.ENCODE_DECODE },
                        icon = {
                            Icon(
                                if (selectedTab == HomeTab.ENCODE_DECODE) Icons.Outlined.Lock
                                else Icons.Outlined.LockOpen,
                                contentDescription = null
                            )
                        },
                        label = { Text("Encode/Decode") },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState    = selectedTab,
            transitionSpec = {
                val toRight = targetState.ordinal > initialState.ordinal
                val enter = slideInHorizontally(
                    animationSpec  = tween(280, easing = FastOutSlowInEasing),
                    initialOffsetX = { if (toRight) it / 5 else -it / 5 }
                ) + fadeIn(tween(200))
                val exit = slideOutHorizontally(
                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                    targetOffsetX = { if (toRight) -it / 5 else it / 5 }
                ) + fadeOut(tween(150))
                enter togetherWith exit
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) { tab ->
            when (tab) {
                HomeTab.FILES        -> FileTab(
                    vm                 = vm,
                    onOpenFile         = onOpenFile,
                    onShowInterstitial = onShowInterstitial,
                )
                HomeTab.ENCODE_DECODE -> EncodeDecodeScreen()
            }
        }
    }
}

// ── File tab ──────────────────────────────────────────────────────────────────

@Composable
private fun FileTab(
    vm                 : HomeViewModel,
    onOpenFile         : (Uri) -> Unit,
    onShowInterstitial : (() -> Unit) -> Unit,
) {
    val recentFiles       by vm.recentFiles.collectAsStateWithLifecycle()
    val licenseState      by vm.licenseVm.licenseState.collectAsStateWithLifecycle()
    val noAdsState        by RewardedNoAdsManager.state.collectAsStateWithLifecycle()
    var showClearDialog   by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()
    var fileOpenCount     by remember { mutableStateOf(0) }

    var tickMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            tickMs = System.currentTimeMillis()
        }
    }
    val isNoAdsActive = (noAdsState.noAdsUntil - tickMs).coerceAtLeast(0L) > 0L
    val isAdsDisabled = licenseState.isNoAds || isNoAdsActive

    val handleOpenFile: (Uri) -> Unit = { uri ->
        fileOpenCount++
        if (!isAdsDisabled && fileOpenCount % 2 == 0 && AdsManager.interstitialReady.value) {
            onShowInterstitial { onOpenFile(uri) }
        } else {
            onOpenFile(uri)
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { handleOpenFile(it) } }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState    = recentFiles.isEmpty(),
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            modifier       = Modifier.fillMaxSize()
        ) { isEmpty ->
            if (isEmpty) {
                EmptyState(
                    modifier   = Modifier.fillMaxSize(),
                    onPickFile = { filePicker.launch(arrayOf("*/*")) }
                )
            } else {
                LazyColumn(
                    modifier       = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp),
                ) {
                    // ── Section header ────────────────────────────────────
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.History, null,
                                    modifier = Modifier.size(14.dp),
                                    tint     = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Riwayat",
                                    style      = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    shape = RoundedCornerShape(5.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        "${recentFiles.size}",
                                        style    = MaterialTheme.typography.labelSmall,
                                        color    = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            TextButton(
                                onClick        = { showClearDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Outlined.DeleteSweep, null, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Hapus Semua", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // ── File list card ────────────────────────────────────
                    item {
                        Surface(
                            modifier       = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape          = RoundedCornerShape(18.dp),
                            color          = MaterialTheme.colorScheme.surfaceContainerLow,
                            tonalElevation = 0.dp,
                        ) {
                            Column {
                                recentFiles.forEachIndexed { index, file ->
                                    RecentFileRow(
                                        file     = file,
                                        onClick  = {
                                            if (file.isPersisted) {
                                                handleOpenFile(Uri.parse(file.path))
                                            } else {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        message  = "Buka lagi dari file manager.",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                }
                                            }
                                        },
                                        onDelete = { vm.removeRecent(file.path) }
                                    )
                                    if (index < recentFiles.lastIndex) {
                                        HorizontalDivider(
                                            modifier  = Modifier.padding(start = 62.dp),
                                            thickness = 0.4.dp,
                                            color     = MaterialTheme.colorScheme.outlineVariant
                                                .copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }

        // FAB di atas konten tab (bukan di Scaffold agar tidak muncul di tab Encode)
        ExtendedFloatingActionButton(
            onClick        = { filePicker.launch(arrayOf("*/*")) },
            icon           = { Icon(Icons.Rounded.FolderOpen, null) },
            text           = { Text("Buka File") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor   = MaterialTheme.colorScheme.onPrimary,
            expanded       = recentFiles.isEmpty(),
            modifier       = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp),
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon             = { Icon(Icons.Outlined.DeleteForever, null) },
            title            = { Text("Hapus Semua Riwayat?") },
            text             = { Text("Semua riwayat file yang pernah dibuka akan dihapus.") },
            confirmButton    = {
                TextButton(onClick = { vm.clearHistory(); showClearDialog = false }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton    = {
                TextButton(onClick = { showClearDialog = false }) { Text("Batal") }
            }
        )
    }
}

// ── File Row ──────────────────────────────────────────────────────────────────

@Composable
private fun RecentFileRow(
    file    : RecentFile,
    onClick : () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu    by remember { mutableStateOf(false) }
    val ext         = file.fileType
    val isTemporary = !file.isPersisted
    val iconType    = FileTypeUtil.iconKey(ext)
    val chipLabel   = FileTypeUtil.label(ext)
    val chipColor   = fileTypeColor(ext)

    Surface(
        onClick  = onClick,
        color    = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Icon kiri ─────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(chipColor.copy(alpha = if (isTemporary) 0.07f else 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = fileTypeIcon(iconType),
                    contentDescription = null,
                    modifier           = Modifier.size(19.dp),
                    tint               = if (isTemporary) chipColor.copy(alpha = 0.35f) else chipColor
                )
            }

            // ── Teks tengah ───────────────────────────────────────────────
            Column(
                modifier            = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Baris 1 — nama file
                Text(
                    text       = file.displayName,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    color      = if (isTemporary)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    else
                        MaterialTheme.colorScheme.onSurface
                )

                // Baris 2 — info utama: badge tipe file + waktu dibuka
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(chipColor.copy(alpha = 0.13f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text       = chipLabel,
                            style      = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color      = chipColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text     = FormatUtil.formatRelativeTime(file.lastOpenedAt),
                        style    = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color    = if (isTemporary)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        else
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                    )
                }

                // Baris 3 — info sekunder: ukuran & jumlah baris (lebih redup)
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text  = FormatUtil.formatSize(file.sizeBytes),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    if (file.lineCount > 0) {
                        MetaDot()
                        Text(
                            text  = "${file.lineCount} baris",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // ── Menu kanan ────────────────────────────────────────────────
            Box {
                IconButton(
                    onClick  = { showMenu = true },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        Icons.Outlined.MoreVert, null,
                        modifier = Modifier.size(15.dp),
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                DropdownMenu(
                    expanded         = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text        = { Text("Hapus dari Riwayat") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick     = { showMenu = false; onDelete() }
                    )
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun fileTypeColor(ext: String): Color {
    val scheme = MaterialTheme.colorScheme
    return when {
        ext == "json"                   -> Color(0xFF43A047)
        ext == "xml"                    -> Color(0xFF1E88E5)
        ext == "yaml" || ext == "yml"   -> Color(0xFFFB8C00)
        ext == "err" || ext.contains("err") -> Color(0xFFE53935)
        ext == "out" || ext.contains("out") -> Color(0xFF8E24AA)
        ext.endsWith(".gz")             -> Color(0xFF6D4C41)
        else                            -> scheme.primary
    }
}

@Composable
private fun fileTypeIcon(iconType: com.aether.lv.util.FileIconType): ImageVector =
    when (iconType) {
        com.aether.lv.util.FileIconType.JSON  -> Icons.Outlined.DataObject
        com.aether.lv.util.FileIconType.XML   -> Icons.Outlined.Code
        com.aether.lv.util.FileIconType.YAML  -> Icons.Outlined.Settings
        com.aether.lv.util.FileIconType.ERROR -> Icons.Outlined.ErrorOutline
        com.aether.lv.util.FileIconType.OUT   -> Icons.Outlined.Terminal
        com.aether.lv.util.FileIconType.GZ    -> Icons.Outlined.FolderZip
        com.aether.lv.util.FileIconType.LOG   -> Icons.Outlined.Article
    }

@Composable
private fun MetaDot() {
    Text(
        "·",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    )
}

// ── Empty State ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(modifier: Modifier = Modifier, onPickFile: () -> Unit) {
    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape    = RoundedCornerShape(28.dp),
            color    = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            modifier = Modifier.size(84.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.FolderOpen, null,
                    modifier = Modifier.size(42.dp),
                    tint     = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Belum Ada Riwayat",
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            "Buka file log, txt, json, xml, gz, dan lainnya",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(26.dp))
        FilledTonalButton(onClick = onPickFile) {
            Icon(Icons.Outlined.FileOpen, null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text("Pilih File")
        }
    }
}
