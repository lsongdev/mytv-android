package me.lsong.mytv.ui.danmaku

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.lsong.media.danmaku.*
import me.lsong.mytv.data.danmaku.*
import me.lsong.mytv.data.preferences.*
import me.lsong.mytv.domain.danmaku.*
import me.lsong.mytv.domain.video.VideoPlaybackSelection

data class DanmakuMatchOption(val candidate: DanmakuCandidate, val episode: DanmakuEpisode, val suggested: Boolean = false) {
    val id get() = "${candidate.id}:${episode.episodeId}"
}
data class DanmakuSourceState(
    val providerId: String, val name: String, val offsetMs: Long = 0,
    val options: List<DanmakuMatchOption> = emptyList(), val selected: DanmakuCandidate? = null,
    val episode: DanmakuEpisode? = null,
    val matching: Boolean = false, val fetching: Boolean = false,
    val message: String = "等待匹配", val commentCount: Int = 0,
)
data class DanmakuState(
    val preferences: DanmakuPreferences, val sources: List<DanmakuSourceState> = emptyList(),
    val comments: List<DanmakuComment> = emptyList(), val renderRevision: Int = 0,
) {
    val loading get() = sources.any { it.matching || it.fetching }
    val activeSources get() = sources.filter { preferences.enabled && it.providerId in preferences.providers && it.episode != null }
    val message get() = when {
        !preferences.enabled -> "开启后匹配当前影片的弹幕"
        loading -> "${activeSources.size} 个源已匹配 · 正在加载…"
        else -> "${activeSources.size} 个源已匹配 · 已合并 ${comments.size} 条弹幕"
    }
}
val danmakuProviderNames = linkedMapOf("bilibili" to "哔哩哔哩", "tencent" to "腾讯视频", "iqiyi" to "爱奇艺", "youku" to "优酷", "mgtv" to "芒果 TV", "letv" to "乐视视频")

class DanmakuViewModel(private val repository: DanmakuRepository, private val store: DanmakuPreferenceStore) : ViewModel() {
    private val mutable = MutableStateFlow(DanmakuState(store.read()))
    val state = mutable.asStateFlow()
    private var selection: VideoPlaybackSelection? = null
    private var position = 0L
    private var lastMergePosition = 0L
    private var renderRevision = 0
    private class SourceRuntime {
        var matchJob: Job? = null
        var fetchJob: Job? = null
        var fetchingWindow: Int? = null
        val windows = linkedMapOf<Int,List<DanmakuComment>>()
        val retryAt = mutableMapOf<Int,Long>()
    }
    private val runtimes = mutableMapOf<String,SourceRuntime>()
    private var mergeJob: Job? = null
    val datasetKey get() = "${selection?.details?.work?.id}|${selection?.partId}"
    private fun source(id: String) = state.value.sources.firstOrNull { it.providerId == id }
    private fun change(id: String, update: (DanmakuSourceState) -> DanmakuSourceState) {
        mutable.update { state -> state.copy(sources = state.sources.map { if (it.providerId == id) update(it) else it }) }
    }
    fun setVideo(value: VideoPlaybackSelection?) {
        if (selection?.details?.work?.id == value?.details?.work?.id && selection?.partId == value?.partId) { selection = value; return }
        selection = value; position = 0; restart()
    }
    fun update(value: DanmakuPreferences) {
        val previous = state.value.preferences
        store.save(value); mutable.update { it.copy(preferences = value) }
        if (previous.enabled != value.enabled || previous.providers != value.providers) {
            renderRevision++
            state.value.sources.forEach { source ->
                if (!value.enabled || source.providerId !in value.providers) {
                    runtimes[source.providerId]?.let { it.matchJob?.cancel(); it.fetchJob?.cancel() }
                    change(source.providerId) { it.copy(matching = false,fetching = false) }
                } else if (!previous.enabled || source.providerId !in previous.providers) {
                    if (source.episode == null) match(source.providerId)
                }
            }
            rebuild(); advance(position)
        }
    }
    fun toggle() = update(state.value.preferences.copy(enabled = !state.value.preferences.enabled))
    fun toggleSource(id: String) {
        val prefs = state.value.preferences
        update(prefs.copy(providers = if (id in prefs.providers) prefs.providers - id else prefs.providers + id))
    }
    fun adjustOffset(id: String, deltaMs: Long) {
        val video = selection ?: return
        val current = source(id) ?: return
        val offset = (current.offsetMs + deltaMs).coerceIn(-600_000,600_000)
        renderRevision++
        store.setOffset(video.details.work.id,video.partId,id,offset)
        change(id) { it.copy(offsetMs = offset) }
        // Remap cached comments immediately; window selection uses this source's clock.
        rebuild(); advance(position)
    }
    fun restart() {
        runtimes.values.forEach { it.matchJob?.cancel(); it.fetchJob?.cancel() }; runtimes.clear(); mergeJob?.cancel()
        renderRevision++
        val video = selection
        val sources = repository.providerNames.map { (id,name) ->
            runtimes[id] = SourceRuntime()
            DanmakuSourceState(id,name,video?.let { store.offset(it.details.work.id,it.partId,id) } ?: 0)
        }
        mutable.value = DanmakuState(state.value.preferences,sources,renderRevision = renderRevision)
        if (video != null && state.value.preferences.enabled) sources.filter { it.providerId in state.value.preferences.providers }.forEach { match(it.providerId) }
    }
    private suspend fun optionsFor(video: VideoPlaybackSelection, candidates: List<DanmakuCandidate>): List<DanmakuMatchOption> = supervisorScope {
        candidates.take(3).map { candidate -> async {
            try {
                val episodes = repository.episodes(candidate)
                val season = Regex("^s([0-9]+)e[0-9]+$").matchEntire(video.partId)?.groupValues?.get(1)?.toIntOrNull()
                val suggested = if (candidate.media.season != null && season != null && candidate.media.season != season) null
                    else matchDanmakuEpisode(video.details.work,video.part,episodes)
                episodes.distinctBy { it.episodeId }.map { DanmakuMatchOption(candidate,it,it == suggested) }
            } catch (error: TimeoutCancellationException) { emptyList() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { emptyList() }
        } }.awaitAll().flatten().sortedWith(compareByDescending<DanmakuMatchOption> { it.suggested }.thenByDescending { it.candidate.score }.thenBy { it.episode.episodeIndex })
    }
    private fun match(id: String, refresh: Boolean = false) {
        val video = selection ?: return
        val runtime = runtimes[id] ?: return
        runtime.matchJob?.cancel()
        runtime.matchJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            change(id) { it.copy(matching = true,message = "正在查找分集候选…") }
            try {
                val saved = store.binding(video.details.work.id,video.partId,id) ?: store.binding(video.details.work.id,null,id)
                val candidates = if (saved != null && !refresh) listOf(DanmakuCandidate(id,source(id)!!.name,
                    DanmakuMedia(id,saved.mediaId,video.details.work.title),100)) else repository.search(video.details.work,id)
                val options = optionsFor(video,candidates); ensureActive()
                val previous = source(id)!!
                val chosen = previous.options.firstOrNull { it.candidate.id == previous.selected?.id && it.episode.episodeId == previous.episode?.episodeId }
                change(id) { it.copy(options = (options + listOfNotNull(chosen)).distinctBy { it.id }) }
                if (previous.episode == null && state.value.preferences.enabled && id in state.value.preferences.providers) {
                    val exact = store.binding(video.details.work.id,video.partId,id)
                    val automatic = options.firstOrNull { it.candidate.media.mediaId == exact?.mediaId && it.episode.episodeId == exact.episodeId }
                        ?: options.filter { it.suggested && it.candidate.score >= 90 }.singleOrNull()
                    if (automatic != null) selectOption(id,automatic,persist = false)
                    else change(id) { it.copy(message = if (options.isEmpty()) "没有找到分集，可重试" else "请选择对应的影片分集") }
                } else change(id) { it.copy(message = if (options.isEmpty()) "没有新的候选" else "${options.size} 个分集候选") }
            } catch (error: TimeoutCancellationException) { change(id) { it.copy(message = "匹配超时，可重试") } }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { change(id) { it.copy(message = "匹配失败，可重试") } }
            finally { if (runtimes[id] === runtime && runtime.matchJob === currentCoroutineContext()[Job]) change(id) { it.copy(matching = false) } }
        }
        runtime.matchJob?.start()
    }
    fun searchMatches(id: String) = match(id,refresh = true)
    fun chooseMatch(id: String, optionId: String) {
        val current = source(id) ?: return
        val option = current.options.firstOrNull { it.id == optionId } ?: return
        val same = current.selected?.id == option.candidate.id && current.episode?.episodeId == option.episode.episodeId
        if (same) { toggleSource(id); return }
        runtimes[id]?.matchJob?.cancel()
        selectOption(id,option,persist = true)
        if (id !in state.value.preferences.providers) update(state.value.preferences.copy(providers = state.value.preferences.providers + id))
    }
    private fun selectOption(id: String, option: DanmakuMatchOption, persist: Boolean) {
        val video = selection ?: return
        val runtime = runtimes[id] ?: return
        runtime.fetchJob?.cancel(); runtime.windows.clear(); runtime.retryAt.clear()
        if (persist) renderRevision++
        if (persist) store.bind(video.details.work.id,video.partId,DanmakuBinding(id,option.candidate.media.mediaId,option.episode.episodeId))
        change(id) { it.copy(selected = option.candidate,episode = option.episode,matching = if (persist) false else it.matching,
            commentCount = 0,message = "${option.episode.title} · 等待弹幕") }
        rebuild(); advance(position)
    }
    fun advance(positionMs: Long) {
        position = positionMs
        if (!state.value.preferences.enabled) return
        state.value.activeSources.forEach { source ->
            val runtime = runtimes[source.providerId] ?: return@forEach
            val episode = source.episode ?: return@forEach
            val current = ((positionMs - source.offsetMs).coerceAtLeast(0) / 300_000).toInt()
            if (runtime.fetchJob?.isActive == true) {
                if (kotlin.math.abs(current - (runtime.fetchingWindow ?: current)) <= 1) return@forEach
                runtime.fetchJob?.cancel()
            }
            runtime.windows.keys.filter { it !in current-1..current+1 }.toList().forEach { runtime.windows.remove(it) }
            runtime.retryAt.keys.filter { it !in current..current+1 }.toList().forEach { runtime.retryAt.remove(it) }
            val needed = listOf(current,current+1).filter { it !in runtime.windows && (runtime.retryAt[it] ?: 0) <= System.currentTimeMillis() }
            if (needed.isEmpty()) return@forEach
            runtime.fetchingWindow = current
            runtime.fetchJob = viewModelScope.launch {
                change(source.providerId) { it.copy(fetching = true) }
                try {
                    for (index in needed) {
                        try {
                            val comments = repository.window(source.providerId,episode.episodeId,index); ensureActive()
                            runtime.windows[index] = comments
                            change(source.providerId) { it.copy(commentCount = runtime.windows.values.sumOf { batch -> batch.size },message = if (comments.isEmpty()) "${episode.title} · 当前时段暂无弹幕" else "${episode.title} · 已加载弹幕") }
                            rebuild()
                        } catch (error: TimeoutCancellationException) { runtime.retryAt[index] = System.currentTimeMillis()+30_000; change(source.providerId) { it.copy(message = "请求超时，稍后重试") } }
                        catch (error: CancellationException) { throw error }
                        catch (_: Exception) { runtime.retryAt[index] = System.currentTimeMillis()+30_000; change(source.providerId) { it.copy(message = "请求失败，稍后重试") } }
                    }
                } finally { if (runtimes[source.providerId] === runtime && runtime.fetchJob === currentCoroutineContext()[Job]) change(source.providerId) { it.copy(fetching = false) } }
            }
        }
        if (kotlin.math.abs(position - lastMergePosition) >= 15_000 && state.value.activeSources.isNotEmpty()) rebuild()
    }
    private fun rebuild() {
        mergeJob?.cancel()
        val snapshots = state.value.activeSources.map { source -> Triple(source.providerId,source.offsetMs,runtimes[source.providerId]?.windows?.values?.toList().orEmpty()) }
        val at = position
        lastMergePosition = at
        val revision = renderRevision
        mergeJob = viewModelScope.launch {
            delay(40) // Coalesce key repeats without delaying the offset label or focus.
            val merged = withContext(Dispatchers.Default) {
                mergeDanmakuSources(snapshots.map { (id,offset,windows) -> DanmakuSourceBatch(id,offset,windows.flatten()) },at)
            }
            mutable.update { it.copy(comments = merged,renderRevision = revision) }
        }
    }
}
