package me.lsong.mytv.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import me.lsong.mytv.ui.video.*
import me.lsong.mytv.domain.video.*

import me.lsong.mytv.ui.focus.prepareFocusAt
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun BrowseList(
    entries: List<BrowseEntry>,
    selectedId: String?,
    contextKey: String,
    active: Boolean,
    focusRequest: Int,
    onFocused: (String) -> Unit,
    onSelected: (String) -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    downFocusRequester: FocusRequester = FocusRequester.Cancel,
) {
    val focusRequester = remember { FocusRequester() }
    val selectedIndex = entries.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val listState = rememberLazyListState()
    var restoringFocus by remember(active, focusRequest, contextKey) { mutableStateOf(true) }
    LaunchedEffect(active, focusRequest, contextKey) {
        restoringFocus = true
        if (active) {
            if (entries.isNotEmpty()) listState.prepareFocusAt(selectedIndex)
            withFrameNanos { }
            focusRequester.requestFocus()
            entries.getOrNull(selectedIndex)?.let { onFocused(it.id) }
        }
        restoringFocus = false
    }
    if (entries.isEmpty()) {
        Box(
            modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.025f))
                .columnKeys(onLeft, onRight)
                .focusRequester(focusRequester)
                .focusProperties { up = FocusRequester.Cancel; down = downFocusRequester }
                .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            Text(emptyMessage, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = modifier.fillMaxSize().focusGroup().columnKeys(onLeft, onRight),
        ) {
            items(entries, key = { it.id }) { entry ->
                ListItem(
                    selected = entry.id == selectedId,
                    onClick = { onSelected(entry.id) },
                    scale = ListItemDefaults.scale(focusedScale = 1f),
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        selectedContainerColor = Color.White.copy(alpha = 0.09f),
                        focusedContainerColor = MaterialTheme.colorScheme.onSurface,
                        focusedContentColor = MaterialTheme.colorScheme.surface,
                    ),
                    headlineContent = { Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = entry.subtitle?.let { subtitle ->
                        { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    },
                    leadingContent = when {
                        entry is BrowseEntry.Application -> { {
                            coil.compose.AsyncImage(model = entry.app.icon, contentDescription = null,
                                contentScale = ContentScale.Fit, modifier = Modifier.size(36.dp))
                        } }
                        entry is BrowseEntry.Channel && entry.showLogo -> { { ChannelLogo(entry.title, entry.logo, entry.fallbackLogo) } }
                        else -> null
                    },
                    trailingContent = if (entry.playing) { { Icon(Icons.Default.Check, "正在播放", Modifier.size(18.dp)) } } else null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .testTag("drawer-entry:${entry.id}")
                        .focusProperties {
                            if (entry.id == entries.first().id) up = FocusRequester.Cancel
                            if (entry.id == entries.last().id) down = downFocusRequester
                        }
                        .then(if (entry.id == entries[selectedIndex].id) Modifier.focusRequester(focusRequester) else Modifier)
                        .onFocusChanged {
                            if (it.isFocused && active && !restoringFocus) onFocused(entry.id)
                        },
                )
            }
        }
    }
}

internal fun Modifier.columnKeys(onLeft: () -> Unit, onRight: () -> Unit): Modifier = onPreviewKeyEvent {
    when (it.key) {
        Key.DirectionLeft, Key.DirectionRight -> {
            if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 0) {
                if (it.key == Key.DirectionLeft) onLeft() else onRight()
            }
            true
        }
        else -> false
    }
}

@Composable
private fun ChannelLogo(title: String, url: String?, fallbackUrl: String?) {
    val urls = remember(url,fallbackUrl) { listOfNotNull(url,fallbackUrl).filter(String::isNotBlank).distinct() }
    var index by remember(urls) { mutableIntStateOf(0) }
    var loaded by remember(urls,index) { mutableStateOf(false) }
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF283544)), contentAlignment = Alignment.Center) {
        if (!loaded) Text(title.take(2), color = Color(0xFFD9E6F5), style = MaterialTheme.typography.labelMedium)
        urls.getOrNull(index)?.let { image ->
            coil.compose.AsyncImage(model = coil.request.ImageRequest.Builder(LocalContext.current).data(image).crossfade(false).build(),
                contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(3.dp),
                onSuccess = { loaded = true }, onError = { index++ })
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.containFocus(): Modifier = focusProperties {
    exit = { direction ->
        if (direction == androidx.compose.ui.focus.FocusDirection.Down || direction == androidx.compose.ui.focus.FocusDirection.Up || direction == androidx.compose.ui.focus.FocusDirection.Right) FocusRequester.Cancel else FocusRequester.Default
    }
}.focusGroup()
