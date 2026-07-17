package com.shaw.zonetune.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shaw.zonetune.ZoneTuneApp
import com.shaw.zonetune.data.api.BiliApiException
import com.shaw.zonetune.data.api.BiliRepository
import com.shaw.zonetune.data.model.Track
import com.shaw.zonetune.data.search.SearchHistoryStore
import com.shaw.zonetune.player.PlayerController
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
    val error: String? = null,
    val loggedIn: Boolean = false,
    val userName: String = "",
    val hasSearched: Boolean = false,
)

class SearchViewModel(
    private val repository: BiliRepository,
    private val player: PlayerController,
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

    fun search() {
        val keyword = _ui.value.query.trim()
        if (keyword.isBlank()) return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null, hasSearched = true) }
            historyStore.add(keyword)
            try {
                val list = repository.searchVideos(keyword)
                _ui.update { it.copy(results = list, loading = false) }
            } catch (e: BiliApiException) {
                _ui.update { it.copy(loading = false, error = "[${e.code}] ${e.message}") }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "搜索失败") }
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
        viewModelScope.launch {
            player.setLoading(true)
            player.setError(null)
            try {
                val playable = repository.buildPlayableTrack(track)
                player.play(playable)
            } catch (e: Exception) {
                player.setError(e.message ?: "播放失败")
            } finally {
                player.setLoading(false)
            }
        }
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
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = ZoneTuneApp.instance
                val repo = BiliRepository(app.biliClient, app.cookieStore)
                return SearchViewModel(
                    repository = repo,
                    player = app.playerController,
                    historyStore = app.searchHistoryStore,
                ) as T
            }
        }
    }
}
