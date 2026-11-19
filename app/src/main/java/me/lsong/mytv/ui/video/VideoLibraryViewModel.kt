package me.lsong.mytv.ui.video

import me.lsong.mytv.domain.identity.CatalogIdentityStore
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
import me.lsong.mytv.domain.provider.ProviderConfig
import me.lsong.mytv.domain.provider.ProviderConfigRepository
import me.lsong.mytv.domain.provider.ProviderKind
import me.lsong.mytv.domain.video.*

/** Each provider has an independent cursor and error, rather than a fictitious merged page number. */
data class VideoProviderProgress(val source: ProviderConfig, val page: Int = 0, val pages: Int = 1, val loading: Boolean = false, val error: String? = null)
data class VideoCatalogState(
    val works: List<VideoWork> = emptyList(), val providers: List<VideoProviderProgress> = emptyList(), val query: String = "",
    val details: VideoDetails? = null, val detailsLoading: Boolean = false, val detailsError: String? = null,
    val selectedPartId: String? = null, val selectedResourceId: String? = null,
) {
    val loading get() = providers.any { it.loading }
    val hasMore get() = providers.any { it.error == null && it.page < it.pages }
}

class VideoLibraryViewModel(
    repository: ProviderConfigRepository,
    private val client: VideoCatalogProvider,
    private val identities: CatalogIdentityStore,
    private val probes: ResourceProbeRepository = ResourceProbeRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(VideoCatalogState())
    val state = _state.asStateFlow()
    private val _configured = MutableStateFlow<List<ProviderConfig>>(emptyList())
    val configured = _configured.asStateFlow()
    private val _resourceProbes = MutableStateFlow<Map<String, ResourceProbe>>(emptyMap())
    val resourceProbes = _resourceProbes.asStateFlow()
    private var probeJob: Job? = null

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

    private var enabled = emptyList<ProviderConfig>()
    private val pages = mutableMapOf<String, List<ProviderVideoRecord>>()
    private val permits = Semaphore(4)
    private val detailPermits = Semaphore(3)
    private val mergeMutex = Mutex()
    private var active = false
    private var catalogJob: Job? = null
    private var detailsJob: Job? = null
    private var generation = 0
    private var detailGeneration = 0

    init {
        viewModelScope.launch {
            repository.providers.map { it.filter { source -> source.kind == ProviderKind.MAC_CMS } }.distinctUntilChanged().collect { providers ->
                _configured.value = providers
                val next = providers.filter { it.isEnabled }
                if (next != enabled) {
                    enabled = next
                    if (active) refresh(_state.value.query, force = false)
                }
            }
        }
    }

    fun activate() {
        if (!active) { active = true; refresh(force = false) }
    }

    fun refresh(query: String = _state.value.query, force: Boolean = true) {
        catalogJob?.cancel(); detailsJob?.cancel(); generation++; detailGeneration++
        catalogJob = SupervisorJob(viewModelScope.coroutineContext[Job])
        pages.clear()
        _state.value = VideoCatalogState(query = query.trim(), providers = enabled.map { VideoProviderProgress(it, loading = true) })
        fetch(enabled, reset = true, force = force)
    }

    fun loadMore() {
        val targets = _state.value.providers.filter { !it.loading && it.page < it.pages && it.error == null }.map { it.source }
        if (targets.isNotEmpty()) fetch(targets, reset = false)
    }

    fun retryFailed() {
        val targets = _state.value.providers.filter { !it.loading && it.error != null }.map { it.source }
        if (targets.isNotEmpty()) fetch(targets, reset = false, force = true)
    }

    private fun fetch(providers: List<ProviderConfig>, reset: Boolean, force: Boolean = false) {
        val token = generation
        val query = _state.value.query
        _state.update { current -> current.copy(providers = current.providers.map { if (it.source in providers) it.copy(loading = true, error = null) else it }) }
        CoroutineScope(viewModelScope.coroutineContext + checkNotNull(catalogJob)).launch {
            supervisorScope {
                providers.map { source -> launch {
                    val progress = _state.value.providers.firstOrNull { it.source.id == source.id } ?: return@launch
                    val nextPage = if (reset) 1 else progress.page + 1
                    suspend fun publish(page: VideoPage, loading: Boolean) {
                        mergeMutex.withLock {
                            if (generation != token) return@withLock
                            val queryKey = normalizeVideoText(query)
                            // Some providers silently ignore wd and return their latest catalog.
                            val relevant = if (queryKey.isBlank()) page.items else page.items.filter { normalizeVideoText(it.title).contains(queryKey) }
                            pages[source.id] = ((if (reset) emptyList() else pages[source.id].orEmpty()) + relevant).associateBy { it.ref }.values.toList()
                            val all = enabled.flatMap { pages[it.id].orEmpty() }
                            val merged = withContext(Dispatchers.Default) { VideoAggregator.merge(all, identities) }
                            if (generation != token) return@withLock
                            _state.update { current -> current.copy(
                                works = merged,
                                providers = current.providers.map { if (it.source.id == source.id) it.copy(page = nextPage, pages = page.pageCount.coerceAtLeast(nextPage), loading = loading, error = page.error) else it },
                            ) }
                        }
                    }
                    try {
                        client.cachedPage(source, query, nextPage)?.let { publish(it, true) }
                        val page = permits.withPermit { client.page(source, query, nextPage, force) }
                        publish(page, false)
                    } catch (error: CancellationException) { throw error }
                    catch (error: Exception) {
                        if (generation == token) _state.update { current -> current.copy(providers = current.providers.map {
                            if (it.source.id == source.id) it.copy(loading = false, error = "加载失败") else it
                        }) }
                    }
                } }.joinAll()
            }
        }
    }

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
                            val matches = if (known.isNotEmpty()) known else client.page(source, work.title, 1).items.filter { candidate ->
                                work.variants.all { VideoAggregator.sameWork(it, candidate) }
                            }
                            for (item in matches.distinctBy { it.ref }) {
                                val detail = client.details(source, item.ref.remoteId)
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
