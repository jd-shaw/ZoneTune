package com.shaw.zonetune.util

import java.util.Locale

fun formatDurationSec(sec: Int): String {
    if (sec <= 0) return ""
    val hours = sec / 3600
    val minutes = (sec % 3600) / 60
    val seconds = sec % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun formatPlayCount(count: Long): String {
    if (count <= 0) return ""
    return when {
        count >= 100_000_000 -> {
            val v = count / 100_000_000.0
            trimTrailingZero(String.format(Locale.CHINA, "%.1f", v)) + "亿"
        }
        count >= 10_000 -> {
            val v = count / 10_000.0
            trimTrailingZero(String.format(Locale.CHINA, "%.1f", v)) + "万"
        }
        else -> count.toString()
    }
}

fun trackMetaLine(
    artist: String,
    durationSec: Int = 0,
    playCount: Long = 0,
    episodeCountText: String = "",
): String {
    return listOfNotNull(
        artist.takeIf { it.isNotBlank() },
        formatDurationSec(durationSec).takeIf { it.isNotBlank() },
        formatPlayCount(playCount).takeIf { it.isNotBlank() }?.let { "${it}播放" },
        episodeCountText.trim().takeIf { it.isNotBlank() },
    ).joinToString(" · ")
}

private fun trimTrailingZero(raw: String): String =
    if (raw.endsWith(".0")) raw.dropLast(2) else raw
