package me.lsong.mytv.ui.browser

import me.lsong.mytv.domain.epg.EpgList
import me.lsong.mytv.domain.epg.EpgList.Companion.getEpgChannel
import me.lsong.mytv.data.preferences.LiveOptions
import me.lsong.mytv.ui.navigation.Section
import me.lsong.mytv.app.AppConstants
import me.lsong.mytv.R
import androidx.compose.ui.res.painterResource

import me.lsong.mytv.domain.live.ChannelGroup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import me.lsong.mytv.ui.settings.SettingsCategory
import me.lsong.mytv.ui.video.*
import me.lsong.mytv.domain.video.*
import me.lsong.mytv.domain.live.LiveCatalog
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import me.lsong.mytv.domain.live.LiveChannel

private const val LIVE_AGGREGATE_ID = "live"
private const val ALL_GROUP = "all"
private val DrawerBackground = Color(0xD911151D)

/** Browsing is independent of playback. Keep this state above the drawer's visibility branch. */
@Stable
class ContentDrawerState(playingChannel: LiveChannel?) {
    val groupIds = mutableStateMapOf<String, String>().apply {
        playingChannel?.let { put(LIVE_AGGREGATE_ID, "group:${it.records.firstOrNull()?.groupTitle.orEmpty()}") }
    }
    val channelIds = mutableStateMapOf<String, String>()
    var column by mutableIntStateOf(if (playingChannel == null) 0 else 2)
    var focusRequest by mutableIntStateOf(0)
    var section by mutableStateOf(Section.LIVE)
    var liveScheduleId by mutableStateOf<String?>(null)
    var videoNavigation by mutableStateOf("latest")
    var videoSearch by mutableStateOf(false)
    var videoWorkId by mutableStateOf<String?>(null)
    var applicationDetailsId by mutableStateOf<String?>(null)
    var applicationId by mutableStateOf<String?>(null)
    var focusSettings by mutableStateOf(false)
    var settingsCategory by mutableStateOf(SettingsCategory.GENERAL)

    private val liveScrollStates = mutableMapOf<String,LazyListState>()
    private val videoScrollStates = linkedMapOf<VideoBrowseKey,LazyGridState>()
    fun liveScroll(groupId: String) = liveScrollStates.getOrPut(groupId) { LazyListState() }
    fun videoScroll(key: VideoBrowseKey): LazyGridState {
        val scroll = videoScrollStates.remove(key) ?: LazyGridState()
        videoScrollStates[key] = scroll
        while (videoScrollStates.size > 24) videoScrollStates.remove(videoScrollStates.keys.first())
        return scroll
    }
    private val playingContexts = linkedMapOf<String,VideoBrowseKey>()

    fun rememberVideoPlayback(selection: VideoPlaybackSelection,context: VideoBrowseKey) {
        playingContexts.remove(selection.details.work.id)
        playingContexts[selection.details.work.id] = context
        while (playingContexts.size > 16) playingContexts.remove(playingContexts.keys.first())
    }

    fun revealLive(catalog: LiveCatalog,channel: LiveChannel) {
        val previous = groupIds[LIVE_AGGREGATE_ID]
        val groupId = previous?.takeIf { it == ALL_GROUP || catalog.groups.any { group ->
            group.id == it && group.channels.any { item -> item.id == channel.id }
        } } ?: catalog.groups.firstOrNull { group -> group.channels.any { it.id == channel.id } }?.id ?: ALL_GROUP
        section = Section.LIVE; focusSettings = false; videoSearch = false
        groupIds[LIVE_AGGREGATE_ID] = groupId
        channelIds["$LIVE_AGGREGATE_ID/$groupId"] = channel.id
        liveScheduleId = null
        enterColumn(2)
    }

    fun revealVideo(selection: VideoPlaybackSelection): VideoBrowseKey {
        val context = playingContexts[selection.details.work.id] ?: VideoBrowseKey()
        section = Section.VIDEOS; focusSettings = false; videoSearch = false; liveScheduleId = null
        videoNavigation = context.categoryId ?: "latest"
        videoWorkId = selection.details.work.id
        enterColumn(2)
        return context
    }

    fun enterColumn(column: Int) {
        this.column = column
        focusRequest++
    }
}

@Composable
fun ContentDrawer(
    catalog: LiveCatalog,
    playingChannel: LiveChannel?,
    state: ContentDrawerState,
    onPlay: (LiveChannel) -> Unit,
    onClose: () -> Unit,
    settingsContent: @Composable (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
    providerCount: Int = 0,
    apps: me.lsong.mytv.data.apps.InstalledAppsSnapshot = me.lsong.mytv.data.apps.InstalledAppsSnapshot(),
    onRefreshApps: () -> Unit = {},
    videoLibrary: VideoLibraryViewModel? = null,
    onPlayVideo: (VideoPlaybackSelection) -> Unit = {},
    playingVideo: VideoPlaybackSelection? = null,
    nowPlayingTitle: String? = playingChannel?.title,
    epg: EpgList = EpgList(),
    liveOptions: LiveOptions = LiveOptions(),
    nowMs: Long = System.currentTimeMillis(),
) {
    val vodState by (videoLibrary?.state ?: remember { kotlinx.coroutines.flow.MutableStateFlow(VideoCatalogState()) }).collectAsState()
    val vodCategories by (videoLibrary?.categories ?: remember { kotlinx.coroutines.flow.MutableStateFlow(VideoCategoriesState()) }).collectAsState()
    val vodSources by (videoLibrary?.configured ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList()) }).collectAsState()
    LaunchedEffect(vodCategories, vodState.categoryId) {
        if (!vodCategories.loading && state.videoNavigation.startsWith("category:") &&
            vodCategories.groups.none { it.id == state.videoNavigation }) {
            state.videoNavigation = "latest"
            if (state.section == Section.VIDEOS && state.column == 1) state.enterColumn(1)
        }
    }
    BackHandler(onBack = onClose)
    val settingsFocusRequester = remember { FocusRequester() }
    val settingsContentFocus = remember { FocusRequester() }
    LaunchedEffect(state.column, state.focusRequest, state.focusSettings, state.settingsCategory) {
        withFrameNanos { }
        if (state.column == 0 && state.focusSettings) settingsFocusRequester.requestFocus()
        if (state.column == 2 && state.section == Section.SETTINGS) settingsContentFocus.requestFocus()
    }
    val playlist = catalog
    val groups = remember(playlist) {
        listOf(ChannelGroup(ALL_GROUP, "全部频道", playlist.channels)) + playlist.groups
    }
    val group = groups.firstOrNull { it.id == state.groupIds[LIVE_AGGREGATE_ID] } ?: groups.firstOrNull()
    val channels = group?.channels.orEmpty()
    val channelContext = "${LIVE_AGGREGATE_ID}/${group?.id}"
    val selectedChannelId = state.channelIds[channelContext]
        ?: channels.firstOrNull { it.id == playingChannel?.id }?.id
        ?: channels.firstOrNull()?.id

    // EPG is contextual to the focused channel. Opening it never moves channel focus.
    LaunchedEffect(state.section,state.column,selectedChannelId,epg,nowMs) {
        if (state.section == Section.LIVE && state.column == 2) {
            state.liveScheduleId = channels.firstOrNull { it.id == selectedChannelId }
                ?.takeIf { epg.getEpgChannel(it)?.programmes?.any { programme -> programme.endAt > nowMs } == true }?.id
        } else if (state.section != Section.LIVE || state.column < 2) state.liveScheduleId = null
    }

    Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.24f))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val maximumDrawerWidth = minOf(maxWidth * .94f,900.dp)
            val liveWidth = minOf(640.dp,(maximumDrawerWidth - 236.dp).coerceAtLeast(360.dp))
            val liveContentWidth = liveWidth - 80.dp
            val applicationListWidth = liveContentWidth * (160f/508f) + 64.dp
            val drawerWidth = when {
                state.section == Section.APPS && state.applicationDetailsId == null -> 64.dp + liveContentWidth * (132f/508f) + applicationListWidth
                state.section == Section.APPS -> 800.dp
                state.section == Section.LIVE && state.liveScheduleId == null -> liveWidth
                else -> 900.dp
            }
            Column(
                Modifier
                    .width(minOf(maxWidth * 0.94f, drawerWidth))
                    .fillMaxHeight()
                    .background(DrawerBackground)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .onPreviewKeyEvent {
                        if (it.key == Key.Menu) {
                            if (it.type == KeyEventType.KeyUp) onClose()
                            true
                        } else false
                    },
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ohmytv_mark), null, tint = Color.Unspecified, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(AppConstants.APP_NAME, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(12.dp))
                    Text(state.section.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    nowPlayingTitle?.takeIf { state.section != Section.APPS }?.let { title ->
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        Text(title, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
                        Spacer(Modifier.width(16.dp))
                    }
                    Text(when (state.section) { Section.APPS -> "OK 打开 · → 详情"; Section.SETTINGS -> "播放源与偏好"; Section.VIDEOS -> "${vodState.works.size} 部影片"; else -> "${playlist.channels.size} 个频道" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column((if (state.section == Section.LIVE || state.section == Section.APPS) Modifier.width(liveContentWidth * (132f/508f)) else Modifier.weight(132f)).fillMaxHeight().testTag("drawer-section-column")) {
                        ColumnHeading("内容", "3")
                        BrowseList(
                            entries = listOf(
                                BrowseEntry.Navigation(Section.LIVE, providerCount, playingChannel != null),
                                BrowseEntry.Navigation(Section.VIDEOS, vodSources.count { it.isEnabled }, playingVideo != null),
                                BrowseEntry.Category(BrowseCategory(Section.APPS.key, Section.APPS.title)),
                            ),
                            selectedId = state.section.key,
                            contextKey = "providers",
                            active = state.column == 0 && !state.focusSettings,
                            focusRequest = state.focusRequest,
                            onFocused = {
                                state.focusSettings = false
                                state.section = Section.entries.first { section -> section.key == it }
                            },
                            onSelected = {
                                state.focusSettings = false
                                state.section = Section.entries.first { section -> section.key == it }
                                state.enterColumn(1)
                            },
                            onLeft = {},
                            onRight = { state.enterColumn(1) },
                            modifier = Modifier.weight(1f),
                            emptyMessage = "还没有播放源",
                            downFocusRequester = settingsFocusRequester,
                        )
                        Spacer(Modifier.height(12.dp))
                        ListItem(
                            selected = false,
                            onClick = { state.section = Section.SETTINGS; state.focusSettings = true; state.enterColumn(1) },
                            headlineContent = { Text("设置") },
                            leadingContent = { Icon(Icons.Default.Settings, null, Modifier.size(20.dp)) },
                            scale = ListItemDefaults.scale(focusedScale = 1f),
                            modifier = Modifier.fillMaxWidth().testTag("drawer-settings").focusRequester(settingsFocusRequester)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        state.section = Section.SETTINGS
                                        state.focusSettings = true
                                    }
                                }
                                .onPreviewKeyEvent {
                                    if (it.key == Key.DirectionUp) {
                                        if (it.type == KeyEventType.KeyDown) {
                                            state.section = Section.APPS
                                            state.focusSettings = false
                                            state.enterColumn(0)
                                        }
                                        true
                                    } else it.key == Key.DirectionDown
                                }
                                .columnKeys(onLeft = {}, onRight = { state.section = Section.SETTINGS; state.enterColumn(1) }),
                        )
                    }
                    if (state.section == Section.APPS) {
                        me.lsong.mytv.ui.apps.InstalledAppsPanel(
                            snapshot = apps, onRefresh = onRefreshApps,
                            selectedId = state.applicationId, active = state.column == 1, focusRequest = state.focusRequest,
                            onFocused = { state.applicationId = it }, onLeft = { state.enterColumn(0) },
                            detailsId = state.applicationDetailsId, detailsActive = state.column == 2,
                            onDetails = { state.applicationDetailsId = it; state.enterColumn(2) },
                            onCloseDetails = { state.applicationDetailsId = null; state.enterColumn(1) },
                            listWidth = applicationListWidth,
                            modifier = Modifier.weight(1f),
                        )
                    } else if (state.section == Section.SETTINGS) {
                        Column(Modifier.weight(160f).fillMaxHeight()) {
                            ColumnHeading("设置分类", "${SettingsCategory.entries.size}")
                            BrowseList(
                                entries = SettingsCategory.entries.map { BrowseEntry.Category(BrowseCategory(it.name, it.title)) },
                                selectedId = state.settingsCategory.name,
                                contextKey = "settings-categories",
                                active = state.column == 1,
                                focusRequest = state.focusRequest,
                                onFocused = { state.settingsCategory = SettingsCategory.valueOf(it) },
                                onSelected = { state.settingsCategory = SettingsCategory.valueOf(it); state.enterColumn(2) },
                                onLeft = { state.focusSettings = true; state.enterColumn(0) },
                                onRight = { state.enterColumn(2) },
                                emptyMessage = "",
                            )
                        }
                        Box(
                            Modifier.weight(476f).fillMaxHeight().containFocus().focusRequester(settingsContentFocus)
                                .onKeyEvent {
                                    if (it.key == Key.DirectionLeft) {
                                        if (it.type == KeyEventType.KeyDown) state.enterColumn(1)
                                        true
                                    } else false
                                },
                        ) {
                            key(state.settingsCategory) { settingsContent(state.settingsCategory) }
                        }
                    } else if (state.section == Section.VIDEOS) {
                        Column(Modifier.weight(160f).fillMaxHeight()) {
                            ColumnHeading("影视分类", if (vodCategories.loading) "加载中" else "${vodCategories.groups.size}")
                            val categories = listOf(BrowseEntry.Category(BrowseCategory("search", "搜索全部源")), BrowseEntry.Category(BrowseCategory("latest", if (vodState.query.isBlank()) "最近更新" else "搜索结果"))) +
                                vodCategories.groups.map { category -> BrowseEntry.Category(BrowseCategory(category.id, category.title, "${category.records.map { it.ref.providerId }.distinct().size} 个源")) } +
                                listOf(BrowseEntry.Category(BrowseCategory("more", "加载更多")), BrowseEntry.Category(BrowseCategory("retry", "重试失败源")), BrowseEntry.Category(BrowseCategory("refresh", "刷新影片")))
                            BrowseList(
                                entries = categories, selectedId = state.videoNavigation, contextKey = "vod-categories", active = state.column == 1, focusRequest = state.focusRequest,
                                onFocused = categoryFocused@ { id ->
                                    // Removing a focused category can briefly focus a neighbour.
                                    // Let the recovery effect restore Latest before accepting it.
                                    if (state.videoNavigation.startsWith("category:") &&
                                        vodCategories.groups.none { it.id == state.videoNavigation }) return@categoryFocused
                                    if (vodState.details != null) videoLibrary?.closeDetails()
                                    state.videoNavigation = id
                                    state.videoSearch = id == "search"
                                    if (id == "latest") videoLibrary?.selectCategory(null)
                                    if (id.startsWith("category:")) {
                                        if (vodState.categoryId != id) state.videoWorkId = null
                                        videoLibrary?.selectCategory(id)
                                    }
                                },
                                onSelected = { id ->
                                    if (vodState.details != null) videoLibrary?.closeDetails()
                                    state.videoNavigation = id
                                    state.videoSearch = id == "search"
                                    if (id == "latest") videoLibrary?.selectCategory(null)
                                    if (id.startsWith("category:")) {
                                        if (vodState.categoryId != id) state.videoWorkId = null
                                        videoLibrary?.selectCategory(id)
                                    }
                                    when (id) {
                                        "more" -> videoLibrary?.loadMore()
                                        "retry" -> videoLibrary?.retryFailed()
                                        "refresh" -> videoLibrary?.refresh()
                                        else -> state.enterColumn(2)
                                    }
                                }, onLeft = { state.enterColumn(0) }, onRight = { state.enterColumn(2) }, emptyMessage = "",
                            )
                        }
                        Box(Modifier.weight(476f).fillMaxHeight().containFocus()) {
                            when {
                                vodState.details != null && videoLibrary != null -> VodDetailsPanel(vodState, state.column == 2, state.focusRequest, playingVideo, onPlayVideo, videoLibrary, { state.enterColumn(1) })
                                state.videoSearch -> VodSearchPanel(vodState.query, state.column == 2, state.focusRequest) { query ->
                                    state.videoSearch = false; state.videoNavigation = "latest"; state.videoWorkId = null
                                    videoLibrary?.search(query); state.enterColumn(2)
                                }
                                else -> VodPosterGrid(vodState, state.videoWorkId, state.column == 2, state.focusRequest,
                                    gridState = state.videoScroll(VideoBrowseKey(vodState.categoryId,vodState.filterId,vodState.query)),
                                    filters = vodCategories.groups.firstOrNull { it.id == vodState.categoryId }?.filters.orEmpty(),
                                    onFilter = { videoLibrary?.selectFilter(it) }, onLoadMore = { videoLibrary?.loadMore() },
                                    onFocused = { state.videoWorkId = it }, onOpen = { videoLibrary?.open(it); state.enterColumn(2) }, onLeft = { state.enterColumn(1) })
                            }
                        }
                    } else {
                    Column(Modifier.width(liveContentWidth * (160f/508f)).fillMaxHeight().testTag("drawer-live-group-column")) {
                        ColumnHeading("分组", "${groups.size}")
                        BrowseList(
                            entries = groups.map { BrowseEntry.Category(BrowseCategory(it.id, it.title, "${it.channels.size} 个频道")) },
                            selectedId = group?.id,
                            contextKey = LIVE_AGGREGATE_ID,
                            active = state.column == 1,
                            focusRequest = state.focusRequest,
                            onFocused = { id -> state.groupIds[LIVE_AGGREGATE_ID] = id; state.liveScheduleId = null },
                            onSelected = { id -> state.groupIds[LIVE_AGGREGATE_ID] = id; state.enterColumn(2) },
                            onLeft = { state.enterColumn(0) },
                            onRight = { state.enterColumn(2) },
                            emptyMessage = "暂无分组",
                        )
                    }
                    Column(Modifier.width(liveContentWidth * (216f/508f)).fillMaxHeight().containFocus().testTag("drawer-live-channel-column")) {
                        ColumnHeading(group?.title ?: "频道", "${channels.size}")
                        BrowseList(
                            entries = channels.map {
                                val schedule = epg.getEpgChannel(it)
                                BrowseEntry.Channel(it, it.id == playingChannel?.id,
                                    subtitle = if (liveOptions.showProgrammes) schedule?.programmes?.firstOrNull { nowMs >= it.startAt && nowMs < it.endAt }?.title else null,
                                    logo = it.logo, showLogo = liveOptions.showLogos, fallbackLogo = schedule?.icon)
                            },
                            selectedId = selectedChannelId,
                            contextKey = channelContext,
                            scrollState = state.liveScroll(group?.id ?: ALL_GROUP),
                            active = state.column == 2,
                            focusRequest = state.focusRequest,
                            onFocused = { state.channelIds[channelContext] = it },
                            onSelected = { id -> channels.firstOrNull { it.id == id }?.let(onPlay) },
                            onLeft = { state.enterColumn(1) },
                            onRight = { if (state.liveScheduleId != null) state.enterColumn(3) },
                            emptyMessage = if (playlist.isLoading) "正在加载频道…" else playlist.error ?: if (providerCount == 0) "在设置中添加直播源" else "这个分组还没有频道",
                        )
                    }
                    if (state.liveScheduleId != null) {
                        val scheduleChannel = channels.firstOrNull { it.id == state.liveScheduleId }
                        if (scheduleChannel != null) Column(Modifier.weight(1f).fillMaxHeight().containFocus().testTag("drawer-live-epg-column")) {
                            ColumnHeading("节目单", scheduleChannel.title)
                            me.lsong.mytv.ui.live.LiveSchedulePanel(scheduleChannel, epg.getEpgChannel(scheduleChannel),
                                state.column == 3, state.focusRequest, { onPlay(scheduleChannel) },
                                { state.enterColumn(2) })
                        }
                    }
                    }
                }

            }
        }
    }
}

@Composable
internal fun ColumnHeading(title: String, count: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(count, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
