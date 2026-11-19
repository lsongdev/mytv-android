package me.lsong.mytv.ui.danmaku

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import me.lsong.mytv.ui.settings.components.SettingsRow

@Composable
fun DanmakuSourceSettings(model: DanmakuViewModel) {
    val state by model.state.collectAsState()
    val prefs = state.preferences
    LazyColumn(Modifier.fillMaxSize(),contentPadding = PaddingValues(12.dp),verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SettingsRow(headlineContent = "弹幕",supportingContent = "开启后匹配影视弹幕；关闭时不请求数据源",trailingContent = if (prefs.enabled) "开启" else "关闭",onSelected = model::toggle) }
        item { Text("弹幕数据源",style = MaterialTheme.typography.titleMedium) }
        item { Text("选择参与影片匹配的平台；影片与分集匹配、显示和同步在播放器的弹幕抽屉中调整。",style = MaterialTheme.typography.bodyMedium) }
        items(danmakuProviderNames.entries.toList(),key = { it.key }) { (id,name) ->
            SettingsRow(headlineContent = name,trailingContent = if (id in prefs.providers) "已启用" else "已停用",onSelected = {
                model.update(prefs.copy(providers = if (id in prefs.providers) prefs.providers - id else prefs.providers + id))
            })
        }
    }
}
