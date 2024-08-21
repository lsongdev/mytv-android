package me.lsong.mytv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyListState
import androidx.tv.foundation.lazy.list.itemsIndexed
import kotlinx.coroutines.flow.distinctUntilChanged
import me.lsong.mytv.epg.EpgList
import me.lsong.mytv.epg.EpgList.Companion.currentProgrammes
import me.lsong.mytv.iptv.TVChannel
import me.lsong.mytv.iptv.TVChannelList
import me.lsong.mytv.ui.theme.LeanbackTheme
import kotlin.math.max

@Composable
fun MyTvChannelList(
    modifier: Modifier = Modifier,
    epgListProvider: () -> EpgList = { EpgList() },
    channelsProvider: () -> TVChannelList = { TVChannelList() },
    focusedProvider: () -> TVChannel = { TVChannel() },
    onUserAction: () -> Unit = {},
    onSelected: (TVChannel) -> Unit = {},
    onFocused: (TVChannel) -> Unit = {},
    onFavoriteToggle: (TVChannel) -> Unit = {},
) {
    val channelList = channelsProvider()
    val focusedChannel = focusedProvider()

    val hasFocused by rememberSaveable { mutableStateOf(!channelList.contains(focusedChannel)) }
    val itemFocusRequesterList = remember(channelList) {
        List(channelList.size) { FocusRequester() }
    }
    val focusedIptv by remember(channelList) { mutableStateOf(focusedChannel) }
    val listState = remember(channelsProvider()) {
        TvLazyListState(
            if (hasFocused) 0
            else max(0, channelList.indexOf(focusedChannel) - 2)
        )
    }

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
            .fillMaxHeight()
            .width(250.dp)
            .background(MaterialTheme.colorScheme.background.copy(0.8f)),
    ) {
        itemsIndexed(channelList, key = { _, iptv -> iptv.hashCode() }) { index, iptv ->
            val isSelected by remember { derivedStateOf { iptv == focusedIptv } }
            val initialFocused by remember {
                derivedStateOf { !hasFocused && iptv == focusedChannel }
            }
            MyTvChannelItem(
                channelProvider = { iptv },
                epgProgrammeProvider = { epgListProvider().currentProgrammes(iptv)?.now },
                focusRequesterProvider = { itemFocusRequesterList[index] },
                isSelectedProvider = { isSelected },
                isFocusedProvider = { initialFocused },
                onSelected = { onSelected(iptv) },
                onFocused = {  onFocused(iptv) },
                onFavoriteToggle = { onFavoriteToggle(iptv) },
            )
        }
    }
}

@Preview
@Composable
private fun MyTvChannelListPreview() {
    LeanbackTheme {
        MyTvChannelList(
            modifier = Modifier.padding(20.dp),
            channelsProvider = { TVChannelList.EXAMPLE },
        )
    }
}