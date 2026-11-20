@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package me.lsong.mytv.data.live

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import me.lsong.mytv.domain.live.*
import me.lsong.mytv.domain.provider.*
import me.lsong.mytv.domain.identity.*
import me.lsong.mytv.data.providers.m3u.M3uLiveProviderAdapter

data class LiveCatalogSnapshot(
    val catalog: LiveCatalog = LiveCatalog(),
    val providers: List<ProviderLiveCatalog> = emptyList(),
    val refreshedAt: Long = 0,
)

/** Application-owned prepared live directory; reading it never parses or aggregates records. */
class LiveCatalogRepository(
    private val provider: LiveCatalogProvider = M3uLiveProviderAdapter(),
    private val identities: CatalogIdentityStore = InMemoryCatalogIdentityStore(),
    private val now: () -> Long = System::currentTimeMillis,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val started = AtomicBoolean()
    private val requests = Channel<Boolean>(Channel.CONFLATED)
    private val mutableState = MutableStateFlow(LiveCatalogSnapshot())
    val state = mutableState.asStateFlow()
    val catalog: LiveCatalog get() = state.value.catalog
    val channels: List<LiveChannel> get() = catalog.channels
    private val permits = Semaphore(4)
    private val mergeMutex = Mutex()

    fun start(configuration: Flow<List<ProviderConfig>>) {
        if (!started.compareAndSet(false,true)) return
        scope.launch {
            configuration.map { all -> all.filter { it.kind == ProviderKind.M3U && it.isEnabled } }
                .distinctUntilChanged().collectLatest { sources ->
                    load(sources)
                    for (force in requests) {
                        if (force || now() - state.value.refreshedAt >= FRESH_FOR) load(sources)
                    }
                }
        }
    }
    fun menuOpened() {
        if (now() - state.value.refreshedAt >= FRESH_FOR) requests.trySend(false)
    }
    /** Rechecks providers while preserving their storage freshness and failure backoff policies. */
    fun refresh() { requests.trySend(true) }

    private suspend fun updateCatalogs(update: (List<ProviderLiveCatalog>) -> List<ProviderLiveCatalog>) = mergeMutex.withLock {
        val previous = state.value
        val sources = update(previous.providers)
        // Loading/health changes should not rebuild channel identities or replace prepared lists.
        val sameContent = sources.map { it.provider to it.records } == previous.providers.map { it.provider to it.records }
        val merged = if (sameContent) previous.catalog.copy(
            isLoading = sources.any { it.isLoading },
            error = if (sources.any { it.error != null } && previous.catalog.channels.isEmpty()) "直播源加载失败，请在设置中检查地址" else null,
        ) else mergeLiveCatalogs(sources,identities)
        mutableState.value = previous.copy(catalog = merged,providers = sources)
    }

    private suspend fun load(configs: List<ProviderConfig>) = coroutineScope {
        // Remove disabled/changed endpoints immediately; never show another endpoint's old records.
        updateCatalogs { previous -> configs.map { config ->
            previous.firstOrNull { it.provider.id == config.id && it.provider.url == config.url }
                ?.copy(provider = config,isLoading = true,error = null) ?: ProviderLiveCatalog(config,isLoading = true)
        } }
        val saved = configs.map { config -> async { config to try { provider.cached(config) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null } } }.awaitAll().toMap()
        updateCatalogs { current -> current.map { item ->
            if (item.records.isEmpty()) saved[item.provider]?.copy(isLoading = true,error = null) ?: item else item
        } }
        configs.map { config -> launch {
            val result = try { permits.withPermit { provider.fetch(config) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { ProviderLiveCatalog(config,error = "直播源加载失败，请在设置中检查地址") }
            updateCatalogs { catalogs -> catalogs.map { old ->
                if (old.provider.id != config.id) old
                else if (result.error != null && result.records.isEmpty()) old.copy(isLoading = false,error = result.error)
                else result
            } }
        } }.joinAll()
        mutableState.value = state.value.copy(refreshedAt = now())
    }
    override fun close() { scope.cancel(); requests.close() }
    private companion object { const val FRESH_FOR = 600_000L }
}
