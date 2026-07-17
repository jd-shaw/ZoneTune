package com.shaw.zonetune.data.api

import java.security.MessageDigest
import java.util.TreeMap

/**
 * Bilibili WBI signature.
 * Ported from biu `src/service/request/wbi-sign.ts` / community algorithm.
 */
object WbiSigner {
    private val MIXIN_KEY_ENC_TAB = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
        37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
        22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52,
    )

    @Volatile
    private var cachedMixinKey: String? = null

    @Volatile
    private var cachedAtMs: Long = 0

    private const val CACHE_TTL_MS = 2 * 60 * 60 * 1000L

    fun clearCache() {
        cachedMixinKey = null
        cachedAtMs = 0
    }

    fun updateKeys(imgUrl: String, subUrl: String) {
        val imgKey = extractKey(imgUrl)
        val subKey = extractKey(subUrl)
        val raw = imgKey + subKey
        cachedMixinKey = MIXIN_KEY_ENC_TAB
            .take(32)
            .map { raw.getOrElse(it) { '0' } }
            .joinToString("")
        cachedAtMs = System.currentTimeMillis()
    }

    fun isKeyFresh(): Boolean {
        val key = cachedMixinKey
        return !key.isNullOrBlank() && System.currentTimeMillis() - cachedAtMs < CACHE_TTL_MS
    }

    fun sign(params: Map<String, String>): Map<String, String> {
        val mixinKey = cachedMixinKey
            ?: error("WBI keys not ready. Call updateKeys() after nav first.")

        val wts = (System.currentTimeMillis() / 1000).toString()
        val sorted = TreeMap<String, String>()
        params.forEach { (k, v) -> sorted[k] = filterValue(v) }
        sorted["wts"] = wts

        val query = sorted.entries.joinToString("&") { "${it.key}=${it.value}" }
        val wRid = md5(query + mixinKey)

        val result = LinkedHashMap<String, String>()
        sorted.forEach { (k, v) -> result[k] = v }
        result["w_rid"] = wRid
        return result
    }

    private fun extractKey(url: String): String {
        val file = url.substringAfterLast('/')
        return file.substringBefore('.')
    }

    private fun filterValue(value: String): String {
        return value.replace(Regex("[!'()*]"), "")
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
