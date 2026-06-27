package com.aether.lv.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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

    // Rotating dashed ring animation
    val inf = rememberInfiniteTransition(label = "ring")
    val angle by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(20_000, easing = LinearEasing), RepeatMode.Restart
        ), label = "angle"
    )
    // Slow counter-ring
    val angle2 by inf.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            tween(32_000, easing = LinearEasing), RepeatMode.Restart
        ), label = "angle2"
    )

    Scaffold(

        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Kembali") }
                    },
                    title = {
                        Text(
                            "Tentang",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleLarge,
                        )
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
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {

            // ── Hero ─────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                primary.copy(alpha = 0.10f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // Outer dashed ring
                Box(
                    Modifier
                        .size(150.dp)
                        .drawBehind {
                            rotate(angle) {
                                drawCircle(
                                    color  = primary.copy(alpha = 0.18f),
                                    radius = size.minDimension / 2f,
                                    style  = Stroke(
                                        width      = 1.2.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                                    )
                                )
                            }
                            rotate(angle2) {
                                drawCircle(
                                    color  = tertiary.copy(alpha = 0.12f),
                                    radius = size.minDimension / 2f - 10.dp.toPx(),
                                    style  = Stroke(
                                        width      = 1.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 14f))
                                    )
                                )
                            }
                        }
                )

                // App icon
                Surface(
                    shape    = RoundedCornerShape(22.dp),
                    color    = primary,
                    modifier = Modifier.size(80.dp),
                    shadowElevation = 12.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Article, null,
                            modifier = Modifier.size(40.dp),
                            tint     = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }

            // ── App name + version badge ──────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 24.dp)
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    "LogLog",
                    style      = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Aether Log Viewer",
                    style  = MaterialTheme.typography.bodyMedium,
                    color  = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = primary.copy(alpha = 0.12f),
                ) {
                    Text(
                        "v${BuildConfig.VERSION_NAME}",
                        modifier   = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        style      = MaterialTheme.typography.labelMedium,
                        color      = primary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // ── Content ───────────────────────────────────────────────────
            Column(
                modifier            = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {

                // Deskripsi singkat
                Text(
                    "Log Viewer modern untuk Android. Baca dan analisis file log, txt, json, xml, yaml, " +
                    "gz, dan lainnya dengan tampilan berwarna dan pencarian cepat.",
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )

                // ── Developer card ────────────────────────────────────────
                AboutCard(title = "Maintainer", icon = Icons.Outlined.Person) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // Avatar — dari res/drawable/avatar.png
                        Image(
                            painter            = painterResource(R.drawable.avatar),
                            contentDescription = "Developer Avatar",
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .border(
                                    width  = 1.5.dp,
                                    brush  = Brush.linearGradient(listOf(primary, tertiary)),
                                    shape  = CircleShape,
                                ),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Aldi Ahmad Khoirudin",
                                style      = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "@AetherDev22",
                                style      = MaterialTheme.typography.bodySmall,
                                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedButton(
                            onClick  = {
                                context.startActivity(Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://t.me/AetherDev22")))
                            },
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Outlined.Send, null, Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Telegram")
                        }
                        Button(
                            onClick  = {
                                context.startActivity(Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/aetherdev01/aether-log")))
                            },
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Outlined.Code, null, Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("GitHub")
                        }
                    }
                }

                // ── Fitur ─────────────────────────────────────────────────
                AboutCard(title = "Fitur Utama", icon = Icons.Outlined.Star) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FeatureRow(Icons.Outlined.Palette,     "Pewarnaan Log",      "V/D/I/W/E/F masing-masing berwarna")
                        FeatureRow(Icons.Outlined.Search,      "Pencarian Cepat",    "Filter baris secara real-time")
                        FeatureRow(Icons.Outlined.Edit,        "Editor Teks",        "Edit, undo/redo, find & replace")
                        FeatureRow(Icons.Outlined.History,     "Riwayat File",       "Akses cepat file yang pernah dibuka")
                        FeatureRow(Icons.Outlined.Compress,    "Dukungan GZip",      "Baca .gz tanpa ekstrak manual")
                        FeatureRow(Icons.Outlined.DarkMode,    "Tema Gelap/Terang",  "Pilih sesuai kenyamanan")
                        FeatureRow(Icons.Outlined.AutoAwesome, "Material You",       "Palet warna dari wallpaper")
                    }
                }

                // ── Format didukung ───────────────────────────────────────
                AboutCard(title = "Format Didukung", icon = Icons.Outlined.FilePresent) {
                    FormatGrid()
                }

                // ── Build info strip ──────────────────────────────────────
                Surface(
                    shape    = RoundedCornerShape(12.dp),
                    color    = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        BuildStat("Package",    BuildConfig.APPLICATION_ID.removePrefix("com.aether."))
                        VerticalDivider(modifier = Modifier.height(28.dp), thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant)
                        BuildStat("Min SDK",   "API 30")
                        VerticalDivider(modifier = Modifier.height(28.dp), thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant)
                        BuildStat("Target",    "API 36")
                    }
                }

                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Card container
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AboutCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape    = RoundedCornerShape(16.dp),
        color    = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier              = Modifier.padding(bottom = 14.dp),
            ) {
                Icon(icon, null, modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Text(title,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface)
            }
            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Feature row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FeatureRow(icon: ImageVector, label: String, desc: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier              = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier         = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(label,
                style      = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color      = MaterialTheme.colorScheme.onSurface)
            Text(desc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Format grid (2 kolom)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FormatGrid() {
    val formats = listOf(
        ".log" to Icons.Outlined.Article,
        ".txt" to Icons.Outlined.TextFields,
        ".json" to Icons.Outlined.DataObject,
        ".xml" to Icons.Outlined.Code,
        ".yaml" to Icons.Outlined.Description,
        ".gz" to Icons.Outlined.Compress,
        ".err" to Icons.Outlined.BugReport,
        ".out" to Icons.Outlined.Terminal,
    )
    val rows = formats.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier              = Modifier.fillMaxWidth(),
            ) {
                row.forEach { (ext, icon) ->
                    Surface(
                        shape    = RoundedCornerShape(10.dp),
                        color    = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            modifier            = Modifier.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(icon, null, modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(ext,
                                style      = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color      = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Build stat column
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BuildStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value,
            style      = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color      = MaterialTheme.colorScheme.onSurface)
        Text(label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
