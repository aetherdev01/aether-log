package com.aether.lv.ads

import android.app.Activity
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Composable Unity Ads Banner.
 *
 * Menggunakan collectAsState() agar recompose otomatis saat SDK selesai init.
 * Trigger loadBannerIntoView via LaunchedEffect(isInitialized) supaya banner
 * di-load ulang setelah init selesai — bukan hanya saat factory pertama kali dibuat.
 */
@Composable
fun BannerAdView(
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(50.dp)
) {
    val context  = LocalContext.current
    val activity = context as? Activity ?: return

    // Reactive — recompose saat SDK init selesai
    val isInitialized by AdsManager.isInitialized.collectAsState()
    if (!isInitialized) return

    var containerRef by remember { mutableStateOf<FrameLayout?>(null) }

    // Load banner ulang setiap kali isInitialized berubah jadi true
    // (menghindari kasus factory sudah dibuat tapi SDK belum siap)
    LaunchedEffect(isInitialized) {
        if (isInitialized) {
            containerRef?.let { frame ->
                AdsManager.loadBannerIntoView(
                    activity  = activity,
                    container = frame,
                    onLoaded  = {},
                    onFailed  = {}
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            containerRef?.let { AdsManager.destroyBanner(it) }
        }
    }

    AndroidView(
        modifier = modifier,
        factory  = { ctx ->
            FrameLayout(ctx).also { frame ->
                containerRef = frame
                // Load langsung jika SDK sudah siap saat factory dibuat
                if (AdsManager.isInitialized.value) {
                    AdsManager.loadBannerIntoView(
                        activity  = activity,
                        container = frame,
                        onLoaded  = {},
                        onFailed  = {}
                    )
                }
            }
        }
    )
}
