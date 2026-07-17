package com.shaw.zonetune.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.shaw.zonetune.data.api.BiliClient
import com.shaw.zonetune.data.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

data class PlayerUiState(
    val current: Track? = null,
    val queue: List<Track> = emptyList(),
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val playMode: PlayMode = PlayMode.Sequential,
)

/**
 * Foreground playback controller using Media3 ExoPlayer.
 * Audio requests include Bilibili Referer header.
 */
class PlayerController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val dataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(BiliClient.USER_AGENT)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(20_000)
        .setDefaultRequestProperties(
            mapOf(
                "Referer" to BiliClient.REFERER,
                "Origin" to BiliClient.ORIGIN,
            ),
        )

    val player: ExoPlayer = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .also { exo ->
            exo.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.update { it.copy(isPlaying = isPlaying) }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> _state.update { it.copy(loading = true) }
                        Player.STATE_READY -> _state.update { it.copy(loading = false, error = null) }
                        Player.STATE_ENDED -> onTrackEnded()
                        Player.STATE_IDLE -> Unit
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    val cause = error.cause?.message ?: error.message ?: "unknown"
                    _state.update {
                        it.copy(
                            loading = false,
                            isPlaying = false,
                            error = "播放失败: $cause",
                        )
                    }
                }
            })
        }

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    fun play(track: Track) {
        if (track.audioUrl.isBlank()) {
            _state.update { it.copy(error = "缺少音频地址", loading = false) }
            return
        }
        val queue = _state.value.queue.toMutableList()
        if (queue.none { it.id == track.id }) {
            queue.add(track)
        }
        _state.update {
            it.copy(
                current = track,
                queue = queue,
                loading = true,
                error = null,
                durationMs = track.durationSec * 1000L,
            )
        }
        val mediaItem = MediaItem.Builder()
            .setUri(track.audioUrl)
            .setMediaId(track.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(track.coverUrl.takeIf { it.isNotBlank() }?.let { android.net.Uri.parse(it) })
                    .build(),
            )
            .build()

        runOnMain {
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        }
    }

    fun toggle() {
        runOnMain {
            if (player.isPlaying) player.pause() else player.play()
        }
    }

    fun pause() = runOnMain { player.pause() }

    fun seekTo(positionMs: Long) = runOnMain { player.seekTo(positionMs) }

    fun cyclePlayMode() {
        _state.update { it.copy(playMode = it.playMode.next()) }
    }

    fun setPlayMode(mode: PlayMode) {
        _state.update { it.copy(playMode = mode) }
    }

    fun addToQueue(track: Track) {
        _state.update { state ->
            if (state.queue.any { it.id == track.id }) state
            else state.copy(queue = state.queue + track)
        }
    }

    fun removeFromQueue(trackId: String) {
        _state.update { state ->
            val nextQueue = state.queue.filterNot { it.id == trackId }
            val currentRemoved = state.current?.id == trackId
            state.copy(
                queue = nextQueue,
                current = if (currentRemoved) null else state.current,
            )
        }
        if (_state.value.current == null) {
            runOnMain {
                player.stop()
                player.clearMediaItems()
            }
            val fallback = _state.value.queue.firstOrNull()
            if (fallback != null) {
                play(fallback)
            } else {
                _state.update {
                    it.copy(isPlaying = false, positionMs = 0, durationMs = 0, loading = false)
                }
            }
        }
    }

    fun replaceQueue(tracks: List<Track>, start: Track? = tracks.firstOrNull()) {
        _state.update {
            it.copy(
                queue = tracks,
                current = start ?: it.current,
            )
        }
        start?.let { play(it) }
    }

    fun playNext() {
        val next = pickNextTrack(forward = true) ?: return
        when {
            next.id == _state.value.current?.id && _state.value.playMode == PlayMode.Single -> replayCurrent()
            else -> play(next)
        }
    }

    fun playPrev() {
        val state = _state.value
        if (state.positionMs > 3_000 && state.playMode != PlayMode.Shuffle) {
            seekTo(0)
            return
        }
        val prev = pickNextTrack(forward = false) ?: return
        play(prev)
    }

    fun refreshProgress() {
        _state.update {
            it.copy(
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = player.duration.takeIf { d -> d > 0 } ?: it.durationMs,
                isPlaying = player.isPlaying,
            )
        }
    }

    fun setLoading(loading: Boolean) {
        _state.update { it.copy(loading = loading) }
    }

    fun setError(message: String?) {
        _state.update { it.copy(error = message, loading = false) }
    }

    fun release() {
        runOnMain { player.release() }
    }

    private fun onTrackEnded() {
        when (_state.value.playMode) {
            PlayMode.Single -> replayCurrent()
            PlayMode.Sequential, PlayMode.Shuffle -> {
                val next = pickNextTrack(forward = true)
                if (next != null) play(next)
            }
        }
    }

    private fun replayCurrent() {
        runOnMain {
            player.seekTo(0)
            player.playWhenReady = true
            player.play()
        }
        _state.update { it.copy(positionMs = 0, isPlaying = true) }
    }

    private fun pickNextTrack(forward: Boolean): Track? {
        val state = _state.value
        val queue = state.queue
        if (queue.isEmpty()) return state.current
        val currentId = state.current?.id
        val idx = queue.indexOfFirst { it.id == currentId }.takeIf { it >= 0 } ?: 0

        return when (state.playMode) {
            PlayMode.Single -> state.current ?: queue.getOrNull(idx)

            PlayMode.Sequential -> {
                if (queue.size == 1) return queue.first()
                val nextIdx = if (forward) {
                    (idx + 1) % queue.size
                } else {
                    (idx - 1 + queue.size) % queue.size
                }
                queue[nextIdx]
            }

            PlayMode.Shuffle -> {
                if (queue.size == 1) return queue.first()
                var nextIdx = Random.nextInt(queue.size)
                var guard = 0
                while (queue[nextIdx].id == currentId && guard < 8) {
                    nextIdx = Random.nextInt(queue.size)
                    guard++
                }
                queue[nextIdx]
            }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
