package com.shaw.zonetune.data.cookie

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import kotlinx.coroutines.runBlocking

/**
 * Bridges OkHttp Set-Cookie responses into [CookieStore].
 */
class PersistentCookieJar(
    private val cookieStore: CookieStore,
) : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val relevant = cookies.filter {
            it.domain.contains("bilibili.com", ignoreCase = true) ||
                url.host.contains("bilibili.com", ignoreCase = true)
        }
        if (relevant.isEmpty()) return

        val map = mutableMapOf<String, String>()
        relevant.forEach { cookie ->
            when (cookie.name) {
                "SESSDATA",
                "bili_jct",
                "DedeUserID",
                "DedeUserID__ckMd5",
                "sid",
                "x-bili-gaia-vtoken",
                -> map[cookie.name] = cookie.value
            }
        }
        if (map.isNotEmpty()) {
            runBlocking { cookieStore.putAll(map) }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!url.host.contains("bilibili.com")) return emptyList()
        val header = cookieStore.cookieHeaderBlocking()
        if (header.isBlank()) return emptyList()

        return header.split(';')
            .map { it.trim() }
            .filter { it.contains('=') }
            .mapNotNull { part ->
                val name = part.substringBefore('=').trim()
                val value = part.substringAfter('=').trim()
                if (name.isBlank()) return@mapNotNull null
                Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain("bilibili.com")
                    .path("/")
                    .build()
            }
    }
}
