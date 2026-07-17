package com.shaw.zonetune.data.api

import com.shaw.zonetune.data.cookie.CookieStore
import com.shaw.zonetune.data.cookie.PersistentCookieJar
import com.shaw.zonetune.data.model.BiliResponse
import com.shaw.zonetune.data.model.NavData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class BiliApiException(
    val code: Int,
    override val message: String,
    val path: String = "",
) : Exception("BiliAPI[$code] $message ($path)")

/**
 * Shared OkHttp client for Bilibili APIs.
 * Cookie / Referer / Origin injected like biu request interceptors.
 */
class BiliClient(
    private val cookieStore: CookieStore,
) {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val wbiMutex = Mutex()

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .cookieJar(PersistentCookieJar(cookieStore))
        .addInterceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .header("Origin", ORIGIN)
            chain.proceed(builder.build())
        }
        .addInterceptor(logging)
        .build()

    suspend fun ensureWbiKeys() = withContext(Dispatchers.IO) {
        if (WbiSigner.isKeyFresh()) return@withContext
        wbiMutex.withLock {
            if (WbiSigner.isKeyFresh()) return@withLock
            val nav = get("https://api.bilibili.com/x/web-interface/nav", emptyMap(), useWbi = false)
            val parsed: BiliResponse<NavData> = decode(nav)
            // Unauthenticated nav often returns code=-101 but still includes wbi_img.
            val img = parsed.data?.wbiImg?.imgUrl.orEmpty()
            val sub = parsed.data?.wbiImg?.subUrl.orEmpty()
            if (img.isBlank() || sub.isBlank()) {
                throw BiliApiException(
                    code = parsed.code,
                    message = "Failed to load WBI keys from nav: ${parsed.message.ifBlank { "empty wbi_img" }}",
                    path = "/x/web-interface/nav",
                )
            }
            WbiSigner.updateKeys(img, sub)
        }
    }

    suspend inline fun <reified T> getJson(
        url: String,
        params: Map<String, String> = emptyMap(),
        useWbi: Boolean = false,
    ): T = withContext(Dispatchers.IO) {
        val body = get(url, params, useWbi)
        decode(body)
    }

    suspend fun get(
        url: String,
        params: Map<String, String> = emptyMap(),
        useWbi: Boolean = false,
    ): String = withContext(Dispatchers.IO) {
        if (useWbi) ensureWbiKeys()
        val signed = if (useWbi) WbiSigner.sign(params) else params
        val httpUrl = url.toHttpUrl().newBuilder().apply {
            signed.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val request = Request.Builder().url(httpUrl).get().build()
        execute(request)
    }

    suspend fun postForm(
        url: String,
        form: Map<String, String>,
        params: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val httpUrl = url.toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val body = FormBody.Builder().apply {
            form.forEach { (k, v) -> add(k, v) }
        }.build()
        val request = Request.Builder().url(httpUrl).post(body).build()
        execute(request)
    }

    inline fun <reified T> decode(raw: String): T {
        return json.decodeFromString(serializer(), raw)
    }

    private fun execute(request: Request): String {
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw BiliApiException(response.code, "HTTP ${response.code}", request.url.encodedPath)
            }
            return body
        }
    }

    companion object {
        const val API = "https://api.bilibili.com"
        const val PASSPORT = "https://passport.bilibili.com"
        const val REFERER = "https://www.bilibili.com"
        const val ORIGIN = "https://www.bilibili.com"
        const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}
