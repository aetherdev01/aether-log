package com.aether.lv.ads

import android.app.Activity
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun BannerAdView(
    modifier : Modifier = Modifier
        .fillMaxWidth()
        .height(50.dp)
) {
    val context  = LocalContext.current
    val activity = context as? Activity ?: return

    // collectAsState() agar recompose otomatis saat SDK selesai init
    val isInitialized by AdsManager.isInitialized.collectAsState()
    if (!isInitialized) return

    var containerRef by remember { mutableStateOf<FrameLayout?>(null) }

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
                AdsManager.loadBannerIntoView(
                    activity  = activity,
                    container = frame,
                    onLoaded  = {},
                    onFailed  = {}
                )
            }
        }
    )
}
