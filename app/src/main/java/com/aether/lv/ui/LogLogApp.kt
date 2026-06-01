package com.aether.lv.ui

import android.net.Uri
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aether.lv.data.preferences.ThemePreferences
import com.aether.lv.ui.screen.AboutScreen
import com.aether.lv.ui.screen.HomeScreen
import com.aether.lv.ui.screen.SettingsScreen
import com.aether.lv.ui.screen.ViewerScreen

sealed class Screen(val route: String) {
    object Home     : Screen("home")
    // FIX: Hapus URI dari route argument — URI content:// tidak boleh di-serialize
    // ke string NavController karena URI permission grant akan hilang.
    object Viewer   : Screen("viewer")
    object Settings : Screen("settings")
    object About    : Screen("about")
}

@Composable
fun LogLogApp(
    externalFileUri     : Uri?,
    themePrefs          : ThemePreferences,
    onRequestPermission : () -> Unit = {},
    onShowInterstitial  : (() -> Unit) -> Unit = { it() }
) {
    val navController = rememberNavController()

    // FIX: URI disimpan di sini sebagai state Compose, bukan di-encode ke NavController.
    // URI content:// membawa URI permission grant yang melekat pada objek Uri aslinya —
    // jika di-serialize ke String lalu di-parse ulang, grant tersebut hilang sehingga
    // contentResolver.openFileDescriptor() melempar SecurityException.
    var pendingFileUri     by remember { mutableStateOf<Uri?>(null) }
    var handledExternalUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(externalFileUri) {
        if (externalFileUri != null && externalFileUri != handledExternalUri) {
            handledExternalUri = externalFileUri
            pendingFileUri = externalFileUri          // simpan URI asli
            navController.navigate(Screen.Viewer.route) {
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController    = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onOpenFile = { uri ->
                    pendingFileUri = uri              // simpan URI asli tanpa encode
                    navController.navigate(Screen.Viewer.route) {
                        launchSingleTop = false
                    }
                },
                onSettings         = { navController.navigate(Screen.Settings.route) },
                onAbout            = { navController.navigate(Screen.About.route) },
                onShowInterstitial = onShowInterstitial
            )
        }

        // FIX: Tidak ada lagi navArgument — URI diambil dari pendingFileUri state
        composable(Screen.Viewer.route) {
            ViewerScreen(
                fileUri             = pendingFileUri,
                onBack              = { navController.popBackStack() },
                onSettings          = { navController.navigate(Screen.Settings.route) },
                onRequestPermission = onRequestPermission
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                themePrefs          = themePrefs,
                onBack              = { navController.popBackStack() },
                onRequestPermission = onRequestPermission
            )
        }

        composable(Screen.About.route) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
