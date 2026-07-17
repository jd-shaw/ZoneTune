package com.shaw.zonetune.data.cookie

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.cookieDataStore: DataStore<Preferences> by preferencesDataStore(name = "zonetune_cookies")

/**
 * Persists Bilibili login cookies (SESSDATA / bili_jct / DedeUserID / gaia token).
 * Ported conceptually from biu Electron session cookie handling.
 */
class CookieStore(context: Context) {
    private val store = context.applicationContext.cookieDataStore

    private object Keys {
        val SESSDATA = stringPreferencesKey("SESSDATA")
        val BILI_JCT = stringPreferencesKey("bili_jct")
        val DEDE_USER_ID = stringPreferencesKey("DedeUserID")
        val DEDE_USER_ID_CKMD5 = stringPreferencesKey("DedeUserID__ckMd5")
        val SID = stringPreferencesKey("sid")
        val GAIA_VTOKEN = stringPreferencesKey("x-bili-gaia-vtoken")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
    }

    val isLoggedInFlow: Flow<Boolean> = store.data.map { prefs ->
        !prefs[Keys.SESSDATA].isNullOrBlank() && !prefs[Keys.BILI_JCT].isNullOrBlank()
    }

    suspend fun get(name: String): String? {
        val prefs = store.data.first()
        return when (name) {
            "SESSDATA" -> prefs[Keys.SESSDATA]
            "bili_jct" -> prefs[Keys.BILI_JCT]
            "DedeUserID" -> prefs[Keys.DEDE_USER_ID]
            "DedeUserID__ckMd5" -> prefs[Keys.DEDE_USER_ID_CKMD5]
            "sid" -> prefs[Keys.SID]
            "x-bili-gaia-vtoken" -> prefs[Keys.GAIA_VTOKEN]
            "refresh_token" -> prefs[Keys.REFRESH_TOKEN]
            else -> null
        }
    }

    fun getBlocking(name: String): String? = runBlocking { get(name) }

    suspend fun set(name: String, value: String) {
        store.edit { prefs ->
            when (name) {
                "SESSDATA" -> prefs[Keys.SESSDATA] = value
                "bili_jct" -> prefs[Keys.BILI_JCT] = value
                "DedeUserID" -> prefs[Keys.DEDE_USER_ID] = value
                "DedeUserID__ckMd5" -> prefs[Keys.DEDE_USER_ID_CKMD5] = value
                "sid" -> prefs[Keys.SID] = value
                "x-bili-gaia-vtoken" -> prefs[Keys.GAIA_VTOKEN] = value
                "refresh_token" -> prefs[Keys.REFRESH_TOKEN] = value
            }
        }
    }

    suspend fun putAll(cookies: Map<String, String>) {
        store.edit { prefs ->
            cookies.forEach { (name, value) ->
                when (name) {
                    "SESSDATA" -> prefs[Keys.SESSDATA] = value
                    "bili_jct" -> prefs[Keys.BILI_JCT] = value
                    "DedeUserID" -> prefs[Keys.DEDE_USER_ID] = value
                    "DedeUserID__ckMd5" -> prefs[Keys.DEDE_USER_ID_CKMD5] = value
                    "sid" -> prefs[Keys.SID] = value
                    "x-bili-gaia-vtoken" -> prefs[Keys.GAIA_VTOKEN] = value
                    "refresh_token" -> prefs[Keys.REFRESH_TOKEN] = value
                }
            }
        }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    suspend fun cookieHeader(): String {
        val prefs = store.data.first()
        return buildList {
            prefs[Keys.SESSDATA]?.let { add("SESSDATA=$it") }
            prefs[Keys.BILI_JCT]?.let { add("bili_jct=$it") }
            prefs[Keys.DEDE_USER_ID]?.let { add("DedeUserID=$it") }
            prefs[Keys.DEDE_USER_ID_CKMD5]?.let { add("DedeUserID__ckMd5=$it") }
            prefs[Keys.SID]?.let { add("sid=$it") }
            prefs[Keys.GAIA_VTOKEN]?.let { add("x-bili-gaia-vtoken=$it") }
        }.joinToString("; ")
    }

    fun cookieHeaderBlocking(): String = runBlocking { cookieHeader() }
}
