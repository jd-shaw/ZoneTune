package com.shaw.zonetune.player

enum class PlayMode(
    val label: String,
) {
    Sequential("顺序播放"),
    Shuffle("随机播放"),
    Single("单曲循环"),
    ;

    fun next(): PlayMode = when (this) {
        Sequential -> Shuffle
        Shuffle -> Single
        Single -> Sequential
    }
}
