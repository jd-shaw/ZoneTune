package com.shaw.zonetune.data.playback

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shaw.zonetune.data.model.Track
import com.shaw.zonetune.player.PlayMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.playbackDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "zonetune_playback",
)

@Serializable
data class PlaybackSession(
    val queue: List<Track> = emptyList(),
    val currentId: String? = null,
    val playMode: String = PlayMode.Sequential.name,
    val recent: List<Track> = emptyList(),
    val positionMs: Long = 0,
)

/**
 * Persists queue / current / play mode / recent history.
 * Audio URLs are stripped before save (CDN links expire).
 */
class PlaybackSessionStore(context: Context) {
    private val store = context.applicationContext.playbackDataStore
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object Keys {
        val SESSION = stringPreferencesKey("session_json")
    }

    val sessionFlow: Flow<PlaybackSession> = store.data.map { prefs ->
        decode(prefs[Keys.SESSION])
    }

    val recentFlow: Flow<List<Track>> = sessionFlow.map { it.recent }

    suspend fun load(): PlaybackSession = sessionFlow.first()

    suspend fun saveSession(
        queue: List<Track>,
        currentId: String?,
        playMode: PlayMode,
        positionMs: Long = 0,
    ) {
        val current = load()
        persist(
            current.copy(
                queue = queue.map { it.withoutAudio() },
                currentId = currentId,
                playMode = playMode.name,
                positionMs = positionMs.coerceAtLeast(0),
            ),
        )
    }

    suspend fun addRecent(track: Track) {
        val current = load()
        val nextRecent = buildList {
            add(track.withoutAudio())
            current.recent
                .filterNot { it.id == track.id }
                .forEach(::add)
        }.take(MaxRecent)
        persist(current.copy(recent = nextRecent))
    }

    suspend fun clearRecent() {
        persist(load().copy(recent = emptyList()))
    }

    private suspend fun persist(session: PlaybackSession) {
        store.edit { prefs ->
            prefs[Keys.SESSION] = json.encodeToString(PlaybackSession.serializer(), session)
        }
    }

    private fun decode(raw: String?): PlaybackSession {
        if (raw.isNullOrBlank()) return PlaybackSession()
        return runCatching {
            json.decodeFromString(PlaybackSession.serializer(), raw)
        }.getOrDefault(PlaybackSession())
    }

    private fun Track.withoutAudio(): Track = copy(audioUrl = "")

    companion object {
        private const val MaxRecent = 50
    }
}
