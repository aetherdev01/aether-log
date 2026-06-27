package com.aether.lv.permission

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Dialog minta izin storage.
 *
 * - [showManageStorage] = false → runtime permission biasa
 * - [showManageStorage] = true  → arahkan ke Settings (MANAGE_EXTERNAL_STORAGE, Android 11+)
 */
@Composable
fun PermissionRationaleDialog(
    showManageStorage   : Boolean = false,
    onRequestPermission : () -> Unit,
    onOpenSettings      : () -> Unit,
    onDismiss           : () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier       = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape          = RoundedCornerShape(28.dp),
            color          = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier            = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Icon
                Surface(
                    shape    = RoundedCornerShape(20.dp),
                    color    = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (showManageStorage) Icons.Outlined.AdminPanelSettings
                            else Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.primary,
                            modifier           = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    if (showManageStorage) "Akses Semua File" else "Izin Storage",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    if (showManageStorage)
                        "Untuk membuka file dari semua lokasi, LogLog memerlukan izin \"Akses ke semua file\"."
                    else
                        "LogLog memerlukan izin baca storage untuk membuka file dari riwayat dan path eksternal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                // Info box
                Spacer(Modifier.height(12.dp))

                if (showManageStorage) {
                    // Langkah-langkah untuk MANAGE_EXTERNAL_STORAGE
                    StepInfoBox {
                        StepRow(step = "1", text = "Ketuk \"Buka Pengaturan\" di bawah")
                        StepRow(step = "2", text = "Pilih \"Izin Aplikasi\"")
                        StepRow(step = "3", text = "Aktifkan \"Akses ke semua file\"")
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    InfoBox(
                        icon    = Icons.Outlined.Info,
                        message = "Android ${Build.VERSION.RELEASE}: File yang dibuka lewat tombol \"Buka File\" tetap dapat diakses tanpa izin ini."
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Tombol
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick  = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Nanti")
                    }

                    Button(
                        onClick  = if (showManageStorage) onOpenSettings else onRequestPermission,
                        modifier = Modifier.weight(2f)
                    ) {
                        if (showManageStorage) {
                            Icon(Icons.Outlined.Settings, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Buka Pengaturan")
                        } else {
                            Icon(Icons.Outlined.Check, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Izinkan")
                        }
                    }
                }
            }
        }
    }
}

// ── Helper composables ────────────────────────────────────────────────────────

@Composable
private fun StepInfoBox(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun StepRow(step: String, text: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape    = RoundedCornerShape(6.dp),
            color    = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    step,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun InfoBox(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment     = Alignment.Top
    ) {
        Icon(
            icon, null,
            modifier = Modifier
                .size(14.dp)
                .padding(top = 1.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Text(
            message,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
