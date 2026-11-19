package me.lsong.mytv.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.lsong.mytv.domain.provider.ProviderKind
import me.lsong.mytv.ui.settings.components.*

@Composable
fun SettingsContent(category: SettingsCategory, sourceViewModel: ProviderSettingsViewModel, danmaku: me.lsong.mytv.ui.danmaku.DanmakuViewModel? = null) {
    when (category) {
        SettingsCategory.LIVE -> ProviderSettingsPanel(ProviderKind.M3U, sourceViewModel)
        SettingsCategory.VIDEOS -> ProviderSettingsPanel(ProviderKind.MAC_CMS, sourceViewModel)
        else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(category.title, style = MaterialTheme.typography.headlineSmall)
            when (category) {
                SettingsCategory.GENERAL -> GeneralSettings()
                SettingsCategory.EPG -> EpgSettings()
                SettingsCategory.DANMAKU -> danmaku?.let { me.lsong.mytv.ui.danmaku.DanmakuSourceSettings(it) }
                SettingsCategory.ABOUT -> AboutSettings()
                else -> Unit
            }
        }
    }
}
