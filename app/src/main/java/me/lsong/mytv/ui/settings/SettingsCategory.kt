package me.lsong.mytv.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector

enum class SettingsCategory(val icon: ImageVector, val title: String) {
    GENERAL(Icons.Default.Settings, "常规"),
    LIVE(Icons.Default.LiveTv, "直播"),
    VIDEOS(Icons.Default.VideoLibrary, "影视"),
    EPG(Icons.Default.Menu, "节目单"),
    DANMAKU(Icons.Default.Menu, "弹幕"),
    ABOUT(Icons.Default.Info, "关于"),
}
