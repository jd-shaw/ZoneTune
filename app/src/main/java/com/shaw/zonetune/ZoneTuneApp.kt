package com.shaw.zonetune

import android.app.Application
import com.shaw.zonetune.data.api.BiliClient
import com.shaw.zonetune.data.cookie.CookieStore
import com.shaw.zonetune.data.favorite.FavoriteStore
import com.shaw.zonetune.data.search.SearchHistoryStore
import com.shaw.zonetune.player.PlayerController

class ZoneTuneApp : Application() {
    lateinit var cookieStore: CookieStore
        private set
    lateinit var favoriteStore: FavoriteStore
        private set
    lateinit var searchHistoryStore: SearchHistoryStore
        private set
    lateinit var biliClient: BiliClient
        private set
    lateinit var playerController: PlayerController
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        cookieStore = CookieStore(this)
        favoriteStore = FavoriteStore(this)
        searchHistoryStore = SearchHistoryStore(this)
        biliClient = BiliClient(cookieStore)
        playerController = PlayerController(this)
    }

    companion object {
        lateinit var instance: ZoneTuneApp
            private set
    }
}
