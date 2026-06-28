package com.aether.lv.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.lv.license.LicenseState
import com.aether.lv.license.LicenseUiState
import com.aether.lv.license.LicenseViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// ═════════════════════════════════════════════════════════════════════════════
// Screen
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseScreen(
    onBack : () -> Unit,
    vm     : LicenseViewModel = viewModel(),
) {
    val licenseState by vm.licenseState.collectAsStateWithLifecycle()
    val uiState      by vm.uiState.collectAsStateWithLifecycle()
    val inputKey     by vm.inputKey.collectAsStateWithLifecycle()
    val keyVisible   by vm.keyVisible.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val uriHandler   = LocalUriHandler.current

    var confirmRevoke by remember { mutableStateOf(false) }

    // Real-time ticker untuk countdown masa berlaku
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(1_000); nowMs = System.currentTimeMillis() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "Kembali")
                    }
                },
                title = { Text("Lisensi", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (licenseState.isPremium) {
                ActiveLicenseSection(
                    state          = licenseState,
                    nowMs          = nowMs,
                    confirmRevoke  = confirmRevoke,
                    onRevokeAsk    = { confirmRevoke = true },
                    onRevokeCancel = { confirmRevoke = false },
                    onRevokeConfirm = { vm.revoke(); confirmRevoke = false },
                )
            } else {
                ActivateSection(
                    inputKey        = inputKey,
                    keyVisible      = keyVisible,
                    uiState         = uiState,
                    onKeyInput      = vm::onKeyInput,
                    onToggleVisible = vm::toggleKeyVisibility,
                    onActivate      = { focusManager.clearFocus(); vm.activate() },
                    onBuy           = { uriHandler.openUri("https://t.me/AetherDev22") },
                )
                BenefitSection()
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Aktif — kartu status + detail + hapus
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun ActiveLicenseSection(
    state           : LicenseState,
    nowMs           : Long,
    confirmRevoke   : Boolean,
    onRevokeAsk     : () -> Unit,
    onRevokeCancel  : () -> Unit,
    onRevokeConfirm : () -> Unit,
) {
    // ── Status card ───────────────────────────────────────────────────────────
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    Icons.Rounded.Verified,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Lisensi Aktif",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        state.productName.ifBlank { state.productId },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Badge status
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        "Premium",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            HorizontalDivider(
                color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.5.dp,
            )

            // ── Countdown masa berlaku ─────────────────────────────────────
            if (!state.isLifetime && state.expiresAt > 0L) {
                val remainMs     = (state.expiresAt - nowMs).coerceAtLeast(0L)
                val totalDays    = TimeUnit.MILLISECONDS.toDays(state.expiresAt - nowMs).coerceAtLeast(0L)
                val hours        = TimeUnit.MILLISECONDS.toHours(remainMs) % 24
                val minutes      = TimeUnit.MILLISECONDS.toMinutes(remainMs) % 60
                val isNearExpiry = totalDays <= 7

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Timer,
                                null,
                                modifier = Modifier.size(15.dp),
                                tint     = if (isNearExpiry)
                                               MaterialTheme.colorScheme.error
                                           else
                                               MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Masa berlaku",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            formatExpiry(state.expiresAt),
                            style      = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = if (isNearExpiry)
                                             MaterialTheme.colorScheme.error
                                         else
                                             MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    // Countdown digital
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isNearExpiry)
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                else
                                    MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Row(
                            modifier              = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            CountdownUnit(totalDays.toString().padStart(2, '0'), "HARI")
                            CountdownSep()
                            CountdownUnit(hours.toString().padStart(2, '0'), "JAM")
                            CountdownSep()
                            CountdownUnit(minutes.toString().padStart(2, '0'), "MENIT")
                        }
                    }

                    if (isNearExpiry) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Warning,
                                null,
                                modifier = Modifier.size(13.dp),
                                tint     = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                "Lisensi akan segera berakhir",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            } else {
                // Lifetime
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.AllInclusive, null,
                        modifier = Modifier.size(15.dp),
                        tint     = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Berlaku seumur hidup",
                        style  = MaterialTheme.typography.bodySmall,
                        color  = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }

    // ── Detail rows ───────────────────────────────────────────────────────────
    Column(
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        DetailRow(
            icon  = Icons.Outlined.Block,
            label = "Iklan",
            value = if (state.isNoAds) "Dinonaktifkan" else "Aktif",
            valueColor = if (state.isNoAds) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        RowDivider()
        DetailRow(
            icon  = Icons.Outlined.Devices,
            label = "Perangkat",
            value = "1 perangkat (eksklusif)",
        )
        RowDivider()
        DetailRow(
            icon  = Icons.Outlined.VpnKey,
            label = "Kunci",
            value = obfuscateKey(state.licenseKey),
            mono  = true,
        )
        RowDivider()
        DetailRow(
            icon  = Icons.Outlined.Schedule,
            label = "Verifikasi terakhir",
            value = formatVerified(state.lastVerifiedAt),
        )
    }

    // ── Hapus lisensi ─────────────────────────────────────────────────────────
    AnimatedContent(
        targetState    = confirmRevoke,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label          = "revokeState",
    ) { confirming ->
        if (confirming) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Outlined.Warning, null,
                            modifier = Modifier.size(16.dp).padding(top = 1.dp),
                            tint     = MaterialTheme.colorScheme.error,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Hapus lisensi?",
                                style      = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color      = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                "Lisensi ini terikat ke 1 perangkat. Setelah dihapus, iklan akan aktif kembali dan slot perangkat ini dibebaskan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick  = onRevokeCancel,
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(10.dp),
                        ) { Text("Batal") }
                        Button(
                            onClick  = onRevokeConfirm,
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(10.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor   = MaterialTheme.colorScheme.onError,
                            ),
                        ) { Text("Hapus", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        } else {
            TextButton(
                onClick = onRevokeAsk,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline, null,
                    modifier = Modifier.size(15.dp),
                    tint     = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Hapus Lisensi dari Perangkat Ini",
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

// ── Countdown unit widget ─────────────────────────────────────────────────────

@Composable
private fun CountdownUnit(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color      = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CountdownSep() {
    Text(
        ":",
        style      = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        modifier   = Modifier.padding(start = 10.dp, top = 0.dp, end = 10.dp, bottom = 14.dp),
    )
}

// ── Detail row ────────────────────────────────────────────────────────────────

@Composable
private fun DetailRow(
    icon       : ImageVector,
    label      : String,
    value      : String,
    valueColor : Color = Color.Unspecified,
    mono       : Boolean = false,
) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Icon(
                icon, null,
                modifier = Modifier.size(15.dp),
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            value,
            style      = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            color      = if (valueColor != Color.Unspecified)
                             valueColor
                         else
                             MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier  = Modifier.padding(start = 40.dp),
        thickness = 0.4.dp,
        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

// ═════════════════════════════════════════════════════════════════════════════
// Belum aktif — form aktivasi
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun ActivateSection(
    inputKey        : String,
    keyVisible      : Boolean,
    uiState         : LicenseUiState,
    onKeyInput      : (String) -> Unit,
    onToggleVisible : () -> Unit,
    onActivate      : () -> Unit,
    onBuy           : () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // Header
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Aktifkan Lisensi",
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Masukkan kode lisensi untuk menghilangkan iklan secara permanen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Info 1 device
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.PhonelinkLock, null,
                    modifier = Modifier.size(15.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Satu kode lisensi hanya dapat digunakan pada 1 perangkat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Input
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value         = inputKey,
                onValueChange = onKeyInput,
                modifier      = Modifier.fillMaxWidth(),
                label         = { Text("Kode Lisensi") },
                placeholder   = { Text("") },
                singleLine    = true,
                visualTransformation = if (keyVisible) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction      = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onActivate() }),
                trailingIcon = {
                    IconButton(onClick = onToggleVisible) {
                        Icon(
                            if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (keyVisible) "Sembunyikan" else "Tampilkan",
                        )
                    }
                },
                isError = uiState is LicenseUiState.Error,
                shape   = RoundedCornerShape(12.dp),
            )

            // Feedback inline
            AnimatedVisibility(
                visible = uiState !is LicenseUiState.Idle,
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically(),
            ) {
                when (val s = uiState) {
                    is LicenseUiState.Error   -> InlineFeedback(s.message, isError = true)
                    is LicenseUiState.Success -> InlineFeedback(s.message, isError = false)
                    else -> {}
                }
            }

            Button(
                onClick  = onActivate,
                enabled  = uiState !is LicenseUiState.Loading,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(12.dp),
            ) {
                AnimatedContent(
                    targetState = uiState is LicenseUiState.Loading,
                    label       = "activateBtn",
                ) { loading ->
                    if (loading) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(17.dp),
                                color       = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                            Text("Memverifikasi…")
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Key, null, modifier = Modifier.size(17.dp))
                            Text("Aktifkan", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // Beli
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Belum punya lisensi?",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "Hubungi @AetherDev22 di Telegram",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onBuy, shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Outlined.ShoppingCart, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Beli", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Benefit list
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun BenefitSection() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Yang kamu dapat",
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.primary,
        )
        val items = listOf(
            Icons.Outlined.Block        to "Tanpa iklan interstitial & rewarded",
            Icons.Outlined.PhonelinkLock to "Terikat ke 1 perangkat — tidak bisa disalahgunakan",
            Icons.Outlined.Speed        to "Pengalaman lebih cepat dan mulus",
            Icons.Outlined.SupportAgent to "Dukungan prioritas via Telegram",
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEach { (icon, label) ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Icon(
                        icon, null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(label, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Shared composables
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun InlineFeedback(message: String, isError: Boolean) {
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val icon  = if (isError) Icons.Outlined.ErrorOutline else Icons.Rounded.CheckCircle
    Row(
        modifier              = Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Utils
// ═════════════════════════════════════════════════════════════════════════════

private fun formatExpiry(epochMs: Long): String {
    if (epochMs == 0L) return "Seumur hidup"
    return SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(Date(epochMs))
}

private fun formatVerified(epochMs: Long): String {
    if (epochMs == 0L) return "Belum pernah"
    return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(epochMs))
}

private fun obfuscateKey(key: String): String {
    if (key.length <= 8) return key
    return key.take(4) + "••••" + key.takeLast(4)
}
