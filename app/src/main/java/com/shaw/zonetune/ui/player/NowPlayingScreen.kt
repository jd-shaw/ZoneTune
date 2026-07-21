package com.shaw.zonetune.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shaw.zonetune.ZoneTuneApp
import com.shaw.zonetune.player.PlayMode
import com.shaw.zonetune.ui.components.BrandMark
import com.shaw.zonetune.ui.components.BrandMarkSize
import com.shaw.zonetune.ui.theme.CoverShape
import com.shaw.zonetune.ui.theme.StudioSteel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun NowPlayingScreen(
    onDismiss: () -> Unit,
) {
    val player = ZoneTuneApp.instance.playerController
    val favoriteStore = ZoneTuneApp.instance.favoriteStore
    val state by player.state.collectAsState()
    val track = state.current
    val scope = rememberCoroutineScope()

    val favoriteIds by favoriteStore.favoritesFlow
        .map { list -> list.map { it.id }.toSet() }
        .collectAsState(initial = emptySet())
    val isFavorite = track?.id?.let { it in favoriteIds } == true

    LaunchedEffect(state.current?.id, state.isPlaying) {
        while (true) {
            player.refreshProgress()
            delay(400)
        }
    }

    var sliderPosition by remember(state.positionMs, state.durationMs) {
        mutableFloatStateOf(
            if (state.durationMs > 0) {
                state.positionMs.toFloat() / state.durationMs.toFloat()
            } else {
                0f
            },
        )
    }
    var sliding by remember { mutableFloatStateOf(-1f) }
    var modeHint by remember { mutableStateOf<String?>(null) }
    var showQueue by remember { mutableStateOf(false) }

    LaunchedEffect(modeHint) {
        if (modeHint != null) {
            delay(1200)
            modeHint = null
        }
    }

    if (showQueue) {
        QueueSheet(
            queue = state.queue.ifEmpty { listOfNotNull(track) },
            currentId = track?.id,
            playMode = state.playMode,
            onDismiss = { showQueue = false },
            onPlayTrack = { item ->
                player.playResolved(item)
                showQueue = false
            },
            onRemoveTrack = { item ->
                player.removeFromQueue(item.id)
            },
            onRemoveCollection = player::removeCollection,
            onClearQueue = player::clearQueue,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        StudioSteel.copy(alpha = 0.10f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "收起",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    BrandMark(size = BrandMarkSize.Compact)
                }
                IconButton(onClick = { showQueue = true }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.QueueMusic,
                        contentDescription = "队列",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (track == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "还没有在播的曲目",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                AsyncImage(
                    model = track.coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(CoverShape),
                    contentScale = ContentScale.Crop,
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(20.dp))

                val displayProgress = if (sliding >= 0f) sliding else sliderPosition
                Slider(
                    value = displayProgress.coerceIn(0f, 1f),
                    onValueChange = { sliding = it },
                    onValueChangeFinished = {
                        val target = sliding
                        sliding = -1f
                        if (target >= 0f && state.durationMs > 0) {
                            player.seekTo((target * state.durationMs).toLong())
                        }
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatMs(
                            if (sliding >= 0f && state.durationMs > 0) {
                                (sliding * state.durationMs).toLong()
                            } else {
                                state.positionMs
                            },
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatMs(state.durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            player.cyclePlayMode()
                            modeHint = player.state.value.playMode.label
                        },
                    ) {
                        Icon(
                            imageVector = when (state.playMode) {
                                PlayMode.Sequential -> Icons.Outlined.Repeat
                                PlayMode.Shuffle -> Icons.Outlined.Shuffle
                                PlayMode.Single -> Icons.Outlined.RepeatOne
                            },
                            contentDescription = state.playMode.label,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    IconButton(onClick = player::playPrev) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "上一首",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp),
                        )
                    }

                    Box(contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = player::toggle,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        ) {
                            if (state.loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Icon(
                                    imageVector = if (state.isPlaying) {
                                        Icons.Default.Pause
                                    } else {
                                        Icons.Default.PlayArrow
                                    },
                                    contentDescription = "播放/暂停",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                        }
                    }

                    IconButton(onClick = player::playNext) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "下一首",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp),
                        )
                    }

                    IconButton(
                        onClick = {
                            scope.launch {
                                favoriteStore.toggle(track)
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (isFavorite) {
                                Icons.Filled.Favorite
                            } else {
                                Icons.Outlined.FavoriteBorder
                            },
                            contentDescription = if (isFavorite) "取消收藏" else "收藏",
                            tint = if (isFavorite) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = modeHint ?: "${state.playMode.label} · 队列 ${state.queue.size.coerceAtLeast(1)} 首",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )

                if (!state.error.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.error ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
