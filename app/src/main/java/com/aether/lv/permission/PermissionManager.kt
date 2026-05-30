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
 * PENTING: App ini menggunakan SAF (Storage Access Framework / ACTION_OPEN_DOCUMENT)
 * sebagai primary flow untuk membuka file. Untuk SAF, runtime permission TIDAK wajib
 * — sistem yang memberikan temporary URI permission secara otomatis saat user memilih file.
 *
 * Runtime permission tetap dibutuhkan untuk:
 * - Membuka file via path langsung (file://)
 * - Membaca file yang dibuka via ACTION_VIEW dari file manager tanpa SAF
 *
 * | API  | Android | Permission relevan                              |
 * |------|---------|-------------------------------------------------|
 * | ≤28  | ≤9      | READ_EXTERNAL_STORAGE + WRITE_EXTERNAL_STORAGE  |
 * | 29   | 10      | READ_EXTERNAL_STORAGE                           |
 * | 30-32| 11-12   | READ_EXTERNAL_STORAGE                           |
 * | ≥33  | 13+     | READ_MEDIA_IMAGES (SAF sudah cukup tanpa ini)   |
 */
object PermissionManager {

    /**
     * Daftar permission yang perlu di-request berdasarkan API level.
     *
     * Untuk Android 13+ (API 33+): SAF sudah bisa buka semua file tanpa permission.
     * READ_MEDIA_IMAGES hanya untuk kasus edge tertentu (path langsung).
     */
    fun requiredPermissions(): List<String> = buildList {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+: untuk SAF tidak perlu permission apapun.
                // READ_MEDIA_IMAGES sebagai fallback untuk path langsung.
                add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // Android 11–12
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            else -> {
                // Android ≤10
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    /**
     * Cek apakah permission storage sudah sufficient.
     *
     * Return true jika:
     * - MANAGE_EXTERNAL_STORAGE granted (Android 11+), ATAU
     * - Semua runtime permission dalam requiredPermissions() granted
     *
     * Untuk SAF flow: selalu return true karena SAF tidak butuh runtime permission.
     */
    fun hasStoragePermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) return true
        }
        return requiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Cek apakah minimal satu read permission tersedia.
     * Untuk SAF flow: selalu return true.
     */
    fun hasBasicReadPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) return true
        }
        // Untuk Android 13+ dengan SAF: permission tidak wajib, return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return true
        return requiredPermissions().any { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Intent ke Settings > Special App Access > All Files Access (Android 11+).
     */
    fun manageStorageSettingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        } catch (_: Exception) {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }
    }

    /**
     * Intent ke App Settings > Permissions untuk buka permission page secara manual.
     */
    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
}
