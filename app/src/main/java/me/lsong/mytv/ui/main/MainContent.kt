package me.lsong.mytv.ui.main

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import me.lsong.mytv.data.entities.EpgList
import me.lsong.mytv.data.entities.TVGroupList
import me.lsong.mytv.data.entities.TVGroupList.Companion.channels
import me.lsong.mytv.ui.components.LeanbackVisible
import me.lsong.mytv.ui.components.LeanbackMonitorScreen
import me.lsong.mytv.ui.player.MyTvVideoScreen
import me.lsong.mytv.ui.settings.LeanbackSettingsViewModel
import me.lsong.mytv.ui.player.rememberLeanbackVideoPlayerState
import me.lsong.mytv.ui.widgets.MyTvMenu
import me.lsong.mytv.ui.widgets.MyTvNowPlaying
import me.lsong.mytv.utils.SP
import me.lsong.mytv.utils.handleLeanbackDragGestures
import me.lsong.mytv.utils.handleLeanbackKeyEvents

@Composable
fun LeanbackMainContent(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit = {},
    epgList: EpgList = EpgList(),
    groupList: TVGroupList = TVGroupList(),
    settingsViewModel: LeanbackSettingsViewModel = viewModel(),
) {
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()

    val videoPlayerState = rememberLeanbackVideoPlayerState(
        defaultAspectRatioProvider = {
            when (settingsViewModel.videoPlayerAspectRatio) {
                SP.VideoPlayerAspectRatio.ORIGINAL -> null
                SP.VideoPlayerAspectRatio.SIXTEEN_NINE -> 16f / 9f
                SP.VideoPlayerAspectRatio.FOUR_THREE -> 4f / 3f
                SP.VideoPlayerAspectRatio.AUTO -> {
                    configuration.screenHeightDp.toFloat() / configuration.screenWidthDp.toFloat()
                }
            }
        }
    )
    val mainContentState = rememberMainContentState(
        videoPlayerState = videoPlayerState,
        tvGroupList = groupList,
    )

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // 防止切换到其他界面时焦点丢失
        // TODO 换一个更好的解决方案
        while (true) {
            if (!mainContentState.isChannelInfoVisible
                && !mainContentState.isMenuVisible
            ) {
                focusRequester.requestFocus()
            }
            delay(100)
        }
    }

    LeanbackBackPressHandledArea(
        modifier = modifier,
        onBackPressed = {
            if (mainContentState.isChannelInfoVisible) mainContentState.isChannelInfoVisible = false
            else if (mainContentState.isMenuVisible) mainContentState.isMenuVisible = false
            else onBackPressed()
        },
    ) {
        MyTvVideoScreen(
            state = videoPlayerState,
            showMetadataProvider = { settingsViewModel.debugShowVideoPlayerMetadata },
            modifier = Modifier
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
                    onLeft = {
                        if (mainContentState.currentChannel.urls.size > 1) {
                            mainContentState.changeCurrentChannel(
                                channel = mainContentState.currentChannel,
                                urlIdx = mainContentState.currentSourceIndex - 1,
                            )
                        }
                    },
                    onRight = {
                        if (mainContentState.currentChannel.urls.size > 1) {
                            mainContentState.changeCurrentChannel(
                                channel = mainContentState.currentChannel,
                                urlIdx = mainContentState.currentSourceIndex + 1,
                            )
                        }
                    },
                    onSelect = { mainContentState.isChannelInfoVisible = true },
                    onLongSelect = { mainContentState.isMenuVisible = true },
                    onSettings = { mainContentState.isMenuVisible = true },
                    onNumber = {
                        // if (settingsViewModel.iptvChannelNoSelectEnable) {
                            // panelChannelNoSelectState.input(it)
                        // }
                    },
                    onLongDown = { mainContentState.isMenuVisible = true },
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
                    onSwipeRight = {
                        if (mainContentState.currentChannel.urls.size > 1) {
                            mainContentState.changeCurrentChannel(
                                channel = mainContentState.currentChannel,
                                urlIdx = mainContentState.currentSourceIndex - 1,
                            )
                        }
                    },
                    onSwipeLeft = {
                        if (mainContentState.currentChannel.urls.size > 1) {
                            mainContentState.changeCurrentChannel(
                                channel = mainContentState.currentChannel,
                                urlIdx = mainContentState.currentSourceIndex + 1,
                            )
                        }
                    },
                ),
        )

        LeanbackVisible({ mainContentState.isMenuVisible && !mainContentState.isChannelInfoVisible }) {
            MyTvMenu(
                epgListProvider = { epgList },
                groupListProvider = { groupList },
                channelProvider = { mainContentState.currentChannel },
                onClose = { mainContentState.isMenuVisible = false },
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