@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package me.lsong.mytv.ui.video

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import kotlinx.coroutines.flow.StateFlow
import me.lsong.mytv.domain.playback.ResourceProbe
import me.lsong.mytv.ui.focus.PaddedEdgeBringIntoViewSpec

internal data class VideoChoice(val id: String, val title: String, val subtitle: String, val selected: Boolean)

@Composable
internal fun VideoChoicesRow(
    choices: List<VideoChoice>, tag: String, width: Dp, focusTarget: FocusRequester,
    onUp: (() -> Unit)? = null, onDown: (() -> Unit)? = null,
    onFocused: (String) -> Unit = {}, onClick: (String) -> Unit, onLeft: () -> Unit,
    probes: StateFlow<Map<String, ResourceProbe>>? = null,
) {
    // Stable requesters per item, rather than moving one requester on every DPAD event.
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    var focusedId by remember { mutableStateOf<String?>(null) }
    val selected = choices.firstOrNull { it.selected }?.id ?: choices.firstOrNull()?.id
    val entryId = focusedId?.takeIf { id -> choices.any { it.id == id } } ?: selected
    val results = probes?.collectAsState()?.value.orEmpty()
    val scroll = rememberLazyListState()
    val inset = with(LocalDensity.current) { 12.dp.toPx() }
    val policy = remember(inset) { PaddedEdgeBringIntoViewSpec(inset) }
    if (choices.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(72.dp).focusRequester(focusTarget).focusable())
        return
    }
    choices.forEach { requesters.getOrPut(it.id) { FocusRequester() } }
    CompositionLocalProvider(LocalBringIntoViewSpec provides policy) {
        LazyRow(
            state = scroll, horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            modifier = Modifier.fillMaxWidth().focusRequester(focusTarget)
                .focusProperties { enter = { requesters[entryId] ?: FocusRequester.Default } }
                .focusGroup().onPreviewKeyEvent { event ->
                    val index = choices.indexOfFirst { it.id == focusedId }.coerceAtLeast(0)
                    val action = when {
                        event.key == Key.DirectionLeft && index == 0 -> onLeft
                        event.key == Key.DirectionUp -> onUp
                        event.key == Key.DirectionDown -> onDown
                        else -> null
                    }
                    if (action != null) {
                        // A held Left must stop at the first card, not escape the detail pane.
                        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) action()
                        true
                    } else false
                },
        ) {
            itemsIndexed(choices, key = { _, choice -> choice.id }) { index, choice ->
                Card(
                    onClick = { onClick(choice.id) }, scale = CardDefaults.scale(focusedScale = 1f),
                    colors = CardDefaults.colors(containerColor = Color.White.copy(alpha = if (choice.selected) .16f else .06f)),
                    modifier = Modifier.width(width).heightIn(min = 64.dp).testTag("$tag:${choice.id}")
                        .focusRequester(requesters.getValue(choice.id))
                        .focusProperties {
                            if (index == 0) left = FocusRequester.Cancel
                            if (index == choices.lastIndex) right = FocusRequester.Cancel
                            if (onDown == null) down = FocusRequester.Cancel
                        }
                        .onFocusChanged { if (it.isFocused) { focusedId = choice.id; onFocused(choice.id) } },
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(choice.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(choice.subtitle, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (probes != null) ResourceProbeBadge(results[choice.id] ?: ResourceProbe.Pending, Modifier.testTag("vod-probe:${choice.id}"))
                    }
                }
            }
        }
    }
}
