package me.lsong.mytv.data.live

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import me.lsong.mytv.domain.live.*
import me.lsong.mytv.domain.provider.*
import me.lsong.mytv.domain.identity.*
import me.lsong.mytv.data.providers.m3u.M3uLiveProviderAdapter

class LiveCatalogRepository(
    private val provider: LiveCatalogProvider = M3uLiveProviderAdapter(),
    private val identities: CatalogIdentityStore = InMemoryCatalogIdentityStore(),
) {
    var providerCatalogs: List<ProviderLiveCatalog> by mutableStateOf(emptyList())
        private set
    var catalog: LiveCatalog by mutableStateOf(LiveCatalog())
        private set
    val channels: List<LiveChannel> get() = catalog.channels
    private val permits = Semaphore(4)
    private val mergeMutex = Mutex()

    private suspend fun updateCatalogs(update: (List<ProviderLiveCatalog>) -> List<ProviderLiveCatalog>) = mergeMutex.withLock {
        providerCatalogs = update(providerCatalogs)
        val snapshot = providerCatalogs
        catalog = withContext(Dispatchers.Default) { mergeLiveCatalogs(snapshot, identities) }
    }

    suspend fun load(configs: List<ProviderConfig>) = coroutineScope {
        val enabled = configs.filter { it.kind == ProviderKind.M3U && it.isEnabled }
        val saved = enabled.mapNotNull { config -> provider.cached(config)?.let { config.id to it } }.toMap()
        updateCatalogs { previousCatalogs ->
            val previous = previousCatalogs.associateBy { it.provider.id }
            enabled.map { config -> (previous[config.id] ?: saved[config.id])?.copy(provider = config, isLoading = true, error = null)
                ?: ProviderLiveCatalog(config, isLoading = true) }
        }
        enabled.map { config -> launch {
            val result = try { permits.withPermit { provider.fetch(config) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { ProviderLiveCatalog(config, error = "直播源加载失败，请在设置中检查地址") }
            updateCatalogs { catalogs -> catalogs.map { if (it.provider.id == config.id) result else it } }
        } }.joinAll()
    }
}
