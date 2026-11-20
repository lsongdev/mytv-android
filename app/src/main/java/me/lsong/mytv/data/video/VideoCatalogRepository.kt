@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package me.lsong.mytv.data.video

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.lsong.mytv.domain.identity.CatalogIdentityStore
import me.lsong.mytv.domain.provider.*
import me.lsong.mytv.domain.video.*

/** Application-owned prepared catalogues. Selection only reads an immutable snapshot. */
class VideoCatalogRepository(
    private val configuration: ProviderConfigRepository,
    private val client: VideoCatalogProvider,
    private val identities: CatalogIdentityStore,
    private val now: () -> Long = System::currentTimeMillis,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val started = AtomicBoolean()
    private val network = Semaphore(4)
    private val metadata = Semaphore(3)
    private val entries = ConcurrentHashMap<VideoBrowseKey,Entry>()
    private val selected = MutableStateFlow(VideoBrowseKey())
    private val mutableState = MutableStateFlow(VideoCatalogSnapshot())
    val state = mutableState.asStateFlow()
    private val mutableCategories = MutableStateFlow(VideoCategoriesState(loading = true))
    val categories = mutableCategories.asStateFlow()
    private val mutableConfigured = MutableStateFlow<List<ProviderConfig>>(emptyList())
    val configured = mutableConfigured.asStateFlow()
    @Volatile private var enabled = emptyList<ProviderConfig>()
    private var revision = 0
    private var metadataJob: Job? = null
    private var warmJob: Job? = null
    private val rawPages = linkedMapOf<Address,Pair<Long,VideoPage>>()
    private val requests = mutableMapOf<Address,Deferred<VideoPage>>()
    private var access = 0L

    private data class Address(val source: ProviderConfig,val query: VideoCatalogQuery)
    private class Entry(val key: VideoBrowseKey) {
        @Volatile var snapshot = VideoCatalogSnapshot(key = key)
        val pages = linkedMapOf<Address,VideoPage>()
        val merging = Mutex()
        var input = emptyList<ProviderVideoRecord>()
        var hydrated = false
        var loadJob: Job? = null
        var background = false
        var mergeJob: Job? = null
        var dataRevision = 0
        var mergedRevision = 0
        var used = 0L
    }

    fun start() {
        if (!started.compareAndSet(false,true)) return
        scope.launch {
            configuration.providers.map { all -> all.filter { it.kind == ProviderKind.MAC_CMS } }.distinctUntilChanged().collect { sources ->
                mutableConfigured.value = sources
                val next = sources.filter { it.isEnabled }
                if (next != enabled || metadataJob == null) {
                    enabled = next; revision++
                    metadataJob?.cancel(); warmJob?.cancel()
                    entries.values.forEach { it.loadJob?.cancel(); it.mergeJob?.cancel() }
                    requests.values.forEach { it.cancel() }; requests.clear(); rawPages.clear(); entries.clear()
                    mutableCategories.value = VideoCategoriesState(loading = true)
                    mutableState.value = VideoCatalogSnapshot(key = selected.value)
                    val latest = entry(VideoBrowseKey())
                    scope.launch { hydrate(latest); load(latest,background = false) }
                    loadCategories(force = false)
                }
            }
        }
    }

    /** No parsing, sorting, aggregation, disk reads or network work on the caller. */
    fun selectCategory(id: String?) {
        if (id != null && categories.value.groups.none { it.id == id }) return
        val current = selected.value
        if (id == current.categoryId) return
        select(VideoBrowseKey(categoryId = id))
    }
    fun selectFilter(id: String?) {
        val current = selected.value
        val group = categories.value.groups.firstOrNull { it.id == current.categoryId } ?: return
        if (id != null && group.filters.none { it.id == id }) return
        if (id != current.filterId) select(current.copy(filterId = id))
    }
    fun search(query: String) = select(VideoBrowseKey(query = query.trim()))
    private fun select(key: VideoBrowseKey) {
        selected.value = key
        val cached = entries[key]?.snapshot
        mutableState.value = cached?.let { if (it.refreshedAt == 0L && it.works.isEmpty()) it.copy(preparing = true) else it }
            ?: describe(key)
        scope.launch {
            val item = entry(key); item.used = ++access
            hydrate(item)
            if (item.background && item.loadJob?.isActive == true) {
                item.loadJob?.cancel(); item.loadJob = null
                item.snapshot = item.snapshot.copy(providers = item.snapshot.providers.map { it.copy(loading = false) })
            }
            publish(item)
            if (item.snapshot.refreshedAt == 0L) load(item,background = false)
            else if (now()-item.snapshot.refreshedAt >= FRESH_FOR) load(item,refresh = true)
            else if (item.snapshot.providers.any { it.page == 0 && it.error == null }) load(item,initialOnly = true)
            else load(item,initialOnly = true)
            evict()
        }
    }

    fun menuOpened() { start(); val key = selected.value; scope.launch {
        entries[key]?.let { item -> if (now()-item.snapshot.refreshedAt >= FRESH_FOR) load(item,refresh = true) }
    } }
    fun refresh() { val key = selected.value; scope.launch { loadCategories(force = true); load(entry(key),refresh = true,force = true) } }
    fun retryFailed() { val key = selected.value; scope.launch {
        loadCategories(force = true)
        load(entry(key),retry = true,force = true)
    } }
    fun loadMore() { val key = selected.value; scope.launch { load(entry(key)) } }
    fun snapshot(key: VideoBrowseKey): VideoCatalogSnapshot? = entries[key]?.snapshot
    suspend fun searchProvider(source: ProviderConfig,title: String) = network.withPermit { client.page(source,VideoCatalogQuery(keyword = title)) }
    suspend fun details(source: ProviderConfig,id: String) = client.details(source,id)

    private fun entry(key: VideoBrowseKey): Entry = entries.getOrPut(key) {
        Entry(key).also { item -> item.snapshot = describe(key).copy(providers = targets(key)); item.used = ++access }
    }
    private fun describe(key: VideoBrowseKey): VideoCatalogSnapshot {
        val group = categories.value.groups.firstOrNull { it.id == key.categoryId }
        return VideoCatalogSnapshot(key,categoryTitle = group?.title,filterTitle = group?.filters?.firstOrNull { it.id == key.filterId }?.title)
    }
    private fun targets(key: VideoBrowseKey): List<VideoProviderProgress> {
        if (key.categoryId == null) return enabled.map { VideoProviderProgress(it) }
        val group = categories.value.groups.firstOrNull { it.id == key.categoryId }
        val records = if (key.filterId == null) group?.records.orEmpty() else group?.filters?.firstOrNull { it.id == key.filterId }?.records.orEmpty()
        val parents = records.mapNotNull { it.parentId?.let { id -> ProviderRecordRef(it.ref.providerId,id) } }.toSet()
        val queues = enabled.map { source -> records.filter { it.ref.providerId == source.id }.sortedBy { it.ref in parents }
            .map { VideoProviderProgress(source,categoryId = it.ref.remoteId) } }
        return (0 until (queues.maxOfOrNull { it.size } ?: 0)).flatMap { index -> queues.mapNotNull { it.getOrNull(index) } }
    }
    private fun batch(item: Entry) = item.snapshot.providers.filter { !it.loading && it.error == null && it.page < it.pages }
        .sortedBy { it.page }.let { if (item.key.categoryId == null) it else it.take(BATCH_SIZE) }
    private fun publish(item: Entry) {
        mutableState.update { current -> if (selected.value == item.key && entries[item.key] === item) item.snapshot else current }
    }
    private fun progress(item: Entry,key: Pair<String,String?>,change: (VideoProviderProgress) -> VideoProviderProgress) {
        item.snapshot = item.snapshot.copy(providers = item.snapshot.providers.map { if (it.key == key) change(it) else it })
        publish(item)
    }
    private fun accept(item: Entry,address: Address,page: VideoPage,fromCache: Boolean = false) {
        if (fromCache && address in item.pages) return
        if (item.snapshot.providers.none { it.source == address.source && it.categoryId == address.query.categoryId }) return
        val query = normalizeVideoText(item.key.query)
        val relevant = if (query.isBlank()) page.items else page.items.filter { normalizeVideoText(it.title).contains(query) }
        val value = page.copy(items = relevant)
        if (item.pages[address] != value) { item.pages[address] = value; item.dataRevision++ }
        val total = maxOf(page.pageCount,address.query.page)
        if (item.pages.keys.removeAll { it.source == address.source && it.query.categoryId == address.query.categoryId && it.query.page > total }) item.dataRevision++
        progress(item,address.source.id to address.query.categoryId) { cursor ->
            cursor.copy(page = maxOf(cursor.page,address.query.page).coerceAtMost(total),pages = total,error = page.error)
        }
    }
    private suspend fun hydrate(item: Entry) {
        if (item.hydrated) return
        item.hydrated = true
        val token = revision
        val cursors = batch(item)
        val cached = coroutineScope { cursors.map { cursor -> async {
            val address = Address(cursor.source,VideoCatalogQuery(item.key.query,1,cursor.categoryId))
            val value = rawPages[address]?.second ?: runCatching { client.cachedPage(cursor.source,address.query) }.getOrNull()
            address to value
        } }.awaitAll() }
        if (revision != token || entries[item.key] !== item) return
        cached.forEach { (address,page) -> if (page != null) {
            val fresh = rawPages[address]?.takeIf { it.first > 0 }?.second
            if (address !in rawPages) rawPages[address] = 0L to page
            accept(item,address,fresh ?: page,fromCache = true)
        } }
        merge(item)
    }
    private suspend fun page(address: Address,force: Boolean): VideoPage {
        if (!force) rawPages[address]?.takeIf { now()-it.first < FRESH_FOR }?.let { return it.second }
        val existing = requests[address]
        if (existing != null) return existing.await()
        val token = revision
        val task = scope.async(start = CoroutineStart.LAZY) {
            try {
                network.withPermit { client.page(address.source,address.query,force) }.also {
                    if (token == revision) {
                        rawPages[address] = now() to it
                        while (rawPages.size > 160) rawPages.remove(rawPages.keys.first())
                    }
                }
            } finally { if (token == revision) requests.remove(address) }
        }
        requests[address] = task; task.start()
        return task.await()
    }

    private fun scheduleMerge(item: Entry) {
        if (item.mergeJob?.isActive == true) return
        item.snapshot = item.snapshot.copy(preparing = true); publish(item)
        item.mergeJob = scope.launch { delay(100); merge(item) }
    }
    private suspend fun merge(item: Entry) = item.merging.withLock {
        val token = revision
        while (item.dataRevision != item.mergedRevision) {
            val version = item.dataRevision
            val input = item.snapshot.providers.flatMap { cursor -> item.pages.entries
                .filter { it.key.source == cursor.source && it.key.query.categoryId == cursor.categoryId }
                .sortedBy { it.key.query.page }.flatMap { it.value.items } }.distinctBy { it.ref }
            val works = if (input == item.input) item.snapshot.works else withContext(Dispatchers.Default) { VideoAggregator.merge(input,identities) }
            if (token != revision || entries[item.key] !== item) return@withLock
            item.input = input; item.mergedRevision = version
            item.snapshot = item.snapshot.copy(works = works)
        }
        item.snapshot = item.snapshot.copy(preparing = item.snapshot.works.isEmpty() && item.snapshot.refreshedAt == 0L && item.snapshot.providers.isNotEmpty()); publish(item)
    }
    private fun load(item: Entry,refresh: Boolean = false,force: Boolean = false,retry: Boolean = false,background: Boolean = false,initialOnly: Boolean = false): Job? {
        if (item.loadJob?.isActive == true) return item.loadJob
        val pending = when {
            initialOnly -> batch(item).filter { it.page == 0 }.take((BATCH_SIZE-item.snapshot.providers.count { it.page > 0 || it.error != null }).coerceAtLeast(0))
            retry -> item.snapshot.providers.filter { it.error != null }
            refresh || item.snapshot.refreshedAt == 0L -> item.snapshot.providers.let { if (item.key.categoryId == null) it else it.take(BATCH_SIZE) }
            else -> batch(item)
        }
        if (pending.isEmpty()) { item.snapshot = item.snapshot.copy(preparing = false,refreshedAt = now()); publish(item); return null }
        val token = revision
        item.background = background
        item.snapshot = item.snapshot.copy(preparing = item.snapshot.works.isEmpty())
        val task = scope.launch {
            var pendingBatch = pending
            var initialPass = refresh || item.snapshot.refreshedAt == 0L
            while (pendingBatch.isNotEmpty() && token == revision && currentCoroutineContext().isActive) {
                pendingBatch.forEach { cursor -> progress(item,cursor.key) { it.copy(loading = true,error = null) } }
                suspend fun fetch(cursor: VideoProviderProgress) {
                    val number = if (initialPass) 1 else if (retry) cursor.page.coerceAtLeast(1) else cursor.page+1
                    val address = Address(cursor.source,VideoCatalogQuery(item.key.query,number,cursor.categoryId))
                    try {
                        val result = page(address,force)
                        if (token != revision) return
                        accept(item,address,result)
                        if (item.dataRevision != item.mergedRevision) scheduleMerge(item)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { if (token == revision) progress(item,cursor.key) { it.copy(error = "加载失败") } }
                    if (token == revision) progress(item,cursor.key) { it.copy(loading = false) }
                }
                if (background) pendingBatch.forEach { fetch(it) } else supervisorScope { pendingBatch.map { launch { fetch(it) } }.joinAll() }
                item.mergeJob?.join(); merge(item)
                if (token != revision) break
                item.snapshot = item.snapshot.copy(refreshedAt = now(),preparing = false); publish(item)
                initialPass = false
                val remaining = batch(item).filter { it.page == 0 }
                val used = item.snapshot.providers.count { it.page > 0 || it.error != null }
                pendingBatch = when {
                    item.snapshot.works.isEmpty() -> remaining
                    used < BATCH_SIZE -> remaining.take(BATCH_SIZE-used)
                    else -> emptyList()
                }
            }
            evict()
        }
        item.loadJob = task
        return task
    }

    private fun loadCategories(force: Boolean) {
        metadataJob?.cancel()
        val token = revision
        val sources = enabled.toList()
        mutableCategories.update { it.copy(loading = true) }
        metadataJob = scope.launch {
            val records = linkedMapOf<String,List<ProviderVideoCategory>>()
            val failed = mutableSetOf<String>()
            suspend fun publishMetadata(loading: Boolean) {
                if (token != revision) return
                val groups = buildVideoCategoryGroups(sources.flatMap { records[it.id].orEmpty() })
                val changed = groups != categories.value.groups
                mutableCategories.value = VideoCategoriesState(groups,loading,failed.toSet())
                if (changed) {
                    entries.values.forEach { item ->
                        val wanted = targets(item.key)
                        val old = item.snapshot.providers.associateBy { it.key }
                        if (wanted.map { it.key }.toSet() != old.keys) {
                            item.snapshot = item.snapshot.copy(categoryTitle = describe(item.key).categoryTitle,
                                providers = wanted.map { old[it.key] ?: it })
                            val allowed = wanted.map { it.key }.toSet()
                            if (item.pages.keys.removeAll { (it.source.id to it.query.categoryId) !in allowed }) { item.dataRevision++; scheduleMerge(item) }
                            publish(item)
                            if (item.key == selected.value) load(item,initialOnly = true)
                        }
                    }
                    warm(groups)
                }
                val key = selected.value
                if (!loading && key.categoryId != null) {
                    val group = groups.firstOrNull { it.id == key.categoryId }
                    if (group == null) select(VideoBrowseKey())
                    else if (key.filterId != null && group.filters.none { it.id == key.filterId }) select(key.copy(filterId = null))
                }
            }
            coroutineScope { sources.map { source -> launch {
                runCatching { client.cachedCategories(source) }.getOrNull()?.let { records[source.id] = it }
            } }.joinAll() }
            publishMetadata(sources.isNotEmpty())
            supervisorScope { sources.map { source -> launch {
                try { records[source.id] = metadata.withPermit { client.categories(source,force) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed += source.id }
                publishMetadata(true)
            } }.joinAll() }
            publishMetadata(false)
        }
    }
    private fun warm(groups: List<VideoCategoryGroup>) {
        warmJob?.cancel()
        warmJob = scope.launch {
            val keys = listOf(VideoBrowseKey()) + groups.map { VideoBrowseKey(it.id) }
            // Disk restoration for every primary catalogue precedes network prefetch.
            keys.forEach { key -> hydrate(entry(key)) }
            keys.forEach { key ->
                val item = entry(key)
                if (item.snapshot.refreshedAt == 0L && key != selected.value) load(item,background = true)?.join()
            }
            entries[selected.value]?.let { if (it.snapshot.refreshedAt == 0L) load(it) }
            evict()
        }
    }
    private fun evict() {
        while (entries.size > 24) {
            val candidate = entries.values.filter { it.key != selected.value && (it.key.filterId != null || it.key.query.isNotEmpty()) && it.loadJob?.isActive != true }
                .minByOrNull { it.used } ?: break
            entries.remove(candidate.key); candidate.mergeJob?.cancel()
        }
    }
    override fun close() { scope.cancel() }
    private companion object { const val BATCH_SIZE = 8; const val FRESH_FOR = 600_000L }
}
