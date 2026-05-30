package com.aether.lv.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.lv.BuildConfig
import com.aether.lv.data.preferences.ThemePreferences
import com.aether.lv.update.UpdateDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themePrefs          : ThemePreferences,
    onBack              : () -> Unit,
    onRequestPermission : () -> Unit = {},
    // HomeViewModel di-share supaya UpdateViewModel sama instance-nya
    homeVm              : HomeViewModel = viewModel()
) {
    val isDark     by themePrefs.isDarkMode.collectAsState(initial = false)
    val isDynamic  by themePrefs.isDynamicColor.collectAsState(initial = true)
    val wrapLines  by themePrefs.isWrapLines.collectAsState(initial = false)
    val showNums   by themePrefs.showLineNumbers.collectAsState(initial = true)
    val showColors by themePrefs.showLogColors.collectAsState(initial = true)

    val updateState by homeVm.updateVm.state.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()

    // Update dialog dipanggil dari Settings juga
    if (updateState.showDialog) {
        UpdateDialog(
            state      = updateState,
            onDismiss  = { homeVm.updateVm.dismissDialog() },
            onDownload = { homeVm.updateVm.startDownload() },
            onInstall  = { homeVm.updateVm.install() },
            onRetry    = { homeVm.updateVm.retryDownload() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "Kembali")
                    }
                },
                title  = { Text("Pengaturan") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier       = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Tema ──────────────────────────────────────────────────────
            item { SettingGroupLabel("Tampilan & Tema") }

            item {
                SettingSwitch(
                    title    = "Mode Gelap",
                    subtitle = "Gunakan tema gelap",
                    icon     = if (isDark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                    checked  = isDark,
                    onCheckedChange = { scope.launch { themePrefs.setDarkMode(it) } }
                )
            }

            item {
                SettingSwitch(
                    title    = "Material You",
                    subtitle = "Warna dinamis dari wallpaper (Android 12+)",
                    icon     = Icons.Outlined.Palette,
                    checked  = isDynamic,
                    onCheckedChange = { scope.launch { themePrefs.setDynamicColor(it) } }
                )
            }

            item { Spacer(Modifier.height(4.dp)) }

            // ── Viewer ────────────────────────────────────────────────────
            item { SettingGroupLabel("Viewer Log") }

            item {
                SettingSwitch(
                    title    = "Warna Level Log",
                    subtitle = "Beri warna berbeda tiap level (V/D/I/W/E/F)",
                    icon     = Icons.Outlined.ColorLens,
                    checked  = showColors,
                    onCheckedChange = { scope.launch { themePrefs.setShowLogColors(it) } }
                )
            }

            item {
                SettingSwitch(
                    title    = "Nomor Baris",
                    subtitle = "Tampilkan nomor baris di sisi kiri",
                    icon     = Icons.Outlined.Tag,
                    checked  = showNums,
                    onCheckedChange = { scope.launch { themePrefs.setShowLineNumbers(it) } }
                )
            }

            item {
                SettingSwitch(
                    title    = "Bungkus Baris (Word Wrap)",
                    subtitle = "Bungkus baris panjang agar tidak perlu scroll horizontal",
                    icon     = Icons.Outlined.WrapText,
                    checked  = wrapLines,
                    onCheckedChange = { scope.launch { themePrefs.setWrapLines(it) } }
                )
            }

            item { Spacer(Modifier.height(4.dp)) }

            // ── Permission ────────────────────────────────────────────────
            item { SettingGroupLabel("Izin Akses") }

            item {
                Card(
                    shape     = RoundedCornerShape(16.dp),
                    colors    = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    elevation = CardDefaults.cardElevation(0.dp),
                    onClick   = { onRequestPermission() }
                ) {
                    Row(
                        modifier  = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Izin Storage", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Diperlukan untuk membuka file dari riwayat dan path eksternal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Outlined.ChevronRight, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // ── Update ────────────────────────────────────────────────────
            item { SettingGroupLabel("Pembaruan") }

            item {
                Card(
                    shape     = RoundedCornerShape(16.dp),
                    colors    = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    elevation = CardDefaults.cardElevation(0.dp),
                    onClick   = { homeVm.updateVm.checkForUpdate(force = true) }
                ) {
                    Row(
                        modifier  = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            if (updateState.updateInfo?.isNewVersion == true)
                                Icons.Outlined.SystemUpdate
                            else
                                Icons.Outlined.Refresh,
                            contentDescription = null,
                            tint = if (updateState.updateInfo?.isNewVersion == true)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cek Pembaruan", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                when {
                                    updateState.isChecking                       -> "Memeriksa…"
                                    updateState.updateInfo?.isNewVersion == true ->
                                        "Versi ${updateState.updateInfo!!.latestVersion} tersedia!"
                                    updateState.updateInfo != null               -> "Aplikasi sudah terkini"
                                    else                                         -> "Ketuk untuk cek update"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (updateState.updateInfo?.isNewVersion == true)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (updateState.isChecking) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else if (updateState.updateInfo?.isNewVersion == true) {
                            Badge(containerColor = MaterialTheme.colorScheme.error) {
                                Text("Baru")
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(4.dp)) }

            // ── Info app ──────────────────────────────────────────────────
            item { SettingGroupLabel("Informasi Aplikasi") }

            item {
                Card(
                    shape  = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Pakai BuildConfig agar otomatis sinkron dengan build.gradle
                        SettingInfoRow(label = "Versi",          value = BuildConfig.VERSION_NAME)
                        SettingInfoRow(label = "Paket",          value = BuildConfig.APPLICATION_ID)
                        SettingInfoRow(label = "Min Android",    value = "Android 11 (API 30)")
                        SettingInfoRow(label = "Target Android", value = "Android 16 (API 36)")
                        SettingInfoRow(label = "Maintainer",     value = "@AetherDev22")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingGroupLabel(label: String) {
    Text(
        label,
        style  = MaterialTheme.typography.labelMedium,
        color  = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun SettingSwitch(
    title           : String,
    subtitle        : String,
    icon            : androidx.compose.ui.graphics.vector.ImageVector,
    checked         : Boolean,
    onCheckedChange : (Boolean) -> Unit
) {
    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(0.dp),
        onClick = { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title,    style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked         = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun SettingInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface)
    }
}
