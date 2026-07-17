package com.shaw.zonetune

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.shaw.zonetune.data.api.BiliRepository
import com.shaw.zonetune.data.model.Track
import com.shaw.zonetune.ui.components.StudioScaffold
import com.shaw.zonetune.ui.discover.DiscoverScreen
import com.shaw.zonetune.ui.discover.MineShortcut
import com.shaw.zonetune.ui.login.LoginScreen
import com.shaw.zonetune.ui.mine.MineScreen
import com.shaw.zonetune.ui.navigation.StudioTab
import com.shaw.zonetune.ui.player.MiniPlayerBar
import com.shaw.zonetune.ui.player.NowPlayingScreen
import com.shaw.zonetune.ui.search.SearchScreen
import com.shaw.zonetune.ui.theme.ZoneTuneTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZoneTuneTheme {
                val scope = rememberCoroutineScope()
                val app = ZoneTuneApp.instance
                val repository = remember {
                    BiliRepository(app.biliClient, app.cookieStore)
                }

                var selectedTab by remember { mutableStateOf(StudioTab.Discover) }
                var showLogin by remember { mutableStateOf(false) }
                var showNowPlaying by remember { mutableStateOf(false) }
                var pendingSearchQuery by remember { mutableStateOf<String?>(null) }
                var searchKey by remember { mutableStateOf(0) }

                var loggedIn by remember { mutableStateOf(false) }
                var userName by remember { mutableStateOf("") }
                var avatarUrl by remember { mutableStateOf("") }

                fun refreshAccount() {
                    scope.launch {
                        try {
                            val nav = repository.getNav()
                            loggedIn = nav.isLogin
                            userName = nav.uname
                            avatarUrl = nav.face.let { face ->
                                when {
                                    face.startsWith("//") -> "https:$face"
                                    else -> face
                                }
                            }
                        } catch (_: Exception) {
                            loggedIn = false
                            userName = ""
                            avatarUrl = ""
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    refreshAccount()
                }

                fun playTrack(track: Track) {
                    scope.launch {
                        val player = app.playerController
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

                fun handleShortcut(shortcut: MineShortcut) {
                    when (shortcut) {
                        MineShortcut.Queue -> showNowPlaying = true
                        MineShortcut.Favorites -> selectedTab = StudioTab.Mine
                        MineShortcut.Recent -> {
                            Toast.makeText(this@MainActivity, "最近播放即将上线", Toast.LENGTH_SHORT).show()
                        }
                        MineShortcut.Local -> {
                            Toast.makeText(this@MainActivity, "本地音乐即将上线", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                when {
                    showNowPlaying -> {
                        NowPlayingScreen(onDismiss = { showNowPlaying = false })
                    }

                    showLogin -> {
                        LoginScreen(
                            onBack = { showLogin = false },
                            onSuccess = {
                                showLogin = false
                                searchKey += 1
                                refreshAccount()
                            },
                        )
                    }

                    else -> {
                        StudioScaffold(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it },
                            miniPlayer = {
                                MiniPlayerBar(onExpand = { showNowPlaying = true })
                            },
                        ) {
                            when (selectedTab) {
                                StudioTab.Discover -> DiscoverScreen(
                                    loggedIn = loggedIn,
                                    userName = userName,
                                    onOpenLogin = {
                                        if (loggedIn) {
                                            selectedTab = StudioTab.Mine
                                        } else {
                                            showLogin = true
                                        }
                                    },
                                    onOpenSearch = { keyword ->
                                        pendingSearchQuery = keyword
                                        selectedTab = StudioTab.Search
                                    },
                                    onOpenMineShortcut = { shortcut ->
                                        if (shortcut == MineShortcut.Queue) {
                                            showNowPlaying = true
                                        } else {
                                            selectedTab = StudioTab.Mine
                                            handleShortcut(shortcut)
                                        }
                                    },
                                    onOpenNowPlaying = { showNowPlaying = true },
                                    onPlayTrack = ::playTrack,
                                )

                                StudioTab.Search -> key(searchKey) {
                                    SearchScreen(
                                        pendingQuery = pendingSearchQuery,
                                        onPendingQueryConsumed = { pendingSearchQuery = null },
                                    )
                                }

                                StudioTab.Mine -> MineScreen(
                                    loggedIn = loggedIn,
                                    userName = userName,
                                    avatarUrl = avatarUrl,
                                    onOpenLogin = { showLogin = true },
                                    onLogout = {
                                        scope.launch {
                                            app.cookieStore.clear()
                                            refreshAccount()
                                            searchKey += 1
                                            Toast.makeText(
                                                this@MainActivity,
                                                "已退出登录",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                    onShortcut = ::handleShortcut,
                                    onPlayTrack = ::playTrack,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
