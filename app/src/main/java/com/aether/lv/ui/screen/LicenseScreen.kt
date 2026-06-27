package com.aether.lv.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.lv.license.LicenseState
import com.aether.lv.license.LicenseUiState
import com.aether.lv.license.LicenseViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseScreen(
    onBack : () -> Unit,
    vm     : LicenseViewModel = viewModel()
) {
    val licenseState by vm.licenseState.collectAsStateWithLifecycle()
    val uiState      by vm.uiState.collectAsStateWithLifecycle()
    val inputKey     by vm.inputKey.collectAsStateWithLifecycle()
    val keyVisible   by vm.keyVisible.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val uriHandler   = LocalUriHandler.current

    var showRevokeDialog by remember { mutableStateOf(false) }

    if (showRevokeDialog) {
        AlertDialog(
            onDismissRequest = { showRevokeDialog = false },
            title  = { Text("Hapus Lisensi?") },
            text   = { Text("Lisensi akan dihapus dari perangkat ini dan iklan akan tampil kembali.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.revoke()
                    showRevokeDialog = false
                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeDialog = false }) { Text("Batal") }
            }
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Kembali")
                        }
                    },
                    title = {
                        Text(
                            "Lisensi Premium",
                            fontWeight = FontWeight.SemiBold,
                            style      = MaterialTheme.typography.titleLarge
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Hero card ─────────────────────────────────────────────────────
            AnimatedContent(
                targetState = licenseState.isPremium,
                transitionSpec = {
                    fadeIn(tween(400)) togetherWith fadeOut(tween(200))
                },
                label = "premium_card"
            ) { isPremium ->
                if (isPremium) {
                    PremiumActiveCard(
                        state             = licenseState,
                        onRevokeRequest   = { showRevokeDialog = true }
                    )
                } else {
                    PremiumPromoCard()
                }
            }

            // ── Activate form (hanya saat belum premium) ──────────────────────
            AnimatedVisibility(
                visible = !licenseState.isPremium,
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    Text(
                        "Masukkan Kode Lisensi",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedTextField(
                        value         = inputKey,
                        onValueChange = vm::onKeyInput,
                        modifier      = Modifier.fillMaxWidth(),
                        label         = { Text("Kode Lisensi") },
                        placeholder   = { Text("Contoh: AETHER-XXXX-XXXX") },
                        singleLine    = true,
                        visualTransformation = if (keyVisible) VisualTransformation.None
                                               else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction      = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus(); vm.activate() }
                        ),
                        trailingIcon = {
                            IconButton(onClick = vm::toggleKeyVisibility) {
                                Icon(
                                    imageVector = if (keyVisible) Icons.Outlined.VisibilityOff
                                                  else Icons.Outlined.Visibility,
                                    contentDescription = if (keyVisible) "Sembunyikan" else "Tampilkan"
                                )
                            }
                        },
                        isError = uiState is LicenseUiState.Error,
                        shape   = RoundedCornerShape(14.dp)
                    )

                    // ── Status feedback ────────────────────────────────────────
                    AnimatedVisibility(visible = uiState !is LicenseUiState.Idle) {
                        when (val s = uiState) {
                            is LicenseUiState.Error -> FeedbackCard(
                                message = s.message,
                                isError = true
                            )
                            is LicenseUiState.Success -> FeedbackCard(
                                message = s.message,
                                isError = false
                            )
                            else -> {}
                        }
                    }

                    // ── Activate button ────────────────────────────────────────
                    Button(
                        onClick  = { focusManager.clearFocus(); vm.activate() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = uiState !is LicenseUiState.Loading,
                        shape   = RoundedCornerShape(14.dp)
                    ) {
                        if (uiState is LicenseUiState.Loading) {
                            CircularProgressIndicator(
                                modifier  = Modifier.size(20.dp),
                                color     = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Memverifikasi...")
                        } else {
                            Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Aktifkan Lisensi", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // ── Beli lisensi ──────────────────────────────────────────
                    OutlinedButton(
                        onClick  = { uriHandler.openUri("https://t.me/AetherDev22") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Outlined.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Beli Lisensi — Rp 15.000")
                    }
                }
            }

            // ── Feature list ─────────────────────────────────────────────────
            FeatureListCard()

            // ── Info footer ───────────────────────────────────────────────────
            InfoCard()

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─── Premium aktif card ───────────────────────────────────────────────────────
@Composable
private fun PremiumActiveCard(
    state           : LicenseState,
    onRevokeRequest : () -> Unit
) {
    val goldGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFFB8860B),
            Color(0xFFFFD700),
            Color(0xFFDAA520),
        )
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    // Gold crown badge
                    Box(
                        modifier          = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment  = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Star,
                            contentDescription = null,
                            tint     = Color(0xFFFFD700),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            "Premium Aktif",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            state.productName.ifBlank { state.productId },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
                // Verified chip
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Verified,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "Valid",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            HorizontalDivider(
                color     = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                thickness = 0.5.dp
            )

            // Info rows
            LicenseInfoRow(
                icon  = Icons.Outlined.Block,
                label = "Iklan",
                value = if (state.isNoAds) "Dinonaktifkan ✓" else "Aktif"
            )
            LicenseInfoRow(
                icon  = Icons.Outlined.CalendarToday,
                label = "Masa berlaku",
                value = if (state.isLifetime) "Seumur hidup ♾"
                        else formatExpiry(state.expiresAt)
            )
            LicenseInfoRow(
                icon  = Icons.Outlined.VpnKey,
                label = "Kunci",
                value = obfuscateKey(state.licenseKey)
            )

            Spacer(Modifier.height(4.dp))

            // Revoke button
            TextButton(
                onClick = onRevokeRequest,
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint     = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Hapus Lisensi",
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

// ─── Promo card (belum premium) ───────────────────────────────────────────────
@Composable
private fun PremiumPromoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Rounded.WorkspacePremium,
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                "Upgrade ke Premium",
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign  = TextAlign.Center
            )
            Text(
                "Hilangkan semua iklan selamanya\nhanya dengan Rp 15.000",
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── Feature list ─────────────────────────────────────────────────────────────
@Composable
private fun FeatureListCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Yang kamu dapat:",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            FeatureRow(Icons.Outlined.Block,          "Tanpa iklan interstitial & rewarded")
            FeatureRow(Icons.Outlined.Speed,          "Pengalaman lebih cepat & mulus")
            FeatureRow(Icons.Outlined.SupportAgent,   "Dukungan prioritas via Telegram")
            FeatureRow(Icons.Outlined.PhoneAndroid,   "Berlaku sesuai jumlah perangkat di paket")
        }
    }
}

// ─── Info card ────────────────────────────────────────────────────────────────
@Composable
private fun InfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.Top
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp).padding(top = 2.dp)
            )
            Text(
                "Hubungi @AetherDev22 di Telegram untuk membeli lisensi. " +
                "Setelah pembayaran dikonfirmasi, kode lisensi dikirim otomatis.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Reusable composables ─────────────────────────────────────────────────────
@Composable
private fun LicenseInfoRow(
    icon  : androidx.compose.ui.graphics.vector.ImageVector,
    label : String,
    value : String
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
        Text(
            value,
            style      = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun FeatureRow(
    icon  : androidx.compose.ui.graphics.vector.ImageVector,
    label : String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint     = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FeedbackCard(message: String, isError: Boolean) {
    val (bgColor, textColor, icon) = if (isError) {
        Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Outlined.ErrorOutline
        )
    } else {
        Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Rounded.CheckCircle
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        color    = bgColor
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(18.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = textColor)
        }
    }
}

// ─── Utils ────────────────────────────────────────────────────────────────────
private fun formatExpiry(epochMs: Long): String {
    if (epochMs == 0L) return "Seumur hidup"
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
    return sdf.format(Date(epochMs))
}

private fun obfuscateKey(key: String): String {
    if (key.length <= 8) return key
    return key.take(4) + "•".repeat(key.length - 8) + key.takeLast(4)
}
