package me.lsong.mytv.ui.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.tv.material3.*
import me.lsong.mytv.app.AppContainer
import me.lsong.mytv.data.preferences.LiveOptions
import me.lsong.mytv.domain.provider.isHttpUrl
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EpgSettings(modifier: Modifier = Modifier) {
    val preferences = AppContainer.livePreferences
    val options by preferences.options.collectAsState()
    val state by AppContainer.epg.state.collectAsState()
    val health by AppContainer.providerStorage.health.collectAsState()
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val format = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    val update: (LiveOptions) -> Unit = preferences::update
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SettingsRow(headlineContent = "频道台标", supportingContent = "优先使用直播源台标，缺失时使用节目单台标；图片自动缓存", trailingContent = if (options.showLogos) "显示" else "隐藏", onSelected = { update(options.copy(showLogos = !options.showLogos)) }) }
        item { SettingsRow(headlineContent = "当前节目信息", supportingContent = "在频道列表和播放控制中显示", trailingContent = if (options.showProgrammes) "显示" else "隐藏", onSelected = { update(options.copy(showProgrammes = !options.showProgrammes)) }) }
        item { SettingsRow(headlineContent = "节目单", trailingContent = if (options.epgEnabled) "开启" else "关闭", onSelected = { update(options.copy(epgEnabled = !options.epgEnabled)) }) }
        item { SettingsRow(headlineContent = "从直播源发现节目单", supportingContent = "读取 M3U 的 x-tvg-url / url-tvg，支持 XMLTV 和 gzip", trailingContent = if (options.discoverEpg) "开启" else "关闭", onSelected = { update(options.copy(discoverEpg = !options.discoverEpg)) }) }
        item { SettingsRow(headlineContent = "默认节目单", supportingContent = "自定义和直播源节目单均不可用时，使用公共 XMLTV 兜底", trailingContent = if (options.useDefaultEpg) "开启" else "关闭", onSelected = { update(options.copy(useDefaultEpg = !options.useDefaultEpg)) }) }
        item { SettingsRow(headlineContent = "自动刷新间隔", trailingContent = "${options.refreshHours} 小时", onSelected = { val values = listOf(1,3,6,12,24); update(options.copy(refreshHours = values[(values.indexOf(options.refreshHours) + 1) % values.size])) }) }
        item { SettingsRow(headlineContent = "立即刷新节目单", supportingContent = if (state.loading) "正在加载…" else "${state.feeds.size} 个节目单 · ${state.programmes.size} 个频道；失败保留缓存，后台自动退避", trailingContent = "刷新", onSelected = { AppContainer.epg.refresh() }) }
        item { Text("添加自定义 XMLTV 地址", style = MaterialTheme.typography.titleMedium) }
        item {
            androidx.compose.material3.OutlinedTextField(value = input, onValueChange = { input = it; error = null },
                singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { androidx.compose.material3.Text("https://example.com/epg.xml.gz") }, isError = error != null)
        }
        item { Button(onClick = {
            val url = input.trim()
            if (!isHttpUrl(url)) error = "请输入完整的 http:// 或 https:// 地址"
            else { update(options.copy(epgUrls = options.epgUrls + url)); input = "" }
        }) { Text("添加节目单") } }
        error?.let { message -> item { Text(message, color = Color(0xFFFF9D8F)) } }
        items((options.epgUrls + state.feeds.map { it.url }).toList(), key = { it }) { url ->
            val feed = state.feeds.firstOrNull { it.url == url }
            // Reading health above subscribes this row to network status updates.
            val status = health[feed?.provider?.id ?: "epg:$url"]
            val description = buildList {
                add(if (url in options.epgUrls) "自定义 · 按确定移除" else if (feed?.fallback == true) "默认公共节目单 · 主节目单不可用时启用" else "来自直播源")
                add(status?.let { if (it.error == null) "响应成功 · ${it.latencyMs} ms" else "${it.error} · 已退避" } ?: "等待加载")
                status?.takeIf { it.respondedAt > 0 }?.let { add("最后响应 ${format.format(Date(it.respondedAt))}") }
                status?.takeIf { it.cachedAt > 0 }?.let { add("缓存 ${format.format(Date(it.cachedAt))}") }
            }.joinToString("\n")
            SettingsRow(headlineContent = url, supportingContent = description, trailingContent = if (url in options.epgUrls) "移除" else "自动", onSelected = {
                if (url in options.epgUrls) update(options.copy(epgUrls = options.epgUrls - url))
            })
        }
    }
}
