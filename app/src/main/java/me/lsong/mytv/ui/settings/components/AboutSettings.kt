package me.lsong.mytv.ui.settings.components

import me.lsong.mytv.ui.components.QrCode
import me.lsong.mytv.ui.theme.TvTheme

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
import androidx.compose.foundation.lazy.LazyColumn
import me.lsong.mytv.R

import me.lsong.mytv.ui.settings.SettingsViewModel

import me.lsong.mytv.app.AppConstants
import me.lsong.mytv.data.remote.ConfigurationServer

@Composable
fun AboutSettings(
    modifier: Modifier = Modifier,
    packageInfo: PackageInfo = rememberPackageInfo(),
    settingsViewModel: SettingsViewModel = viewModel(),
) {
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
                    leadingContent = {
                        Image(
                            painter = painterResource(id = R.drawable.ohmytv_logo),
                            contentDescription = AppConstants.APP_NAME,
                            modifier = Modifier.size(40.dp)
                        )
                    },
                    headlineContent = AppConstants.APP_NAME,
                    trailingContent = packageInfo.versionName.orEmpty(),
                    supportingContent = packageInfo.packageName,
                )
            }

            item {
                SettingsRow(
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
                SettingsRow(
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
                SettingsRow(
                    headlineContent = "扫码进入设置页面",
                    supportingContent = ConfigurationServer.serverUrl,
                    trailingContent = { QrCode(
                        text = ConfigurationServer.serverUrl,
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
    TvTheme {
        AboutSettings(
            packageInfo = PackageInfo().apply {
                versionName = "1.0.0"
            }
        )
    }
}
