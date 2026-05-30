package com.aether.lv.permission

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Dialog minta izin storage.
 *
 * Ada dua mode:
 * - [showManageStorage] = false → minta runtime permission biasa (READ_EXTERNAL_STORAGE / READ_MEDIA_*)
 * - [showManageStorage] = true  → arahkan ke Settings > All Files Access (Android 11+)
 *   karena MANAGE_EXTERNAL_STORAGE tidak bisa di-request via runtime dialog biasa
 */
@Composable
fun PermissionRationaleDialog(
    showManageStorage : Boolean = false,
    onRequestPermission: () -> Unit,
    onOpenSettings    : () -> Unit,
    onDismiss         : () -> Unit
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Text(
                    "Izin Akses Storage",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    if (showManageStorage) {
                        "Untuk membuka file log dari semua lokasi, LogLog membutuhkan izin " +
                        "\"Akses ke semua file\" (All Files Access).\n\n" +
                        "Buka Pengaturan → Izin Aplikasi → Akses ke semua file → aktifkan LogLog."
                    } else {
                        "LogLog membutuhkan izin baca storage untuk membuka file log " +
                        "dari riwayat dan path eksternal.\n\n" +
                        "Izin ini hanya digunakan untuk membaca file — tidak ada file yang ditulis atau diunggah."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Tunjukkan info API level jika relevan
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !showManageStorage) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            "Android ${Build.VERSION.RELEASE}: Izin storage dibatasi. " +
                            "File via tombol \"Buka File\" tetap bisa diakses tanpa izin ini.",
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

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
                            Text("Izinkan")
                        }
                    }
                }
            }
        }
    }
}
