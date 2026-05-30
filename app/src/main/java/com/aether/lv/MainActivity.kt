package com.aether.lv

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.ContextCompat
import com.aether.lv.data.preferences.ThemePreferences
import com.aether.lv.permission.PermissionManager
import com.aether.lv.permission.PermissionRationaleDialog
import com.aether.lv.ui.LogLogApp
import com.aether.lv.ui.theme.LogLogTheme

class MainActivity : ComponentActivity() {

    private var externalFileUri: Uri? = null

    // State untuk tampilkan rationale dialog dari Compose
    private var showPermissionDialog by mutableStateOf(false)
    private var showManageStorageDialog by mutableStateOf(false)

    // Launcher untuk runtime permission (READ_EXTERNAL_STORAGE / READ_MEDIA_IMAGES)
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val allGranted = results.values.all { it }
            if (!allGranted) {
                // User tolak — tunjukkan dialog arahkan ke Settings
                showPermissionDialog = false
                showManageStorageDialog = false
            }
            // Jika granted, app sudah bisa baca file. Tidak perlu action tambahan
            // karena FileRepository.readLines() akan retry otomatis saat file dibuka lagi.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        externalFileUri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            else               -> null
        }

        // Request permission saat launch (hanya jika belum granted)
        requestStoragePermissionIfNeeded()

        val themePrefs = ThemePreferences(this)

        setContent {
            val isDark    by themePrefs.isDarkMode.collectAsState(initial = false)
            val isDynamic by themePrefs.isDynamicColor.collectAsState(initial = true)

            LogLogTheme(darkTheme = isDark, dynamicColor = isDynamic) {
                LogLogApp(
                    externalFileUri = externalFileUri,
                    themePrefs      = themePrefs,
                    onRequestPermission = { requestStoragePermissionIfNeeded(force = true) }
                )

                // Dialog rationale — ditampilkan dari state Activity
                if (showPermissionDialog) {
                    PermissionRationaleDialog(
                        showManageStorage    = false,
                        onRequestPermission  = {
                            showPermissionDialog = false
                            requestPermissionLauncher.launch(
                                PermissionManager.requiredPermissions().toTypedArray()
                            )
                        },
                        onOpenSettings = {
                            showPermissionDialog = false
                            PermissionManager.appSettingsIntent(this).also { startActivity(it) }
                        },
                        onDismiss = { showPermissionDialog = false }
                    )
                }

                if (showManageStorageDialog) {
                    PermissionRationaleDialog(
                        showManageStorage  = true,
                        onRequestPermission = { /* tidak dipakai di mode ini */ },
                        onOpenSettings = {
                            showManageStorageDialog = false
                            PermissionManager.manageStorageSettingsIntent(this)
                                ?.also { startActivity(it) }
                        },
                        onDismiss = { showManageStorageDialog = false }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) {
            externalFileUri = intent.data
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check saat kembali dari Settings — kalau sudah granted, dismiss dialog
        if (PermissionManager.hasStoragePermission(this)) {
            showPermissionDialog = false
            showManageStorageDialog = false
        }
    }

    /**
     * Request storage permission sesuai API level.
     *
     * Flow yang benar:
     * 1. SAF (ACTION_OPEN_DOCUMENT) tidak butuh runtime permission — sistem yang handle.
     * 2. Untuk Android 13+: READ_MEDIA_IMAGES di-request untuk non-SAF flow.
     * 3. Untuk Android 11-12: READ_EXTERNAL_STORAGE.
     * 4. MANAGE_EXTERNAL_STORAGE hanya ditampilkan via tombol di Settings, bukan auto-prompt.
     *
     * [force] = true → dipanggil dari tombol "Izinkan Akses Storage" di ErrorState.
     */
    fun requestStoragePermissionIfNeeded(force: Boolean = false) {
        // Jika sudah punya MANAGE_EXTERNAL_STORAGE (full access), tidak perlu apa-apa
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            android.os.Environment.isExternalStorageManager()) return

        val perms = PermissionManager.requiredPermissions()

        // Cek apakah runtime permission sudah granted
        val allGranted = perms.all { perm ->
            androidx.core.content.ContextCompat.checkSelfPermission(this, perm) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (allGranted && !force) return

        val shouldShowRationale = perms.any { perm ->
            shouldShowRequestPermissionRationale(perm)
        }

        when {
            // Kalau force (dari tombol UI) dan sudah pernah deny berkali-kali
            // → arahkan ke App Settings, bukan MANAGE_EXTERNAL_STORAGE
            force && shouldShowRationale -> {
                showPermissionDialog = true
            }
            // Pertama kali atau force tanpa rationale → langsung request
            else -> {
                requestPermissionLauncher.launch(perms.toTypedArray())
            }
        }
    }
}
