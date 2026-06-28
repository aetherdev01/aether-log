package com.aether.lv.ui.screen

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.lv.BuildConfig
import com.aether.lv.ads.AdsManager
import com.aether.lv.ads.RewardedNoAdsManager
import com.aether.lv.data.preferences.ThemePreferences
import com.aether.lv.update.UpdateDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themePrefs                : ThemePreferences,
    onBack                    : () -> Unit,
    onRequestPermission       : () -> Unit = {},
    onShowInterstitial        : (() -> Unit) -> Unit = { it() },
    onShowRewarded            : () -> Unit = {},
    onOpenLicenseFromSettings : () -> Unit = {},
    homeVm                    : HomeViewModel = viewModel()
) {
    val isDark      by themePrefs.isDarkMode.collectAsState(initial = false)
    val isDynamic   by themePrefs.isDynamicColor.collectAsState(initial = true)
    val wrapLines   by themePrefs.isWrapLines.collectAsState(initial = false)
    val showNums    by themePrefs.showLineNumbers.collectAsState(initial = true)
    val showColors  by themePrefs.showLogColors.collectAsState(initial = true)
    val updateState  by homeVm.updateVm.state.collectAsStateWithLifecycle()
    val licenseState by homeVm.licenseVm.licenseState.collectAsStateWithLifecycle()
    val noAdsState   by RewardedNoAdsManager.state.collectAsStateWithLifecycle()
    val rewardedReady by AdsManager.rewardedReady.collectAsStateWithLifecycle()
    val scope        = rememberCoroutineScope()
    val context      = LocalContext.current

    // Ticker tiap detik untuk countdown real-time
    var tickMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            tickMs = System.currentTimeMillis()
        }
    }
    val remainingMs   = (noAdsState.noAdsUntil - tickMs).coerceAtLeast(0L)
    val isNoAdsActive = remainingMs > 0L
    // Premium dari lisensi mematikan iklan secara permanen.
    val isPremium     = licenseState.isNoAds

    var toggleCount by remember { mutableIntStateOf(0) }

    fun doToggle(applyPref: () -> Unit) {
        toggleCount++
        if (!isPremium && toggleCount % 3 == 0 && AdsManager.interstitialReady.value) {
            onShowInterstitial { applyPref() }
        } else {
            applyPref()
        }
    }

    // ── Toast "Update Belum Tersedia" saat hasil cek manual tidak ada update ──
    LaunchedEffect(updateState.noUpdateEvent) {
        if (updateState.noUpdateEvent != 0L) {
            Toast.makeText(context, "Update Belum Tersedia", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Aksi tonton rewarded ad dengan pengecekan ketersediaan ─────────────────
    fun handleWatchRewarded() {
        if (!rewardedReady) {
            Toast.makeText(context, "Iklan Belum Tersedia", Toast.LENGTH_SHORT).show()
        } else {
            onShowRewarded()
        }
    }

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
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.ArrowBack, "Kembali")
                        }
                    },
                    title = {
                        Text(
                            "Pengaturan",
                            fontWeight = FontWeight.SemiBold,
                            style      = MaterialTheme.typography.titleLarge,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                HorizontalDivider(thickness = 0.5.dp)
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {

            // ── No Ads & Lisensi ──────────────────────────────────────────────
            item { SectionLabel("No Ads & Lisensi") }
            item {
                SettingsCard {
                    if (isPremium) {
                        LicenseStatusRow(
                            isFirst     = true,
                            isLast      = true,
                            productName = licenseState.productName,
                            onClick     = onOpenLicenseFromSettings,
                        )
                    } else {
                        NoAdsRewardedRow(
                            isActive        = isNoAdsActive,
                            remainingMs     = remainingMs,
                            remainingClaims = noAdsState.remainingClaims,
                            canWatch        = noAdsState.canWatchRewarded,
                            rewardedReady   = rewardedReady,
                            onWatch         = { handleWatchRewarded() },
                            isFirst         = true,
                            isLast          = false,
                        )
                        RowDivider()
                        LicenseStatusRow(
                            isFirst     = false,
                            isLast      = true,
                            productName = licenseState.productName,
                            onClick     = onOpenLicenseFromSettings,
                        )
                    }
                }
            }

            // ── Tampilan ────────────────────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SectionLabel("Tampilan")
                    SettingsCard {
                        val modeIcon = if (isDark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode
                        val modeTint = if (isDark) Color(0xFF9FA8DA) else Color(0xFFFFA726)
                        SettingsToggleItem(
                            icon      = modeIcon,
                            iconTint  = modeTint,
                            title     = "Mode Gelap",
                            subtitle  = if (isDark) "Tema gelap aktif" else "Tema terang aktif",
                            checked   = isDark,
                            onChecked = { v -> doToggle { scope.launch { themePrefs.setDarkMode(v) } } },
                            isFirst   = true,
                            isLast    = false,
                        )
                        RowDivider()
                        SettingsToggleItem(
                            icon      = Icons.Outlined.AutoAwesome,
                            iconTint  = MaterialTheme.colorScheme.primary,
                            title     = "Material You",
                            subtitle  = "Warna dinamis dari wallpaper (Android 12+)",
                            checked   = isDynamic,
                            onChecked = { v -> doToggle { scope.launch { themePrefs.setDynamicColor(v) } } },
                            isFirst   = false,
                            isLast    = true,
                        )
                    }
                }
            }

            // ── Viewer Log ──────────────────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SectionLabel("Viewer Log")
                    SettingsCard {
                        SettingsToggleItem(
                            icon      = Icons.Outlined.ColorLens,
                            iconTint  = MaterialTheme.colorScheme.tertiary,
                            title     = "Warna Level Log",
                            subtitle  = "Warnai baris sesuai level (debug, info, error...)",
                            checked   = showColors,
                            onChecked = { v -> doToggle { scope.launch { themePrefs.setShowLogColors(v) } } },
                            isFirst   = true,
                            isLast    = false,
                        )
                        RowDivider()
                        SettingsToggleItem(
                            icon      = Icons.Outlined.Tag,
                            iconTint  = MaterialTheme.colorScheme.secondary,
                            title     = "Nomor Baris",
                            subtitle  = "Tampilkan nomor di gutter kiri",
                            checked   = showNums,
                            onChecked = { v -> doToggle { scope.launch { themePrefs.setShowLineNumbers(v) } } },
                            isFirst   = false,
                            isLast    = false,
                        )
                        RowDivider()
                        SettingsToggleItem(
                            icon      = Icons.AutoMirrored.Outlined.WrapText,
                            iconTint  = MaterialTheme.colorScheme.primary,
                            title     = "Word Wrap",
                            subtitle  = "Bungkus baris yang terlalu panjang",
                            checked   = wrapLines,
                            onChecked = { v -> doToggle { scope.launch { themePrefs.setWrapLines(v) } } },
                            isFirst   = false,
                            isLast    = true,
                        )
                    }
                }
            }

            // ── Sistem ──────────────────────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SectionLabel("Sistem")
                    SettingsCard {
                        val hasUpdate = updateState.updateInfo?.isNewVersion == true
                        SettingsActionItem(
                            icon     = if (hasUpdate) Icons.Outlined.SystemUpdate else Icons.Outlined.Refresh,
                            iconTint = if (hasUpdate) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                            title    = "Cek Pembaruan",
                            subtitle = when {
                                updateState.isChecking         -> "Memeriksa…"
                                hasUpdate                      -> "v${updateState.updateInfo!!.latestVersion} tersedia!"
                                updateState.updateInfo != null -> "Versi terbaru · v${BuildConfig.VERSION_NAME}"
                                else                            -> "Ketuk untuk memeriksa · v${BuildConfig.VERSION_NAME}"
                            },
                            onClick  = {
                                if (!isPremium && AdsManager.interstitialReady.value)
                                    onShowInterstitial { homeVm.updateVm.checkForUpdate(force = true) }
                                else
                                    homeVm.updateVm.checkForUpdate(force = true)
                            },
                            isFirst  = true,
                            isLast   = true,
                            endSlot  = {
                                AnimatedContent(
                                    targetState    = updateState.isChecking to hasUpdate,
                                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                                    label          = "updateBadge",
                                ) { (checking, update) ->
                                    when {
                                        checking -> CircularProgressIndicator(
                                            modifier    = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color       = MaterialTheme.colorScheme.primary,
                                        )
                                        update -> Badge(containerColor = MaterialTheme.colorScheme.error) {
                                            Text("Baru", style = MaterialTheme.typography.labelSmall)
                                        }
                                        else -> Icon(
                                            Icons.Outlined.ChevronRight, null,
                                            modifier = Modifier.size(18.dp),
                                            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ── Section label ─────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text       = text,
        style      = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color      = MaterialTheme.colorScheme.primary,
        modifier   = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

// ── Card container ────────────────────────────────────────────────────────────

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape          = RoundedCornerShape(18.dp),
        color          = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        modifier       = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

// ── License status row (item menu biasa, bukan card besar) ───────────────────

@Composable
private fun LicenseStatusRow(
    isFirst     : Boolean,
    isLast      : Boolean,
    productName : String,
    onClick     : () -> Unit,
) {
    SettingsActionItem(
        icon     = Icons.Outlined.VerifiedUser,
        iconTint = MaterialTheme.colorScheme.primary,
        title    = "Lisensi",
        subtitle = if (productName.isNotBlank()) "Aktif · $productName" else "Masukkan kode lisensi",
        onClick  = onClick,
        isFirst  = isFirst,
        isLast   = isLast,
    )
}

// ── No Ads rewarded row — flat, netral, konsisten dengan item lain ───────────

@Composable
private fun NoAdsRewardedRow(
    isActive        : Boolean,
    remainingMs     : Long,
    remainingClaims : Int,
    canWatch        : Boolean,
    rewardedReady   : Boolean,
    onWatch         : () -> Unit,
    isFirst         : Boolean,
    isLast          : Boolean,
) {
    val countdownStr = formatCountdown(remainingMs)

    Surface(
        color    = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start  = 16.dp,
                    end    = 16.dp,
                    top    = if (isFirst) 15.dp else 12.dp,
                    bottom = if (isLast)  15.dp else 12.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SettingsIconBox(
                    icon = if (isActive) Icons.Outlined.Block else Icons.Outlined.OndemandVideo,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(
                    modifier            = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = if (isActive) "No Ads Aktif" else "Tonton Iklan untuk No Ads",
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = when {
                            isActive        -> "Iklan dinonaktifkan sementara"
                            !canWatch        -> "Batas harian tercapai · reset tengah malam"
                            !rewardedReady   -> "Memuat iklan, harap tunggu…"
                            else             -> "Tonton 1 iklan · bebas iklan 5 menit"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isActive) {
                    Text(
                        countdownStr,
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            "$remainingClaims sisa",
                            style      = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier   = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // ── Progress bar — hanya saat aktif, mengikuti durasi 5 menit ──
            if (isActive) {
                val maxMs    = 5L * 60_000L
                val progress = (remainingMs.toFloat() / maxMs).coerceIn(0f, 1f)
                val animProgress by animateFloatAsState(
                    targetValue   = progress,
                    animationSpec = tween(600, easing = EaseInOutCubic),
                    label         = "noAdsProgress"
                )
                LinearProgressIndicator(
                    progress   = { animProgress },
                    modifier   = Modifier
                        .fillMaxWidth()
                        .padding(start = 50.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color      = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }

            // ── Tombol tonton — hanya saat belum aktif ─────────────────────
            if (!isActive) {
                FilledTonalButton(
                    onClick  = onWatch,
                    enabled  = canWatch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 50.dp)
                        .height(38.dp),
                    shape    = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Outlined.PlayCircleOutline, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (canWatch) "Tonton Sekarang" else "Limit Tercapai",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

// ── Toggle row ────────────────────────────────────────────────────────────────

@Composable
private fun SettingsToggleItem(
    icon      : ImageVector,
    iconTint  : Color,
    title     : String,
    subtitle  : String,
    checked   : Boolean,
    onChecked : (Boolean) -> Unit,
    isFirst   : Boolean,
    isLast    : Boolean,
) {
    Surface(
        color    = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
        onClick  = { onChecked(!checked) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start  = 16.dp,
                    end    = 14.dp,
                    top    = if (isFirst) 15.dp else 12.dp,
                    bottom = if (isLast)  15.dp else 12.dp,
                ),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsIconBox(icon, iconTint)
            Column(
                modifier            = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(title,    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,  color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked         = checked,
                onCheckedChange = onChecked,
                modifier        = Modifier.height(24.dp),
            )
        }
    }
}

// ── Action row ────────────────────────────────────────────────────────────────

@Composable
private fun SettingsActionItem(
    icon     : ImageVector,
    iconTint : Color,
    title    : String,
    subtitle : String,
    onClick  : () -> Unit,
    isFirst  : Boolean,
    isLast   : Boolean,
    endSlot  : (@Composable () -> Unit)? = null,
) {
    Surface(
        color    = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
        onClick  = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start  = 16.dp,
                    end    = 16.dp,
                    top    = if (isFirst) 15.dp else 12.dp,
                    bottom = if (isLast)  15.dp else 12.dp,
                ),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsIconBox(icon, iconTint)
            Column(
                modifier            = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(title,    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,  color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (endSlot != null) endSlot()
            else Icon(
                Icons.Outlined.ChevronRight, null,
                modifier = Modifier.size(18.dp),
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Icon box ──────────────────────────────────────────────────────────────────

@Composable
private fun SettingsIconBox(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp), tint = tint)
    }
}

// ── Divider ───────────────────────────────────────────────────────────────────

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier  = Modifier.padding(start = 66.dp),
        thickness = 0.4.dp,
        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )
}

// ── Format helpers ────────────────────────────────────────────────────────────

private fun formatCountdown(ms: Long): String {
    val totalSec = (ms / 1_000L).coerceAtLeast(0L)
    return "%02d:%02d".format(totalSec / 60, totalSec % 60)
}
