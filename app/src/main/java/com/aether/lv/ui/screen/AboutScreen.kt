package com.aether.lv.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aether.lv.BuildConfig
import com.aether.lv.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.ArrowBack, "Kembali")
                        }
                    },
                    title = {
                        Text("Tentang", fontWeight = FontWeight.SemiBold)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                HorizontalDivider(thickness = 0.5.dp)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {

            Spacer(Modifier.height(28.dp))

            // ── App identity ──────────────────────────────────────────────
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    shape           = RoundedCornerShape(18.dp),
                    color           = primary,
                    shadowElevation = 4.dp,
                    modifier        = Modifier.size(64.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Article, null,
                            modifier = Modifier.size(32.dp),
                            tint     = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "LogLog",
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = primary.copy(alpha = 0.1f),
                        ) {
                            Text(
                                "v${BuildConfig.VERSION_NAME}",
                                style      = MaterialTheme.typography.labelSmall,
                                color      = primary,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                modifier   = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                        Text(
                            "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                        Text(
                            "API ${android.os.Build.VERSION.SDK_INT}",
                            style  = MaterialTheme.typography.labelSmall,
                            color  = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Text(
                        BuildConfig.APPLICATION_ID,
                        style      = MaterialTheme.typography.labelSmall,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 10.sp,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Developer ─────────────────────────────────────────────────
            Surface(
                shape    = RoundedCornerShape(16.dp),
                color    = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier              = Modifier.padding(14.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Image(
                        painter            = painterResource(R.drawable.avatar),
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .border(
                                width = 1.5.dp,
                                brush = Brush.linearGradient(listOf(primary, tertiary)),
                                shape = CircleShape,
                            ),
                    )
                    Column(
                        modifier            = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            "Aldi Ahmad Khoirudin",
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Developer & Maintainer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Tombol Telegram & GitHub inline
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick  = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/AetherDev22"))
                                )
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Send, "Telegram",
                                modifier = Modifier.size(18.dp),
                                tint     = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(
                            onClick  = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/aetherdev01/aether-log"))
                                )
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Code, "GitHub",
                                modifier = Modifier.size(18.dp),
                                tint     = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Build info ────────────────────────────────────────────────
            Surface(
                shape    = RoundedCornerShape(16.dp),
                color    = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Build Info")
                    BuildRow(Icons.Outlined.Tag,           "Version",    "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    BuildRow(Icons.Outlined.Android,       "Min SDK",    "API 30  ·  Target API 36")
                    BuildRow(Icons.Outlined.Inventory2,    "Package",    BuildConfig.APPLICATION_ID)
                    BuildRow(Icons.Outlined.Memory,        "Native Lib", "libxplus.so  ·  liblv.so")
                    BuildRow(Icons.Outlined.Architecture,  "ABI",        "arm64-v8a  ·  x86_64")
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Stack ─────────────────────────────────────────────────────
            Surface(
                shape    = RoundedCornerShape(16.dp),
                color    = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Tech Stack")
                    BuildRow(Icons.Outlined.Layers,        "UI",         "Jetpack Compose · Material 3")
                    BuildRow(Icons.Outlined.Storage,       "Database",   "Room · DataStore Preferences")
                    BuildRow(Icons.Outlined.Code,          "Native",     "C++17 via Android NDK · JNI")
                    BuildRow(Icons.Outlined.Brush,         "Theme",      "Material You · Dynamic Color")
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Format didukung ───────────────────────────────────────────
            Surface(
                shape    = RoundedCornerShape(16.dp),
                color    = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Format Didukung")
                    Spacer(Modifier.height(2.dp))
                    val formats = listOf(
                        ".log", ".txt", ".json", ".xml",
                        ".yaml", ".gz", ".err", ".out",
                    )
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        formats.forEach { ext ->
                            Surface(
                                shape    = RoundedCornerShape(8.dp),
                                color    = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.wrapContentWidth(),
                            ) {
                                Text(
                                    ext,
                                    style      = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color      = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier   = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Footer ────────────────────────────────────────────────────
            Text(
                "Made with ♥ by Aether · ${BuildConfig.APPLICATION_ID}",
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style      = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color      = MaterialTheme.colorScheme.primary,
        letterSpacing = 0.6.sp,
    )
}

@Composable
private fun BuildRow(icon: ImageVector, label: String, value: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            icon, null,
            modifier = Modifier.size(15.dp),
            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        Text(
            "$label  ",
            style      = MaterialTheme.typography.bodySmall,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        Text(
            value,
            style      = MaterialTheme.typography.bodySmall,
            color      = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
        )
    }
}
