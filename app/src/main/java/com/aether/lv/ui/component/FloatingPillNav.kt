package com.aether.lv.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * ── Floating Pill Navigation ────────────────────────────────────────────────
 *
 * Capsule mengambang ala iOS yang bisa ditahan-geser (long-press & drag) ke
 * mana saja di dalam batas layar. Saat dilepas, pill akan snap secara halus
 * ke tepi kiri/kanan terdekat (seperti bubble PiP), tapi posisi vertikal
 * tetap bebas sesuai tempat dilepas.
 *
 * Tab terpilih ditandai indikator capsule yang "meluncur" (animated) di
 * belakang ikon — bukan background solid statis per item, mendekati gestur
 * tab-switch iOS 17+.
 *
 * @param initialOffsetFraction posisi awal pill dinyatakan sebagai fraksi
 *   (0f..1f) dari lebar & tinggi area yang tersedia, supaya independen dari
 *   ukuran layar. Simpan & pulihkan nilai ini lewat DataStore/Preferences di
 *   level pemanggil. Jika null, pill dimulai di bottom-center.
 * @param onPositionChanged dipanggil setiap kali drag selesai (setelah snap),
 *   dengan fraksi posisi baru — simpan ini di pemanggil agar posisi diingat.
 */
data class PillNavItem(
    val key            : String,
    val label          : String,
    val selectedIcon   : ImageVector,
    val unselectedIcon : ImageVector,
)

@Composable
fun FloatingPillNav(
    items                   : List<PillNavItem>,
    selectedKey             : String,
    onSelect                : (String) -> Unit,
    modifier                : Modifier = Modifier,
    initialOffsetFraction   : Offset? = null,
    onPositionChanged       : (Offset) -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density     = LocalDensity.current
        val maxWidthPx  = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val edgeMarginPx   = with(density) { 14.dp.toPx() }

        var pillSize by remember { mutableStateOf(IntSize.Zero) }
        var isDragging by remember { mutableStateOf(false) }

        // offsetPx == null artinya "belum ditentukan", dipakai supaya kita bisa
        // menunggu pillSize terukur dulu sebelum menghitung posisi default/awal.
        var offsetPx by remember { mutableStateOf<Offset?>(null) }

        LaunchedEffect(pillSize) {
            if (offsetPx == null && pillSize != IntSize.Zero) {
                offsetPx = if (initialOffsetFraction != null) {
                    Offset(
                        x = initialOffsetFraction.x * (maxWidthPx - pillSize.width),
                        y = initialOffsetFraction.y * (maxHeightPx - pillSize.height),
                    )
                } else {
                    Offset(
                        x = (maxWidthPx - pillSize.width) / 2f,
                        y = maxHeightPx - pillSize.height - with(density) { 28.dp.toPx() },
                    )
                }
            }
        }

        val elevation by animateDpAsState(
            targetValue   = if (isDragging) 16.dp else 8.dp,
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label         = "pillElevation",
        )

        val current = offsetPx
        if (current != null && pillSize != IntSize.Zero) {
            Surface(
                shape          = CircleShape,
                color          = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                contentColor   = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp,
                modifier       = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(current.x.roundToInt(), current.y.roundToInt()) }
                    .onSizeChanged { pillSize = it }
                    .shadow(elevation = elevation, shape = CircleShape, clip = false)
                    .pointerInput(maxWidthPx, maxHeightPx, pillSize) {
                        detectDragGestures(
                            onDragStart = { isDragging = true },
                            onDrag = onDrag@ { change, drag ->
                                change.consume()
                                val base = offsetPx ?: return@onDrag
                                val newX = (base.x + drag.x)
                                    .coerceIn(0f, (maxWidthPx - pillSize.width).coerceAtLeast(0f))
                                val newY = (base.y + drag.y)
                                    .coerceIn(0f, (maxHeightPx - pillSize.height).coerceAtLeast(0f))
                                offsetPx = Offset(newX, newY)
                            },
                            onDragEnd = onDragEnd@ {
                                isDragging = false
                                val base = offsetPx ?: return@onDragEnd
                                // Snap horizontal ke tepi kiri/kanan terdekat, vertikal tetap bebas.
                                val snappedX = if (base.x + pillSize.width / 2f < maxWidthPx / 2f) {
                                    edgeMarginPx
                                } else {
                                    maxWidthPx - pillSize.width - edgeMarginPx
                                }
                                val clampedY = base.y.coerceIn(
                                    edgeMarginPx,
                                    (maxHeightPx - pillSize.height - edgeMarginPx).coerceAtLeast(edgeMarginPx),
                                )
                                offsetPx = Offset(snappedX, clampedY)
                                val widthRange  = (maxWidthPx - pillSize.width).coerceAtLeast(1f)
                                val heightRange = (maxHeightPx - pillSize.height).coerceAtLeast(1f)
                                onPositionChanged(
                                    Offset(snappedX / widthRange, clampedY / heightRange)
                                )
                            },
                            onDragCancel = { isDragging = false },
                        )
                    },
            ) {
                PillNavContent(
                    items       = items,
                    selectedKey = selectedKey,
                    onSelect    = onSelect,
                )
            }
        } else {
            // Render tak terlihat sekali untuk mengukur pillSize sebelum posisi final dihitung.
            Surface(
                shape    = CircleShape,
                color    = Color.Transparent,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(-10000, -10000) }
                    .onSizeChanged { pillSize = it },
            ) {
                PillNavContent(items = items, selectedKey = selectedKey, onSelect = {})
            }
        }
    }
}

@Composable
private fun PillNavContent(
    items       : List<PillNavItem>,
    selectedKey : String,
    onSelect    : (String) -> Unit,
) {
    val itemWidth  = 64.dp
    val itemHeight = 54.dp
    val selectedIndex = items.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)

    val indicatorOffset by animateDpAsState(
        targetValue   = itemWidth * selectedIndex,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessLow,
        ),
        label = "pillIndicatorOffset",
    )

    Box(modifier = Modifier.padding(5.dp)) {
        // Indikator capsule yang meluncur di belakang ikon terpilih.
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .size(width = itemWidth, height = itemHeight)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary)
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            items.forEach { item ->
                val selected = item.key == selectedKey
                val contentColor by animateColorAsState(
                    targetValue = if (selected)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "pillItemColor",
                )
                Box(
                    modifier = Modifier
                        .width(itemWidth)
                        .height(itemHeight)
                        .clip(RoundedCornerShape(50))
                        .noRippleClickable { onSelect(item.key) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector        = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        tint               = contentColor,
                        modifier           = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/** Click handler tanpa efek ripple, supaya capsule terasa solid & mulus saat ditekan. */
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication        = null,
        onClick           = onClick,
    )
}
