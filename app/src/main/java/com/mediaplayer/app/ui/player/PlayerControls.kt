@file:OptIn(ExperimentalMaterial3Api::class)

package com.mediaplayer.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mediaplayer.app.util.formatDuration
import com.mediaplayer.app.util.formatSpeed
import com.mediaplayer.app.util.progressOf

private val ScrimTop = Brush.verticalGradient(
    colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent),
)

private val ScrimBottom = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
)

@Composable
fun PlayerTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleOrientation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(ScrimTop)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.ifBlank { "正在播放" },
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onEnterPip) {
                Icon(
                    Icons.Filled.PictureInPictureAlt,
                    contentDescription = "画中画",
                    tint = Color.White,
                )
            }
            IconButton(onClick = onToggleOrientation) {
                Icon(
                    Icons.Filled.ScreenRotation,
                    contentDescription = "旋转屏幕",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
fun PlayerCenterControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    isEnded: Boolean,
    hasNext: Boolean,
    hasPrevious: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        ControlIcon(Icons.Filled.Replay10, "快退 10 秒", onSeekBack, enabled = true)
        ControlIcon(
            Icons.Filled.SkipPrevious,
            "上一个",
            onPrevious,
            enabled = hasPrevious,
        )
        Box(contentAlignment = Alignment.Center) {
            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.45f),
                    contentColor = Color.White,
                ),
            ) {
                if (isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = Color.White,
                        strokeWidth = 3.dp,
                    )
                } else {
                    Icon(
                        imageVector = when {
                            isEnded -> Icons.Filled.Replay
                            isPlaying -> Icons.Filled.Pause
                            else -> Icons.Filled.PlayArrow
                        },
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
        ControlIcon(Icons.Filled.SkipNext, "下一个", onNext, enabled = hasNext)
        ControlIcon(Icons.Filled.Forward10, "快进 10 秒", onSeekForward, enabled = true)
    }
}

@Composable
private fun ControlIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
fun PlayerBottomBar(
    position: Long,
    duration: Long,
    speed: Float,
    resizeLabel: String,
    subtitleLabel: String?,
    audioLabel: String?,
    onSeek: (Long) -> Unit,
    onDraggingChanged: (Boolean) -> Unit,
    onOpenSpeed: () -> Unit,
    onOpenSubtitle: () -> Unit,
    onOpenResize: () -> Unit,
    onOpenAudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val displayPosition = if (dragging) (dragValue * duration).toLong() else position

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(ScrimBottom)
            .padding(horizontal = 12.dp)
            .padding(top = 24.dp, bottom = 10.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDuration(displayPosition),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    value = if (dragging) dragValue else progressOf(position, duration),
                    onValueChange = {
                        dragging = true
                        dragValue = it
                        onDraggingChanged(true)
                    },
                    onValueChangeFinished = {
                        onSeek((dragValue * duration).toLong())
                        dragging = false
                        onDraggingChanged(false)
                    },
                    enabled = duration > 0L,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                Text(
                    text = formatDuration(duration),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                BarAction(Icons.Filled.Speed, formatSpeed(speed), onOpenSpeed)
                BarAction(
                    Icons.Filled.Subtitles,
                    subtitleLabel ?: "字幕",
                    onOpenSubtitle,
                    highlighted = subtitleLabel != null,
                )
                if (audioLabel != null) {
                    BarAction(Icons.Filled.Audiotrack, audioLabel, onOpenAudio)
                }
                BarAction(Icons.Filled.AspectRatio, resizeLabel, onOpenResize)
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    TextButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (highlighted) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = if (highlighted) MaterialTheme.colorScheme.primary else Color.White,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

@Composable
fun PlayerLockButton(locked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    FilledIconButton(
        onClick = onToggle,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.45f),
            contentColor = Color.White,
        ),
        modifier = modifier.size(44.dp),
    ) {
        Icon(
            imageVector = if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
            contentDescription = if (locked) "解锁" else "锁定屏幕",
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun GestureHud(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = 0.68f),
                    shape = MaterialTheme.shapes.medium,
                )
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
fun PlayerErrorDialog(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("播放失败") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onRetry) { Text("重试") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}
