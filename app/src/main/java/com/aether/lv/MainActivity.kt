package com.aether.lv

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.aether.lv.data.preferences.ThemePreferences
import com.aether.lv.ui.LogLogApp
import com.aether.lv.ui.theme.LogLogTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private var externalFileUri: Uri? = null

    // ── Permission launcher ──────────────────────────────────────────────────
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Lanjut apapun hasilnya — SAF tetap bisa dipakai tanpa permission
        startUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Tangkap URI dari intent eksternal (open with)
        externalFileUri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            else               -> null
        }

        // Tahan splash sampai theme dimuat
        var splashDone = false
        splash.setKeepOnScreenCondition { !splashDone }

        // Request permission dulu, baru tampilkan UI
        val needed = permissionsNeeded()
        if (needed.isEmpty()) {
            splashDone = true
            startUi()
        } else {
            permissionLauncher.launch(needed)
            splashDone = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) {
            externalFileUri = intent.data
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun permissionsNeeded(): Array<String> {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            // Android 11-12
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }

    private fun startUi() {
        val themePrefs = ThemePreferences(this)
        val isDark = runBlocking { themePrefs.isDarkMode.first() }

        setContent {
            LogLogTheme(darkTheme = isDark) {
                LogLogApp(
                    externalFileUri = externalFileUri,
                    themePrefs      = themePrefs
                )
            }
        }
    }
}
