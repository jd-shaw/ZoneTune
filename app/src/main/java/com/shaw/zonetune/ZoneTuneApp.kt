package com.shaw.zonetune

import android.app.Application
import android.widget.Toast
import com.shaw.zonetune.data.api.BiliClient
import com.shaw.zonetune.data.api.BiliRepository
import com.shaw.zonetune.data.cookie.CookieStore
import com.shaw.zonetune.data.favorite.FavoriteStore
import com.shaw.zonetune.data.model.Track
import com.shaw.zonetune.data.playback.PlaybackSessionStore
import com.shaw.zonetune.data.search.SearchHistoryStore
import com.shaw.zonetune.player.PlayerController
import com.shaw.zonetune.util.isNetworkAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CollectionPlayRequest(
    val title: String,
    val count: Int,
    val prepared: BiliRepository.PreparedPlayback,
)

class ZoneTuneApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var cookieStore: CookieStore
        private set
    lateinit var favoriteStore: FavoriteStore
        private set
    lateinit var searchHistoryStore: SearchHistoryStore
        private set
    lateinit var playbackSessionStore: PlaybackSessionStore
        private set
    lateinit var biliClient: BiliClient
        private set
    lateinit var biliRepository: BiliRepository
        private set
    lateinit var playerController: PlayerController
        private set

    private val _pendingCollectionPlay = MutableStateFlow<CollectionPlayRequest?>(null)
    val pendingCollectionPlay: StateFlow<CollectionPlayRequest?> = _pendingCollectionPlay.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        instance = this
        cookieStore = CookieStore(this)
        favoriteStore = FavoriteStore(this)
        searchHistoryStore = SearchHistoryStore(this)
        playbackSessionStore = PlaybackSessionStore(this)
        biliClient = BiliClient(cookieStore)
        biliRepository = BiliRepository(biliClient, cookieStore)
        playerController = PlayerController(this, playbackSessionStore, cookieStore)
        playerController.resolveTrack = { track ->
            biliRepository.buildPlayableTrack(track)
        }
    }

    fun playTrack(track: Track, expandCollection: Boolean? = null) {
        appScope.launch {
            val player = playerController
            if (!isNetworkAvailable()) {
                player.setError("网络不可用，请检查连接后重试")
                return@launch
            }
            player.setLoading(true)
            player.setError(null)
            try {
                val prepared = withContext(Dispatchers.IO) {
                    biliRepository.preparePlayback(track)
                }
                when {
                    prepared.queue.size <= 1 -> player.play(prepared.start)
                    expandCollection == true -> player.playQueue(prepared.queue, prepared.start)
                    expandCollection == false -> player.play(prepared.start.asSingle())
                    else -> {
                        player.setLoading(false)
                        _pendingCollectionPlay.value = CollectionPlayRequest(
                            title = prepared.start.collectionTitle.ifBlank { prepared.start.title },
                            count = prepared.queue.size,
                            prepared = prepared,
                        )
                    }
                }
            } catch (e: Exception) {
                player.setError(e.message ?: "播放失败")
                player.setLoading(false)
            }
        }
    }

    fun confirmCollectionPlay(expand: Boolean) {
        val request = _pendingCollectionPlay.value ?: return
        _pendingCollectionPlay.value = null
        if (expand) {
            playerController.playQueue(request.prepared.queue, request.prepared.start)
        } else {
            playerController.play(request.prepared.start.asSingle())
        }
    }

    fun dismissCollectionPlay() {
        _pendingCollectionPlay.value = null
    }

    fun addTrackToQueue(track: Track) {
        playerController.addToQueue(track)
        Toast.makeText(this, "已加入队列", Toast.LENGTH_SHORT).show()
    }

    private fun Track.asSingle(): Track = copy(
        collectionId = "",
        collectionTitle = "",
        episodeCountText = "",
    )

    companion object {
        lateinit var instance: ZoneTuneApp
            private set
    }
}
