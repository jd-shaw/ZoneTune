package com.shaw.zonetune.data.search

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.searchHistoryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "zonetune_search_history",
)

class SearchHistoryStore(context: Context) {
    private val store = context.applicationContext.searchHistoryDataStore
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object Keys {
        val QUERIES = stringPreferencesKey("queries_json")
    }

    val historyFlow: Flow<List<String>> = store.data.map { prefs ->
        decode(prefs[Keys.QUERIES])
    }

    suspend fun list(): List<String> = historyFlow.first()

    suspend fun add(query: String) {
        val keyword = query.trim()
        if (keyword.isEmpty()) return
        val next = buildList {
            add(keyword)
            list().filterNot { it.equals(keyword, ignoreCase = true) }.forEach(::add)
        }.take(MaxItems)
        persist(next)
    }

    suspend fun remove(query: String) {
        persist(list().filterNot { it.equals(query, ignoreCase = true) })
    }

    suspend fun clear() {
        persist(emptyList())
    }

    private suspend fun persist(items: List<String>) {
        store.edit { prefs ->
            prefs[Keys.QUERIES] = json.encodeToString(
                ListSerializer(String.serializer()),
                items,
            )
        }
    }

    private fun decode(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val MaxItems = 20
    }
}
