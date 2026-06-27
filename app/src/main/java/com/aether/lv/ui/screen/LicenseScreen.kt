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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

    // Aktivasi TIDAK memunculkan dialog/modal apa pun — semua feedback
    // (loading, sukses, error) ditampilkan inline di bawah field input.
    // Hanya aksi destruktif (hapus lisensi) yang tetap pakai konfirmasi ringkas,
    // ditampilkan sebagai state inline juga, bukan AlertDialog modal.
    var confirmRevoke by remember { mutableStateOf(false) }

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
                            "Lisensi",
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
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            if (licenseState.isPremium) {
                PremiumStatusSection(
                    state          = licenseState,
                    confirmRevoke  = confirmRevoke,
                    onRevokeAsk    = { confirmRevoke = true },
                    onRevokeCancel = { confirmRevoke = false },
                    onRevokeConfirm = {
                        vm.revoke()
                        confirmRevoke = false
                    }
                )
            } else {
                ActivateSection(
                    inputKey       = inputKey,
                    keyVisible     = keyVisible,
                    uiState        = uiState,
                    onKeyInput     = vm::onKeyInput,
                    onToggleVisible = vm::toggleKeyVisibility,
                    onActivate     = { focusManager.clearFocus(); vm.activate() },
                    onBuy          = { uriHandler.openUri("https://t.me/AetherDev22") },
                )
                Spacer(Modifier.height(4.dp))
                FeatureList()
            }
        }
    }
}

// ─── Belum premium: form aktivasi + promo, layout flat tanpa card berlapis ───

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
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {

        // ── Header sederhana ──────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Aktifkan Lisensi Plus",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Masukkan kode lisensi untuk menghilangkan iklan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Input field ────────────────────────────────────────────────────
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
                    imeAction      = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onActivate() }),
                trailingIcon = {
                    IconButton(onClick = onToggleVisible) {
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

            // ── Feedback inline — tanpa modal/dialog ────────────────────────
            AnimatedVisibility(
                visible = uiState !is LicenseUiState.Idle,
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically()
            ) {
                when (val s = uiState) {
                    is LicenseUiState.Error   -> InlineFeedback(s.message, isError = true)
                    is LicenseUiState.Success -> InlineFeedback(s.message, isError = false)
                    else -> {}
                }
            }

            Button(
                onClick  = onActivate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = uiState !is LicenseUiState.Loading,
                shape   = RoundedCornerShape(14.dp)
            ) {
                if (uiState is LicenseUiState.Loading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(18.dp),
                        color       = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Memverifikasi…")
                } else {
                    Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Aktifkan", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // ── Belum punya lisensi ────────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Belum punya lisensi?",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Hubungi @AetherDev22 di Telegram",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun FeatureList() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "Yang kamu dapat",
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.primary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureRow(Icons.Outlined.Block,        "Tanpa iklan interstitial & rewarded")
            FeatureRow(Icons.Outlined.Speed,        "Pengalaman lebih cepat & mulus")
            FeatureRow(Icons.Outlined.SupportAgent, "Dukungan prioritas via Telegram")
            FeatureRow(Icons.Outlined.PhoneAndroid, "Berlaku sesuai jumlah perangkat di paket")
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

// ─── Sudah premium: status + opsi hapus, flat tanpa kartu emas mencolok ──────

@Composable
private fun PremiumStatusSection(
    state           : LicenseState,
    confirmRevoke   : Boolean,
    onRevokeAsk     : () -> Unit,
    onRevokeCancel  : () -> Unit,
    onRevokeConfirm : () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {

        // ── Header status ─────────────────────────────────────────────────
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Verified, null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    "Lisensi Aktif",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    state.productName.ifBlank { state.productId },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Info rows — flat list, bukan card emas ─────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            LicenseInfoRow(Icons.Outlined.Block,        "Iklan", if (state.isNoAds) "Dinonaktifkan" else "Aktif")
            LicenseInfoRow(Icons.Outlined.CalendarToday, "Masa berlaku",
                if (state.isLifetime) "Seumur hidup" else formatExpiry(state.expiresAt))
            LicenseInfoRow(Icons.Outlined.VpnKey,        "Kunci", obfuscateKey(state.licenseKey))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // ── Hapus lisensi — konfirmasi inline, bukan AlertDialog ────────────
        AnimatedContent(
            targetState = confirmRevoke,
            label       = "revokeConfirm"
        ) { confirming ->
            if (confirming) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Hapus lisensi dari perangkat ini? Iklan akan tampil kembali.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick  = onRevokeCancel,
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(10.dp)
                        ) { Text("Batal") }
                        Button(
                            onClick  = onRevokeConfirm,
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(10.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor   = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        ) { Text("Hapus") }
                    }
                }
            } else {
                TextButton(onClick = onRevokeAsk) {
                    Icon(
                        Icons.Outlined.DeleteOutline, null,
                        modifier = Modifier.size(16.dp),
                        tint     = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Hapus Lisensi", color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
private fun LicenseInfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

// ─── Feedback inline kecil (pengganti dialog) ─────────────────────────────────

@Composable
private fun InlineFeedback(message: String, isError: Boolean) {
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val icon  = if (isError) Icons.Outlined.ErrorOutline else Icons.Rounded.CheckCircle
    Row(
        modifier              = Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = color)
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
