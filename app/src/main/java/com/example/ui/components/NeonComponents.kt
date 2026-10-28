package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.JunkCategory
import com.example.data.MediaItem
import com.example.data.SecurityScanItem
import com.example.data.UploadStatus
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.NeonBorderCyan
import com.example.ui.theme.NeonBorderGreen
import com.example.ui.theme.NeonBorderPink
import com.example.ui.theme.NeonCardGlassGradient
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonCyanBlueGradient
import com.example.ui.theme.NeonError
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonSurface
import com.example.ui.theme.NeonSurfaceGlass
import com.example.ui.theme.NeonSurfaceGlassLight
import com.example.ui.theme.NeonTextMuted
import com.example.ui.theme.NeonTextPrimary
import com.example.ui.theme.NeonTextSecondary
import java.util.Locale


@Composable
fun NeonGlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = Color(0x1AFFFFFF), // border-white/10
    borderWidth: Dp = 1.dp,
    shapeRadius: Dp = 28.dp,
    glowColor: Color = Color(0x1A00F2FE),
    showTopRightGlow: Boolean = false,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(shapeRadius),
                ambientColor = glowColor,
                spotColor = glowColor
            )
            .clip(RoundedCornerShape(shapeRadius))
            .background(Color(0x0DFFFFFF)) // bg-white/5
            .border(
                BorderStroke(borderWidth, borderColor),
                shape = RoundedCornerShape(shapeRadius)
            )
    ) {
        if (showTopRightGlow) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .align(Alignment.TopEnd)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0x2E00F2FE),
                                Color(0x0000F2FE)
                            )
                        )
                    )
            )
        }
        Box(modifier = Modifier.padding(20.dp)) {
            content()
        }
    }
}

@Composable
fun NeonGlowingButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    gradient: Brush = NeonCyanBlueGradient,
    glowColor: Color = NeonCyan,
    testTag: String = "neon_button"
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier
            .testTag(testTag)
            .shadow(
                elevation = if (enabled) 12.dp else 0.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = if (enabled) glowColor.copy(alpha = pulseAlpha) else Color.Transparent,
                spotColor = if (enabled) glowColor.copy(alpha = pulseAlpha) else Color.Transparent
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (enabled) gradient else Brush.horizontalGradient(
                    listOf(Color(0xFF334155), Color(0xFF1E293B))
                )
            )
            .clickable(enabled = enabled && !isLoading) { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) Color(0xFF0B0F19) else NeonTextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Text(
                text = text,
                color = if (enabled) Color(0xFF0B0F19) else NeonTextMuted,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun NeonOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    borderColor: Color = NeonCyan,
    textColor: Color = NeonCyan,
    enabled: Boolean = true,
    testTag: String = "neon_outlined_button"
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .testTag(testTag)
            .height(48.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (enabled) borderColor.copy(alpha = 0.6f) else NeonTextMuted),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0x0DFFFFFF),
            contentColor = textColor,
            disabledContentColor = NeonTextMuted
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) textColor else NeonTextMuted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
fun NeonCircularProgress(
    progress: Float,
    uploadedCount: Int,
    totalCount: Int,
    isSyncing: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_circle")
    val pulseSize by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = modifier
            .size(144.dp)
            .drawBehind {
                if (isSyncing) {
                    drawCircle(
                        color = Color(0x1F00F2FE),
                        radius = (size.minDimension / 2f) * pulseSize
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(126.dp)) {
            val strokeWidth = 8.dp.toPx()
            val canvasSize = size.minDimension - strokeWidth
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

            // Background Track (rgba(255,255,255,0.05))
            drawArc(
                color = Color(0x14FFFFFF),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(canvasSize, canvasSize),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Active Glowing Cyan Arc
            val sweepAngle = progress.coerceIn(0f, 1f) * 360f
            if (sweepAngle > 0f) {
                drawArc(
                    color = NeonCyan,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(canvasSize, canvasSize),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val percentage = (progress.coerceIn(0f, 1f) * 100).toInt()
            Text(
                text = "$percentage%",
                color = Color(0xFFF8FAFC),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.8).sp
            )
            Text(
                text = if (isSyncing) "OPTIMIZING" else "STANDBY",
                color = Color(0xFF94A3B8),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }
    }
}

@Composable
fun NeonLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(CircleShape)
            .background(Color(0x33334155))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(5.dp)
                .clip(CircleShape)
                .background(NeonCyan)
                .shadow(6.dp, shape = CircleShape, ambientColor = NeonCyan, spotColor = NeonCyan)
        )
    }
}

@Composable
fun NeonStatusBadge(
    status: UploadStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, label, icon) = when (status) {
        UploadStatus.PENDING -> Quadruple(
            Color(0x1A64748B),
            Color(0xFF94A3B8),
            "PENDING",
            Icons.Default.HourglassEmpty
        )
        UploadStatus.UPLOADING -> Quadruple(
            Color(0x1A00F2FE),
            NeonCyan,
            "SYNCING",
            Icons.Default.Sync
        )
        UploadStatus.COMPLETED -> Quadruple(
            Color(0x1A00FF88),
            NeonGreen,
            "SYNCED",
            Icons.Default.CheckCircle
        )
        UploadStatus.FAILED -> Quadruple(
            Color(0x1AFF007F),
            NeonPink,
            "FAILED",
            Icons.Default.Error
        )
        UploadStatus.SKIPPED -> Quadruple(
            Color(0x1A334155),
            Color(0xFF64748B),
            "SKIPPED",
            Icons.Default.HourglassEmpty
        )
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, textColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(11.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun NeonMetricTile(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x0DFFFFFF)) // bg-white/5
            .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title.uppercase(),
                    color = Color(0xFF64748B), // text-slate-500
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                color = Color(0xFFF1F5F9), // text-slate-100
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )
        }
    }
}

@Composable
fun NeonActivityItemRow(
    item: MediaItem,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x0DFFFFFF)) // bg-white/5
            .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(16.dp)) // border-white/5
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 40x40 container with rounded-xl bg-slate-800/50
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x801E293B)), // bg-slate-800/50
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (item.isVideo) Icons.Default.PlayArrow else Icons.Default.CloudDone,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayName,
                color = Color(0xFFF1F5F9),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${formatFileSize(item.size)} • ${
                    when (item.status) {
                        UploadStatus.COMPLETED -> "Successfully Synced"
                        UploadStatus.UPLOADING -> "Uploading to Cloud..."
                        UploadStatus.FAILED -> "Upload Failed"
                        UploadStatus.PENDING -> "Queued for Sync"
                        UploadStatus.SKIPPED -> "Skipped"
                    }
                }",
                color = Color(0xFF64748B), // text-slate-500
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        when (item.status) {
            UploadStatus.COMPLETED -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Synced",
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
            UploadStatus.UPLOADING -> {
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color(0x80334155)) // bg-slate-700/50
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(item.progress.coerceIn(0.1f, 1f))
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(NeonCyan)
                            .shadow(4.dp, shape = CircleShape, ambientColor = NeonCyan, spotColor = NeonCyan)
                    )
                }
            }
            UploadStatus.FAILED -> {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Failed",
                    tint = NeonPink,
                    modifier = Modifier.size(20.dp)
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Default.HourglassEmpty,
                    contentDescription = "Queued",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun NeonMediaCard(
    item: MediaItem,
    modifier: Modifier = Modifier
) {
    val borderColor = when (item.status) {
        UploadStatus.UPLOADING -> NeonCyan
        UploadStatus.COMPLETED -> NeonGreen.copy(alpha = 0.5f)
        UploadStatus.FAILED -> NeonPink
        else -> Color(0x33334155)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(NeonSurface)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
    ) {
        // Media Thumbnail
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.uri)
                .crossfade(true)
                .build(),
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient overlay for text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x66000000),
                            Color.Transparent,
                            Color(0xDD0B0F19)
                        )
                    )
                )
        )

        // Top Status Badge & Media Type Tag
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeonStatusBadge(status = item.status)

            if (item.isVideo) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = formatDuration(item.duration),
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Bottom File details
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(6.dp)
        ) {
            Text(
                text = item.displayName,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatFileSize(item.size),
                    color = NeonTextMuted,
                    fontSize = 9.sp
                )
                if (item.status == UploadStatus.UPLOADING) {
                    Text(
                        text = "${(item.progress * 100).toInt()}%",
                        color = NeonCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (item.status == UploadStatus.UPLOADING) {
                Spacer(modifier = Modifier.height(2.dp))
                NeonLinearProgress(
                    progress = item.progress,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0:00"
    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / (1000 * 60)) % 60
    val hours = durationMs / (1000 * 60 * 60)
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

@Composable
fun CleanerSpeedometerGauge(
    junkBytes: Long,
    isScanning: Boolean,
    isCleaning: Boolean,
    isCleaned: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cleaner_radar")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_sweep"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = modifier
            .size(190.dp)
            .shadow(
                elevation = if (isScanning || isCleaning) 24.dp else 12.dp,
                shape = CircleShape,
                ambientColor = if (isCleaned) NeonGreen else NeonCyan,
                spotColor = if (isCleaned) NeonGreen else NeonCyan
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 10.dp.toPx()
            val arcSize = size.width - strokeWidth

            // Base Background Track
            drawArc(
                color = Color(0x331E293B),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(arcSize, arcSize),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Dynamic Active Progress Arc
            val activeColor = if (isCleaned) NeonGreen else NeonCyan
            val sweep = if (isCleaned) 270f else if (isScanning) (rotation % 270f) else 210f

            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        activeColor.copy(alpha = 0.3f),
                        activeColor,
                        if (isCleaned) NeonGreen else NeonBlue
                    )
                ),
                startAngle = 135f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(arcSize, arcSize),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Radar Scanning Sweep Overlay (if active)
        if (isScanning || isCleaning) {
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .rotate(rotation)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                0.0f to Color.Transparent,
                                0.75f to Color.Transparent,
                                1.0f to NeonCyan.copy(alpha = 0.4f)
                            )
                        )
                    }
            )
        }

        // Center Content Badge
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isCleaned) Icons.Default.CheckCircle
                else if (isScanning) Icons.Default.Sync
                else if (isCleaning) Icons.Default.DeleteSweep
                else Icons.Default.Bolt,
                contentDescription = null,
                tint = if (isCleaned) NeonGreen else NeonCyan,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isCleaned) "0 B"
                else if (isScanning) "SCANNING"
                else formatFileSize(junkBytes),
                color = Color.White,
                fontSize = if (isScanning) 15.sp else 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp
            )

            Text(
                text = if (isCleaned) "STORAGE CLEAN"
                else if (isScanning) "ANALYZING JUNK..."
                else if (isCleaning) "CLEANING SPACE..."
                else "JUNK FOUND",
                color = if (isCleaned) NeonGreen else Color(0xFF94A3B8),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun JunkCategoryCard(
    category: JunkCategory,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when (category.iconName) {
        "cached" -> Icons.Default.Cached
        "history" -> Icons.Default.History
        "folder_zip" -> Icons.Default.FolderZip
        "delete_sweep" -> Icons.Default.DeleteSweep
        "image" -> Icons.Default.Image
        else -> Icons.Default.Memory
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x0DFFFFFF)) // bg-white/5
            .border(1.dp, if (category.isSelected) NeonCyan.copy(alpha = 0.35f) else Color(0x0DFFFFFF), RoundedCornerShape(16.dp))
            .clickable { onToggle() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x801E293B)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = category.title,
                tint = NeonCyan,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.title,
                color = Color(0xFFF1F5F9),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = category.description,
                color = Color(0xFF64748B),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = formatFileSize(category.sizeBytes),
            color = if (category.sizeBytes > 0) NeonCyan else Color(0xFF64748B),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.width(6.dp))

        Checkbox(
            checked = category.isSelected,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = NeonCyan,
                uncheckedColor = Color(0xFF475569),
                checkmarkColor = Color(0xFF060910)
            ),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun RamBoostGauge(
    usagePercent: Int,
    usedMb: Int,
    totalMb: Int,
    isBoosting: Boolean,
    isBoosted: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ram_orbit")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_rot"
    )

    Box(
        modifier = modifier
            .size(190.dp)
            .shadow(
                elevation = if (isBoosting) 24.dp else 12.dp,
                shape = CircleShape,
                ambientColor = if (isBoosted) NeonGreen else NeonCyan,
                spotColor = if (isBoosted) NeonGreen else NeonCyan
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 10.dp.toPx()
            val arcSize = size.width - strokeWidth

            // Track
            drawArc(
                color = Color(0x331E293B),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(arcSize, arcSize),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Active Ram Arc
            val sweep = (usagePercent / 100f) * 270f
            val arcColor = if (isBoosted) NeonGreen else if (usagePercent > 70) NeonPink else NeonCyan

            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        arcColor.copy(alpha = 0.4f),
                        arcColor,
                        if (isBoosted) NeonGreen else NeonCyan
                    )
                ),
                startAngle = 135f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(arcSize, arcSize),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        if (isBoosting) {
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .rotate(rotation)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                0.0f to Color.Transparent,
                                0.7f to Color.Transparent,
                                1.0f to NeonCyan.copy(alpha = 0.5f)
                            )
                        )
                    }
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isBoosted) Icons.Default.CheckCircle else Icons.Default.Speed,
                contentDescription = null,
                tint = if (isBoosted) NeonGreen else NeonCyan,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isBoosting) "BOOSTING" else "$usagePercent%",
                color = Color.White,
                fontSize = if (isBoosting) 16.sp else 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp
            )

            Text(
                text = if (isBoosted) "RAM OPTIMIZED"
                else if (isBoosting) "RELEASING MEMORY..."
                else "${usedMb}MB / ${totalMb}MB",
                color = if (isBoosted) NeonGreen else Color(0xFF94A3B8),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }
    }
}

@Composable
fun SecurityShieldGauge(
    isScanning: Boolean,
    isProtected: Boolean,
    progress: Float,
    scannedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shield_anim")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shield_rot"
    )

    Box(
        modifier = modifier
            .size(190.dp)
            .shadow(
                elevation = 20.dp,
                shape = CircleShape,
                ambientColor = if (isProtected && !isScanning) NeonGreen else NeonCyan,
                spotColor = if (isProtected && !isScanning) NeonGreen else NeonCyan
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()
            val arcSize = size.width - strokeWidth

            drawArc(
                color = Color(0x331E293B),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(arcSize, arcSize),
                style = Stroke(width = strokeWidth)
            )

            val sweep = if (isScanning) progress * 360f else 360f
            val tint = if (isProtected && !isScanning) NeonGreen else NeonCyan

            drawArc(
                color = tint,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(arcSize, arcSize),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        if (isScanning) {
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .rotate(rotation)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                0.0f to Color.Transparent,
                                0.7f to Color.Transparent,
                                1.0f to NeonCyan.copy(alpha = 0.45f)
                            )
                        )
                    }
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = if (isProtected && !isScanning) NeonGreen else NeonCyan,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isScanning) "${(progress * 100).toInt()}%" else "100%",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )

            Text(
                text = if (isScanning) "SCANNED $scannedCount/$totalCount" else "DEVICE SECURE",
                color = if (isProtected && !isScanning) NeonGreen else Color(0xFF94A3B8),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

