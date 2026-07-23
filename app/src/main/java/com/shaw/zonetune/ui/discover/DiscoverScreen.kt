package com.shaw.zonetune.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.shaw.zonetune.ZoneTuneApp
import com.shaw.zonetune.data.model.Track
import com.shaw.zonetune.ui.components.BrandMark
import com.shaw.zonetune.ui.components.BrandMarkSize
import com.shaw.zonetune.ui.theme.CoverShape
import com.shaw.zonetune.ui.theme.StudioSteel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@Composable
fun DiscoverScreen(
    loggedIn: Boolean,
    userName: String,
    onOpenLogin: () -> Unit,
    onOpenSearch: (String) -> Unit,
    onOpenMineShortcut: (MineShortcut) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    viewModel: DiscoverViewModel = viewModel(factory = DiscoverViewModel.factory()),
) {
    val discoverUi by viewModel.ui.collectAsState()
    val playerState by ZoneTuneApp.instance.playerController.state.collectAsState()
    val favorites by ZoneTuneApp.instance.favoriteStore.favoritesFlow.collectAsState(initial = emptyList())
    val recent by ZoneTuneApp.instance.playbackSessionStore.recentFlow.collectAsState(initial = emptyList())
    val continueTracks = remember(playerState.current, playerState.queue, recent, favorites) {
        buildList {
            playerState.current?.let(::add)
            addAll(playerState.queue)
            addAll(recent)
            addAll(favorites)
        }.distinctBy { it.id }.take(8)
    }
    val pageScroll = rememberScrollState()

    // Infinite waterfall: load next page when scrolling near bottom.
    LaunchedEffect(pageScroll, discoverUi.canViewMoreHot) {
        if (!discoverUi.canViewMoreHot) return@LaunchedEffect
        snapshotFlow { pageScroll.value to pageScroll.maxValue }
            .map { (value, max) ->
                // Avoid auto-loading when the first page still fits on screen.
                max > 900 && value >= max - 640
            }
            .distinctUntilChanged()
            .filter { nearBottom -> nearBottom }
            .collect {
                viewModel.viewMoreHot()
                kotlinx.coroutines.delay(180)
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(pageScroll),
    ) {
        // Header — brand + actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 4.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                BrandMark(size = BrandMarkSize.Hero)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (loggedIn) "录音棚 · $userName" else "搜得到就播得了",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onOpenSearch("") }) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = "搜索",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            TextButton(onClick = onOpenLogin) {
                Text(
                    text = if (loggedIn) "我的" else "登录",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val resumeTrack = playerState.current ?: playerState.queue.firstOrNull()
        val favoriteTrack = favorites.firstOrNull()
        val hotTrack = discoverUi.hotTracks.firstOrNull()
        val workstation = when {
            resumeTrack != null -> WorkstationSlot(
                mode = WorkstationMode.Resume,
                track = resumeTrack,
            )
            favoriteTrack != null -> WorkstationSlot(
                mode = WorkstationMode.Favorite,
                track = favoriteTrack,
            )
            hotTrack != null -> WorkstationSlot(
                mode = WorkstationMode.Hot,
                track = hotTrack,
            )
            discoverUi.loadingHot -> WorkstationSlot(
                mode = WorkstationMode.Loading,
                track = null,
            )
            else -> WorkstationSlot(
                mode = WorkstationMode.Idle,
                track = null,
            )
        }
        WorkstationCard(
            slot = workstation,
            modifier = Modifier.padding(horizontal = 20.dp),
            onClick = {
                val track = workstation.track ?: return@WorkstationCard
                when (workstation.mode) {
                    WorkstationMode.Resume -> {
                        if (playerState.current != null) {
                            onOpenNowPlaying()
                        } else {
                            onPlayTrack(track)
                            onOpenNowPlaying()
                        }
                    }
                    WorkstationMode.Favorite, WorkstationMode.Hot -> {
                        onPlayTrack(track)
                        onOpenNowPlaying()
                    }
                    WorkstationMode.Loading, WorkstationMode.Idle -> Unit
                }
            },
            onRetryHot = viewModel::refreshHot,
        )

        Spacer(modifier = Modifier.height(22.dp))

        // Circular shortcut row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            CircleShortcut("最近", Icons.Outlined.History) {
                onOpenMineShortcut(MineShortcut.Recent)
            }
            CircleShortcut("收藏", Icons.Outlined.FavoriteBorder) {
                onOpenMineShortcut(MineShortcut.Favorites)
            }
            CircleShortcut("队列", Icons.AutoMirrored.Outlined.QueueMusic) {
                onOpenMineShortcut(MineShortcut.Queue)
            }
            CircleShortcut("关键词", Icons.Outlined.Tag) {
                onOpenSearch("")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader(
            title = "继续听",
            action = if (continueTracks.isNotEmpty()) "播放页" else null,
            onAction = if (continueTracks.isNotEmpty()) onOpenNowPlaying else null,
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (continueTracks.isEmpty()) {
            EmptyHint(
                text = "搜一首、播一曲，封面会出现在这里",
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                continueTracks.forEach { track ->
                    CoverCard(
                        track = track,
                        onClick = {
                            onPlayTrack(track)
                            onOpenNowPlaying()
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader(
            title = "今日热门",
            action = "刷新",
            onAction = viewModel::refreshHot,
        )
        Spacer(modifier = Modifier.height(10.dp))

        when {
            discoverUi.loadingHot && discoverUi.hotTracks.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "加载热门中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            discoverUi.hotError != null && discoverUi.hotTracks.isEmpty() -> {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(
                        text = discoverUi.hotError ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = viewModel::refreshHot) {
                        Text(
                            text = "重试",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            else -> {
                HotWaterfall(
                    tracks = discoverUi.visibleHotTracks,
                    onPlay = { track ->
                        onPlayTrack(track)
                        onOpenNowPlaying()
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                if (discoverUi.canViewMoreHot) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

enum class MineShortcut {
    Recent,
    Favorites,
    Local,
    Queue,
}

private enum class WorkstationMode {
    Resume,
    Favorite,
    Hot,
    Loading,
    Idle,
}

private data class WorkstationSlot(
    val mode: WorkstationMode,
    val track: Track?,
)

@Composable
private fun WorkstationCard(
    slot: WorkstationSlot,
    onClick: () -> Unit,
    onRetryHot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clickable = slot.mode == WorkstationMode.Resume ||
        slot.mode == WorkstationMode.Favorite ||
        slot.mode == WorkstationMode.Hot

    val eyebrow = when (slot.mode) {
        WorkstationMode.Resume -> "继续聆听"
        WorkstationMode.Favorite -> "收藏备播"
        WorkstationMode.Hot -> "今日备播"
        WorkstationMode.Loading -> "今日工位"
        WorkstationMode.Idle -> "今日工位"
    }
    val title = when (slot.mode) {
        WorkstationMode.Resume, WorkstationMode.Favorite, WorkstationMode.Hot ->
            slot.track?.title?.ifBlank { "未命名曲目" } ?: "未命名曲目"
        WorkstationMode.Loading -> "正在备好一首"
        WorkstationMode.Idle -> "还没有可播内容"
    }
    val subtitle = when (slot.mode) {
        WorkstationMode.Resume -> slot.track?.artist?.ifBlank { "轻触回到播放台" } ?: "轻触回到播放台"
        WorkstationMode.Favorite -> slot.track?.artist?.ifBlank { "来自你的收藏" } ?: "来自你的收藏"
        WorkstationMode.Hot -> slot.track?.artist?.ifBlank { "来自今日热门" } ?: "来自今日热门"
        WorkstationMode.Loading -> "热门备播加载中"
        WorkstationMode.Idle -> "播过或收藏后，这里会接上"
    }
    val action = when (slot.mode) {
        WorkstationMode.Resume -> "轻触续播"
        WorkstationMode.Favorite, WorkstationMode.Hot -> "轻触播放"
        WorkstationMode.Loading -> null
        WorkstationMode.Idle -> "重试备播"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        StudioSteel,
                        StudioSteel.copy(alpha = 0.92f),
                        StudioSteel.copy(alpha = 0.82f),
                    ),
                ),
            )
            .then(
                if (clickable) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = MaterialTheme.colorScheme.onPrimary),
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (action != null) {
                Spacer(modifier = Modifier.height(10.dp))
                if (slot.mode == WorkstationMode.Idle) {
                    Text(
                        text = action,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f),
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false),
                            onClick = onRetryHot,
                        ),
                    )
                } else {
                    Text(
                        text = action,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f),
                    )
                }
            } else if (slot.mode == WorkstationMode.Loading) {
                Spacer(modifier = Modifier.height(10.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CoverShape)
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f))
                .border(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f), CoverShape),
            contentAlignment = Alignment.Center,
        ) {
            if (!slot.track?.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = slot.track?.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = when (slot.mode) {
                        WorkstationMode.Favorite -> Icons.Outlined.FavoriteBorder
                        WorkstationMode.Hot, WorkstationMode.Loading, WorkstationMode.Idle ->
                            Icons.AutoMirrored.Outlined.QueueMusic
                        WorkstationMode.Resume -> Icons.AutoMirrored.Outlined.QueueMusic
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun CircleShortcut(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(
                    text = action,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun HotWaterfall(
    tracks: List<Track>,
    onPlay: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (left, right) = remember(tracks) { splitWaterfallColumns(tracks) }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            left.forEach { track ->
                WaterfallCard(track = track, onClick = { onPlay(track) })
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            right.forEach { track ->
                WaterfallCard(track = track, onClick = { onPlay(track) })
            }
        }
    }
}

private fun splitWaterfallColumns(tracks: List<Track>): Pair<List<Track>, List<Track>> {
    val left = mutableListOf<Track>()
    val right = mutableListOf<Track>()
    var leftWeight = 0
    var rightWeight = 0
    tracks.forEach { track ->
        val weight = 120 + track.title.length * 2 + (track.id.hashCode().and(0x1F))
        if (leftWeight <= rightWeight) {
            left += track
            leftWeight += weight
        } else {
            right += track
            rightWeight += weight
        }
    }
    return left to right
}

@Composable
private fun WaterfallCard(
    track: Track,
    onClick: () -> Unit,
) {
    val aspect = when (track.id.hashCode().and(3)) {
        0 -> 0.72f
        1 -> 0.88f
        2 -> 1.0f
        else -> 0.80f
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            ),
    ) {
        AsyncImage(
            model = track.coverUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            contentScale = ContentScale.Crop,
        )
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(
                text = track.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = track.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyHint(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun CoverCard(
    track: Track,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            ),
    ) {
        AsyncImage(
            model = track.coverUrl,
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .clip(CoverShape),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = track.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = track.artist,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
