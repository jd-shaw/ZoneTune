package com.shaw.zonetune.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shaw.zonetune.ZoneTuneApp
import com.shaw.zonetune.data.api.BiliApiException
import com.shaw.zonetune.data.api.BiliRepository
import com.shaw.zonetune.data.model.Track
import com.shaw.zonetune.util.isNetworkAvailable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.UnknownHostException

data class DiscoverUiState(
    val hotTracks: List<Track> = emptyList(),
    val hotVisibleCount: Int = HotPageSize,
    val loadingHot: Boolean = false,
    val hotError: String? = null,
) {
    val visibleHotTracks: List<Track>
        get() = hotTracks.take(hotVisibleCount)

    val canViewMoreHot: Boolean
        get() = hotVisibleCount < hotTracks.size
}

/** Default page size for waterfall "View more". */
const val HotPageSize = 10
private const val HotFetchLimit = 40

class DiscoverViewModel(
    private val repository: BiliRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(DiscoverUiState())
    val ui: StateFlow<DiscoverUiState> = _ui.asStateFlow()

    init {
        refreshHot()
    }

    fun refreshHot() {
        viewModelScope.launch {
            if (!ZoneTuneApp.instance.isNetworkAvailable()) {
                _ui.update {
                    it.copy(
                        loadingHot = false,
                        hotError = "网络不可用，请检查连接后重试",
                    )
                }
                return@launch
            }
            _ui.update {
                it.copy(
                    loadingHot = true,
                    hotError = null,
                    hotVisibleCount = HotPageSize,
                )
            }
            try {
                val list = repository.getMusicHotTracks(limit = HotFetchLimit)
                _ui.update {
                    it.copy(
                        hotTracks = list,
                        hotVisibleCount = HotPageSize.coerceAtMost(list.size),
                        loadingHot = false,
                        hotError = if (list.isEmpty()) "暂时没有热门内容" else null,
                    )
                }
            } catch (e: BiliApiException) {
                _ui.update {
                    it.copy(
                        loadingHot = false,
                        hotError = "[${e.code}] ${e.message}",
                    )
                }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        loadingHot = false,
                        hotError = friendlyNetworkError(e),
                    )
                }
            }
        }
    }

    fun viewMoreHot() {
        _ui.update { state ->
            if (!state.canViewMoreHot) return@update state
            state.copy(
                hotVisibleCount = (state.hotVisibleCount + HotPageSize)
                    .coerceAtMost(state.hotTracks.size),
            )
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = ZoneTuneApp.instance
                val repo = BiliRepository(app.biliClient, app.cookieStore)
                return DiscoverViewModel(repo) as T
            }
        }
    }
}

private fun friendlyNetworkError(e: Exception): String {
    val cause = generateSequence(e as Throwable?) { it.cause }.firstOrNull { it is UnknownHostException }
    return when {
        cause != null || e is UnknownHostException ->
            "无法解析 bilibili 域名，请检查网络 / DNS 后点重试"
        else -> e.message ?: "加载失败"
    }
}
