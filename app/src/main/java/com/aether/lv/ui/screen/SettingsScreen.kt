package com.aether.lv.ui.screen

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.ArrowBack
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
    val isDark        by themePrefs.isDarkMode.collectAsState(initial = false)
    val isDynamic     by themePrefs.isDynamicColor.collectAsState(initial = true)
    val wrapLines     by themePrefs.isWrapLines.collectAsState(initial = false)
    val showNums      by themePrefs.showLineNumbers.collectAsState(initial = true)
    val showColors    by themePrefs.showLogColors.collectAsState(initial = true)
    val updateState   by homeVm.updateVm.state.collectAsStateWithLifecycle()
    val licenseState  by homeVm.licenseVm.licenseState.collectAsStateWithLifecycle()
    val noAdsState    by RewardedNoAdsManager.state.collectAsStateWithLifecycle()
    val rewardedReady by AdsManager.rewardedReady.collectAsStateWithLifecycle()
    val scope         = rememberCoroutineScope()
    val context       = LocalContext.current

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
            modifier       = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── No Ads & Lisensi ──────────────────────────────────────────────
            item {
                SectionLabel("No Ads & Lisensi")
            }
            item {
                SettingsCard {
                    if (isPremium) {
                        SettingsRow(
                            icon       = Icons.Outlined.VerifiedUser,
                            iconColor  = Color(0xFF4CAF50),
                            title      = "Lisensi Premium",
                            subtitle   = if (licenseState.productName.isNotBlank())
                                             licenseState.productName
                                         else "Aktif",
                            onClick    = onOpenLicenseFromSettings,
                            end        = { NavArrow() },
                        )
                    } else {
                        NoAdsCard(
                            isActive        = isNoAdsActive,
                            remainingMs     = remainingMs,
                            remainingClaims = noAdsState.remainingClaims,
                            canWatch        = noAdsState.canWatchRewarded,
                            rewardedReady   = rewardedReady,
                            onWatch         = {
                                if (!rewardedReady)
                                    Toast.makeText(context, "Iklan belum tersedia", Toast.LENGTH_SHORT).show()
                                else onShowRewarded()
                            },
                        )
                        RowDivider()
                        SettingsRow(
                            icon      = Icons.Outlined.CardMembership,
                            iconColor = Color(0xFF9C27B0),
                            title     = "Aktifkan Lisensi",
                            subtitle  = "Masukkan kode untuk akses premium",
                            onClick   = onOpenLicenseFromSettings,
                            end       = { NavArrow() },
                        )
                    }
                }
            }

            // ── Tampilan ──────────────────────────────────────────────────────
            item { SectionLabel("Tampilan") }
            item {
                SettingsCard {
                    SettingsRow(
                        icon      = if (isDark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                        iconColor = if (isDark) Color(0xFF5C6BC0) else Color(0xFFF9A825),
                        title     = "Mode Gelap",
                        subtitle  = if (isDark) "Aktif" else "Nonaktif",
                        onClick   = { doToggle { scope.launch { themePrefs.setDarkMode(!isDark) } } },
                        end       = {
                            Switch(
                                checked         = isDark,
                                onCheckedChange = { v -> doToggle { scope.launch { themePrefs.setDarkMode(v) } } },
                            )
                        },
                    )
                    RowDivider()
                    SettingsRow(
                        icon      = Icons.Outlined.AutoAwesome,
                        iconColor = Color(0xFF00897B),
                        title     = "Material You",
                        subtitle  = "Warna dari wallpaper · Android 12+",
                        onClick   = { doToggle { scope.launch { themePrefs.setDynamicColor(!isDynamic) } } },
                        end       = {
                            Switch(
                                checked         = isDynamic,
                                onCheckedChange = { v -> doToggle { scope.launch { themePrefs.setDynamicColor(v) } } },
                            )
                        },
                    )
                }
            }

            // ── Viewer Log ────────────────────────────────────────────────────
            item { SectionLabel("Viewer Log") }
            item {
                SettingsCard {
                    SettingsRow(
                        icon      = Icons.Outlined.Palette,
                        iconColor = Color(0xFFE91E63),
                        title     = "Warna Level Log",
                        subtitle  = "Debug, Info, Warn, Error berbeda warna",
                        onClick   = { doToggle { scope.launch { themePrefs.setShowLogColors(!showColors) } } },
                        end       = {
                            Switch(
                                checked         = showColors,
                                onCheckedChange = { v -> doToggle { scope.launch { themePrefs.setShowLogColors(v) } } },
                            )
                        },
                    )
                    RowDivider()
                    SettingsRow(
                        icon      = Icons.Outlined.Tag,
                        iconColor = Color(0xFF1E88E5),
                        title     = "Nomor Baris",
                        subtitle  = "Tampilkan nomor di gutter kiri",
                        onClick   = { doToggle { scope.launch { themePrefs.setShowLineNumbers(!showNums) } } },
                        end       = {
                            Switch(
                                checked         = showNums,
                                onCheckedChange = { v -> doToggle { scope.launch { themePrefs.setShowLineNumbers(v) } } },
                            )
                        },
                    )
                    RowDivider()
                    SettingsRow(
                        icon      = Icons.AutoMirrored.Outlined.WrapText,
                        iconColor = Color(0xFF43A047),
                        title     = "Word Wrap",
                        subtitle  = "Bungkus baris yang terlalu panjang",
                        onClick   = { doToggle { scope.launch { themePrefs.setWrapLines(!wrapLines) } } },
                        end       = {
                            Switch(
                                checked         = wrapLines,
                                onCheckedChange = { v -> doToggle { scope.launch { themePrefs.setWrapLines(v) } } },
                            )
                        },
                    )
                }
            }

            // ── Sistem ────────────────────────────────────────────────────────
            item { SectionLabel("Sistem") }
            item {
                val hasUpdate = updateState.updateInfo?.isNewVersion == true
                SettingsCard {
                    SettingsRow(
                        icon      = if (hasUpdate) Icons.Outlined.SystemUpdate else Icons.Outlined.Refresh,
                        iconColor = if (hasUpdate) Color(0xFFE53935) else Color(0xFF757575),
                        title     = "Pembaruan Aplikasi",
                        subtitle  = when {
                            updateState.isChecking         -> "Memeriksa…"
                            hasUpdate                      -> "v${updateState.updateInfo!!.latestVersion} tersedia — ketuk untuk pasang"
                            updateState.updateInfo != null -> "Sudah versi terbaru · v${BuildConfig.VERSION_NAME}"
                            else                           -> "v${BuildConfig.VERSION_NAME}"
                        },
                        onClick = {
                            if (!isPremium && AdsManager.interstitialReady.value)
                                onShowInterstitial { homeVm.updateVm.checkForUpdate(force = true) }
                            else
                                homeVm.updateVm.checkForUpdate(force = true)
                        },
                        end = {
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
                                    update -> Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                    ) {
                                        Text(
                                            "Baru",
                                            style    = MaterialTheme.typography.labelSmall,
                                            color    = MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                        )
                                    }
                                    else -> NavArrow()
                                }
                            }
                        },
                    )
                }
            }

            // ── Footer ────────────────────────────────────────────────────────
            item {
                Column(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        "LogLog",
                        style      = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    )
                    Text(
                        "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    )
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Layout primitives
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionLabel(title: String) {
    Text(
        text       = title,
        style      = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        letterSpacing = 0.8.sp,
        modifier   = Modifier.padding(start = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape  = RoundedCornerShape(18.dp),
        color  = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsRow(
    icon      : ImageVector,
    iconColor : Color,
    title     : String,
    subtitle  : String,
    onClick   : () -> Unit,
    end       : @Composable () -> Unit = { NavArrow() },
) {
    Surface(
        onClick  = onClick,
        color    = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Icon dengan background warna
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    modifier           = Modifier.size(19.dp),
                    tint               = iconColor,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color      = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            end()
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier  = Modifier.padding(start = 66.dp, end = 14.dp),
        thickness = 0.4.dp,
        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun NavArrow() {
    Icon(
        Icons.Outlined.ChevronRight, null,
        modifier = Modifier.size(18.dp),
        tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
    )
}

// ═════════════════════════════════════════════════════════════════════════════
// No Ads card — rework lebih bersih
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun NoAdsCard(
    isActive        : Boolean,
    remainingMs     : Long,
    remainingClaims : Int,
    canWatch        : Boolean,
    rewardedReady   : Boolean,
    onWatch         : () -> Unit,
) {
    val iconColor = if (isActive) Color(0xFF43A047) else Color(0xFFFF9800)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = if (isActive) Icons.Outlined.Block else Icons.Outlined.OndemandVideo,
                    contentDescription = null,
                    modifier           = Modifier.size(19.dp),
                    tint               = iconColor,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isActive) "No Ads Aktif" else "No Ads · Tonton Iklan",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color      = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    when {
                        isActive       -> "Bebas iklan sementara"
                        !canWatch      -> "Batas harian tercapai"
                        !rewardedReady -> "Memuat iklan…"
                        else           -> "Tonton 1 iklan · bebas iklan 5 menit"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isActive) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF43A047).copy(alpha = 0.12f),
                ) {
                    Text(
                        formatCountdown(remainingMs),
                        style      = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color      = Color(0xFF43A047),
                        modifier   = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        "$remainingClaims sisa",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // Progress bar countdown
        if (isActive) {
            val maxMs    = 5L * 60_000L
            val progress = (remainingMs.toFloat() / maxMs).coerceIn(0f, 1f)
            val animProg by animateFloatAsState(
                targetValue   = progress,
                animationSpec = tween(600, easing = EaseInOutCubic),
                label         = "noAdsProgress",
            )
            LinearProgressIndicator(
                progress   = { animProg },
                modifier   = Modifier
                    .fillMaxWidth()
                    .padding(start = 52.dp)
                    .height(3.dp)
                    .clip(CircleShape),
                color      = Color(0xFF43A047),
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
            )
        }

        // Tombol tonton
        if (!isActive) {
            Button(
                onClick  = onWatch,
                enabled  = canWatch && rewardedReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 52.dp)
                    .height(38.dp),
                shape    = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF9800),
                    contentColor   = Color.White,
                ),
            ) {
                Icon(Icons.Outlined.PlayCircleOutline, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        !canWatch      -> "Limit Tercapai"
                        !rewardedReady -> "Memuat…"
                        else           -> "Tonton Sekarang"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
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
