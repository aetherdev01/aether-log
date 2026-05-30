package com.aether.lv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.aether.lv.ui.LogLogApp
import com.aether.lv.ui.theme.LogLogTheme
import com.aether.lv.data.preferences.ThemePreferences
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var externalFileUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Tangkap URI dari intent eksternal (open with)
        externalFileUri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            else               -> null
        }

        val themePrefs = ThemePreferences(this)
        var isDark = false
        var splash_done = false

        lifecycleScope.launch {
            isDark = themePrefs.isDarkMode.first()
            splash_done = true
        }

        splash.setKeepOnScreenCondition { !splash_done }

        setContent {
            LogLogTheme(darkTheme = isDark) {
                LogLogApp(
                    externalFileUri = externalFileUri,
                    themePrefs      = themePrefs
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) {
            externalFileUri = intent.data
        }
    }
}
