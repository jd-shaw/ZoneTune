package com.shaw.zonetune.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector

enum class StudioTab(
    val label: String,
    val icon: ImageVector,
) {
    Discover("发现", Icons.Outlined.Explore),
    Search("搜索", Icons.Outlined.Search),
    Mine("我的", Icons.Outlined.Person),
}
