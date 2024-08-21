package me.lsong.mytv.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import me.lsong.mytv.ui.settings.LeanbackSettingsViewModel
import me.lsong.mytv.ui.theme.LeanbackTheme
import me.lsong.mytv.utils.Constants.APP_NAME
import me.lsong.mytv.utils.SP

@Composable
fun LeanbackSettingsCategoryEpg(
    modifier: Modifier = Modifier,
    settingsViewModel: LeanbackSettingsViewModel = viewModel(),
) {
    TvLazyColumn (
        modifier = modifier.padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "节目单刷新时间阈值",
                supportingContent = "短按增加1小时，长按设为0小时；时间不到${settingsViewModel.epgRefreshTimeThreshold}:00节目单将不会刷新",
                trailingContent = "${settingsViewModel.epgRefreshTimeThreshold}小时",
                onSelected = {
                    settingsViewModel.epgRefreshTimeThreshold =
                        (settingsViewModel.epgRefreshTimeThreshold + 1) % 12
                },
                onLongSelected = {
                    settingsViewModel.epgRefreshTimeThreshold = 0
                },
            )
        }
        item{
            Column {
                Text(text = "自定义节目单", style = MaterialTheme.typography.titleMedium)
                var epgUrls by remember { mutableStateOf(settingsViewModel.epgUrls) }
                URLListEditor(
                    urls = epgUrls,
                    onUrlsChange = { newUrls ->
                        epgUrls = newUrls
                        settingsViewModel.epgUrls = newUrls
                    }
                )
                Text(
                    style = MaterialTheme.typography.bodySmall,
                    text = "「${APP_NAME}」支持从直播源中获取 EPG 节目单 (x-tvg-url)，您也可以在这里添加自定义节目单，如果没有提供，将使用默认的节目单提供商。"
                )
            }
        }
    }

}

@Preview
@Composable
private fun LeanbackSettingsCategoryEpgPreview() {
    SP.init(LocalContext.current)
    LeanbackTheme {
        LeanbackSettingsCategoryEpg(
            modifier = Modifier.padding(20.dp),
            settingsViewModel = LeanbackSettingsViewModel().apply {
            }
        )
    }
}