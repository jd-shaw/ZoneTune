package com.shaw.zonetune.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shaw.zonetune.data.model.Track
import com.shaw.zonetune.player.PlayMode
import com.shaw.zonetune.ui.theme.CoverShape
import kotlinx.coroutines.yield

private data class QueueBlock(
    val key: String,
    val collectionId: String,
    val title: String,
    val tracks: List<Track>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: List<Track>,
    currentId: String?,
    playMode: PlayMode,
    onDismiss: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onRemoveTrack: (Track) -> Unit,
    onRemoveCollection: (String) -> Unit,
    onClearQueue: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val blocks = remember(queue) { groupQueueBlocks(queue) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    // Ensure the playing collection starts expanded so we can scroll to the track.
    LaunchedEffect(currentId, blocks) {
        if (currentId.isNullOrBlank()) return@LaunchedEffect
        blocks.forEach { block ->
            val isCollection = block.collectionId.isNotBlank() && block.tracks.size > 1
            if (isCollection && block.tracks.any { it.id == currentId }) {
                expanded[block.key] = true
            }
        }
    }

    LaunchedEffect(currentId, blocks, expanded.toMap()) {
        if (currentId.isNullOrBlank()) return@LaunchedEffect
        val target = visibleIndexOfCurrent(blocks, expanded, currentId) ?: return@LaunchedEffect
        yield()
        runCatching { listState.animateScrollToItem(target) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "播放队列",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${playMode.label} · ${queue.size} 首",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (queue.isNotEmpty()) {
                    TextButton(onClick = onClearQueue) {
                        Text("清空")
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "队列还是空的",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    blocks.forEach { block ->
                        val isCollection = block.collectionId.isNotBlank() && block.tracks.size > 1
                        if (isCollection) {
                            val containsCurrent = block.tracks.any { it.id == currentId }
                            val isExpanded = expanded[block.key] ?: containsCurrent
                            item(key = "header-${block.key}") {
                                CollectionHeader(
                                    title = block.title,
                                    count = block.tracks.size,
                                    expanded = isExpanded,
                                    playing = containsCurrent,
                                    onToggle = { expanded[block.key] = !isExpanded },
                                    onRemove = { onRemoveCollection(block.collectionId) },
                                )
                            }
                            if (isExpanded) {
                                items(
                                    items = block.tracks,
                                    key = { it.id },
                                ) { track ->
                                    QueueRow(
                                        index = queue.indexOfFirst { it.id == track.id } + 1,
                                        track = track,
                                        playing = track.id == currentId,
                                        indented = true,
                                        onClick = { onPlayTrack(track) },
                                        onRemove = { onRemoveTrack(track) },
                                    )
                                }
                            }
                        } else {
                            items(
                                items = block.tracks,
                                key = { it.id },
                            ) { track ->
                                QueueRow(
                                    index = queue.indexOfFirst { it.id == track.id } + 1,
                                    track = track,
                                    playing = track.id == currentId,
                                    indented = false,
                                    onClick = { onPlayTrack(track) },
                                    onRemove = { onRemoveTrack(track) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    playing: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (playing) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onToggle,
            )
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = if (expanded) "收起" else "展开",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                color = if (playing) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = "合集 · $count 首" + if (playing) " · 在播" else "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Outlined.DeleteSweep,
                contentDescription = "移除合集",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QueueRow(
    index: Int,
    track: Track,
    playing: Boolean,
    indented: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            )
            .background(
                if (playing) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .padding(
                start = if (indented) 28.dp else 16.dp,
                end = 16.dp,
                top = 10.dp,
                bottom = 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = index.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (playing) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.width(22.dp),
        )
        AsyncImage(
            model = track.coverUrl,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(CoverShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CoverShape),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                color = if (playing) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = track.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (playing) {
            Text(
                text = "在播",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "移出队列",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun visibleIndexOfCurrent(
    blocks: List<QueueBlock>,
    expanded: Map<String, Boolean>,
    currentId: String,
): Int? {
    var index = 0
    blocks.forEach { block ->
        val isCollection = block.collectionId.isNotBlank() && block.tracks.size > 1
        if (isCollection) {
            val containsCurrent = block.tracks.any { it.id == currentId }
            val isExpanded = expanded[block.key] ?: containsCurrent
            if (containsCurrent) {
                if (!isExpanded) return index
                index += 1 // header
                block.tracks.forEach { track ->
                    if (track.id == currentId) return index
                    index += 1
                }
            } else {
                index += 1 // header
                if (isExpanded) index += block.tracks.size
            }
        } else {
            block.tracks.forEach { track ->
                if (track.id == currentId) return index
                index += 1
            }
        }
    }
    return null
}

private fun groupQueueBlocks(queue: List<Track>): List<QueueBlock> {
    if (queue.isEmpty()) return emptyList()
    val blocks = mutableListOf<QueueBlock>()
    var index = 0
    while (index < queue.size) {
        val first = queue[index]
        val collectionId = first.collectionId
        if (collectionId.isBlank()) {
            blocks += QueueBlock(
                key = "single-${first.id}",
                collectionId = "",
                title = first.title,
                tracks = listOf(first),
            )
            index += 1
            continue
        }
        var end = index + 1
        while (end < queue.size && queue[end].collectionId == collectionId) {
            end += 1
        }
        val tracks = queue.subList(index, end).toList()
        blocks += QueueBlock(
            key = "$collectionId@$index",
            collectionId = collectionId,
            title = first.collectionTitle.ifBlank { first.title },
            tracks = tracks,
        )
        index = end
    }
    return blocks
}
