package com.aether.lv.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Centralized storage permission logic.
 *
 * Tiga skenario berdasarkan API level:
 *
 * | API  | Android | Cara akses file log                          | Permission dibutuhkan          |
 * |------|---------|----------------------------------------------|--------------------------------|
 * | ≤28  | ≤9      | Direct path / SAF                            | READ + WRITE_EXTERNAL_STORAGE  |
 * | 29   | 10      | SAF (scoped) atau requestLegacyExternalStorage | READ_EXTERNAL_STORAGE (legacy) |
 * | 30-32| 11-12   | SAF wajib, atau MANAGE_EXTERNAL_STORAGE      | READ_EXTERNAL_STORAGE          |
 * | ≥33  | 13+     | SAF wajib, READ_MEDIA_* untuk non-SAF        | READ_MEDIA_IMAGES (partial)    |
 *
 * App ini pakai SAF (OpenDocument) sebagai primary flow — permission sebenarnya
 * tidak wajib untuk SAF. Tapi untuk membuka ulang URI dari riwayat (persistable URI),
 * atau untuk path langsung, permission tetap dibutuhkan.
 */
object PermissionManager {

    /**
     * Daftar permission yang perlu di-request berdasarkan API level saat ini.
     */
    fun requiredPermissions(): List<String> = buildList {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+: READ_MEDIA_IMAGES mencakup file non-media via SAF
                // READ_EXTERNAL_STORAGE deprecated dan tidak berlaku di sini
                add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // Android 11–12: READ_EXTERNAL_STORAGE
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            else -> {
                // Android ≤10: READ + WRITE
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    /**
     * Cek apakah permission storage sudah di-grant.
     *
     * Catatan penting:
     * - Untuk SAF (ACTION_OPEN_DOCUMENT) flow: runtime permission TIDAK wajib.
     *   Yang penting adalah persistent URI permission via takePersistableUriPermission.
     * - Untuk path langsung (file://): READ_EXTERNAL_STORAGE / READ_MEDIA_* diperlukan.
     * - MANAGE_EXTERNAL_STORAGE memberi akses penuh ke semua file di Android 11+.
     */
    fun hasStoragePermission(context: Context): Boolean {
        // Android 11+: MANAGE_EXTERNAL_STORAGE memberi akses penuh
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) return true
        }
        // Fallback: cek semua runtime permissions yang relevan untuk API level ini
        return requiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Cek apakah READ permission dasar sudah granted.
     * Untuk SAF flow: ini opsional. Untuk path langsung: wajib.
     * Return true jika SALAH SATU permission tersedia (berbeda dengan hasStoragePermission
     * yang perlu SEMUA permission).
     */
    fun hasBasicReadPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) return true
        }
        return requiredPermissions().any { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Intent ke Settings > Special App Access > All Files Access untuk Android 11+.
     * Digunakan sebagai fallback jika runtime permission tidak cukup.
     */
    fun manageStorageSettingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        } catch (_: Exception) {
            // Fallback ke halaman umum jika deep link tidak tersedia
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }
    }

    /**
     * Intent ke App Settings untuk buka permission page secara manual.
     */
    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
}
