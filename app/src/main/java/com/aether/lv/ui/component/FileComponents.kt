package com.aether.lv.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aether.lv.util.FileIconType

@Composable
fun FileTypeIcon(
    iconType : FileIconType,
    modifier : Modifier = Modifier,
    tint     : androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val vector = when (iconType) {
        FileIconType.JSON  -> Icons.Outlined.DataObject
        FileIconType.XML   -> Icons.Outlined.Code
        FileIconType.YAML  -> Icons.Outlined.Settings
        FileIconType.ERROR -> Icons.Outlined.ErrorOutline
        FileIconType.OUT   -> Icons.Outlined.Terminal
        FileIconType.LOG   -> Icons.Outlined.Article
        FileIconType.GZ    -> Icons.Outlined.FolderZip
    }
    Icon(vector, contentDescription = iconType.name, modifier = modifier, tint = tint)
}

@Composable
fun FileTypeChip(
    label    : String,
    modifier : Modifier = Modifier
) {
    Surface(
        shape  = RoundedCornerShape(4.dp),
        color  = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        modifier = modifier
    ) {
        Text(
            label,
            style    = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color    = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}
