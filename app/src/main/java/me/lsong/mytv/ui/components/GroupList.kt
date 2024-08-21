package me.lsong.mytv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.ListItemDefaults
import kotlinx.coroutines.flow.distinctUntilChanged
import me.lsong.mytv.iptv.TVGroup
import me.lsong.mytv.iptv.TVGroupList
import me.lsong.mytv.ui.theme.LeanbackTheme
import me.lsong.mytv.utils.handleLeanbackKeyEvents
import kotlin.math.max

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MyTvGroupList(
    modifier: Modifier = Modifier,
    groupListProvider: () -> TVGroupList = { TVGroupList() },
    focusedGroupProvider: () -> TVGroup = { TVGroup() },
    exitFocusRequesterProvider: () -> FocusRequester = { FocusRequester.Default },
    onGroupFocused: (TVGroup) -> Unit = {},
    onUserAction: () -> Unit = {},
) {
    val iptvGroupList = groupListProvider()
    val initialIptvGroup = focusedGroupProvider()
    val focusRequester = remember { FocusRequester() }
    var focusedIptvGroup by remember { mutableStateOf(initialIptvGroup) }

    val listState = rememberTvLazyListState(max(0, iptvGroupList.indexOf(initialIptvGroup) - 2))

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { _ -> onUserAction() }
    }

    TvLazyColumn(
        state = listState,
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .width(190.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background.copy(0.9f))
            .focusRequester(focusRequester)
            .focusProperties {
                exit = {
                    focusRequester.saveFocusedChild()
                    exitFocusRequesterProvider()
                }
                enter = {
                    if (focusRequester.restoreFocusedChild()) FocusRequester.Cancel
                    else FocusRequester.Default
                }
            },
    ) {
        items(iptvGroupList) { iptvGroup ->
            val isSelected by remember { derivedStateOf { iptvGroup == focusedIptvGroup } }
            MyTvGroupItem(
                groupProvider = { iptvGroup },
                isSelectedProvider = { isSelected },
                isFocusedProvider = { iptvGroup == initialIptvGroup },
                onFocused = {
                    focusedIptvGroup = it
                    onGroupFocused(it)
                },
            )
        }

    }
}

@Composable
private fun MyTvGroupItem(
    modifier: Modifier = Modifier,
    groupProvider: () -> TVGroup = { TVGroup() },
    isSelectedProvider: () -> Boolean = { false },
    isFocusedProvider: () -> Boolean = { false },
    onFocused: (TVGroup) -> Unit = {},
) {
    val iptvGroup = groupProvider()
    val focusRequester = remember { FocusRequester() }
    var hasFocused by rememberSaveable { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasFocused && isFocusedProvider()) {
            focusRequester.requestFocus()
        }
        hasFocused = true
    }

    CompositionLocalProvider(
        LocalContentColor provides if (isFocused) MaterialTheme.colorScheme.background
        else MaterialTheme.colorScheme.onBackground
    ) {
        androidx.tv.material3.ListItem(
            modifier = modifier
                .focusRequester(focusRequester)
                .onFocusChanged {
                    isFocused = it.isFocused || it.hasFocus

                    if (isFocused) {
                        onFocused(iptvGroup)
                    }
                }
                .handleLeanbackKeyEvents(
                    onSelect = {
                        focusRequester.requestFocus()
                    },
                ),
            colors = ListItemDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.onBackground,
                selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = 0.5f
                ),
            ),
            selected = isSelectedProvider(),
            onClick = { },
            headlineContent = {
                Text(
                    text = iptvGroup.title,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }
}

@Preview
@Composable
private fun MyTvGroupListPreview() {
    LeanbackTheme {
        MyTvGroupList(
            modifier = Modifier.padding(20.dp),
            groupListProvider = { TVGroupList.EXAMPLE },
        )
    }
}