package com.aether.lv.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class PillNavItem(
    val key            : String,
    val label          : String,
    val selectedIcon   : ImageVector,
    val unselectedIcon : ImageVector,
)

/**
 * Floating pill nav — posisi tetap (tidak bisa digeser).
 * Penggantian tab dilakukan via swipe hold+drag di konten (HomeScreen),
 * bukan dengan menggeser pill ini.
 */
@Composable
fun FloatingPillNav(
    items       : List<PillNavItem>,
    selectedKey : String,
    onSelect    : (String) -> Unit,
    modifier    : Modifier = Modifier,
) {
    Surface(
        shape          = CircleShape,
        color          = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        contentColor   = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        modifier       = modifier
            .shadow(elevation = 8.dp, shape = CircleShape, clip = false),
    ) {
        PillNavContent(
            items       = items,
            selectedKey = selectedKey,
            onSelect    = onSelect,
        )
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
        // Indikator capsule yang meluncur di belakang ikon terpilih
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

private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication        = null,
        onClick           = onClick,
    )
}
