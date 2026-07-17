package com.shaw.zonetune.data.api

import com.shaw.zonetune.data.model.BiliResponse
import com.shaw.zonetune.data.model.NavData
import com.shaw.zonetune.data.model.PlayUrlData
import com.shaw.zonetune.data.model.QrGenerateData
import com.shaw.zonetune.data.model.QrPollData
import com.shaw.zonetune.data.model.RankingData
import com.shaw.zonetune.data.model.RankingItem
import com.shaw.zonetune.data.model.SearchResultData
import com.shaw.zonetune.data.model.SearchVideoItem
import com.shaw.zonetune.data.model.Track
import com.shaw.zonetune.data.model.VideoViewData
import com.shaw.zonetune.data.cookie.CookieStore

class BiliRepository(
    private val client: BiliClient,
    private val cookieStore: CookieStore,
) {
    suspend fun getNav(): NavData {
        val resp: BiliResponse<NavData> = client.getJson(
            "${BiliClient.API}/x/web-interface/nav",
            useWbi = false,
        )
        // Not logged in → code -101, but data (incl. wbi_img / isLogin) is still useful.
        if (resp.code != 0 && resp.code != -101) {
            throw BiliApiException(resp.code, resp.message.ifBlank { "unknown error" }, "/x/web-interface/nav")
        }
        return resp.data ?: NavData()
    }

    suspend fun searchVideos(keyword: String, page: Int = 1, pageSize: Int = 20): List<Track> {
        val resp: BiliResponse<SearchResultData> = client.getJson(
            url = "${BiliClient.API}/x/web-interface/wbi/search/type",
            params = mapOf(
                "search_type" to "video",
                "keyword" to keyword,
                "page" to page.toString(),
                "page_size" to pageSize.toString(),
            ),
            useWbi = true,
        )
        ensureOk(resp, "/x/web-interface/wbi/search/type")
        return resp.data?.result.orEmpty().map { it.toTrack() }
    }

    /**
     * Music partition hot ranking (rid=3). Returns top [limit] entries.
     */
    suspend fun getMusicHotTracks(limit: Int = 10): List<Track> {
        val resp: BiliResponse<RankingData> = client.getJson(
            url = "${BiliClient.API}/x/web-interface/ranking/v2",
            params = mapOf(
                "rid" to "3",
                "type" to "all",
            ),
            useWbi = false,
        )
        ensureOk(resp, "/x/web-interface/ranking/v2")
        return resp.data?.list.orEmpty()
            .take(limit.coerceIn(1, 50))
            .map { it.toTrack() }
    }

    suspend fun getVideoDetail(bvid: String): VideoViewData {
        val resp: BiliResponse<VideoViewData> = client.getJson(
            url = "${BiliClient.API}/x/web-interface/view",
            params = mapOf("bvid" to bvid),
        )
        ensureOk(resp, "/x/web-interface/view")
        return resp.data ?: throw BiliApiException(-1, "empty video detail", bvid)
    }

    suspend fun resolveAudioUrl(bvid: String, cid: Long, aid: Long = 0): Pair<String, Int> {
        val params = mutableMapOf(
            "bvid" to bvid,
            "cid" to cid.toString(),
            "qn" to "64",
            "fnver" to "0",
            "fnval" to "16",
            "fourk" to "1",
            // Helps unauthenticated clients avoid some risk-control empty responses.
            "gaia_source" to "view-card",
        )
        if (aid > 0) params["avid"] = aid.toString()
        if (cookieStore.get("SESSDATA").isNullOrBlank()) {
            params["try_look"] = "1"
        }

        val resp: BiliResponse<PlayUrlData> = client.getJson(
            url = "${BiliClient.API}/x/player/wbi/playurl",
            params = params,
            useWbi = true,
        )
        ensureOk(resp, "/x/player/wbi/playurl")
        val data = resp.data ?: throw BiliApiException(-1, "empty playurl", bvid)

        if (!data.vVoucher.isNullOrBlank()) {
            throw BiliApiException(
                code = -403,
                message = "暂时无法取流，请先扫码登录后再试",
                path = bvid,
            )
        }

        val audio = data.dash?.audio
            ?.maxByOrNull { it.bandwidth }
            ?.resolveUrl()
            .orEmpty()
        if (audio.isNotBlank()) {
            val durationSec = (data.timelength / 1000).toInt().coerceAtLeast(0)
            return audio to durationSec
        }

        val durl = data.durl.firstOrNull()?.url.orEmpty()
        if (durl.isNotBlank()) {
            val durationSec = (data.timelength / 1000).toInt().coerceAtLeast(0)
            return durl to durationSec
        }

        throw BiliApiException(-1, "No audio stream available", bvid)
    }

    suspend fun buildPlayableTrack(item: Track): Track {
        val detail = if (item.cid <= 0L) getVideoDetail(item.bvid) else null
        val cid = if (item.cid > 0) item.cid else (detail?.cid ?: detail?.pages?.firstOrNull()?.cid ?: 0L)
        val aid = if (item.aid > 0) item.aid else (detail?.aid ?: 0L)
        require(cid > 0) { "cid missing for ${item.bvid}" }

        val (audioUrl, durationSec) = resolveAudioUrl(item.bvid, cid, aid)
        return item.copy(
            aid = aid,
            cid = cid,
            title = detail?.title?.takeIf { it.isNotBlank() } ?: item.title,
            artist = detail?.owner?.name?.takeIf { it.isNotBlank() } ?: item.artist,
            coverUrl = normalizePic(detail?.pic ?: item.coverUrl),
            durationSec = if (durationSec > 0) durationSec else (detail?.duration ?: item.durationSec),
            audioUrl = audioUrl,
        )
    }

    suspend fun generateQrCode(): QrGenerateData {
        val resp: BiliResponse<QrGenerateData> = client.getJson(
            "${BiliClient.PASSPORT}/x/passport-login/web/qrcode/generate",
        )
        ensureOk(resp, "/x/passport-login/web/qrcode/generate")
        return resp.data ?: throw BiliApiException(-1, "empty qrcode data")
    }

    /**
     * Poll QR login.
     * code: 0 success, 86090 scanned, 86101 waiting, 86038 expired
     */
    suspend fun pollQrCode(qrcodeKey: String): QrPollData {
        val raw = client.get(
            url = "${BiliClient.PASSPORT}/x/passport-login/web/qrcode/poll",
            params = mapOf("qrcode_key" to qrcodeKey),
        )
        val resp: BiliResponse<QrPollData> = client.decode(raw)
        // poll wraps status inside data.code
        val data = resp.data ?: QrPollData(code = resp.code, message = resp.message)
        if (data.code == 0) {
            // Cookies are set by Set-Cookie on passport responses; OkHttp won't auto-persist
            // our DataStore. Parse from poll URL query if present, and refresh via nav cookies
            // after success is handled by caller reading Cookie jar — for MVP we re-fetch nav
            // after user confirms login via SESSDATA already stored when available.
            data.refreshToken.takeIf { it.isNotBlank() }?.let {
                cookieStore.set("refresh_token", it)
            }
            parseCookiesFromUrl(data.url)?.let { cookieStore.putAll(it) }
        }
        return data
    }

    suspend fun logout() {
        cookieStore.clear()
        WbiSigner.clearCache()
    }

    private fun <T> ensureOk(resp: BiliResponse<T>, path: String) {
        if (resp.code != 0) {
            throw BiliApiException(resp.code, resp.message.ifBlank { "unknown error" }, path)
        }
    }

    private fun SearchVideoItem.toTrack(): Track {
        val cleanTitle = title.replace(Regex("<[^>]+>"), "")
        return Track(
            id = bvid.ifBlank { aid.toString() },
            bvid = bvid,
            aid = aid,
            title = cleanTitle,
            artist = author,
            coverUrl = normalizePic(pic),
            durationSec = parseDuration(duration),
        )
    }

    private fun RankingItem.toTrack(): Track {
        return Track(
            id = bvid.ifBlank { aid.toString() },
            bvid = bvid,
            aid = aid,
            cid = cid,
            title = title,
            artist = owner?.name.orEmpty(),
            coverUrl = normalizePic(pic),
            durationSec = duration.coerceAtLeast(0),
        )
    }

    private fun parseDuration(raw: String): Int {
        if (raw.isBlank()) return 0
        val parts = raw.split(':').mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0
        }
    }

    private fun normalizePic(pic: String): String {
        if (pic.isBlank()) return ""
        return when {
            pic.startsWith("https://") -> pic
            pic.startsWith("http://") -> pic.replace("http://", "https://")
            pic.startsWith("//") -> "https:$pic"
            else -> "https:$pic"
        }
    }

    private fun parseCookiesFromUrl(url: String): Map<String, String>? {
        if (url.isBlank()) return null
        // Some poll success URLs include cookie-like query params; keep defensive.
        val result = mutableMapOf<String, String>()
        val query = url.substringAfter('?', missingDelimiterValue = "")
        if (query.isBlank()) return null
        query.split('&').forEach { pair ->
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            if (key in setOf("SESSDATA", "bili_jct", "DedeUserID", "DedeUserID__ckMd5", "sid")) {
                result[key] = java.net.URLDecoder.decode(value, Charsets.UTF_8)
            }
        }
        return result.takeIf { it.isNotEmpty() }
    }
}
