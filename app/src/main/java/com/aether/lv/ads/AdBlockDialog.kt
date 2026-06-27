package com.aether.lv.ads

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aether.lv.ads.AdBlockDetector.BlockSignal
import com.aether.lv.ads.AdBlockDetector.SignalSource

/**
 * Dialog AdBlock detection — rapi, smooth, tidak bisa di-dismiss tap luar.
 *
 * Perubahan vs versi lama:
 *  - Pakai custom Dialog bukan AlertDialog agar animasi masuk bisa di-control
 *  - Enter animation: scale + fade (Material 3 style)
 *  - Icon header punya pulse animation
 *  - Tombol primary pakai animasi ripple yang lebih natural
 *  - Sinyal card lebih compact, tidak overflow
 */
@Composable
fun AdBlockDialog(
    signals   : List<BlockSignal>,
    confidence: Int,
    onDismiss : () -> Unit,
    onAllowed : () -> Unit,
) {
    // Dialog-level enter animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Dialog(
        onDismissRequest = { /* tidak bisa dismiss tap luar */ },
        properties       = DialogProperties(
            dismissOnBackPress        = false,
            dismissOnClickOutside     = false,
            usePlatformDefaultWidth  = false,
        ),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter   = scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMedium,
                ),
                initialScale  = 0.85f,
            ) + fadeIn(tween(200)),
            exit    = scaleOut(targetScale = 0.9f) + fadeOut(tween(150)),
        ) {
            Surface(
                modifier       = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape          = RoundedCornerShape(24.dp),
                color          = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
            ) {
                Column(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    // ── Animated icon header ──────────────────────────────
                    PulsingIconHeader()

                    // ── Judul & deskripsi ─────────────────────────────────
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text       = "AdBlock Terdeteksi",
                            style      = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign  = TextAlign.Center,
                        )
                        Text(
                            text  = "Sepertinya kamu menggunakan pemblokir iklan. " +
                                    "Iklan membantu kami terus mengembangkan aplikasi ini secara gratis.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp,
                        )
                    }

                    // ── Sinyal terdeteksi (jika ada) ──────────────────────
                    if (signals.isNotEmpty()) {
                        DetectionSignalCard(signals = signals, confidence = confidence)
                    }

                    // ── Panduan disable ───────────────────────────────────
                    DisableGuideCard()

                    // ── Tombol aksi ───────────────────────────────────────
                    Column(
                        modifier            = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick  = onAllowed,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape    = RoundedCornerShape(14.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation  = 0.dp,
                                pressedElevation  = 0.dp,
                            ),
                        ) {
                            Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sudah Dinonaktifkan", fontWeight = FontWeight.SemiBold)
                        }

                        TextButton(
                            onClick  = onDismiss,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape    = RoundedCornerShape(14.dp),
                        ) {
                            Text(
                                "Keluar dari Aplikasi",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Pulse animation icon ──────────────────────────────────────────────────────

@Composable
private fun PulsingIconHeader() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue   = 1f,
        targetValue    = 1.08f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "iconPulse",
    )

    Box(
        modifier         = Modifier
            .size(72.dp)
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.errorContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector        = Icons.Outlined.Block,
            contentDescription = null,
            modifier           = Modifier.size(36.dp),
            tint               = MaterialTheme.colorScheme.error,
        )
    }
}

// ── Kartu sinyal deteksi ──────────────────────────────────────────────────────

@Composable
private fun DetectionSignalCard(signals: List<BlockSignal>, confidence: Int) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        Icons.Outlined.Info, null,
                        modifier = Modifier.size(13.dp),
                        tint     = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "Terdeteksi via",
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.error,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                ) {
                    Text(
                        "$confidence% confidence",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            signals.take(3).forEach { signal -> SignalRow(signal) }

            if (signals.size > 3) {
                Text(
                    "+${signals.size - 3} sinyal lainnya",
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun SignalRow(signal: BlockSignal) {
    val (icon, label, color) = when (signal.source) {
        SignalSource.DNS_FAST_FAIL       -> Triple(Icons.Outlined.Dns,                   "Hosts file / Magisk module",       Color(0xFFFF7043))
        SignalSource.DNS_SINKHOLE        -> Triple(Icons.Outlined.Storage,               "DNS sinkhole terdeteksi",          Color(0xFFFF7043))
        SignalSource.DNS_NXDOMAIN        -> Triple(Icons.Outlined.CloudOff,              "DNS blocking aktif",               Color(0xFFFFA726))
        SignalSource.HTTP_UNREACHABLE    -> Triple(Icons.Outlined.Security,              "VPN / proxy memblokir",            Color(0xFFFFA726))
        SignalSource.HTTP_WRONG_RESPONSE -> Triple(Icons.Outlined.SettingsInputAntenna,  "Transparent proxy terdeteksi",     Color(0xFFEF5350))
        SignalSource.SDK_INIT_TIMEOUT    -> Triple(Icons.Outlined.Tv,                    "SDK iklan tidak bisa init",        Color(0xFFEF5350))
        SignalSource.SDK_LOAD_TIMEOUT    -> Triple(Icons.Outlined.BrokenImage,           "Ad unit gagal di-load",            Color(0xFFFFA726))
        SignalSource.HTTP_TIMEOUT        -> Triple(Icons.Outlined.HourglassEmpty,        "Koneksi timeout (sinyal lemah)",   Color(0xFFBDBDBD))
    }

    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier         = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, modifier = Modifier.size(12.dp), tint = color)
        }
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (signal.timingMs > 0L) {
            Text(
                "${signal.timingMs}ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
        }
    }
}

// ── Panduan cara disable AdBlock ──────────────────────────────────────────────

@Composable
private fun DisableGuideCard() {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape   = RoundedCornerShape(14.dp),
        color   = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = { expanded = !expanded },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.HelpOutline, null,
                        modifier = Modifier.size(15.dp),
                        tint     = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Cara menonaktifkan",
                        style      = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.primary,
                    )
                }
                // Animated chevron rotate
                val rotation by animateFloatAsState(
                    targetValue   = if (expanded) 180f else 0f,
                    animationSpec = tween(250, easing = FastOutSlowInEasing),
                    label         = "chevronRotate",
                )
                Icon(
                    Icons.Rounded.ExpandMore, null,
                    modifier = Modifier
                        .size(18.dp)
                        .then(Modifier /* rotation via graphicsLayer */),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter   = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(200)),
                exit    = shrinkVertically(tween(180)) + fadeOut(tween(150)),
            ) {
                Column(
                    modifier            = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GuideItem("1", "Private DNS",      "Pengaturan → Jaringan & Internet → Private DNS → Nonaktifkan")
                    GuideItem("2", "VPN aktif",        "Nonaktifkan VPN (AdGuard, Blokada, NetGuard) lalu buka ulang.")
                    GuideItem("3", "Modul Magisk/KSU", "Disable AdAway / Bindhosts / Energized dari Magisk/KSU Manager.")
                    GuideItem("4", "DNS kustom",       "Jika pakai NextDNS / AdGuard DNS, pilih profil tanpa filter.")
                }
            }
        }
    }
}

@Composable
private fun GuideItem(number: String, title: String, desc: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment     = Alignment.Top,
    ) {
        Box(
            modifier         = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number,
                style      = MaterialTheme.typography.labelSmall,
                color      = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Column {
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text(
                desc,
                style      = MaterialTheme.typography.bodySmall,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
            )
        }
    }
}
