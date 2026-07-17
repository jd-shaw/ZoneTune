package com.shaw.zonetune.data.favorite

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shaw.zonetune.data.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.favoriteDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "zonetune_favorites",
)

/**
 * Local favorites (not Bilibili cloud folders).
 * Audio URLs may expire; callers should resolve playable URLs before play.
 */
class FavoriteStore(context: Context) {
    private val store = context.applicationContext.favoriteDataStore
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object Keys {
        val TRACKS = stringPreferencesKey("tracks_json")
    }

    val favoritesFlow: Flow<List<Track>> = store.data.map { prefs ->
        decode(prefs[Keys.TRACKS])
    }

    suspend fun list(): List<Track> = favoritesFlow.first()

    suspend fun contains(trackId: String): Boolean =
        list().any { it.id == trackId }

    suspend fun toggle(track: Track): Boolean {
        val current = list().toMutableList()
        val index = current.indexOfFirst { it.id == track.id }
        val nowFavorite = if (index >= 0) {
            current.removeAt(index)
            false
        } else {
            // Drop ephemeral audioUrl; resolve again on play.
            current.add(0, track.copy(audioUrl = ""))
            true
        }
        persist(current)
        return nowFavorite
    }

    suspend fun remove(trackId: String) {
        persist(list().filterNot { it.id == trackId })
    }

    private suspend fun persist(tracks: List<Track>) {
        store.edit { prefs ->
            prefs[Keys.TRACKS] = json.encodeToString(ListSerializer(Track.serializer()), tracks)
        }
    }

    private fun decode(raw: String?): List<Track> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(Track.serializer()), raw)
        }.getOrDefault(emptyList())
    }
}
