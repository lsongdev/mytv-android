package me.lsong.mytv.ui.video

import me.lsong.mytv.domain.playback.ResourceProbe
import java.util.Locale
import kotlinx.coroutines.delay

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.ui.platform.LocalDensity
import me.lsong.mytv.ui.focus.PaddedEdgeBringIntoViewSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.lsong.mytv.domain.video.*
import me.lsong.mytv.ui.focus.placedFocusTarget
import me.lsong.mytv.ui.focus.PlacedFocusTargets
import me.lsong.mytv.ui.focus.prepareFocusAt

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun VodPosterGrid(
    state: VideoCatalogState, selectedId: String?, active: Boolean, request: Int,
    filters: List<VideoCategory>, onFilter: (String?) -> Unit, onLoadMore: () -> Unit,
    onFocused: (String) -> Unit, onOpen: (VideoWork) -> Unit, onLeft: () -> Unit,
) {
    val works = state.works
    val selectedIndex = works.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val target = remember { FocusRequester() }
    val grid = key(state.categoryId,state.filterId,state.query) { rememberLazyGridState() }
    val footerTarget = remember { FocusRequester() }
    val placedCards = remember { PlacedFocusTargets() }
    var filterRequest by remember(state.categoryId) { mutableIntStateOf(0) }
    var gridRequest by remember { mutableIntStateOf(0) }
    var gridDestination by remember(state.categoryId) { mutableStateOf(false) }
    fun focusGrid() { gridDestination = true; gridRequest++ }
    fun focusFilters() { gridDestination = false; filterRequest++ }
    val focusPadding = with(LocalDensity.current) { 12.dp.toPx() }
    val scrollPolicy = remember(focusPadding) { PaddedEdgeBringIntoViewSpec(focusPadding) }
    LaunchedEffect(active, request, state.categoryId) {
        if (active) {
            if (filters.isNotEmpty() && works.none { it.id == selectedId }) focusFilters() else focusGrid()
        }
    }
    // Cancel obsolete scroll/frame work when leaving the column or changing
    // category/filter. The requester belongs to the persistent content group,
    // never to a card that can disappear between these suspension points.
    LaunchedEffect(active,state.categoryId,state.filterId,works.isEmpty(),gridDestination,gridRequest) {
        if (active && gridDestination) {
            if (works.isNotEmpty()) grid.prepareFocusAt(selectedIndex)
            withFrameNanos { }
            val id = selectedId?.takeIf { selected -> works.any { it.id == selected } } ?: works.firstOrNull()?.id
            (placedCards.requester(id) ?: target).requestFocus()
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().testTag("vod-grid-header"),verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (state.query.isBlank()) "${state.categoryTitle ?: "最近更新"} · ${works.size} 部" else "“${state.query}” · ${works.size} 部",
                modifier = (if (filters.isEmpty()) Modifier.weight(1f) else Modifier.widthIn(max = 160.dp)).testTag("vod-grid-heading"),
                style = MaterialTheme.typography.titleMedium,maxLines = 1,overflow = TextOverflow.Ellipsis)
            if (filters.isNotEmpty()) key(state.categoryId) {
                VideoCategoryFilters(filters,state.filterId,filterRequest,modifier = Modifier.weight(1f),
                    onSelect = { gridDestination = false; onFilter(it) },onDown = ::focusGrid,onLeft = onLeft)
            }
        }
        val sources = state.providers.groupBy { it.source.id }
        val finished = sources.count { (_, cursors) -> cursors.any { it.page > 0 || it.error != null } }
        val failed = sources.count { (_, cursors) -> cursors.any { it.error != null } }
        Text(
            when {
                state.preparing && state.providers.isEmpty() -> "正在准备影片目录…"
                state.providers.isEmpty() -> "在设置中添加并启用影视源"
                state.loading -> "正在汇集影片 · $finished / ${sources.size} 个源已响应"
                failed > 0 -> "${sources.size - failed} 个源可用 · $failed 个源暂不可用"
                finished < sources.size -> "已浏览 $finished / ${sources.size} 个源 · 可继续加载"
                else -> "来自 ${sources.size} 个源 · 同片资源已合并"
            }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f).fillMaxWidth().testTag("vod-grid-content")
            .focusRequester(target).focusProperties {
                enter = { placedCards.requester(selectedId ?: works.firstOrNull()?.id) ?: FocusRequester.Default }
            }.focusGroup()) {
        if (works.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f)
                .onPreviewKeyEvent {
                    val action = when {
                        it.key == Key.DirectionLeft -> onLeft
                        it.key == Key.DirectionUp && filters.isNotEmpty() -> ::focusFilters
                        it.key == Key.DirectionDown && (state.hasMore || state.loading) -> ({ footerTarget.requestFocus() })
                        else -> null
                    }
                    if (action != null) { if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 0) action(); true } else false
                }.focusable(), contentAlignment = Alignment.Center) {
                Text(if (state.loading) "正在加载影片…" else if (state.hasMore) "暂未找到影片，试试加载更多" else "暂无影片，试试搜索或刷新", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.hasMore || state.loading) Button(onClick = onLoadMore,
                modifier = Modifier.testTag("vod-load-more").focusRequester(footerTarget).vodLeft(onLeft)) {
                Text(if (state.loading) "加载中…" else "加载更多")
            }
        } else {
            CompositionLocalProvider(LocalBringIntoViewSpec provides scrollPolicy) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4), state = grid, contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f).focusGroup(),
                ) {
                    itemsIndexed(works, key = { _, work -> work.id }) { index, work ->
                        val cardTarget = remember(work.id) { FocusRequester() }
                        Card(
                            onClick = { onOpen(work) },
                            scale = CardDefaults.scale(focusedScale = 1.03f),
                            modifier = Modifier.fillMaxWidth().testTag("vod-work:${work.id}")
                                .focusRequester(cardTarget).placedFocusTarget(work.id,cardTarget,placedCards)
                                .onFocusChanged { if (it.isFocused) onFocused(work.id) }
                                .onPreviewKeyEvent {
                                    if (it.key == Key.DirectionLeft && index % 4 == 0) {
                                        if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 0) onLeft()
                                        true
                                    } else if (it.key == Key.DirectionUp && index < 4 && filters.isNotEmpty()) {
                                        if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 0) focusFilters()
                                        true
                                    } else false
                                },
                        ) {
                            Column {
                                VodArtwork(work, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                                Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(work.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${work.item.year.ifBlank { work.item.kind.title }} · ${work.providerCount} 源", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                            }
                        }
                    }
                    if (state.hasMore || state.loading) item(span = { GridItemSpan(maxLineSpan) }) {
                        Button(onClick = onLoadMore,modifier = Modifier.testTag("vod-load-more").focusRequester(footerTarget).vodLeft(onLeft)) {
                            Text(if (state.loading) "加载中…" else "加载更多")
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
fun VodSearchPanel(query: String, active: Boolean, request: Int, onSearch: (String) -> Unit) {
    var text by remember(query) { mutableStateOf(query) }
    val target = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("搜索全部影视源", style = MaterialTheme.typography.titleLarge)
        Text("同名影片会结合年份和类型合并，保留各源的播放资源", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BasicTextField(
            text, { text = it }, singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); onSearch(text) }),
            modifier = Modifier.fillMaxWidth().focusRequester(target).testTag("vod-search-input")
                .background(Color(0xFF263447), RoundedCornerShape(10.dp)).padding(16.dp),
            decorationBox = { field -> Box { if (text.isEmpty()) Text("输入片名", color = MaterialTheme.colorScheme.onSurfaceVariant); field() } },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { keyboard?.hide(); onSearch(text) }, modifier = Modifier.testTag("vod-search-submit")) { Text("搜索") }
            OutlinedButton(onClick = { keyboard?.hide(); onSearch("") }) { Text("清空搜索") }
        }
    }
    LaunchedEffect(active, request) { if (active) { withFrameNanos { }; target.requestFocus() } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VodDetailsPanel(
    state: VideoCatalogState, active: Boolean, request: Int,
    playing: VideoPlaybackSelection?,
    onPlay: (VideoPlaybackSelection) -> Unit, model: VideoLibraryViewModel, onLeft: () -> Unit,
) {
    val details = state.details ?: return
    val item = details.work.item
    val episode = details.parts.firstOrNull { it.id == state.selectedPartId }
    val resources = episode?.resources.orEmpty()
    LaunchedEffect(resources) { model.cancelProbes(); delay(350); model.probeResources(resources) }
    DisposableEffect(model) { onDispose { model.cancelProbes() } }
    val playTarget = remember { FocusRequester() }
    val episodeTarget = remember { FocusRequester() }
    val resourceTarget = remember { FocusRequester() }
    val vertical = rememberLazyListState()
    val focusInset = with(LocalDensity.current) { 12.dp.toPx() }
    val scrollPolicy = remember(focusInset) { PaddedEdgeBringIntoViewSpec(focusInset) }
    LaunchedEffect(active, request, details.work.id, details.parts.isEmpty()) {
        if (active && details.parts.isNotEmpty()) { withFrameNanos { }; playTarget.requestFocus() }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides scrollPolicy) {
    LazyColumn(
        Modifier.fillMaxSize(), state = vertical,
        contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                VodArtwork(details.work, Modifier.width(68.dp).height(102.dp).clip(RoundedCornerShape(8.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(details.work.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${item.year} · ${item.kind.title} · ${item.area}", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    Text(details.work.variants.firstOrNull { it.summary.isNotBlank() }?.summary.orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (episode != null) item {
            Button(onClick = { model.selection()?.let(onPlay) }, modifier = Modifier.testTag("vod-play").focusRequester(playTarget).focusProperties { down = episodeTarget }.vodLeft(onLeft)) { Text("播放 ${episode.title}") }
        }
        item {
            Text("选集 · ${details.parts.size} 集", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            if (details.parts.isNotEmpty()) {
                VideoChoicesRow(
                    choices = details.parts.map { VideoChoice(it.id, it.title, "${it.resources.size} 条线路", it.id == episode?.id) },
                    tag = "vod-episode", width = 92.dp,
                    focusTarget = episodeTarget,
                    onUp = { playTarget.requestFocus() }, onDown = { resourceTarget.requestFocus() },
                    onFocused = model::selectPart,
                    onClick = { id -> model.selection(id)?.let(onPlay) }, onLeft = onLeft,
                )
            } else {
                val target = remember { FocusRequester() }
                Box(Modifier.fillMaxWidth().height(56.dp).focusRequester(target).vodLeft(onLeft).focusable(), contentAlignment = Alignment.CenterStart) {
                    Text(if (state.detailsLoading) "正在加载剧集…" else "暂无可直接播放的剧集", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LaunchedEffect(active, request) { if (active) { withFrameNanos { }; target.requestFocus() } }
            }
        }
        item {
            Text("${episode?.title ?: "播放资源"} · ${episode?.resources?.size ?: 0} 条可用线路", style = MaterialTheme.typography.titleSmall)
            Text("响应延迟 / 样本速度 · 仅供参考", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            key(episode?.id) {
                VideoChoicesRow(
                    choices = episode?.resources.orEmpty().map { candidate ->
                        val isPlaying = playing?.resourceId == candidate.id
                        VideoChoice(candidate.id, candidate.providerName, if (isPlaying) "播放中 · ${candidate.line?.title.orEmpty()}" else candidate.line?.title.orEmpty(), isPlaying)
                    },
                    tag = "vod-candidate", width = 208.dp, focusTarget = resourceTarget, probes = model.resourceProbes,
                    onUp = { episodeTarget.requestFocus() },
                    onClick = { id -> model.selection(resourceId = id)?.let(onPlay) }, onLeft = onLeft,
                )
            }
        }
        if (state.detailsLoading) item { Text("正在汇集各源的剧集和线路…", style = MaterialTheme.typography.labelMedium) }
        if (state.detailsError != null) item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(state.detailsError, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { model.open(details.work) }, modifier = Modifier.vodLeft(onLeft)) { Text("重试详情") }
            }
        }
        if (details.errors.isNotEmpty()) item { Text("${details.errors.keys.joinToString("、")} 暂不可用", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
}

private fun Modifier.vodLeft(onLeft: () -> Unit): Modifier = onPreviewKeyEvent {
    if (it.key == Key.DirectionLeft) {
        if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 0) onLeft()
        true
    } else false
}

/** Try the next provider's artwork when its preferred CDN is unavailable. */
@Composable
fun VodArtwork(work: VideoWork, modifier: Modifier = Modifier) {
    val urls = work.variants.map { it.poster }.filter { it.isNotBlank() }.distinct()
    var failed by remember(work.id) { mutableStateOf(emptySet<String>()) }
    var loaded by remember(work.id) { mutableStateOf(false) }
    val url = urls.firstOrNull { it !in failed }
    val context = LocalContext.current
    Box(modifier.background(Color(0xFF263447)), contentAlignment = Alignment.Center) {
        if (!loaded) Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(work.title.take(12), style = MaterialTheme.typography.labelMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(if (url == null) "暂无封面" else "加载封面…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (url != null) AsyncImage(
            model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
            contentDescription = work.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            onSuccess = { loaded = true },
            onError = { error ->
                android.util.Log.w("VodArtwork", "Cover failed: ${android.net.Uri.parse(url).host}: ${error.result.throwable}")
                loaded = false
                failed = failed + url
            },
        )
    }
}


@Composable
internal fun ResourceProbeBadge(probe: ResourceProbe, modifier: Modifier = Modifier) {
    val muted = Color(0xFF9AA6B6)
    val green = Color(0xFF63D98B)
    val orange = Color(0xFFFFC060)
    val red = Color(0xFFFF7F7F)
    val measured = probe as? ResourceProbe.Measured
    val first = when (probe) {
        ResourceProbe.Pending -> "待测"
        ResourceProbe.Testing -> "探测中"
        is ResourceProbe.Failed -> if (probe.timedOut) "超时" else "失败"
        is ResourceProbe.Measured -> "${probe.latencyMs} ms"
    }
    val second = measured?.let { if (it.bytesPerSecond >= 1024 * 1024) String.format(Locale.ROOT, "%.1f MB/s", it.bytesPerSecond / 1048576.0) else "${it.bytesPerSecond / 1024} KB/s" } ?: "—"
    Column(modifier.clip(RoundedCornerShape(5.dp)).background(Color(0xDD101820)).padding(horizontal = 6.dp, vertical = 4.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(first, color = when {
            probe is ResourceProbe.Failed -> red
            measured == null -> muted
            measured.latencyMs < 800 -> green
            measured.latencyMs < 2000 -> orange
            else -> red
        }, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(second, color = when {
            measured == null -> muted
            measured.bytesPerSecond >= 1024 * 1024 -> green
            measured.bytesPerSecond >= 256 * 1024 -> orange
            else -> red
        }, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
