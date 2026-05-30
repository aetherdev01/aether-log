package com.aether.lv.ui

import android.net.Uri
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aether.lv.data.preferences.ThemePreferences
import com.aether.lv.ui.screen.AboutScreen
import com.aether.lv.ui.screen.HomeScreen
import com.aether.lv.ui.screen.SettingsScreen
import com.aether.lv.ui.screen.ViewerScreen

sealed class Screen(val route: String) {
    object Home     : Screen("home")
    object Viewer   : Screen("viewer?uri={uri}") {
        fun createRoute(encodedUri: String) = "viewer?uri=$encodedUri"
    }
    object Settings : Screen("settings")
    object About    : Screen("about")
}

@Composable
fun LogLogApp(
    externalFileUri: Uri?,
    themePrefs: ThemePreferences
) {
    val navController = rememberNavController()

    // Track uri yang sudah dihandle agar tidak navigate ulang saat recompose
    var handledExternalUri by remember { mutableStateOf<Uri?>(null) }

    // Navigasi ke viewer bila ada file eksternal saat launch
    LaunchedEffect(externalFileUri) {
        if (externalFileUri != null && externalFileUri != handledExternalUri) {
            handledExternalUri = externalFileUri
            val encoded = Uri.encode(externalFileUri.toString())
            navController.navigate(Screen.Viewer.createRoute(encoded)) {
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
                    val encoded = Uri.encode(uri.toString())
                    navController.navigate(Screen.Viewer.createRoute(encoded)) {
                        launchSingleTop = false   // izinkan buka banyak file berbeda
                    }
                },
                onSettings = { navController.navigate(Screen.Settings.route) },
                onAbout    = { navController.navigate(Screen.About.route) }
            )
        }

        composable(
            route     = Screen.Viewer.route,
            arguments = listOf(navArgument("uri") {
                type         = NavType.StringType
                nullable     = true
                defaultValue = null
            })
        ) { backStack ->
            val rawUri = backStack.arguments?.getString("uri")
            val uri    = rawUri?.let { Uri.parse(Uri.decode(it)) }
            ViewerScreen(
                fileUri    = uri,
                onBack     = { navController.popBackStack() },
                onSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                themePrefs = themePrefs,
                onBack     = { navController.popBackStack() }
            )
        }

        composable(Screen.About.route) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
