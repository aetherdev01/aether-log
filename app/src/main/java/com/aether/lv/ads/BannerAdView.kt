package com.aether.lv.ads

import android.app.Activity
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Composable yang menampilkan Unity Ads Banner di dalam Compose layout.
 *
 * Gunakan di bottom of screen (HomeScreen, ViewerScreen, dsb):
 * ```kotlin
 * Column {
 *     // ... konten ...
 *     BannerAdView()
 * }
 * ```
 *
 * Banner height standar Unity Ads: 50dp (BANNER) atau 90dp (LEADERBOARD).
 * Default di sini 50dp sesuai standard banner.
 */
@Composable
fun BannerAdView(
    modifier : Modifier = Modifier
        .fillMaxWidth()
        .height(50.dp)
) {
    val context  = LocalContext.current
    val activity = context as? Activity ?: return

    // Hanya tampil jika SDK sudah initialized
    if (!AdsManager.isInitialized.value) return

    var containerRef by remember { mutableStateOf<FrameLayout?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            // Destroy banner saat Composable leave composition
            containerRef?.let { AdsManager.destroyBanner(it) }
        }
    }

    AndroidView(
        modifier = modifier,
        factory  = { ctx ->
            FrameLayout(ctx).also { frame ->
                containerRef = frame
                AdsManager.loadBannerIntoView(
                    activity  = activity,
                    container = frame,
                    onLoaded  = { /* banner sudah tampil */ },
                    onFailed  = { /* banner gagal — view tetap ada tapi kosong */ }
                )
            }
        }
    )
}
