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
import androidx.media3.session.MediaSession
import com.shaw.zonetune.data.api.BiliClient
import com.shaw.zonetune.data.cookie.CookieStore
import com.shaw.zonetune.data.model.Track
import com.shaw.zonetune.data.playback.PlaybackSession
import com.shaw.zonetune.data.playback.PlaybackSessionStore
import com.shaw.zonetune.util.isAutomotiveDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class PlayerUiState(
    val current: Track? = null,
    val queue: List<Track> = emptyList(),
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val errorNeedsLogin: Boolean = false,
    val playMode: PlayMode = PlayMode.Sequential,
)

/**
 * Foreground playback controller using Media3 ExoPlayer.
 * On car (Zeekr), keeps an in-process [MediaSession] for steering-wheel media keys
 * without starting a phone-style foreground service.
 */
class PlayerController(
    context: Context,
    private val sessionStore: PlaybackSessionStore,
    private val cookieStore: CookieStore,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val automotive = appContext.isAutomotiveDevice()

    /** Resolve blank/expired audio URLs before play. Set from app startup. */
    var resolveTrack: (suspend (Track) -> Track)? = null

    private val dataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(BiliClient.USER_AGENT)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(20_000)

    private var resolveRetryForId: String? = null
    private var resumeTrackId: String? = null
    private var resumePositionMs: Long = 0
    private var localMediaSession: MediaSession? = null

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
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()
        .also { exo ->
            exo.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.update { it.copy(isPlaying = isPlaying) }
                    if (!isPlaying) persistSession()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> _state.update { it.copy(loading = true) }
                        Player.STATE_READY -> {
                            resolveRetryForId = null
                            applyPendingResumeSeek(exo)
                            _state.update { it.copy(loading = false, error = null, errorNeedsLogin = false) }
                        }
                        Player.STATE_ENDED -> onTrackEnded()
                        Player.STATE_IDLE -> Unit
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    val cause = error.cause?.message ?: error.message ?: "unknown"
                    val current = _state.value.current
                    // CDN links expire; refresh once automatically.
                    if (current != null && resolveRetryForId != current.id) {
                        resolveRetryForId = current.id
                        resolveAndStart(current, forceRefresh = true)
                        return
                    }
                    resolveRetryForId = null
                    publishError("播放失败: $cause")
                }
            })
        }

    /** Shared wrapper used by MediaSession (car keys / notification). */
    val sessionPlayer: QueueAwarePlayer = QueueAwarePlayer(player, this)

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    init {
        // Car installs (Jetuo / Zeekr): MediaSession in-process for steering keys.
        // Phone keeps using PlaybackService for notification controls.
        if (automotive) {
            ensureLocalMediaSession()
        }
        scope.launch(Dispatchers.IO) {
            runCatching { restoreSession(sessionStore.load()) }
        }
    }

    /** In-process session so Zeekr steering keys can reach us without FGS. */
    private fun ensureLocalMediaSession() {
        if (localMediaSession != null) return
        runCatching {
            localMediaSession = MediaSession.Builder(appContext, sessionPlayer)
                .setId("zonetune_local_session")
                .build()
        }
    }

    fun restoreSession(session: PlaybackSession) {
        val mode = runCatching { PlayMode.valueOf(session.playMode) }.getOrDefault(PlayMode.Sequential)
        val current = session.queue.firstOrNull { it.id == session.currentId }
            ?: session.queue.firstOrNull()
        resumeTrackId = current?.id
        resumePositionMs = session.positionMs.coerceAtLeast(0)
        _state.update {
            it.copy(
                queue = session.queue,
                current = current,
                playMode = mode,
                isPlaying = false,
                loading = false,
                error = null,
                errorNeedsLogin = false,
                positionMs = resumePositionMs,
                durationMs = (current?.durationSec ?: 0) * 1000L,
            )
        }
    }

    fun play(track: Track) {
        upsertCurrent(track)
        scope.launch(Dispatchers.IO) {
            runCatching { sessionStore.addRecent(track) }
        }
        // Always refresh CDN URL — bilibili audio links expire quickly.
        resolveAndStart(track, forceRefresh = true)
    }

    /**
     * Append [queue] onto the current playlist (or set it when empty), then play [start].
     * Re-adding the same collection replaces that collection's previous block.
     */
    fun playQueue(queue: List<Track>, start: Track) {
        require(queue.isNotEmpty()) { "queue empty" }
        val collectionId = queue.firstOrNull { it.collectionId.isNotBlank() }?.collectionId.orEmpty()
        val merged = _state.value.queue.toMutableList()
        if (collectionId.isNotBlank()) {
            merged.removeAll { it.collectionId == collectionId }
        }
        val existingIds = merged.mapTo(HashSet()) { it.id }
        queue.forEach { track ->
            if (track.id in existingIds) {
                val index = merged.indexOfFirst { it.id == track.id }
                if (index >= 0) merged[index] = track
            } else {
                merged.add(track)
                existingIds.add(track.id)
            }
        }
        _state.update {
            it.copy(
                queue = merged,
                current = start,
                loading = true,
                error = null,
                durationMs = start.durationSec * 1000L,
            )
        }
        persistSession()
        scope.launch(Dispatchers.IO) {
            runCatching { sessionStore.addRecent(start) }
        }
        resolveAndStart(start, forceRefresh = true)
    }

    fun playResolved(track: Track) {
        resolveAndStart(track, forceRefresh = true)
    }

    private fun resolveAndStart(track: Track, forceRefresh: Boolean) {
        if (track.id != resumeTrackId) {
            resumeTrackId = null
            resumePositionMs = 0
        }
        val resolver = resolveTrack
        if (resolver == null) {
            publishError("缺少音频地址")
            return
        }
        scope.launch {
            _state.update { it.copy(loading = true, error = null, errorNeedsLogin = false, current = track) }
            try {
                val playable = withContext(Dispatchers.IO) {
                    refreshMediaRequestHeaders()
                    val seed = if (forceRefresh) track.copy(audioUrl = "") else track
                    resolver(seed).let { resolved ->
                        resolved.copy(audioUrl = normalizeStreamUrl(resolved.audioUrl))
                    }
                }
                if (playable.audioUrl.isBlank()) {
                    publishError("缺少音频地址")
                    return@launch
                }
                upsertCurrent(playable)
                startPlayer(playable)
            } catch (e: Exception) {
                resolveRetryForId = null
                publishError(e.message ?: "播放失败")
            }
        }
    }

    private fun upsertCurrent(track: Track) {
        val queue = _state.value.queue.toMutableList()
        val index = queue.indexOfFirst { it.id == track.id }
        if (index >= 0) {
            queue[index] = track
        } else {
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
        persistSession()
    }

    fun toggle() {
        val current = _state.value.current ?: return
        val mediaReady = player.mediaItemCount > 0 &&
            player.currentMediaItem?.mediaId == current.id &&
            player.playbackState != Player.STATE_IDLE
        if (!mediaReady) {
            resolveAndStart(current, forceRefresh = true)
            return
        }
        runOnMain {
            if (player.isPlaying) player.pause() else {
                player.playWhenReady = true
                player.play()
            }
        }
    }

    fun pause() = runOnMain { player.pause() }

    fun seekTo(positionMs: Long) = runOnMain { player.seekTo(positionMs) }

    fun cyclePlayMode() {
        _state.update { it.copy(playMode = it.playMode.next()) }
        persistSession()
    }

    fun setPlayMode(mode: PlayMode) {
        _state.update { it.copy(playMode = mode) }
        persistSession()
    }

    fun addToQueue(track: Track) {
        _state.update { state ->
            if (state.queue.any { it.id == track.id }) state
            else state.copy(queue = state.queue + track)
        }
        persistSession()
    }

    fun removeFromQueue(trackId: String) {
        applyQueueFilter { it.id != trackId }
    }

    fun removeCollection(collectionId: String) {
        if (collectionId.isBlank()) return
        applyQueueFilter { it.collectionId != collectionId }
    }

    fun clearQueue() {
        runOnMain {
            player.stop()
            player.clearMediaItems()
        }
        _state.update {
            it.copy(
                queue = emptyList(),
                current = null,
                isPlaying = false,
                positionMs = 0,
                durationMs = 0,
                loading = false,
                error = null,
            )
        }
        persistSession()
    }

    private fun applyQueueFilter(keep: (Track) -> Boolean) {
        val previousCurrentId = _state.value.current?.id
        _state.update { state ->
            val nextQueue = state.queue.filter(keep)
            val currentRemoved = state.current?.let { keep(it).not() } == true
            state.copy(
                queue = nextQueue,
                current = if (currentRemoved) null else state.current,
            )
        }
        persistSession()
        if (_state.value.current == null && previousCurrentId != null) {
            runOnMain {
                player.stop()
                player.clearMediaItems()
            }
            val fallback = _state.value.queue.firstOrNull()
            if (fallback != null) {
                playResolved(fallback)
            } else {
                _state.update {
                    it.copy(isPlaying = false, positionMs = 0, durationMs = 0, loading = false)
                }
                persistSession()
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
        persistSession()
        start?.let { playResolved(it) }
    }

    fun playNext() {
        val next = pickNextTrack(forward = true) ?: return
        when {
            next.id == _state.value.current?.id && _state.value.playMode == PlayMode.Single -> replayCurrent()
            else -> playResolved(next)
        }
    }

    fun playPrev() {
        val state = _state.value
        if (state.positionMs > 3_000 && state.playMode != PlayMode.Shuffle) {
            seekTo(0)
            return
        }
        val prev = pickNextTrack(forward = false) ?: return
        playResolved(prev)
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
        if (message.isNullOrBlank()) {
            _state.update { it.copy(error = null, errorNeedsLogin = false, loading = false) }
        } else {
            publishError(message)
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null, errorNeedsLogin = false) }
    }

    fun retryCurrent() {
        val current = _state.value.current ?: return
        clearError()
        resolveAndStart(current, forceRefresh = true)
    }

    fun release() {
        runOnMain {
            localMediaSession?.release()
            localMediaSession = null
            player.release()
        }
    }

    /** Activity / car key events (steering wheel often injects KeyEvent to foreground app). */
    fun handleMediaKeyCode(keyCode: Int): Boolean {
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                if (!state.value.isPlaying) toggle()
                true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (state.value.isPlaying) pause()
                true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            android.view.KeyEvent.KEYCODE_HEADSETHOOK,
            -> {
                toggle()
                true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                playNext()
                true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                playPrev()
                true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_STOP -> {
                pause()
                true
            }
            else -> false
        }
    }

    private fun startPlayer(track: Track) {
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
            player.play()
        }
    }

    /** Must run off the main thread — CookieStore.getBlocking uses runBlocking. */
    private fun refreshMediaRequestHeaders() {
        val headers = linkedMapOf(
            "Referer" to BiliClient.REFERER,
            "Origin" to BiliClient.ORIGIN,
        )
        val cookie = buildList {
            cookieStore.getBlocking("SESSDATA")?.takeIf { it.isNotBlank() }?.let { add("SESSDATA=$it") }
            cookieStore.getBlocking("bili_jct")?.takeIf { it.isNotBlank() }?.let { add("bili_jct=$it") }
            cookieStore.getBlocking("DedeUserID")?.takeIf { it.isNotBlank() }?.let { add("DedeUserID=$it") }
        }.joinToString("; ")
        if (cookie.isNotBlank()) {
            headers["Cookie"] = cookie
        }
        dataSourceFactory.setDefaultRequestProperties(headers)
    }

    private fun normalizeStreamUrl(url: String): String {
        if (url.isBlank()) return url
        return if (url.startsWith("http://")) url.replaceFirst("http://", "https://") else url
    }

    private fun persistSession() {
        val snapshot = _state.value
        val positionMs = player.currentPosition.coerceAtLeast(0)
        scope.launch(Dispatchers.IO) {
            runCatching {
                sessionStore.saveSession(
                    queue = snapshot.queue,
                    currentId = snapshot.current?.id,
                    playMode = snapshot.playMode,
                    positionMs = positionMs,
                )
            }
        }
    }

    private fun applyPendingResumeSeek(exo: ExoPlayer) {
        val trackId = resumeTrackId ?: return
        val position = resumePositionMs
        if (position <= 0) {
            resumeTrackId = null
            return
        }
        if (exo.currentMediaItem?.mediaId != trackId) return
        val duration = exo.duration
        val target = if (duration > 0) position.coerceAtMost(duration - 1_000) else position
        if (target > 1_000) {
            exo.seekTo(target)
            _state.update { it.copy(positionMs = target) }
        }
        resumeTrackId = null
        resumePositionMs = 0
    }

    private fun publishError(message: String) {
        val needsLogin = message.contains("登录") ||
            message.contains("-403") ||
            message.contains("v_voucher", ignoreCase = true)
        _state.update {
            it.copy(
                loading = false,
                isPlaying = false,
                error = message,
                errorNeedsLogin = needsLogin,
            )
        }
    }

    private fun onTrackEnded() {
        when (_state.value.playMode) {
            PlayMode.Single -> replayCurrent()
            PlayMode.Sequential, PlayMode.Shuffle -> {
                val next = pickNextTrack(forward = true)
                if (next != null && next.id != _state.value.current?.id) {
                    playResolved(next)
                } else if (next != null && _state.value.queue.size == 1) {
                    // single-item sequential: loop that item
                    playResolved(next)
                }
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
