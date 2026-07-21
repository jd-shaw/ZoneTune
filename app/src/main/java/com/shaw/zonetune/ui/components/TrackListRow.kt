package com.shaw.zonetune.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shaw.zonetune.data.model.Track
import com.shaw.zonetune.ui.theme.CoverShape
import com.shaw.zonetune.util.trackMetaLine

@Composable
fun TrackListRow(
    track: Track,
    onClick: () -> Unit,
    onAddToQueue: (() -> Unit)? = null,
    showDivider: Boolean = true,
    enterDelayMs: Int = 0,
) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(track.id) {
        alpha.snapTo(0f)
        alpha.animateTo(1f, animationSpec = tween(durationMillis = 280, delayMillis = enterDelayMs))
    }

    val meta = remember(
        track.artist,
        track.durationSec,
        track.playCount,
        track.episodeCountText,
    ) {
        trackMetaLine(
            artist = track.artist,
            durationSec = track.durationSec,
            playCount = track.playCount,
            episodeCountText = track.episodeCountText,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha.value),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = onClick,
                )
                .padding(start = 20.dp, end = if (onAddToQueue != null) 4.dp else 20.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = track.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CoverShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (meta.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = meta,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (onAddToQueue != null) {
                IconButton(onClick = onAddToQueue) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.PlaylistAdd,
                        contentDescription = "加入队列",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 90.dp, end = 20.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
