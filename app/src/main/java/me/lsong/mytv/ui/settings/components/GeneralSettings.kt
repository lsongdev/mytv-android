package me.lsong.mytv.ui.settings.components

import me.lsong.mytv.ui.theme.TvTheme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
