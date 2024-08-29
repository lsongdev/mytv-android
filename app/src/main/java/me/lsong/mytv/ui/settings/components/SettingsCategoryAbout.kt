package me.lsong.mytv.ui.settings.components

import android.content.Context
import android.content.pm.PackageInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import me.lsong.mytv.R
import me.lsong.mytv.ui.components.LeanbackQrcode
import me.lsong.mytv.ui.settings.MyTvSettingsViewModel
import me.lsong.mytv.ui.theme.LeanbackTheme
import me.lsong.mytv.utils.Constants
import me.lsong.mytv.utils.HttpServer

@Composable
fun LeanbackSettingsCategoryAbout(
    modifier: Modifier = Modifier,
    packageInfo: PackageInfo = rememberPackageInfo(),
    settingsViewModel: MyTvSettingsViewModel = viewModel(),
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
                    leadingContent = {
                        Image(
                            painter = painterResource(id = R.mipmap.ic_launcher),
                            contentDescription = "DuckTV",
                            modifier = Modifier.size(40.dp)
                        )
                    },
                    headlineContent = Constants.APP_NAME,
                    trailingContent = packageInfo.versionName,
                    supportingContent = packageInfo.packageName,
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

            item{
                LeanbackSettingsCategoryListItem(
                    headlineContent = "扫码进入设置页面",
                    supportingContent = HttpServer.serverUrl,
                    trailingContent = { LeanbackQrcode(
                        text = HttpServer.serverUrl,
                        modifier = Modifier
                            .width(80.dp)
                            .height(80.dp),
                    )
                    }
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
