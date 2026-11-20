package me.lsong.mytv.ui.settings.components

import me.lsong.mytv.ui.theme.TvTheme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import android.text.format.Formatter
import me.lsong.mytv.data.cache.CacheKind
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.lazy.LazyColumn
import me.lsong.mytv.ui.settings.SettingsViewModel

import me.lsong.mytv.data.preferences.AppPreferences

@Composable
fun GeneralSettings(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = viewModel(),
    ) {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    LaunchedEffect(settingsViewModel) {
        if (!inspection) settingsViewModel.refreshCacheSize()
    }
    fun sizeText(bytes: Long?): String = when {
        settingsViewModel.cacheBusy -> "处理中…"
        bytes == null -> "重试"
        else -> Formatter.formatFileSize(context, bytes)
    }
    var backend by remember { mutableStateOf(AppPreferences.playerBackend) }
    var software by remember { mutableStateOf(AppPreferences.softwareDecode) }
    Column (
        modifier = modifier.padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {

            item {
                SettingsRow(
                    headlineContent = "播放器后端",
                    supportingContent = "下次启动生效；Android 7 使用 Media3",
                    trailingContent = backend,
                    onSelected = { backend = if (backend == "MPV") "MEDIA3" else "MPV"; AppPreferences.playerBackend = backend },
                )
            }
            item {
                SettingsRow(
                    headlineContent = "解码方式",
                    supportingContent = "下次启动生效",
                    trailingContent = if (software) "软件解码" else "硬件优先",
                    onSelected = { software = !software; AppPreferences.softwareDecode = software },
                )
            }
            item {
                SettingsRow(
                    headlineContent = "全局画面比例",
                    trailingContent = settingsViewModel.videoPlayerAspectRatio.title,
                    onSelected = settingsViewModel::cycleVideoAspectRatio,
                )
            }
            item {
                SettingsRow(
                    headlineContent = "换台反转",
                    supportingContent = "调整上下方向键的换台顺序",
                    trailingContent = if (settingsViewModel.iptvChannelChangeFlip) "开启" else "关闭",
                    onSelected = { settingsViewModel.iptvChannelChangeFlip = !settingsViewModel.iptvChannelChangeFlip },
                )
            }
            item {
                SettingsRow(
                    modifier = Modifier.testTag("clear-data-cache"),
                    headlineContent = "清除数据缓存",
                    supportingContent = "频道、影视目录、节目单和弹幕；保留源配置、收藏和播放记录",
                    trailingContent = sizeText(settingsViewModel.cacheSize?.dataBytes),
                    onSelected = { settingsViewModel.clearCache(CacheKind.DATA) },
                )
            }
            item {
                SettingsRow(
                    modifier = Modifier.testTag("clear-image-cache"),
                    headlineContent = "清除图片缓存",
                    supportingContent = "清除封面、台标的磁盘及内存缓存；大小仅统计磁盘",
                    trailingContent = sizeText(settingsViewModel.cacheSize?.imageBytes),
                    onSelected = { settingsViewModel.clearCache(CacheKind.IMAGES) },
                )
            }
            settingsViewModel.cacheMessage?.let { message ->
                item { androidx.tv.material3.Text(message, modifier = Modifier.testTag("cache-result")) }
            }
        }
    }
}

@Preview
@Composable
private fun LeanbackSettingsCategoryAppPreview() {
    AppPreferences.init(LocalContext.current)
    TvTheme {
        GeneralSettings(
            modifier = Modifier.padding(20.dp),
            settingsViewModel = SettingsViewModel(),
        )
    }
}
