package me.lsong.mytv.ui.danmaku

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun DanmakuSettingsPanel(model: DanmakuViewModel, onClose: () -> Unit) {
    val state by model.state.collectAsState()
    val prefs = state.preferences
    var tab by remember { mutableIntStateOf(0) }
    val initial = remember { FocusRequester() }
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) { withFrameNanos { }; initial.requestFocus() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .24f)).testTag("danmaku-settings"), contentAlignment = Alignment.CenterEnd) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(.72f).fillMaxHeight()
            .background(Color(0xD911151D))
            .padding(24.dp).focusProperties { exit = { FocusRequester.Cancel } }.focusGroup(),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("弹幕", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                Button(onClick = model::toggle, modifier = Modifier.focusRequester(initial).testTag("danmaku-toggle")) { Text(if (prefs.enabled) "已开启" else "开启弹幕") }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = onClose) { Text("完成") }
            }
            Text(if (state.loading) "${state.message}…" else state.message, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("影片匹配", "匹配分集", "显示与同步").forEachIndexed { i, name ->
                    Button(onClick = { tab = i }) { Text(if (tab == i) "● $name" else name) }
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (tab) {
                    0 -> {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(onClick = { model.restart() }) { Text("重新匹配") }
                            }
                        }
                        item { Text("匹配候选 · ${state.candidates.size}", style = MaterialTheme.typography.titleSmall) }
                        items(state.candidates, key = { it.id }) { candidate ->
                            ListItem(selected = candidate.id == state.selected?.id, onClick = { model.choose(candidate); tab = 1 },
                                headlineContent = { Text(candidate.media.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text("${candidate.providerName} · ${candidate.media.year ?: ""}") },
                                scale = ListItemDefaults.scale(focusedScale = 1f), modifier = Modifier.testTag("danmaku-source:${candidate.id}"))
                        }
                    }
                    1 -> {
                        item { Text(state.selected?.let { "${it.providerName} · ${it.media.title}" } ?: "请先选择弹幕来源") }
                        items(state.episodes, key = { it.episodeId }) { episode ->
                            ListItem(selected = episode.episodeId == state.episode?.episodeId, onClick = { model.chooseEpisode(episode) },
                                headlineContent = { Text(episode.title) },
                                scale = ListItemDefaults.scale(focusedScale = 1f), modifier = Modifier.testTag("danmaku-episode:${episode.episodeId}"))
                        }
                    }
                    else -> {
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
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("时间偏移 · ${prefs.offsetMs / 1000.0} 秒（正值为延后显示）")
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(onClick = { model.update(prefs.copy(offsetMs = prefs.offsetMs - 1000)) }) { Text("提前 1 秒") }
                                    OutlinedButton(onClick = { model.update(prefs.copy(offsetMs = 0)) }) { Text("重置") }
                                    OutlinedButton(onClick = { model.update(prefs.copy(offsetMs = prefs.offsetMs + 1000)) }) { Text("延后 1 秒") }
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
    ListItem(selected = false, onClick = onClick, headlineContent = { Text(title) }, trailingContent = { Text(value) }, scale = ListItemDefaults.scale(focusedScale = 1f))
}
