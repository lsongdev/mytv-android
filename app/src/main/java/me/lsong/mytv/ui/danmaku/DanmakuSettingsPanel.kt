@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package me.lsong.mytv.ui.danmaku

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import java.util.Locale

@Composable
fun DanmakuSettingsPanel(model: DanmakuViewModel, onClose: () -> Unit) {
    val state by model.state.collectAsState()
    val prefs = state.preferences
    var tab by remember { mutableIntStateOf(1) }
    val expanded = remember { mutableStateMapOf<String,Boolean>() }
    val sourcesScroll = rememberLazyListState()
    val stylesScroll = rememberLazyListState()
    val initial = remember { FocusRequester() }
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) { withFrameNanos { }; initial.requestFocus() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .24f)).testTag("danmaku-settings"), contentAlignment = Alignment.CenterEnd) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(.72f).fillMaxHeight()
            .background(Color(0xD911151D)).padding(24.dp)
            .focusProperties { exit = { FocusRequester.Cancel } }.focusGroup(),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("弹幕", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                Button(onClick = model::toggle, modifier = Modifier.focusRequester(initial).testTag("danmaku-toggle")) { Text(if (prefs.enabled) "已开启" else "开启弹幕") }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = onClose) { Text("完成") }
            }
            Text(state.message, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("弹幕样式", "弹幕来源").forEachIndexed { i,name ->
                    Button(onClick = { tab = i },modifier = Modifier.testTag("danmaku-tab:$i")) { Text(if (tab == i) "● $name" else name) }
                }
            }
            when {
                tab == 0 -> LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("danmaku-style-list"),state = stylesScroll,contentPadding = PaddingValues(8.dp),verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Setting("显示区域", when (prefs.area) { 1f -> "全屏"; .34f -> "顶部 1/3"; else -> "半屏" }) {
                        model.update(prefs.copy(area = when (prefs.area) { .34f -> .5f; .5f -> 1f; else -> .34f }))
                    } }
                    item { Setting("透明度", "${(prefs.rendering.opacity * 100).toInt()}%") {
                        val steps = listOf(.4f,.55f,.7f,.85f,1f); model.update(prefs.copy(rendering = prefs.rendering.copy(opacity = steps[(steps.indexOf(prefs.rendering.opacity) + 1) % steps.size])))
                    } }
                    item { Setting("弹幕字号", "${prefs.rendering.fontScale}×") {
                        val steps = listOf(.7f,.85f,1f,1.2f,1.4f); model.update(prefs.copy(rendering = prefs.rendering.copy(fontScale = steps[(steps.indexOf(prefs.rendering.fontScale) + 1) % steps.size])))
                    } }
                    item { Setting("滚动速度", "${prefs.rendering.speed}×") {
                        val steps = listOf(.5f,.75f,1f,1.5f,2f); model.update(prefs.copy(rendering = prefs.rendering.copy(speed = steps[(steps.indexOf(prefs.rendering.speed) + 1) % steps.size])))
                    } }
                    item { Setting("显示密度", "${prefs.rendering.densityPct}%") {
                        val steps = listOf(25,50,75,100); model.update(prefs.copy(rendering = prefs.rendering.copy(densityPct = steps[(steps.indexOf(prefs.rendering.densityPct) + 1) % steps.size])))
                    } }
                    item { Setting("防止重叠", if (prefs.rendering.antiOverlap) "开启" else "关闭") { model.update(prefs.copy(rendering = prefs.rendering.copy(antiOverlap = !prefs.rendering.antiOverlap))) } }
                }
                else -> {
                    Text("OK 选择 / 停用    已选分集：← 提前 1 秒 / → 延后 1 秒",style = MaterialTheme.typography.bodySmall)
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("danmaku-source-list"),state = sourcesScroll,
                        contentPadding = PaddingValues(12.dp),verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.sources.forEach { source ->
                            val enabled = source.providerId in prefs.providers
                            val selectedId = source.selected?.let { "${it.id}:${source.episode?.episodeId}" }
                            val primary = source.options.filter { it.suggested || it.id == selectedId }
                            val visible = if (expanded[source.providerId] == true) source.options else primary.ifEmpty { source.options.take(6) }
                            item(key = "heading:${source.providerId}") {
                                Column(Modifier.padding(top = 8.dp,bottom = 2.dp),verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(source.name,style = MaterialTheme.typography.titleMedium,modifier = Modifier.testTag("danmaku-heading:${source.providerId}"))
                                    Text(if (!enabled) "未启用 · ${source.message}" else source.message,style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            items(visible,key = { "option:${it.id}" }) { option ->
                                val chosen = option.id == selectedId
                                ListItem(selected = chosen && enabled,onClick = { model.chooseMatch(source.providerId,option.id) },
                                    headlineContent = { Text("${option.candidate.media.title} · ${option.episode.title}",maxLines = 2,overflow = TextOverflow.Ellipsis) },
                                    supportingContent = { Text(listOfNotNull(option.candidate.media.year?.toString(),option.candidate.media.season?.let { "第${it}季" },
                                        if (chosen) (if (enabled) "已启用" else "未启用") else if (option.suggested) "匹配当前分集" else null).joinToString(" · ")) },
                                    trailingContent = if (chosen) { { Text(String.format(Locale.getDefault(),"%+.1f 秒",source.offsetMs / 1000.0),Modifier.width(84.dp),maxLines = 1,textAlign = androidx.compose.ui.text.style.TextAlign.End) } } else null,
                                    scale = ListItemDefaults.scale(focusedScale = 1f),
                                    modifier = Modifier.testTag("danmaku-option:${option.id}").onPreviewKeyEvent {
                                        if (it.key == Key.DirectionLeft || it.key == Key.DirectionRight) {
                                            if (chosen && it.type == KeyEventType.KeyDown) model.adjustOffset(source.providerId,if (it.key == Key.DirectionLeft) -1_000 else 1_000)
                                            true
                                        } else false
                                    })
                            }
                            item(key = "actions:${source.providerId}") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (source.options.size > primary.size && source.options.size > visible.size || expanded[source.providerId] == true) {
                                        OutlinedButton(onClick = { expanded[source.providerId] = expanded[source.providerId] != true },
                                            modifier = Modifier.testTag("danmaku-expand:${source.providerId}")) { Text(if (expanded[source.providerId] == true) "收起其他分集" else "其他分集（${source.options.size - visible.size}）") }
                                    }
                                    OutlinedButton(onClick = { model.searchMatches(source.providerId) },modifier = Modifier.testTag("danmaku-search:${source.providerId}")) {
                                        Text(if (source.matching) "搜索中…" else if (source.options.isEmpty()) "查找匹配" else "重新搜索")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun Setting(title: String, value: String, onClick: () -> Unit) {
    ListItem(selected = false,onClick = onClick,headlineContent = { Text(title) },trailingContent = { Text(value) },scale = ListItemDefaults.scale(focusedScale = 1f))
}
