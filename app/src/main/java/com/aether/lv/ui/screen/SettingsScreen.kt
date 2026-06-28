package com.aether.lv.ui.screen

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.lv.BuildConfig
import com.aether.lv.ads.AdsManager
import com.aether.lv.ads.RewardedNoAdsManager
import com.aether.lv.data.preferences.ThemePreferences
import com.aether.lv.update.UpdateDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ═════════════════════════════════════════════════════════════════════════════
// Screen
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themePrefs                : ThemePreferences,
    onBack                    : () -> Unit,
    onRequestPermission       : () -> Unit = {},
    onShowInterstitial        : (() -> Unit) -> Unit = { it() },
    onShowRewarded            : () -> Unit = {},
    onOpenLicenseFromSettings : () -> Unit = {},
    homeVm                    : HomeViewModel = viewModel(),
) {
    val isDark       by themePrefs.isDarkMode.collectAsState(initial = false)
    val isDynamic    by themePrefs.isDynamicColor.collectAsState(initial = true)
    val wrapLines    by themePrefs.isWrapLines.collectAsState(initial = false)
    val showNums     by themePrefs.showLineNumbers.collectAsState(initial = true)
    val showColors   by themePrefs.showLogColors.collectAsState(initial = true)
    val updateState  by homeVm.updateVm.state.collectAsStateWithLifecycle()
    val licenseState by homeVm.licenseVm.licenseState.collectAsStateWithLifecycle()
    val noAdsState   by RewardedNoAdsManager.state.collectAsStateWithLifecycle()
    val rewardedReady by AdsManager.rewardedReady.collectAsStateWithLifecycle()
    val scope        = rememberCoroutineScope()
    val context      = LocalContext.current

    // Ticker real-time countdown
    var tickMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(1_000); tickMs = System.currentTimeMillis() }
    }
    val remainingMs   = (noAdsState.noAdsUntil - tickMs).coerceAtLeast(0L)
    val isNoAdsActive = remainingMs > 0L
    val isPremium     = licenseState.isNoAds

    var toggleCount by remember { mutableIntStateOf(0) }
    fun doToggle(applyPref: () -> Unit) {
        toggleCount++
        if (!isPremium && toggleCount % 3 == 0 && AdsManager.interstitialReady.value)
            onShowInterstitial { applyPref() }
        else applyPref()
    }

    LaunchedEffect(updateState.noUpdateEvent) {
        if (updateState.noUpdateEvent != 0L)
            Toast.makeText(context, "Sudah versi terbaru", Toast.LENGTH_SHORT).show()
    }

    fun handleWatchRewarded() {
        if (!rewardedReady) Toast.makeText(context, "Iklan belum tersedia", Toast.LENGTH_SHORT).show()
        else onShowRewarded()
    }

    if (updateState.showDialog) {
        UpdateDialog(
            state      = updateState,
            onDismiss  = { homeVm.updateVm.dismissDialog() },
            onDownload = { homeVm.updateVm.startDownload() },
            onInstall  = { homeVm.updateVm.install() },
            onRetry    = { homeVm.updateVm.retryDownload() },
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
                title = {
                    Text("Pengaturan", fontWeight = FontWeight.SemiBold)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {

            // ── No Ads & Lisensi ──────────────────────────────────────────────
            item { PrefGroup("No Ads & Lisensi") }

            if (isPremium) {
                item {
                    PrefItem(
                        icon     = Icons.Outlined.VerifiedUser,
                        title    = "Lisensi",
                        subtitle = if (licenseState.productName.isNotBlank())
                                       "Aktif · ${licenseState.productName}"
                                   else "Masukkan kode lisensi",
                        onClick  = onOpenLicenseFromSettings,
                        end      = { NavArrow() },
                    )
                }
            } else {
                item {
                    NoAdsRow(
                        isActive        = isNoAdsActive,
                        remainingMs     = remainingMs,
                        remainingClaims = noAdsState.remainingClaims,
                        canWatch        = noAdsState.canWatchRewarded,
                        rewardedReady   = rewardedReady,
                        onWatch         = { handleWatchRewarded() },
                    )
                }
                item { PrefDivider() }
                item {
                    PrefItem(
                        icon     = Icons.Outlined.VerifiedUser,
                        title    = "Lisensi",
                        subtitle = "Masukkan kode lisensi",
                        onClick  = onOpenLicenseFromSettings,
                        end      = { NavArrow() },
                    )
                }
            }

            // ── Tampilan ──────────────────────────────────────────────────────
            item { PrefGroup("Tampilan") }

            item {
                PrefItem(
                    icon     = if (isDark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                    title    = "Mode Gelap",
                    subtitle = if (isDark) "Tema gelap aktif" else "Tema terang aktif",
                    onClick  = { doToggle { scope.launch { themePrefs.setDarkMode(!isDark) } } },
                    end      = {
                        Switch(
                            checked         = isDark,
                            onCheckedChange = { v -> doToggle { scope.launch { themePrefs.setDarkMode(v) } } },
                        )
                    },
                )
            }
            item { PrefDivider() }
            item {
                PrefItem(
                    icon     = Icons.Outlined.AutoAwesome,
                    title    = "Material You",
                    subtitle = "Warna dinamis dari wallpaper · Android 12+",
                    onClick  = { doToggle { scope.launch { themePrefs.setDynamicColor(!isDynamic) } } },
                    end      = {
                        Switch(
                            checked         = isDynamic,
                            onCheckedChange = { v -> doToggle { scope.launch { themePrefs.setDynamicColor(v) } } },
                        )
                    },
                )
            }

            // ── Viewer Log ────────────────────────────────────────────────────
            item { PrefGroup("Viewer Log") }

            item {
                PrefItem(
                    icon     = Icons.Outlined.Palette,
                    title    = "Warna Level Log",
                    subtitle = "Warnai baris sesuai level debug, info, error",
                    onClick  = { doToggle { scope.launch { themePrefs.setShowLogColors(!showColors) } } },
                    end      = {
                        Switch(
                            checked         = showColors,
                            onCheckedChange = { v -> doToggle { scope.launch { themePrefs.setShowLogColors(v) } } },
                        )
                    },
                )
            }
            item { PrefDivider() }
            item {
                PrefItem(
                    icon     = Icons.Outlined.Tag,
                    title    = "Nomor Baris",
                    subtitle = "Tampilkan nomor di gutter kiri",
                    onClick  = { doToggle { scope.launch { themePrefs.setShowLineNumbers(!showNums) } } },
                    end      = {
                        Switch(
                            checked         = showNums,
                            onCheckedChange = { v -> doToggle { scope.launch { themePrefs.setShowLineNumbers(v) } } },
                        )
                    },
                )
            }
            item { PrefDivider() }
            item {
                PrefItem(
                    icon     = Icons.AutoMirrored.Outlined.WrapText,
                    title    = "Word Wrap",
                    subtitle = "Bungkus baris yang terlalu panjang",
                    onClick  = { doToggle { scope.launch { themePrefs.setWrapLines(!wrapLines) } } },
                    end      = {
                        Switch(
                            checked         = wrapLines,
                            onCheckedChange = { v -> doToggle { scope.launch { themePrefs.setWrapLines(v) } } },
                        )
                    },
                )
            }

            // ── Sistem ────────────────────────────────────────────────────────
            item { PrefGroup("Sistem") }

            item {
                val hasUpdate = updateState.updateInfo?.isNewVersion == true
                PrefItem(
                    icon     = if (hasUpdate) Icons.Outlined.SystemUpdate else Icons.Outlined.Refresh,
                    title    = "Pembaruan",
                    subtitle = when {
                        updateState.isChecking          -> "Memeriksa…"
                        hasUpdate                       -> "v${updateState.updateInfo!!.latestVersion} tersedia"
                        updateState.updateInfo != null  -> "Sudah terbaru · v${BuildConfig.VERSION_NAME}"
                        else                            -> "v${BuildConfig.VERSION_NAME}"
                    },
                    onClick  = {
                        if (!isPremium && AdsManager.interstitialReady.value)
                            onShowInterstitial { homeVm.updateVm.checkForUpdate(force = true) }
                        else
                            homeVm.updateVm.checkForUpdate(force = true)
                    },
                    end      = {
                        AnimatedContent(
                            targetState    = updateState.isChecking to hasUpdate,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label          = "updateEnd",
                        ) { (checking, update) ->
                            when {
                                checking -> CircularProgressIndicator(
                                    modifier    = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                update -> Badge(
                                    containerColor = MaterialTheme.colorScheme.error,
                                ) {
                                    Text("Baru")
                                }
                                else -> NavArrow()
                            }
                        }
                    },
                )
            }

            // ── Footer versi ──────────────────────────────────────────────────
            item {
                Box(
                    modifier          = Modifier.fillMaxWidth().padding(top = 24.dp),
                    contentAlignment  = Alignment.Center,
                ) {
                    Text(
                        "LogLog v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Primitives
// ═════════════════════════════════════════════════════════════════════════════

// ── Group header ─────────────────────────────────────────────────────────────

@Composable
private fun PrefGroup(title: String) {
    Text(
        text     = title,
        style    = MaterialTheme.typography.labelMedium,
        color    = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

// ── Single row ───────────────────────────────────────────────────────────────

@Composable
private fun PrefItem(
    icon     : ImageVector,
    title    : String,
    subtitle : String,
    onClick  : () -> Unit,
    end      : @Composable () -> Unit = { NavArrow() },
) {
    Surface(
        onClick = onClick,
        color   = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Icon — kecil, inline, tidak di-box
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color      = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    subtitle,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            end()
        }
    }
}

// ── Divider ───────────────────────────────────────────────────────────────────

@Composable
private fun PrefDivider() {
    HorizontalDivider(
        modifier  = Modifier.padding(start = 52.dp),
        thickness = 0.4.dp,
        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

// ── Nav arrow ─────────────────────────────────────────────────────────────────

@Composable
private fun NavArrow() {
    Icon(
        Icons.Outlined.ChevronRight, null,
        modifier = Modifier.size(18.dp),
        tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
    )
}

// ═════════════════════════════════════════════════════════════════════════════
// No Ads row — kompak, tidak berlebihan
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun NoAdsRow(
    isActive        : Boolean,
    remainingMs     : Long,
    remainingClaims : Int,
    canWatch        : Boolean,
    rewardedReady   : Boolean,
    onWatch         : () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Baris atas — ikon + teks + status
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector        = if (isActive) Icons.Outlined.Block else Icons.Outlined.OndemandVideo,
                contentDescription = null,
                modifier           = Modifier.size(20.dp),
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isActive) "No Ads Aktif" else "Tonton Iklan untuk No Ads",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color      = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    when {
                        isActive      -> "Iklan dinonaktifkan sementara"
                        !canWatch     -> "Batas harian tercapai"
                        !rewardedReady -> "Memuat iklan…"
                        else          -> "Tonton 1 iklan · bebas iklan 5 menit"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Status kanan
            if (isActive) {
                Text(
                    formatCountdown(remainingMs),
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    "$remainingClaims sisa",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Progress bar saat aktif
        if (isActive) {
            val maxMs    = 5L * 60_000L
            val progress = (remainingMs.toFloat() / maxMs).coerceIn(0f, 1f)
            val animProgress by animateFloatAsState(
                targetValue   = progress,
                animationSpec = tween(600, easing = EaseInOutCubic),
                label         = "noAdsProgress",
            )
            LinearProgressIndicator(
                progress      = { animProgress },
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(start = 36.dp)
                    .height(2.dp),
                color         = MaterialTheme.colorScheme.primary,
                trackColor    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            )
        }

        // Tombol tonton — hanya saat belum aktif
        if (!isActive) {
            FilledTonalButton(
                onClick  = onWatch,
                enabled  = canWatch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 36.dp)
                    .height(36.dp),
                shape    = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Icon(Icons.Outlined.PlayCircleOutline, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (canWatch) "Tonton Sekarang" else "Limit Tercapai",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Helpers
// ═════════════════════════════════════════════════════════════════════════════

private fun formatCountdown(ms: Long): String {
    val s = (ms / 1_000L).coerceAtLeast(0L)
    return "%02d:%02d".format(s / 60, s % 60)
}
