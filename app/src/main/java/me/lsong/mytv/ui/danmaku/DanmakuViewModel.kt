package me.lsong.mytv.ui.danmaku

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.lsong.media.danmaku.*
import me.lsong.mytv.data.danmaku.*
import me.lsong.mytv.data.preferences.*
import me.lsong.mytv.domain.danmaku.matchDanmakuEpisode
import me.lsong.mytv.domain.video.VideoPlaybackSelection

data class DanmakuState(
    val preferences: DanmakuPreferences,
    val candidates: List<DanmakuCandidate> = emptyList(),
    val selected: DanmakuCandidate? = null,
    val episodes: List<DanmakuEpisode> = emptyList(),
    val episode: DanmakuEpisode? = null,
    val comments: List<DanmakuComment> = emptyList(),
    val loading: Boolean = false,
    val message: String = "开启后匹配当前影片的弹幕",
)

val danmakuProviderNames = linkedMapOf("bilibili" to "哔哩哔哩", "tencent" to "腾讯视频", "iqiyi" to "爱奇艺", "youku" to "优酷", "mgtv" to "芒果 TV", "letv" to "乐视视频")

class DanmakuViewModel(private val repository: DanmakuRepository, private val store: DanmakuPreferenceStore) : ViewModel() {
    private val _state = MutableStateFlow(DanmakuState(store.read()))
    val state = _state.asStateFlow()
    private var selection: VideoPlaybackSelection? = null
    private var job: Job? = null
    private var windowJob: Job? = null
    private var position = 0L
    private val windows = mutableMapOf<Int,List<DanmakuComment>>()
    private val loaded = mutableSetOf<Int>()
    private var generation = 0
    val datasetKey get() = "${selection?.details?.work?.id}|${selection?.partId}|${state.value.selected?.id}|${state.value.episode?.episodeId}"

    fun setVideo(value: VideoPlaybackSelection?) {
        if (selection?.details?.work?.id == value?.details?.work?.id && selection?.partId == value?.partId) { selection = value; return }
        selection = value; position = 0; restart()
    }
    fun update(value: DanmakuPreferences) {
        val old = _state.value.preferences
        store.save(value); _state.update { it.copy(preferences = value) }
        if (old.enabled != value.enabled || old.providers != value.providers) restart()
    }
    fun toggle() = update(state.value.preferences.copy(enabled = !state.value.preferences.enabled))
    fun restart() {
        job?.cancel(); windowJob?.cancel(); generation++; windows.clear(); loaded.clear()
        val value = selection
        _state.value = DanmakuState(state.value.preferences)
        if (value == null || !state.value.preferences.enabled) return
        val token = generation
        job = viewModelScope.launch {
            _state.update { it.copy(loading = true, message = "正在匹配弹幕源…") }
            try {
                val candidates = repository.search(value.details.work, state.value.preferences.providers)
                ensureActive(); if (token != generation) return@launch
                _state.update { it.copy(candidates = candidates) }
                val saved = store.binding(bindingKey())
                val chosen = candidates.firstOrNull { it.providerId == saved?.providerId && it.media.mediaId == saved.mediaId } ?: candidates.firstOrNull { it.score >= 90 }
                if (chosen == null) _state.update { it.copy(loading = false, message = if (candidates.isEmpty()) "没有匹配到弹幕，可重试或更换启用源" else "找到候选，请手动选择准确的影片") }
                else loadCandidate(chosen, saved?.takeIf { it.providerId == chosen.providerId && it.mediaId == chosen.media.mediaId }?.episodeId)
            } catch (error: TimeoutCancellationException) { _state.update { it.copy(loading = false, message = "弹幕请求超时，可选择其他源") } }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { _state.update { it.copy(loading = false, message = "弹幕匹配暂不可用，可重试") } }
        }
    }
    fun choose(candidate: DanmakuCandidate) {
        job?.cancel(); windowJob?.cancel(); generation++; windows.clear(); loaded.clear()
        job = viewModelScope.launch {
            try {
                loadCandidate(candidate,null)
                state.value.episode?.let { episode -> store.bind(bindingKey(),DanmakuBinding(candidate.providerId,candidate.media.mediaId,episode.episodeId)) }
            }
            catch (error: TimeoutCancellationException) { _state.update { it.copy(loading = false, message = "弹幕请求超时，可选择其他源") } }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { _state.update { it.copy(loading = false,message = "无法取得分集，可选择其他源") } }
        }
    }
    private suspend fun loadCandidate(candidate: DanmakuCandidate, savedEpisode: String?) {
        _state.update { it.copy(selected = candidate,episode = null,comments = emptyList(),episodes = emptyList(),loading = true,message = "正在加载弹幕分集…") }
        val episodes = repository.episodes(candidate)
        val value = selection ?: return
        val episode = episodes.firstOrNull { it.episodeId == savedEpisode } ?: matchDanmakuEpisode(value.details.work,value.part,episodes)
        _state.update { it.copy(episodes = episodes,episode = episode,loading = false,message = if (episode == null) "未匹配当前集，请在分集中手动选择" else "正在加载弹幕…") }
        if (episode != null) advance(position)
    }
    fun chooseEpisode(episode: DanmakuEpisode) {
        windowJob?.cancel(); generation++; windows.clear(); loaded.clear()
        val candidate = state.value.selected ?: return
        store.bind(bindingKey(), DanmakuBinding(candidate.providerId,candidate.media.mediaId,episode.episodeId))
        _state.update { it.copy(episode = episode,comments = emptyList(),message = "正在加载弹幕…") }
        advance(position)
    }
    private fun bindingKey() = "${selection?.details?.work?.id}|${selection?.partId}"
    fun advance(positionMs: Long) {
        position = positionMs
        val current = ((positionMs - state.value.preferences.offsetMs).coerceAtLeast(0) / 300000).toInt()
        val candidate = state.value.selected ?: return
        val episode = state.value.episode ?: return
        if (!state.value.preferences.enabled || windowJob?.isActive == true) return
        val needed = listOf(current,current + 1).filter { it !in loaded }
        if (needed.isEmpty()) return
        val token = generation
        windowJob = viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            for (index in needed) {
                val comments = try { repository.window(candidate.providerId,episode.episodeId,index) }
                catch (error: TimeoutCancellationException) { emptyList() }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { emptyList() }
                ensureActive(); if (token != generation) return@launch
                loaded += index; windows[index] = comments
                windows.keys.filter { it !in current - 1..current + 1 }.toList().forEach { windows.remove(it); loaded.remove(it) }
                val combined = withContext(Dispatchers.Default) { windows.values.flatten().distinctBy { Triple(it.timeMs,it.mode,it.content) }.sortedBy { it.timeMs } }
                _state.update { it.copy(comments = combined,message = if (combined.isEmpty()) "当前时间段暂无弹幕，可更换源或分集" else "已载入 ${combined.size} 条弹幕") }
            }
            _state.update { it.copy(loading = false) }
        }
    }
}
