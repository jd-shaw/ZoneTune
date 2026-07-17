package com.shaw.zonetune.ui.player

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shaw.zonetune.ZoneTuneApp
import com.shaw.zonetune.player.PlayMode
import com.shaw.zonetune.player.PlaybackService
import com.shaw.zonetune.ui.theme.CoverShape
import com.shaw.zonetune.ui.theme.MiniBarShape
import kotlinx.coroutines.delay

@Composable
fun MiniPlayerBar(
    onExpand: () -> Unit = {},
) {
    val context = LocalContext.current
    val player = ZoneTuneApp.instance.playerController
    val state by player.state.collectAsState()
    var showQueue by remember { mutableStateOf(false) }

    LaunchedEffect(state.current?.id, state.isPlaying) {
        while (true) {
            player.refreshProgress()
            delay(500)
        }
    }

    LaunchedEffect(state.isPlaying, state.current?.id) {
        if (state.isPlaying && state.current != null) {
            runCatching {
                context.startForegroundService(Intent(context, PlaybackService::class.java))
            }
        }
    }

    val track = state.current
    val playScale by animateFloatAsState(
        targetValue = if (state.isPlaying) 1f else 0.94f,
        animationSpec = tween(180),
        label = "playScale",
    )
    val queueCount = state.queue.size.coerceAtLeast(if (track != null) 1 else 0)

    if (showQueue) {
        QueueSheet(
            queue = state.queue.ifEmpty { listOfNotNull(track) },
            currentId = track?.id,
            playMode = state.playMode,
            onDismiss = { showQueue = false },
            onPlayTrack = { item ->
                if (item.audioUrl.isNotBlank()) {
                    player.play(item)
                } else {
                    Toast.makeText(context, "该曲目还不能直接切换，请重新点播", Toast.LENGTH_SHORT).show()
                }
                showQueue = false
            },
            onRemoveTrack = { item ->
                player.removeFromQueue(item.id)
            },
        )
    }

    AnimatedVisibility(
        visible = track != null,
        enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 3 },
    ) {
        if (track == null) return@AnimatedVisibility

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MiniBarShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = MiniBarShape,
                ),
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

            val progress = if (state.durationMs > 0) {
                (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            onClick = onExpand,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = track.coverUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CoverShape),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${track.artist} · ${state.playMode.label}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (state.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                IconButton(
                    onClick = {
                        player.cyclePlayMode()
                        Toast.makeText(context, player.state.value.playMode.label, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = when (state.playMode) {
                            PlayMode.Sequential -> Icons.Outlined.Repeat
                            PlayMode.Shuffle -> Icons.Outlined.Shuffle
                            PlayMode.Single -> Icons.Outlined.RepeatOne
                        },
                        contentDescription = state.playMode.label,
                        tint = if (state.playMode == PlayMode.Sequential) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }

                IconButton(
                    onClick = player::playPrev,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "上一首",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(
                    onClick = player::toggle,
                    modifier = Modifier
                        .size(40.dp)
                        .scale(playScale),
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "播放/暂停",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(
                    onClick = player::playNext,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "下一首",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Box(contentAlignment = Alignment.TopEnd) {
                    IconButton(
                        onClick = { showQueue = true },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.QueueMusic,
                            contentDescription = "播放队列",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    if (queueCount > 0) {
                        Text(
                            text = if (queueCount > 99) "99+" else queueCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .padding(top = 4.dp, end = 2.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }

            if (!state.error.isNullOrBlank()) {
                Text(
                    text = state.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
                )
            }
        }
    }
}
