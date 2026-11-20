package me.lsong.mytv.ui.video

import me.lsong.mytv.data.playback.ResourceProbeRepository
import me.lsong.mytv.domain.playback.PlaybackResource
import me.lsong.mytv.domain.playback.ResourceProbe

import me.lsong.mytv.domain.provider.ProviderRecordRef

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.lsong.mytv.domain.video.*

data class VideoCatalogState(
    val works: List<VideoWork> = emptyList(), val providers: List<VideoProviderProgress> = emptyList(), val query: String = "",
    val details: VideoDetails? = null, val detailsLoading: Boolean = false, val detailsError: String? = null,
    val selectedPartId: String? = null, val selectedResourceId: String? = null,
    val categoryId: String? = null, val categoryTitle: String? = null,
    val filterId: String? = null, val filterTitle: String? = null, val preparing: Boolean = false,
) {
    val loading get() = preparing || providers.any { it.loading }
    val hasMore get() = providers.any { it.error == null && it.page < it.pages }
}

class VideoLibraryViewModel(
    private val catalog: me.lsong.mytv.data.video.VideoCatalogRepository,
    private val probes: ResourceProbeRepository = ResourceProbeRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(VideoCatalogState(preparing = true))
    val state = _state.asStateFlow()
    val configured = catalog.configured
    val categories = catalog.categories
    private val enabled get() = configured.value.filter { it.isEnabled }
    private val _resourceProbes = MutableStateFlow<Map<String, ResourceProbe>>(emptyMap())
    val resourceProbes = _resourceProbes.asStateFlow()
    private var probeJob: Job? = null
    private var detailsJob: Job? = null
    private var detailGeneration = 0
    private val detailPermits = Semaphore(3)

    init {
        catalog.start()
        viewModelScope.launch {
            catalog.state.collect { snapshot ->
                val old = _state.value
                val changed = old.categoryId != snapshot.key.categoryId || old.filterId != snapshot.key.filterId || old.query != snapshot.key.query
                if (changed) { detailsJob?.cancel(); detailGeneration++; cancelProbes() }
                _state.value = (if (changed) VideoCatalogState() else old).copy(works = snapshot.works,
                    providers = snapshot.providers,query = snapshot.key.query,categoryId = snapshot.key.categoryId,
                    categoryTitle = snapshot.categoryTitle,filterId = snapshot.key.filterId,filterTitle = snapshot.filterTitle,
                    preparing = snapshot.preparing)
            }
        }
    }
    fun selectCategory(id: String?) = catalog.selectCategory(id)
    fun selectFilter(id: String?) = catalog.selectFilter(id)
    fun search(query: String) = catalog.search(query)
    fun refresh() = catalog.refresh()
    fun loadMore() = catalog.loadMore()
    fun retryFailed() = catalog.retryFailed()
    fun menuOpened() = catalog.menuOpened()

    fun probeResources(resources: List<PlaybackResource>) {
        probeJob?.cancel()
        _resourceProbes.value = resources.associate { it.id to ResourceProbe.Pending }
        probeJob = viewModelScope.launch {
            resources.forEach { resource -> launch {
                val result = probes.probe(resource) {
                    _resourceProbes.update { it + (resource.id to ResourceProbe.Testing) }
                }
                ensureActive()
                _resourceProbes.update { it + (resource.id to result) }
            } }
        }
    }

    fun cancelProbes() { probeJob?.cancel(); _resourceProbes.value = emptyMap() }

    fun open(work: VideoWork) {
        detailsJob?.cancel(); val token = ++detailGeneration
        _state.update { it.copy(details = VideoDetails(work, emptyList()), detailsLoading = true, detailsError = null, selectedPartId = null, selectedResourceId = null) }
        detailsJob = viewModelScope.launch {
            val variants = mutableMapOf<ProviderRecordRef, ProviderVideoRecord>()
            val errors = mutableMapOf<String, String>()
            val detailMergeMutex = Mutex()
            suspend fun publish() = detailMergeMutex.withLock {
                if (token != detailGeneration) return@withLock
                val ordered = enabled.flatMap { source -> variants.values.filter { it.ref.providerId == source.id } }
                val failures = errors.toMap()
                val combined = withContext(Dispatchers.Default) { VideoAggregator.details(work, ordered, failures) }
                if (token != detailGeneration) return@withLock
                _state.update { current -> current.copy(
                    details = combined,
                    selectedPartId = current.selectedPartId?.takeIf { id -> combined.parts.any { it.id == id } } ?: combined.parts.firstOrNull()?.id,
                ) }
            }
            supervisorScope {
                // Known resources load first; other providers are searched only for this work.
                val orderedSources = enabled.sortedBy { source -> if (work.variants.any { it.ref.providerId == source.id }) 0 else 1 }
                orderedSources.map { source -> launch {
                    try {
                        detailPermits.withPermit {
                            val known = work.variants.filter { it.ref.providerId == source.id }
                            val matches = if (known.isNotEmpty()) known else catalog.searchProvider(source, work.title).items.filter { candidate ->
                                work.variants.all { VideoAggregator.sameWork(it, candidate) }
                            }
                            for (item in matches.distinctBy { it.ref }) {
                                val detail = catalog.details(source, item.ref.remoteId)
                                if (token != detailGeneration) return@withPermit
                                variants[item.ref] = detail
                                publish()
                            }
                        }
                    } catch (error: CancellationException) { throw error }
                    catch (error: Exception) { errors[source.name] = "详情加载失败"; publish() }
                } }.joinAll()
            }
            if (token == detailGeneration) _state.update { it.copy(detailsLoading = false,
                detailsError = if (it.details?.parts.isNullOrEmpty()) "暂无可直接播放的资源，可重试或更换来源" else null) }
        }
    }

    fun closeDetails() {
        cancelProbes()
        detailsJob?.cancel(); detailGeneration++
        _state.update { it.copy(details = null, detailsLoading = false, detailsError = null, selectedPartId = null, selectedResourceId = null) }
    }

    fun selectPart(id: String) {
        _state.update { it.copy(selectedPartId = id, selectedResourceId = null) }
    }

    fun selection(partId: String = _state.value.selectedPartId.orEmpty(), resourceId: String? = null): VideoPlaybackSelection? {
        val detail = _state.value.details ?: return null
        val episode = detail.parts.firstOrNull { it.id == partId } ?: return null
        val candidate = episode.resources.firstOrNull { it.id == resourceId } ?: episode.resources.firstOrNull() ?: return null
        return VideoPlaybackSelection(detail, episode.id, candidate.id)
    }
}
