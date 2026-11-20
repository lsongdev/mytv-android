@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package me.lsong.mytv.ui.video

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import me.lsong.mytv.domain.video.VideoCategory
import me.lsong.mytv.ui.focus.placedFocusTarget
import me.lsong.mytv.ui.focus.PlacedFocusTargets
import me.lsong.mytv.ui.focus.prepareFocusAt
import me.lsong.mytv.ui.focus.PaddedEdgeBringIntoViewSpec

@Composable
internal fun VideoCategoryFilters(
    filters: List<VideoCategory>, selectedId: String?, focusRequest: Int,
    onSelect: (String?) -> Unit, onDown: () -> Unit, onLeft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val choices = listOf(null to "全部") + filters.map { it.id to it.title }
    val target = remember { FocusRequester() }
    val placedChoices = remember { PlacedFocusTargets() }
    val scroll = rememberLazyListState()
    val inset = with(LocalDensity.current) { 12.dp.toPx() }
    val scrollPolicy = remember(inset) { PaddedEdgeBringIntoViewSpec(inset) }
    var focusedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(focusRequest,filters.map { it.id }) {
        if (focusRequest > 0) {
            val id = focusedId?.takeIf { item -> choices.any { it.first == item } } ?: selectedId?.takeIf { item -> choices.any { it.first == item } }
            scroll.prepareFocusAt(choices.indexOfFirst { it.first == id }.coerceAtLeast(0))
            withFrameNanos { }
            (placedChoices.requester(id ?: "all") ?: target).requestFocus()
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides scrollPolicy) {
    LazyRow(
        state = scroll,contentPadding = PaddingValues(8.dp),horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().focusRequester(target).focusProperties {
            enter = {
                val id = focusedId?.takeIf { item -> choices.any { it.first == item } }
                    ?: selectedId?.takeIf { item -> choices.any { it.first == item } }
                placedChoices.requester(id ?: "all") ?: FocusRequester.Default
            }
        }.focusGroup().testTag("vod-category-filters")
            .onPreviewKeyEvent { event ->
                val action = when {
                    event.key == Key.DirectionLeft && focusedId == null -> onLeft
                    event.key == Key.DirectionDown -> onDown
                    event.key == Key.DirectionUp -> ({})
                    else -> null
                }
                if (action != null) {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) action()
                    true
                } else false
            },
    ) {
        itemsIndexed(choices,key = { _,entry -> entry.first ?: "all" }) { index,(id,title) ->
            val choiceTarget = remember(id) { FocusRequester() }
            Card(onClick = { onSelect(id) },scale = CardDefaults.scale(focusedScale = 1f),
                colors = CardDefaults.colors(containerColor = Color.White.copy(alpha = if (id == selectedId) .18f else .06f)),
                modifier = Modifier.widthIn(min = 64.dp,max = 128.dp).heightIn(min = 32.dp)
                    .testTag("vod-filter:${id ?: "all"}").focusRequester(choiceTarget).placedFocusTarget(id ?: "all",choiceTarget,placedChoices)
                    .focusProperties {
                        if (index == 0) left = FocusRequester.Cancel
                        if (index == choices.lastIndex) right = FocusRequester.Cancel
                    }.onFocusChanged { if (it.isFocused) { focusedId = id; onSelect(id) } },
            ) {
                Text(title,modifier = Modifier.padding(horizontal = 12.dp,vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,maxLines = 1,overflow = TextOverflow.Ellipsis)
            }
        }
    }
    }
}
