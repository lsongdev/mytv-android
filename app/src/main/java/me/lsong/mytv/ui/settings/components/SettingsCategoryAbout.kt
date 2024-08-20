package me.lsong.mytv.ui.settings.components

import android.content.Context
import android.content.pm.PackageInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import me.lsong.mytv.utils.Constants
import me.lsong.mytv.ui.components.LeanbackQrcodeDialog
import me.lsong.mytv.ui.settings.LeanbackSettingsViewModel
import me.lsong.mytv.ui.theme.LeanbackTheme

@Composable
fun LeanbackSettingsCategoryAbout(
    modifier: Modifier = Modifier,
    packageInfo: PackageInfo = rememberPackageInfo(),
    settingsViewModel: LeanbackSettingsViewModel = viewModel(),
) {
    Column (
        modifier = modifier.padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {

        TvLazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                LeanbackSettingsCategoryListItem(
                    headlineContent = "应用名称",
                    trailingContent = "${Constants.APP_TITLE} ${packageInfo.versionName}",
                )
            }

            item {
                LeanbackSettingsCategoryListItem(
                    headlineContent = "显示FPS",
                    supportingContent = "在屏幕左上角显示fps和柱状图",
                    trailingContent = {
                        Switch(checked = settingsViewModel.debugShowFps, onCheckedChange = null)
                    },
                    onSelected = {
                        settingsViewModel.debugShowFps = !settingsViewModel.debugShowFps
                    },
                )
            }

            item {
                LeanbackSettingsCategoryListItem(
                    headlineContent = "显示播放器信息",
                    supportingContent = "显示播放器详细信息（编码、解码器、采样率等）",
                    trailingContent = {
                        Switch(
                            checked = settingsViewModel.debugShowVideoPlayerMetadata,
                            onCheckedChange = null
                        )
                    },
                    onSelected = {
                        settingsViewModel.debugShowVideoPlayerMetadata =
                            !settingsViewModel.debugShowVideoPlayerMetadata
                    },
                )
            }
        }
    }
}

@Composable
private fun rememberPackageInfo(context: Context = LocalContext.current): PackageInfo =
    context.packageManager.getPackageInfo(context.packageName, 0)

@Preview
@Composable
private fun SettingsAboutPreview() {
    LeanbackTheme {
        LeanbackSettingsCategoryAbout(
            packageInfo = PackageInfo().apply {
                versionName = "1.0.0"
            }
        )
    }
}
