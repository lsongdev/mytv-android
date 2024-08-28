package me.lsong.mytv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.lsong.mytv.R
import me.lsong.mytv.epg.EpgList
import me.lsong.mytv.epg.EpgList.Companion.currentProgrammes
import me.lsong.mytv.epg.EpgRepository
import me.lsong.mytv.iptv.IPTVProvider
import me.lsong.mytv.iptv.TVChannel
import me.lsong.mytv.iptv.TVGroupList
import me.lsong.mytv.iptv.TVGroupList.Companion.channels
import me.lsong.mytv.iptv.TVGroupList.Companion.findGroupIndex
import me.lsong.mytv.iptv.TVProvider
import me.lsong.mytv.ui.components.LeanbackMonitorScreen
import me.lsong.mytv.ui.components.LeanbackVisible
import me.lsong.mytv.ui.player.MyTvVideoScreen
import me.lsong.mytv.ui.player.rememberLeanbackVideoPlayerState
import me.lsong.mytv.ui.settings.MyTvSettingsViewModel
import me.lsong.mytv.ui.theme.LeanbackTheme
import me.lsong.mytv.ui.widgets.MyTvMenu
import me.lsong.mytv.ui.widgets.MyTvMenuItem
import me.lsong.mytv.ui.widgets.MyTvNowPlaying
import me.lsong.mytv.utils.Constants
import me.lsong.mytv.utils.handleLeanbackDragGestures
import me.lsong.mytv.utils.handleLeanbackKeyEvents
import top.yogiczy.mytv.ui.screens.leanback.settings.MyTvSettingsScreen


@Composable
fun MyTvMenuWidget(
    modifier: Modifier = Modifier,
    epgListProvider: () -> EpgList = { EpgList() },
    channelProvider: () -> TVChannel = { TVChannel() },
    groupListProvider: () -> TVGroupList = { TVGroupList() },
    onSelected: (TVChannel) -> Unit = {},
    onSettings: () -> Unit = {},
    onUserAction: () -> Unit = {}
) {
    val groupList = groupListProvider()
    val currentChannel = channelProvider()
    val epgList = epgListProvider()

    val groups = remember(groupList) {
        groupList.map { group ->
            MyTvMenuItem(title = group.title)
        }
    }

    var currentGroup = remember(groupList, currentChannel) {
        groups.firstOrNull { it.title == groupList[groupList.findGroupIndex(currentChannel)].title }
            ?: MyTvMenuItem()
    }

    val currentMenuItem = remember(currentChannel) {
        MyTvMenuItem(
            icon = currentChannel.logo ?: "",
            title = currentChannel.title,
            description = epgList.currentProgrammes(currentChannel)?.now?.title
        )
    }

    val itemsProvider: (String) -> List<MyTvMenuItem> = { groupTitle ->
        groupList.find { it.title == groupTitle }?.channels?.map { channel ->
            MyTvMenuItem(
                icon = channel.logo ?: "",
                title = channel.title,
                description = epgList.currentProgrammes(channel)?.now?.title
            )
        } ?: emptyList()
    }

    Row {
        MyTvMenu(
            groups = groups,
            itemsProvider = itemsProvider,
            currentGroup = currentGroup,
            currentItem = currentMenuItem,
            onItemSelected = { selectedItem ->
                val selectedChannel = groupList.channels.first { it.title == selectedItem.title }
                onSelected(selectedChannel)
            },
            modifier = modifier,
            onSettings = onSettings,
            onUserAction = onUserAction,
        )
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit = {},
    mainViewModel: MainViewModel = viewModel(),
    settingsViewModel: MyTvSettingsViewModel = viewModel()
) {
    val uiState by mainViewModel.uiState.collectAsState()

    LeanbackBackPressHandledArea(
        modifier = modifier,
        onBackPressed = onBackPressed
    ) {
        when (val state = uiState) {
            is LeanbackMainUiState.Loading,
            is LeanbackMainUiState.Error -> StartScreen(state)
            is LeanbackMainUiState.Ready -> MainContent(
                modifier = modifier,
                groups = state.groups,
                epgList = state.epgList,
                onBackPressed = onBackPressed,
                settingsViewModel = settingsViewModel
            )
        }
    }
}

@Composable
private fun StartScreen(state: LeanbackMainUiState) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher),
                contentDescription = "DuckTV",
                modifier = Modifier.size(96.dp)
            )
            Text(
                text = Constants.APP_NAME,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )

            when (state) {
                is LeanbackMainUiState.Loading -> {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .widthIn(300.dp, 800.dp)
                            .height(8.dp)
                    )
                    state.message?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                            modifier = Modifier.sizeIn(maxWidth = 500.dp),
                        )
                    }
                }
                is LeanbackMainUiState.Error -> {
                    state.message?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.sizeIn(maxWidth = 500.dp),
                        )
                    }
                }
                else -> {} // This case should never happen
            }
        }
    }
}

sealed interface LeanbackMainUiState {
    data class Loading(val message: String? = null) : LeanbackMainUiState
    data class Error(val message: String? = null) : LeanbackMainUiState
    data class Ready(
        val groups: TVGroupList = TVGroupList(),
        val epgList: EpgList = EpgList(),
    ) : LeanbackMainUiState
}

@Composable
fun MainContent(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit = {},
    epgList: EpgList = EpgList(),
    groups: TVGroupList = TVGroupList(),
    settingsViewModel: MyTvSettingsViewModel = viewModel(),
) {
    val videoPlayerState = rememberLeanbackVideoPlayerState()
    val mainContentState = rememberMainContentState(
        videoPlayerState = videoPlayerState,
        groups = groups,
    )

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // 防止切换到其他界面时焦点丢失
        // TODO 换一个更好的解决方案
        // while (true) {
        //     if (!mainContentState.isChannelInfoVisible && !mainContentState.isMenuVisible
        //     ) {
        //         focusRequester.requestFocus()
        //     }
        //     delay(100)
        // }
    }

    LeanbackBackPressHandledArea(
        modifier = modifier,
        onBackPressed = {
            if (mainContentState.isChannelInfoVisible) {
                mainContentState.isMenuVisible = false
                mainContentState.isChannelInfoVisible = false
            }
            else if (mainContentState.isMenuVisible) {
                mainContentState.isMenuVisible = false
                mainContentState.isChannelInfoVisible = false
            } else onBackPressed()
        },
    ) {
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
                    onSwipeDown = {
                        if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentChannelToNext()
                        else mainContentState.changeCurrentChannelToPrev()
                    },
                    onSwipeUp = {
                        if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentChannelToPrev()
                        else mainContentState.changeCurrentChannelToNext()
                    },
                    onSwipeLeft = {
                        mainContentState.changeToPrevSource()
                    },
                    onSwipeRight = {
                        mainContentState.changeToNextSource()
                    },
                ),
        )

        LeanbackVisible({ mainContentState.isMenuVisible && !mainContentState.isChannelInfoVisible }) {
            MyTvMenuWidget(
                epgListProvider = { epgList },
                groupListProvider = { groups },
                channelProvider = { mainContentState.currentChannel },
                onSelected = { channel -> mainContentState.changeCurrentChannel(channel) },
                onSettings = { mainContentState.showSettings() }
            )
        }

        LeanbackVisible({  mainContentState.isChannelInfoVisible }) {
            MyTvNowPlaying(
                modifier = modifier,
                epgListProvider = { epgList },
                channelProvider = { mainContentState.currentChannel },
                channelIndexProvider = { mainContentState.currentChannelIndex },
                sourceIndexProvider = { mainContentState.currentSourceIndex },
                videoPlayerMetadataProvider = { videoPlayerState.metadata },
                onClose = { mainContentState.isChannelInfoVisible = false },
            )
        }

        LeanbackVisible({ settingsViewModel.debugShowFps }) {
            LeanbackMonitorScreen()
        }

        LeanbackVisible({ mainContentState.isSettingsVisale }) {
            MyTvSettingsScreen()
        }

    }
}

@Composable
fun LeanbackBackPressHandledArea(
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) = Box(
    modifier = Modifier
        .onPreviewKeyEvent {
            if (it.key == Key.Back && it.type == KeyEventType.KeyUp) {
                onBackPressed()
                true
            } else {
                false
            }
        }
        .then(modifier),
    content = content,
)

// MainViewModel.kt
class MainViewModel : ViewModel() {
    private val providers: List<TVProvider> = listOf(
        IPTVProvider(EpgRepository())
    )
    private val _uiState = MutableStateFlow<LeanbackMainUiState>(LeanbackMainUiState.Loading())
    val uiState: StateFlow<LeanbackMainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            refreshData()
        }
    }

    private suspend fun refreshData() {
        try {
            _uiState.value = LeanbackMainUiState.Loading("Initializing providers...")
            providers.forEachIndexed { index, provider ->
                _uiState.value = LeanbackMainUiState.Loading("Initializing provider ${index + 1}/${providers.size}...")
                provider.load()
            }

            val groupList = providers.flatMap { it.groups() }
            val epgList = providers.map { it.epg() }.reduce { acc, epgList -> (acc + epgList) as EpgList }

            _uiState.value = LeanbackMainUiState.Ready(
                groups = TVGroupList(groupList),
                epgList = epgList
            )
        } catch (error: Exception) {
            _uiState.value = LeanbackMainUiState.Error(error.message)
        }
    }
}

@Preview(device = "id:pixel_5")
@Composable
private fun MyTvMainScreenPreview() {
    LeanbackTheme {
        MainScreen()
    }
}

