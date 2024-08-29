package me.lsong.mytv.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.lsong.mytv.R
import me.lsong.mytv.providers.MyTvProviderManager
import me.lsong.mytv.providers.TVChannel
import me.lsong.mytv.providers.TVGroupList.Companion.channels
import me.lsong.mytv.providers.TVGroupList.Companion.findGroupIndex
import me.lsong.mytv.ui.components.LeanbackVisible
import me.lsong.mytv.ui.components.MonitorScreen
import me.lsong.mytv.ui.components.MyTvMenuItem
import me.lsong.mytv.ui.components.MyTvMenuItemList
import me.lsong.mytv.ui.components.MyTvNowPlaying
import me.lsong.mytv.ui.player.MyTvVideoScreen
import me.lsong.mytv.ui.player.rememberLeanbackVideoPlayerState
import me.lsong.mytv.ui.settings.MyTvSettingsViewModel
import me.lsong.mytv.ui.settings.SettingsScreen
import me.lsong.mytv.ui.theme.LeanbackTheme
import me.lsong.mytv.utils.Constants
import me.lsong.mytv.utils.handleLeanbackDragGestures
import me.lsong.mytv.utils.handleLeanbackKeyEvents

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel = viewModel(),
) {
    val uiState by mainViewModel.uiState.collectAsState()
    when (val state = uiState) {
        is LeanbackMainUiState.Loading,
        is LeanbackMainUiState.Error -> StartScreen(state)
        is LeanbackMainUiState.Ready -> MainContent(
            modifier = modifier,
            providerManager = state.providerManager,
        )
    }
}

@Composable
fun MyTvMenuWidget(
    modifier: Modifier = Modifier,
    providerManager: MyTvProviderManager,
    // epgListProvider: () -> EpgList = { EpgList() },
    // groupListProvider: () -> TVGroupList = { TVGroupList() },
    channelProvider: () -> TVChannel = { TVChannel() },
    onSelected: (TVChannel) -> Unit = {},
    onSettings: (() -> Unit)? = null,
    onUserAction: () -> Unit = {}
) {
    // val epgList = epgListProvider()
    val groupList = providerManager.groups();
    val currentChannel = channelProvider()

    val groups = remember(groupList) {
        groupList.map { group ->
            MyTvMenuItem(title = group.title)
        }
    }

    val currentGroup = remember(groupList, currentChannel) {
        groups.firstOrNull { it.title == currentChannel.groupTitle }
            ?: MyTvMenuItem()
    }

    Log.d("currentGroup", "$currentGroup $currentChannel")

    val currentMenuItem = remember(currentChannel) {
        MyTvMenuItem(
            icon = currentChannel.logo ?: "",
            title = currentChannel.title,
            // description = epgList.currentProgrammes(currentChannel)?.now?.title
        )
    }

    val itemsProvider: (String) -> List<MyTvMenuItem> = { groupTitle ->
        groupList.find { it.title == groupTitle }?.channels?.map { channel ->
            MyTvMenuItem(
                icon = channel.logo ?: "",
                title = channel.title,
                // description = epgList.currentProgrammes(channel)?.now?.title
            )
        } ?: emptyList()
    }

    var focusedGroup by remember { mutableStateOf(currentGroup) }
    var focusedItem by remember { mutableStateOf(currentMenuItem) }
    var items by remember { mutableStateOf(itemsProvider(focusedGroup.title)) }
    val rightListFocusRequester = remember { FocusRequester() }

    Row(modifier = modifier) {
        Column (
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .width(250.dp)
                .fillMaxHeight()
                .background(androidx.tv.material3.MaterialTheme.colorScheme.background.copy(0.9f)),
        ) {
            MyTvMenuItemList(
                items = groups,
                selectedItem = focusedGroup,
                onFocused = { menuItem ->
                    focusedGroup = menuItem
                    items = itemsProvider(focusedGroup.title)
                },
                onSelected = { menuItem ->
                    focusedGroup = menuItem
                    items = itemsProvider(focusedGroup.title)
                    focusedItem = items.firstOrNull() ?: MyTvMenuItem()
                    rightListFocusRequester.requestFocus()
                },
                onUserAction = onUserAction,
                modifier = Modifier.weight(1f)
            )
            LeanbackVisible ({ onSettings != null }) {
                TvLazyColumn(
                    modifier = Modifier.width(250.dp),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    item {
                        MyTvMenuItem(
                            item = MyTvMenuItem(icon = Icons.Default.Settings, title = "Settings"),
                            onSelected = onSettings!!
                        )
                    }
                }
            }
        }
        MyTvMenuItemList(
            items = items,
            modifier = Modifier
                .fillMaxHeight()
                .background(androidx.tv.material3.MaterialTheme.colorScheme.background.copy(0.8f)),
            selectedItem = focusedItem,
            onSelected = { menuItem ->
                focusedItem = menuItem
                val selectedChannel = groupList.channels.first { it.title == menuItem.title }
                onSelected(selectedChannel)
            },
            onUserAction = onUserAction,
            focusRequester = rightListFocusRequester,
        )
    }

    LaunchedEffect(Unit) {
        rightListFocusRequester.requestFocus()
    }
}

@Composable
fun MainContent(
    modifier: Modifier = Modifier,
    providerManager: MyTvProviderManager,
    settingsViewModel: MyTvSettingsViewModel = viewModel(),
) {
    val videoPlayerState = rememberLeanbackVideoPlayerState()
    val mainContentState = rememberMainContentState(
        providerManager = providerManager,
        videoPlayerState = videoPlayerState,
    )

    BackHandler (
        mainContentState.isMenuVisible ||
                mainContentState.isSettingsVisale ||
                mainContentState.isChannelInfoVisible
    ) {
        mainContentState.isMenuVisible = false
        mainContentState.isSettingsVisale = false
        mainContentState.isChannelInfoVisible = false
    }
    val focusRequester = remember { FocusRequester() }
    MyTvVideoScreen(
        state = videoPlayerState,
        aspectRatioProvider = { settingsViewModel.videoPlayerAspectRatio },
        showMetadataProvider = { settingsViewModel.debugShowVideoPlayerMetadata },
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .handleLeanbackKeyEvents(
                onUp = {
                    if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentChannelToNext()
                    else mainContentState.changeCurrentChannelToPrev()
                },
                onDown = {
                    if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentChannelToPrev()
                    else mainContentState.changeCurrentChannelToNext()
                },
                onLeft = { mainContentState.changeToPrevSource() },
                onRight = { mainContentState.changeToNextSource() },
                onSelect = { mainContentState.showChannelInfo() },
                onLongDown = { mainContentState.showMenu() },
                onLongSelect = { mainContentState.showMenu() },
                onSettings = { mainContentState.showMenu() },
                onNumber = {},
            )
            .handleLeanbackDragGestures(
                onSwipeLeft = { mainContentState.changeToPrevSource() },
                onSwipeRight = { mainContentState.changeToNextSource() },
                onSwipeDown = {
                    if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentChannelToNext()
                    else mainContentState.changeCurrentChannelToPrev()
                },
                onSwipeUp = {
                    if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentChannelToPrev()
                    else mainContentState.changeCurrentChannelToNext()
                },
            ),
    )

    LeanbackVisible({ mainContentState.isMenuVisible && !mainContentState.isChannelInfoVisible }) {
        MyTvMenuWidget(
            providerManager = providerManager,
            channelProvider = { mainContentState.currentChannel },
            onSelected = { channel -> mainContentState.changeCurrentChannel(channel) },
            onSettings = { mainContentState.showSettings() }
        )
    }

    LeanbackVisible({  mainContentState.isChannelInfoVisible }) {
        MyTvNowPlaying(
            modifier = modifier,
            channelProvider = { mainContentState.currentChannel },
            channelIndexProvider = { mainContentState.currentChannelIndex },
            sourceIndexProvider = { mainContentState.currentSourceIndex },
            videoPlayerMetadataProvider = { videoPlayerState.metadata },
            onClose = { mainContentState.isChannelInfoVisible = false },
        )
    }

    LeanbackVisible({ settingsViewModel.debugShowFps }) {
        MonitorScreen()
    }

    LeanbackVisible({ mainContentState.isSettingsVisale }) {
        SettingsScreen()
    }
}

@Preview(device = "id:pixel_5")
@Composable
private fun MyTvMainScreenPreview() {
    LeanbackTheme {
        MainScreen()
    }
}

