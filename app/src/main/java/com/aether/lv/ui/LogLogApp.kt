package com.aether.lv.ui

import android.app.Activity
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aether.lv.ads.AdBlockDialog
import com.aether.lv.ads.AdBlockManager
import com.aether.lv.data.preferences.ThemePreferences
import com.aether.lv.ui.screen.AboutScreen
import com.aether.lv.ui.screen.EditorScreen
import com.aether.lv.ui.screen.HomeScreen
import com.aether.lv.ui.screen.LicenseScreen
import com.aether.lv.ui.screen.SettingsScreen
import com.aether.lv.ui.screen.ViewerScreen

sealed class Screen(val route: String) {
    object Home     : Screen("home")
    object Viewer   : Screen("viewer")
    object Editor   : Screen("editor")
    object Settings : Screen("settings")
    object About    : Screen("about")
    object License  : Screen("license")
}

private const val NAV_ANIM_MS = 320
private val emphasizedEasing  = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private fun enterFromRight(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(NAV_ANIM_MS, easing = emphasizedEasing),
        initialOffsetX = { it / 4 }
    ) + fadeIn(animationSpec = tween(NAV_ANIM_MS / 2, easing = emphasizedEasing))

private fun exitToLeft(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(NAV_ANIM_MS, easing = emphasizedEasing),
        targetOffsetX = { -it / 4 }
    ) + fadeOut(animationSpec = tween(NAV_ANIM_MS / 2, easing = emphasizedEasing))

private fun enterFromLeft(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(NAV_ANIM_MS, easing = emphasizedEasing),
        initialOffsetX = { -it / 4 }
    ) + fadeIn(animationSpec = tween(NAV_ANIM_MS / 2, easing = emphasizedEasing))

private fun exitToRight(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(NAV_ANIM_MS, easing = emphasizedEasing),
        targetOffsetX = { it / 4 }
    ) + fadeOut(animationSpec = tween(NAV_ANIM_MS / 2, easing = emphasizedEasing))

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LogLogApp(
    externalFileUri     : Uri?,
    themePrefs          : ThemePreferences,
    onRequestPermission : () -> Unit = {},
    onShowInterstitial  : (() -> Unit) -> Unit = { it() },
    onShowRewarded      : () -> Unit = {}
) {
    val navController = rememberNavController()
    val context       = LocalContext.current

    var pendingFileUri     by remember { mutableStateOf<Uri?>(null) }
    var pendingEditorUri   by remember { mutableStateOf<Uri?>(null) }
    var handledExternalUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(externalFileUri) {
        if (externalFileUri != null && externalFileUri != handledExternalUri) {
            handledExternalUri = externalFileUri
            pendingFileUri     = externalFileUri
            navController.navigate(Screen.Viewer.route) { launchSingleTop = true }
        }
    }

    // ── AdBlock dialog state ───────────────────────────────────────────────
    val adBlockState by AdBlockManager.state.collectAsState()

    NavHost(
        navController       = navController,
        startDestination    = Screen.Home.route,
        enterTransition     = { enterFromRight() },
        exitTransition      = { exitToLeft() },
        popEnterTransition  = { enterFromLeft() },
        popExitTransition   = { exitToRight() }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onOpenFile = { uri ->
                    pendingFileUri = uri
                    navController.navigate(Screen.Viewer.route) { launchSingleTop = false }
                },
                onSettings         = { navController.navigate(Screen.Settings.route) },
                onAbout            = { navController.navigate(Screen.About.route) },
                onOpenLicense      = { navController.navigate(Screen.License.route) },
                onShowInterstitial = onShowInterstitial
            )
        }

        composable(Screen.Viewer.route) {
            ViewerScreen(
                fileUri             = pendingFileUri,
                onBack              = { navController.popBackStack() },
                onSettings          = { navController.navigate(Screen.Settings.route) },
                onRequestPermission = onRequestPermission,
                onOpenInEditor      = { uri ->
                    pendingEditorUri = uri
                    navController.navigate(Screen.Editor.route)
                }
            )
        }

        composable(Screen.Editor.route) {
            EditorScreen(
                fileUri = pendingEditorUri,
                onBack  = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                themePrefs                = themePrefs,
                onBack                    = { navController.popBackStack() },
                onRequestPermission       = onRequestPermission,
                onShowInterstitial        = onShowInterstitial,
                onShowRewarded            = onShowRewarded,
                onOpenLicenseFromSettings = { navController.navigate(Screen.License.route) },
            )
        }

        composable(Screen.About.route) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.License.route) {
            LicenseScreen(onBack = { navController.popBackStack() })
        }
    }

    // ── AdBlock dialog overlay ─────────────────────────────────────────────
    // Ditampilkan di atas semua screen tanpa memutus navigation stack.
    if (adBlockState.isBlocked) {
        AdBlockDialog(
            signals    = adBlockState.signals,
            confidence = adBlockState.confidence,
            onDismiss  = {
                // User memilih "Keluar dari Aplikasi"
                AdBlockManager.dismiss()
                (context as? Activity)?.finishAffinity()
            },
            onAllowed  = {
                // User klaim sudah matikan AdBlock → re-check
                AdBlockManager.onUserClaimedDisabled(context)
            },
        )
    }
}
