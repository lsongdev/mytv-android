package me.lsong.mytv.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class MyTvSettingsCategories(
    val icon: ImageVector,
    val title: String
) {
    APP(Icons.Default.Settings, "应用"),
    IPTV(Icons.Default.LiveTv, "直播源"),
    EPG(Icons.Default.Menu, "节目单"),
    ABOUT(Icons.Default.Info, "关于"),
}