package me.lsong.mytv.ui

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import me.lsong.mytv.R
import me.lsong.mytv.epg.EpgChannel
import me.lsong.mytv.epg.EpgList
import me.lsong.mytv.epg.EpgList.Companion.currentProgrammes
import me.lsong.mytv.epg.EpgRepository
import me.lsong.mytv.iptv.IptvRepository
import me.lsong.mytv.iptv.TVChannel
import me.lsong.mytv.iptv.TVChannelList
import me.lsong.mytv.iptv.TVGroup
import me.lsong.mytv.iptv.TVGroupList
import me.lsong.mytv.iptv.TVGroupList.Companion.channels
import me.lsong.mytv.iptv.TVGroupList.Companion.findGroupIndex
import me.lsong.mytv.iptv.TVSource
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
import me.lsong.mytv.utils.Settings
import me.lsong.mytv.utils.handleLeanbackDragGestures
import me.lsong.mytv.utils.handleLeanbackKeyEvents

@Composable
fun MyTvMenuWidget(
    modifier: Modifier = Modifier,
    groupListProvider: () -> TVGroupList = { TVGroupList() },
    epgListProvider: () -> EpgList = { EpgList() },
    channelProvider: () -> TVChannel = { TVChannel() },
    onSelected: (TVChannel) -> Unit = {},
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

    val currentGroup = remember(groupList, currentChannel) {
        groups.firstOrNull { it.title == groupList[groupList.findGroupIndex(currentChannel)].title }
            ?: MyTvMenuItem()
    }

    val currentMenuItem = remember(currentChannel) {
        MyTvMenuItem(
            icon = currentChannel.logo,
            title = currentChannel.title,
            description = epgList.currentProgrammes(currentChannel)?.now?.title ?: currentChannel.name
        )
    }

    val itemsProvider: (String) -> List<MyTvMenuItem> = { groupTitle ->
        groupList.find { it.title == groupTitle }?.channels?.map { channel ->
            MyTvMenuItem(
                icon = channel.logo ?: "",
                title = channel.title,
                description = epgList.currentProgrammes(channel)?.now?.title ?: channel.name
            )
        } ?: emptyList()
    }

    MyTvMenu(
        groups = groups,
        itemsProvider = itemsProvider,
        currentGroupProvider = { currentGroup },
        currentItemProvider = { currentMenuItem },
        onGroupSelected = { /* 可以在这里添加组被选中时的逻辑 */ },
        onItemSelected = { selectedItem ->
            val selectedChannel = groupList.channels.first { it.title == selectedItem.title }
            onSelected(selectedChannel)
        },
        modifier = modifier,
        onUserAction = onUserAction
    )
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
                groupList = state.tvGroupList,
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

@Composable
fun MainContent(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit = {},
    epgList: EpgList = EpgList(),
    groupList: TVGroupList = TVGroupList(),
    settingsViewModel: MyTvSettingsViewModel = viewModel(),
) {
    val videoPlayerState = rememberLeanbackVideoPlayerState()
    val mainContentState = rememberMainContentState(
        videoPlayerState = videoPlayerState,
        tvGroupList = groupList,
    )

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // 防止切换到其他界面时焦点丢失
        // TODO 换一个更好的解决方案
        while (true) {
            if (!mainContentState.isChannelInfoVisible && !mainContentState.isMenuVisible
            ) {
                focusRequester.requestFocus()
            }
            delay(100)
        }
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
                groupListProvider = { groupList },
                channelProvider = { mainContentState.currentChannel },
                onSelected = { channel -> mainContentState.changeCurrentChannel(channel) }
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

class MainViewModel : ViewModel() {
    private val iptvRepository = IptvRepository()
    private val epgRepository = EpgRepository()

    private val _uiState = MutableStateFlow<LeanbackMainUiState>(LeanbackMainUiState.Loading())
    val uiState: StateFlow<LeanbackMainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            refreshData()
        }
    }

    private suspend fun refreshData() {
        var epgUrls = emptyArray<String>()
        var iptvUrls = emptyArray<String>()
        if (Settings.iptvSourceUrls.isNotEmpty()) {
            iptvUrls += Settings.iptvSourceUrls
        }
        if (iptvUrls.isEmpty()) {
            iptvUrls += Constants.IPTV_SOURCE_URL
        }
        flow {
            val allSources = mutableListOf<TVSource>()
            iptvUrls.forEachIndexed { index, url ->
                emit(LoadingState(index + 1, iptvUrls.size, url, "IPTV"))
                val m3u = fetchDataWithRetry { iptvRepository.getChannelSourceList(sourceUrl = url) }
                allSources.addAll(m3u.sources)
                if (m3u.epgUrl != null)
                    epgUrls += (m3u.epgUrl).toString()
            }
            if (epgUrls.isEmpty()) {
                epgUrls += Constants.EPG_XML_URL
            }
            val epgChannels = mutableListOf<EpgChannel>()
            epgUrls.distinct().toTypedArray().forEachIndexed { index, url ->
                emit(LoadingState(index + 1, epgUrls.size, url, "EPG"))
                val epg = fetchDataWithRetry { epgRepository.getEpgList(url) }
                epgChannels.addAll(epg.value)
            }
            val groupList = processChannelSources(allSources)
            emit(DataResult(groupList, EpgList(epgChannels.distinctBy{ it.id })))
        }
            .catch { error ->
                _uiState.value = LeanbackMainUiState.Error(error.message)
                Settings.iptvSourceUrlHistoryList -= iptvUrls.toList()
            }
            .collect { result ->
                when (result) {
                    is LoadingState -> {
                        _uiState.value =
                            LeanbackMainUiState.Loading("获取${result.type}数据(${result.currentSource}/${result.totalSources})...")
                    }
                    is DataResult -> {
                        Log.d("epg","合并节目单完成：${result.epgList.size}")
                        _uiState.value = LeanbackMainUiState.Ready(
                            tvGroupList = result.groupList,
                            epgList = result.epgList
                        )
                        Settings.iptvSourceUrlHistoryList += iptvUrls.toList()
                    }
                }
            }
    }

    private suspend fun <T> fetchDataWithRetry(fetch: suspend () -> T): T {
        var attempt = 0
        while (attempt < Constants.HTTP_RETRY_COUNT) {
            try {
                return fetch()
            } catch (e: Exception) {
                attempt++
                if (attempt >= Constants.HTTP_RETRY_COUNT) throw e
                delay(Constants.HTTP_RETRY_INTERVAL)
            }
        }
        throw IllegalStateException("Failed to fetch data after $attempt attempts")
    }

    private fun processChannelSources(sources: List<TVSource>): TVGroupList {
        val sourceList = TVChannelList(sources.groupBy { it.name }.map { channelEntry ->
            TVChannel(
                name = channelEntry.key,
                title = channelEntry.value.first().title,
                sources = channelEntry.value)
        })
        val groupList = TVGroupList(sourceList.groupBy { it.groupTitle ?: "其他" }.map { groupEntry ->
            TVGroup(title = groupEntry.key, channels = TVChannelList(groupEntry.value))
        })
        return groupList
    }
    private data class LoadingState(val currentSource: Int, val totalSources: Int, val currentUrl: String, val type: String)
    private data class DataResult(val groupList: TVGroupList, val epgList: EpgList)
}

sealed interface LeanbackMainUiState {
    data class Loading(val message: String? = null) : LeanbackMainUiState
    data class Error(val message: String? = null) : LeanbackMainUiState
    data class Ready(
        val tvGroupList: TVGroupList = TVGroupList(),
        val epgList: EpgList = EpgList(),
    ) : LeanbackMainUiState
}

@Preview(device = "id:pixel_5")
@Composable
private fun MyTvMainScreenPreview() {
    LeanbackTheme {
        MainScreen()
    }
}

