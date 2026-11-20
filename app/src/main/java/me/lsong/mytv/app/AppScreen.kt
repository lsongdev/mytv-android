package me.lsong.mytv.app

import me.lsong.mytv.playback.ui.PlayerControls
import me.lsong.mytv.playback.danmaku.PlaybackDanmakuLayer
import me.lsong.mytv.ui.danmaku.DanmakuViewModel
import me.lsong.mytv.ui.danmaku.DanmakuSettingsPanel
import me.lsong.mytv.playback.ui.PlayerSurface
import me.lsong.mytv.ui.debug.MonitorScreen
import me.lsong.mytv.ui.live.ChannelDetailsOverlay

import me.lsong.mytv.ui.browser.ContentDrawer

import me.lsong.mytv.ui.components.VisibilityContainer
import me.lsong.mytv.ui.settings.SettingsContent

import me.lsong.mytv.data.preferences.AppPreferences

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.lsong.mytv.data.live.LiveCatalogRepository

import me.lsong.mytv.ui.browser.ContentDrawerState

import me.lsong.mytv.playback.rememberPlaybackState
import me.lsong.mytv.ui.video.VideoLibraryViewModel
import me.lsong.mytv.ui.settings.SettingsViewModel

import me.lsong.mytv.ui.settings.ProviderSettingsViewModel
import me.lsong.mytv.ui.input.onPlayerGestures
import me.lsong.mytv.ui.input.onRemoteKeyActions

@Composable
fun AppScreen(
    modifier: Modifier = Modifier,
    appViewModel: AppViewModel = viewModel(),
    homeRequest: Int = 0,
) {
    AppContent(modifier = modifier, liveCatalog = appViewModel.liveCatalog, homeRequest = homeRequest)
}

@Composable
fun AppContent(
    modifier: Modifier = Modifier,
    liveCatalog: LiveCatalogRepository,
    homeRequest: Int = 0,
    settingsViewModel: SettingsViewModel = viewModel(),
    providerSettings: ProviderSettingsViewModel = viewModel(),
    videoLibrary: VideoLibraryViewModel = viewModel(factory = AppContainer.videoViewModelFactory),
) {
    val epg by AppContainer.epg.state.collectAsState()
    val liveOptions by AppContainer.livePreferences.options.collectAsState()
    var epgClock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { epgClock = System.currentTimeMillis(); kotlinx.coroutines.delay(30_000) } }
    val videoPlayerState = rememberPlaybackState()
    val danmaku: DanmakuViewModel = viewModel(factory = AppContainer.danmakuViewModelFactory)
    val danmakuState by danmaku.state.collectAsState()
    var danmakuPanel by remember { mutableStateOf(false) }
    var resumeAfterDanmaku by remember { mutableStateOf(false) }
    var controlsFocusRequest by remember { mutableIntStateOf(0) }
    val playback = rememberPlaybackCoordinator(
        liveCatalog = liveCatalog,
        videoPlayerState = videoPlayerState,
    )

    var restoringPlayback by remember { mutableStateOf(true) }
    var drawerVisible by remember { mutableStateOf(liveCatalog.channels.isEmpty()) }
    var channelDetailsVisible by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(false) }
    val showControls: () -> Unit = {
        drawerVisible = false
        channelDetailsVisible = false
        controlsVisible = true
    }
    val showMenu: () -> Unit = { controlsVisible = false; run { drawerVisible = true; channelDetailsVisible = false } }

    val channels = liveCatalog.channels
    LaunchedEffect(channels,restoringPlayback) {
        if (!restoringPlayback) {
            if (!playback.hasMedia && channels.isNotEmpty()) {
                playback.changeCurrentChannel(channels.getOrElse(AppPreferences.iptvLastIptvIdx) { channels.first() })
                drawerVisible = false
            } else playback.reconcileLiveCatalog()
        }
    }

    val drawerState = remember(liveCatalog) {
        ContentDrawerState(playback.currentChannel.takeIf { it.resources.map { it.uri }.isNotEmpty() })
    }

    LaunchedEffect(homeRequest, restoringPlayback) {
        if (homeRequest > 0 && !restoringPlayback) {
            danmakuPanel = false
            showMenu()
            drawerState.section = me.lsong.mytv.ui.navigation.Section.APPS
            drawerState.focusSettings = false
            drawerState.enterColumn(0)
        }
    }

    LaunchedEffect(playback) {
        val saved = runCatching { AppContainer.playbackSessions.load()?.available(AppContainer.providerConfigs.providers.value) }.getOrNull()
        if (saved != null && playback.restore(saved)) {
            drawerVisible = false
            controlsVisible = saved.paused
            if (saved.video != null) drawerState.section = me.lsong.mytv.ui.navigation.Section.VIDEOS
        }
        restoringPlayback = false
        saved?.video?.let { restored -> launch {
            val semaphore = Semaphore(2)
            val client = me.lsong.mytv.data.providers.maccms.MacCmsVideoProvider(storage = AppContainer.providerStorage)
            val records = supervisorScope { restored.details.work.variants.map { record -> async {
                val provider = AppContainer.providerConfigs.providers.value.firstOrNull { it.id == record.ref.providerId && it.isEnabled }
                if (provider == null) null else try { semaphore.withPermit { client.details(provider,record.ref.remoteId) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            } }.awaitAll().filterNotNull() }
            if (records.isNotEmpty()) {
                val details = withContext(Dispatchers.Default) { me.lsong.mytv.domain.video.VideoAggregator.details(restored.details.work,records) }
                if (playback.videoSelection?.partId == restored.partId) playback.refreshRestoredVideo(details)
            }
        } }
    }
    val saveSession by rememberUpdatedState(newValue = {
        if (!restoringPlayback) playback.snapshot(if (danmakuPanel) !resumeAfterDanmaku else null)?.let { AppContainer.playbackSessions.save(it) }
    })
    LaunchedEffect(restoringPlayback) {
        if (!restoringPlayback) while (true) { saveSession(); delay(5_000) }
    }
    LaunchedEffect(restoringPlayback,playback) {
        if (!restoringPlayback) snapshotFlow { Triple(playback.videoSelection?.let { "${it.details.work.id}|${it.partId}|${it.resourceId}" }
            ?: "${playback.currentChannel.id}|${playback.currentResourceIndex}",videoPlayerState.isPaused,playback.videoSpeed) }
            .collectLatest { saveSession() }
    }
    val playbackLifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(playbackLifecycle) {
        val observer = LifecycleEventObserver { _,event -> if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) saveSession() }
        playbackLifecycle.addObserver(observer)
        onDispose { saveSession(); playbackLifecycle.removeObserver(observer) }
    }

    BackHandler {
        if (drawerVisible) {
            drawerVisible = false
            channelDetailsVisible = false
        } else showMenu()
    }
    val focusRequester = remember { FocusRequester() }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(drawerVisible, channelDetailsVisible, controlsVisible, windowFocused) {
        if (windowFocused && !controlsVisible && !drawerVisible && !channelDetailsVisible) {
            withFrameNanos { }
            focusRequester.requestFocus()
        }
    }
    PlayerSurface(
        state = videoPlayerState,
        aspectRatioProvider = { settingsViewModel.videoPlayerAspectRatio },
        showMetadataProvider = { settingsViewModel.debugShowVideoPlayerMetadata },
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusProperties {
                canFocus = !controlsVisible && !drawerVisible &&
                    !channelDetailsVisible
            }
            .onRemoteKeyActions(
                onUp = {
                    if (playback.videoSelection == null) {
                        if (settingsViewModel.iptvChannelChangeFlip) playback.changeCurrentChannelToNext()
                        else playback.changeCurrentChannelToPrev()
                    }
                },
                onDown = {
                    if (playback.videoSelection == null) {
                        if (settingsViewModel.iptvChannelChangeFlip) playback.changeCurrentChannelToPrev()
                        else playback.changeCurrentChannelToNext()
                    }
                },
                onLeft = showControls, onRight = showControls,
                onSelect = showControls,
                onSettings = showMenu, onNumber = {},
            )
            .onPlayerGestures(onSwipeLeft = showControls, onSwipeRight = showControls, onSwipeDown = showControls, onSwipeUp = showControls)
            .focusable(),
    )

    PlaybackDanmakuLayer(videoPlayerState, playback.videoSelection, playback.videoSpeed, danmaku,
        visible = !drawerVisible, suspended = danmakuPanel)
    if (controlsVisible) PlayerControls(
        videoPlayerState, playback.playingTitle.orEmpty(), playback.videoSelection == null,
        playback.videoSpeed,
        onToggle = { if (playback.videoSelection != null) playback.toggleVideoPause() else if (videoPlayerState.isPaused) videoPlayerState.play() else videoPlayerState.pause() },
        onSpeed = playback::cycleVideoSpeed, onMenu = showMenu, onClose = { controlsVisible = false }, onBack = { controlsVisible = false },
        resourceLabel = playback.currentChannel.takeIf { playback.videoSelection == null && it.resources.map { it.uri }.size > 1 }?.let {
            "${it.resources.getOrNull(playback.currentResourceIndex)?.providerName.orEmpty()} · ${playback.currentResourceIndex + 1}/${it.resources.map { it.uri }.size}"
        },
        onResource = playback::nextResource,
        aspectRatioLabel = settingsViewModel.videoPlayerAspectRatio.title,
        onAspectRatio = settingsViewModel::cycleVideoAspectRatio,
        liveProgramme = if (playback.videoSelection == null && liveOptions.showProgrammes) {
            val schedule = with(me.lsong.mytv.domain.epg.EpgList) { epg.programmes.getEpgChannel(playback.currentChannel) }
            val current = schedule?.programmes?.firstOrNull { epgClock >= it.startAt && epgClock < it.endAt }
            val next = schedule?.programmes?.firstOrNull { it.startAt > epgClock }
            listOfNotNull(current?.let { "正在播放 · ${it.title}" }, next?.let { "接下来 · ${it.title}" }).joinToString("    ").takeIf(String::isNotBlank)
        } else null,
        restoreFocus = controlsFocusRequest,
        danmakuLabel = if (playback.videoSelection == null) null else if (!danmakuState.preferences.enabled) "弹幕 · 关" else "弹幕 · ${danmakuState.activeSources.size} 源",
        onDanmakuToggle = danmaku::toggle,
        onDanmakuSettings = {
            resumeAfterDanmaku = !videoPlayerState.isPaused
            videoPlayerState.pause(); danmakuPanel = true
        },
    )

    androidx.compose.animation.AnimatedVisibility(visible = danmakuPanel,
        enter = androidx.compose.animation.slideInHorizontally { it },
        exit = androidx.compose.animation.slideOutHorizontally { it }) {
    DanmakuSettingsPanel(danmaku) {
        danmakuPanel = false
        if (resumeAfterDanmaku) videoPlayerState.play()
        controlsFocusRequest++
    }
    }

    VisibilityContainer({ drawerVisible && !channelDetailsVisible }) {
        ContentDrawer(
            catalog = liveCatalog.catalog,
            providerCount = liveCatalog.providerCatalogs.size,
            playingChannel = playback.currentChannel.takeIf { it.resources.map { it.uri }.isNotEmpty() && playback.videoSelection == null },
            state = drawerState,
            onPlay = { channel ->
                playback.changeCurrentChannel(channel)
                drawerVisible = false
            },
            onClose = { drawerVisible = false },
            settingsContent = { category -> SettingsContent(category, providerSettings, danmaku) },
            videoLibrary = videoLibrary,
            onPlayVideo = { selection -> playback.playVideo(selection); drawerVisible = false },
            playingVideo = playback.videoSelection,
            nowPlayingTitle = playback.playingTitle,
            epg = epg.programmes, liveOptions = liveOptions, nowMs = epgClock,
        )
    }

    VisibilityContainer({  channelDetailsVisible }) {
        ChannelDetailsOverlay(
            modifier = modifier,
            channelProvider = { playback.currentChannel },
            channelIndexProvider = { playback.currentChannelIndex },
            sourceIndexProvider = { playback.currentResourceIndex },
            videoPlayerMetadataProvider = { videoPlayerState.metadata },
            onClose = { channelDetailsVisible = false },
        )
    }

    VisibilityContainer({ settingsViewModel.debugShowFps }) {
        MonitorScreen()
    }

}
