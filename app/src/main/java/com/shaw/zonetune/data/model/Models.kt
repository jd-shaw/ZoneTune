package com.shaw.zonetune.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BiliResponse<T>(
    val code: Int = -1,
    val message: String = "",
    val ttl: Int = 1,
    val data: T? = null,
)

@Serializable
data class SearchResultData(
    val result: List<SearchVideoItem> = emptyList(),
    @SerialName("numPages") val numPages: Int = 0,
    @SerialName("numResults") val numResults: Int = 0,
)

@Serializable
data class SearchVideoItem(
    val bvid: String = "",
    val aid: Long = 0,
    val title: String = "",
    val author: String = "",
    val pic: String = "",
    val duration: String = "",
    val play: Long = 0,
    val description: String = "",
)

@Serializable
data class VideoViewData(
    val bvid: String = "",
    val aid: Long = 0,
    val title: String = "",
    val pic: String = "",
    val desc: String = "",
    val duration: Int = 0,
    val cid: Long = 0,
    val owner: VideoOwner? = null,
    val pages: List<VideoPage> = emptyList(),
)

@Serializable
data class VideoOwner(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
)

@Serializable
data class VideoPage(
    val cid: Long = 0,
    val page: Int = 1,
    val part: String = "",
    val duration: Int = 0,
)

@Serializable
data class PlayUrlData(
    val dash: DashInfo? = null,
    val durl: List<DurlItem> = emptyList(),
    val timelength: Long = 0,
    @SerialName("v_voucher") val vVoucher: String? = null,
)

@Serializable
data class DashInfo(
    val audio: List<DashMedia> = emptyList(),
    val video: List<DashMedia> = emptyList(),
)

@Serializable
data class DashMedia(
    val id: Int = 0,
    @SerialName("baseUrl") val baseUrl: String = "",
    @SerialName("base_url") val baseUrlAlt: String = "",
    @SerialName("backupUrl") val backupUrl: List<String> = emptyList(),
    @SerialName("backup_url") val backupUrlAlt: List<String> = emptyList(),
    val bandwidth: Int = 0,
    @SerialName("mimeType") val mimeType: String = "",
    val codecs: String = "",
) {
    /** Prefer stable bilivideo hosts; avoid mcdn:*:8082 which often fails on emulators. */
    fun resolveUrl(): String {
        val candidates = buildList {
            add(baseUrl)
            add(baseUrlAlt)
            addAll(backupUrl)
            addAll(backupUrlAlt)
        }.map { it.trim() }.filter { it.isNotBlank() }.distinct()

        return candidates.maxByOrNull(::cdnScore).orEmpty()
    }

    private fun cdnScore(url: String): Int {
        val authority = runCatching { java.net.URI(url).authority.orEmpty().lowercase() }
            .getOrDefault("")
        var score = 0
        if ("mcdn" in authority) score -= 20
        if (Regex(""":\d+$""").containsMatchIn(authority)) score -= 10
        if (authority.endsWith("bilivideo.com")) score += 10
        if (authority.startsWith("upos-")) score += 6
        if (url.startsWith("https://")) score += 2
        return score
    }
}

@Serializable
data class DurlItem(
    val url: String = "",
    val length: Long = 0,
)

@Serializable
data class QrGenerateData(
    val url: String = "",
    @SerialName("qrcode_key") val qrcodeKey: String = "",
)

@Serializable
data class QrPollData(
    val url: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    val timestamp: Long = 0,
    val code: Int = -1,
    val message: String = "",
)

@Serializable
data class NavData(
    @SerialName("isLogin") val isLogin: Boolean = false,
    val mid: Long = 0,
    val uname: String = "",
    val face: String = "",
    @SerialName("wbi_img") val wbiImg: WbiImg? = null,
)

@Serializable
data class WbiImg(
    @SerialName("img_url") val imgUrl: String = "",
    @SerialName("sub_url") val subUrl: String = "",
)

@Serializable
data class RankingData(
    val list: List<RankingItem> = emptyList(),
    val note: String = "",
)

@Serializable
data class RankingItem(
    val bvid: String = "",
    val aid: Long = 0,
    val cid: Long = 0,
    val title: String = "",
    val pic: String = "",
    val duration: Int = 0,
    val owner: VideoOwner? = null,
    val stat: RankingStat? = null,
)

@Serializable
data class RankingStat(
    val view: Long = 0,
    val like: Long = 0,
    val favorite: Long = 0,
)

/** App-level track used by UI / player */
@Serializable
data class Track(
    val id: String,
    val bvid: String,
    val aid: Long = 0,
    val cid: Long = 0,
    val title: String,
    val artist: String,
    val coverUrl: String,
    val durationSec: Int = 0,
    val audioUrl: String = "",
)
