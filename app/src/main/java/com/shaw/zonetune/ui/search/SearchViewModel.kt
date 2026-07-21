package com.shaw.zonetune.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shaw.zonetune.ZoneTuneApp
import com.shaw.zonetune.data.api.BiliApiException
import com.shaw.zonetune.data.api.BiliRepository
import com.shaw.zonetune.data.model.Track
import com.shaw.zonetune.data.search.SearchHistoryStore
import com.shaw.zonetune.util.isNetworkAvailable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<Track> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val page: Int = 1,
    val error: String? = null,
    val loggedIn: Boolean = false,
    val userName: String = "",
    val hasSearched: Boolean = false,
)

class SearchViewModel(
    private val repository: BiliRepository,
    private val historyStore: SearchHistoryStore,
) : ViewModel() {
    private val _ui = MutableStateFlow(SearchUiState())
    val ui: StateFlow<SearchUiState> = _ui.asStateFlow()

    val history: StateFlow<List<String>> = historyStore.historyFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    init {
        refreshLoginState()
    }

    fun onQueryChange(value: String) {
        _ui.update { it.copy(query = value) }
    }

    fun clearQuery() {
        _ui.update {
            it.copy(
                query = "",
                results = emptyList(),
                error = null,
                hasSearched = false,
                loading = false,
                loadingMore = false,
                canLoadMore = false,
                page = 1,
            )
        }
    }

    fun search() {
        val keyword = _ui.value.query.trim()
        if (keyword.isBlank()) return
        viewModelScope.launch {
            if (!ZoneTuneApp.instance.isNetworkAvailable()) {
                _ui.update {
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        hasSearched = true,
                        error = "网络不可用，请检查连接后重试",
                        results = emptyList(),
                        canLoadMore = false,
                    )
                }
                return@launch
            }
            _ui.update {
                it.copy(
                    loading = true,
                    loadingMore = false,
                    error = null,
                    hasSearched = true,
                    page = 1,
                    canLoadMore = false,
                )
            }
            historyStore.add(keyword)
            try {
                val list = repository.searchVideos(keyword, page = 1, pageSize = PageSize)
                _ui.update {
                    it.copy(
                        results = list,
                        loading = false,
                        page = 1,
                        canLoadMore = list.size >= PageSize,
                    )
                }
            } catch (e: BiliApiException) {
                _ui.update { it.copy(loading = false, error = "[${e.code}] ${e.message}") }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "搜索失败") }
            }
        }
    }

    fun loadMore() {
        val state = _ui.value
        if (state.loading || state.loadingMore || !state.canLoadMore) return
        val keyword = state.query.trim()
        if (keyword.isBlank()) return
        viewModelScope.launch {
            if (!ZoneTuneApp.instance.isNetworkAvailable()) {
                _ui.update { it.copy(error = "网络不可用，请检查连接后重试") }
                return@launch
            }
            val nextPage = state.page + 1
            _ui.update { it.copy(loadingMore = true, error = null) }
            try {
                val list = repository.searchVideos(keyword, page = nextPage, pageSize = PageSize)
                _ui.update {
                    it.copy(
                        results = it.results + list,
                        loadingMore = false,
                        page = nextPage,
                        canLoadMore = list.size >= PageSize,
                    )
                }
            } catch (e: BiliApiException) {
                _ui.update {
                    it.copy(loadingMore = false, error = "[${e.code}] ${e.message}")
                }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(loadingMore = false, error = e.message ?: "加载失败")
                }
            }
        }
    }

    fun searchFromHistory(keyword: String) {
        _ui.update { it.copy(query = keyword) }
        search()
    }

    fun removeHistory(keyword: String) {
        viewModelScope.launch { historyStore.remove(keyword) }
    }

    fun clearHistory() {
        viewModelScope.launch { historyStore.clear() }
    }

    fun play(track: Track) {
        ZoneTuneApp.instance.playTrack(track)
    }

    fun addToQueue(track: Track) {
        ZoneTuneApp.instance.addTrackToQueue(track)
    }

    fun refreshLoginState() {
        viewModelScope.launch {
            try {
                val nav = repository.getNav()
                _ui.update {
                    it.copy(
                        loggedIn = nav.isLogin,
                        userName = nav.uname,
                    )
                }
            } catch (_: Exception) {
                _ui.update { it.copy(loggedIn = false, userName = "") }
            }
        }
    }

    companion object {
        private const val PageSize = 20

        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = ZoneTuneApp.instance
                val repo = BiliRepository(app.biliClient, app.cookieStore)
                return SearchViewModel(
                    repository = repo,
                    historyStore = app.searchHistoryStore,
                ) as T
            }
        }
    }
}
