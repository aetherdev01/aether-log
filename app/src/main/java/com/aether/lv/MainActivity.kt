package com.aether.lv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.aether.lv.data.preferences.ThemePreferences
import com.aether.lv.ui.LogLogApp
import com.aether.lv.ui.theme.LogLogTheme

class MainActivity : ComponentActivity() {

    private var externalFileUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Tangkap URI dari intent eksternal (open with)
        externalFileUri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            else               -> null
        }

        val themePrefs = ThemePreferences(this)

        setContent {
            // Observe theme prefs secara reaktif — settings langsung berpengaruh
            val isDark    by themePrefs.isDarkMode.collectAsState(initial = false)
            val isDynamic by themePrefs.isDynamicColor.collectAsState(initial = true)

            LogLogTheme(darkTheme = isDark, dynamicColor = isDynamic) {
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
