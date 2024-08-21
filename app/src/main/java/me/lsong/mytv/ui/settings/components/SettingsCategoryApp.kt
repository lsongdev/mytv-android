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
                    headlineContent = "全局画面比例",
                    trailingContent = when (settingsViewModel.videoPlayerAspectRatio) {
                        Settings.VideoPlayerAspectRatio.ORIGINAL -> "原始"
                        Settings.VideoPlayerAspectRatio.ASPECT_16_9 -> "16:9"
                        Settings.VideoPlayerAspectRatio.ASPECT_4_3 -> "4:3"
                        Settings.VideoPlayerAspectRatio.FULL_SCREEN -> "自动拉伸"
                    },
                    onSelected = {
                        settingsViewModel.videoPlayerAspectRatio =
                            Settings.VideoPlayerAspectRatio.entries.let {
                                it[(it.indexOf(settingsViewModel.videoPlayerAspectRatio) + 1) % it.size]
                            }
                    },
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
