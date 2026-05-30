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
     * [force] = true untuk trigger dari UI (misal tombol di Settings).
     */
    fun requestStoragePermissionIfNeeded(force: Boolean = false) {
        if (PermissionManager.hasStoragePermission(this) && !force) return

        val perms = PermissionManager.requiredPermissions()

        // Cek apakah perlu show rationale (user sudah pernah deny sebelumnya)
        val shouldShowRationale = perms.any { perm ->
            shouldShowRequestPermissionRationale(perm)
        }

        when {
            // Android 11+: MANAGE_EXTERNAL_STORAGE tidak bisa di-request via dialog biasa
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !android.os.Environment.isExternalStorageManager() &&
            force -> {
                showManageStorageDialog = true
            }
            // User sebelumnya deny → show rationale dulu
            shouldShowRationale -> {
                showPermissionDialog = true
            }
            // Langsung request (pertama kali atau force tanpa rationale)
            else -> {
                requestPermissionLauncher.launch(perms.toTypedArray())
            }
        }
    }
}
