package me.lsong.mytv.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import me.lsong.mytv.ui.settings.MyTvSettingsViewModel
import me.lsong.mytv.ui.theme.LeanbackTheme
import me.lsong.mytv.utils.Constants
import me.lsong.mytv.utils.Settings
import me.lsong.mytv.utils.humanizeMs
import java.text.DecimalFormat
import kotlin.math.max

@Composable
fun LeanbackSettingsCategoryApp(
    modifier: Modifier = Modifier,
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
                    headlineContent = "开机自启",
                    supportingContent = "请确保当前设备支持该功能",
                    trailingContent = {
                        Switch(checked = settingsViewModel.appBootLaunch, onCheckedChange = null)
                    },
                    onSelected = {
                        settingsViewModel.appBootLaunch = !settingsViewModel.appBootLaunch
                    },
                )
            }

            item {
                val defaultScale = 1f
                val minScale = 1f
                val maxScale = 2f
                val stepScale = 0.1f

                LeanbackSettingsCategoryListItem(
                    headlineContent = "界面整体缩放比例",
                    supportingContent = "短按切换缩放比例，长按恢复默认；部分界面受影响",
                    trailingContent = "×${DecimalFormat("#.#").format(settingsViewModel.uiDensityScaleRatio)}",
                    onSelected = {
                        if (settingsViewModel.uiDensityScaleRatio >= maxScale) {
                            settingsViewModel.uiDensityScaleRatio = minScale
                        } else {
                            settingsViewModel.uiDensityScaleRatio =
                                (settingsViewModel.uiDensityScaleRatio + stepScale).coerceIn(
                                    minScale, maxScale
                                )
                        }
                    },
                    onLongSelected = {
                        settingsViewModel.uiDensityScaleRatio = defaultScale
                    },
                )
            }

            item {
                val defaultScale = 1f
                val minScale = 1f
                val maxScale = 2f
                val stepScale = 0.1f

                LeanbackSettingsCategoryListItem(
                    headlineContent = "界面字体缩放比例",
                    supportingContent = "短按切换缩放比例，长按恢复默认；部分界面受影响",
                    trailingContent = "×${DecimalFormat("#.#").format(settingsViewModel.uiFontScaleRatio)}",
                    onSelected = {
                        if (settingsViewModel.uiFontScaleRatio >= maxScale) {
                            settingsViewModel.uiFontScaleRatio = minScale
                        } else {
                            settingsViewModel.uiFontScaleRatio =
                                (settingsViewModel.uiFontScaleRatio + stepScale).coerceIn(
                                    minScale, maxScale
                                )
                        }
                    },
                    onLongSelected = {
                        settingsViewModel.uiFontScaleRatio = defaultScale
                    },
                )
            }

            item {
                LeanbackSettingsCategoryListItem(
                    headlineContent = "HTTP请求重试次数",
                    supportingContent = "影响直播源、节目单数据获取",
                    trailingContent = Constants.HTTP_RETRY_COUNT.toString(),
                )
            }

            item {
                LeanbackSettingsCategoryListItem(
                    headlineContent = "HTTP请求重试间隔时间",
                    supportingContent = "影响直播源、节目单数据获取",
                    trailingContent = Constants.HTTP_RETRY_INTERVAL.humanizeMs(),
                )
            }

            item {
                LeanbackSettingsCategoryListItem(
                    headlineContent = "全局画面比例",
                    trailingContent = when (settingsViewModel.videoPlayerAspectRatio) {
                        Settings.VideoPlayerAspectRatio.ORIGINAL -> "原始"
                        Settings.VideoPlayerAspectRatio.SIXTEEN_NINE -> "16:9"
                        Settings.VideoPlayerAspectRatio.FOUR_THREE -> "4:3"
                        Settings.VideoPlayerAspectRatio.AUTO -> "自动拉伸"
                    },
                    onSelected = {
                        settingsViewModel.videoPlayerAspectRatio =
                            Settings.VideoPlayerAspectRatio.entries.let {
                                it[(it.indexOf(settingsViewModel.videoPlayerAspectRatio) + 1) % it.size]
                            }
                    },
                )
            }


            item {
                val min = 1000 * 5L
                val max = 1000 * 30L
                val step = 1000 * 5L

                LeanbackSettingsCategoryListItem(
                    headlineContent = "播放器加载超时",
                    supportingContent = "影响超时换源、断线重连",
                    trailingContent = settingsViewModel.videoPlayerLoadTimeout.humanizeMs(),
                    onSelected = {
                        settingsViewModel.videoPlayerLoadTimeout =
                            max(min, (settingsViewModel.videoPlayerLoadTimeout + step) % (max + step))
                    },
                )
            }

            item {
                LeanbackSettingsCategoryListItem(
                    headlineContent = "播放器自定义UA",
                    supportingContent = settingsViewModel.videoPlayerUserAgent,
                )
            }

        }
    }
}

@Preview
@Composable
private fun LeanbackSettingsCategoryAppPreview() {
    Settings.init(LocalContext.current)
    LeanbackTheme {
        LeanbackSettingsCategoryApp(
            modifier = Modifier.padding(20.dp),
            settingsViewModel = MyTvSettingsViewModel(),
        )
    }
}
