package me.lsong.mytv.data.live

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.lsong.mytv.domain.identity.*
import me.lsong.mytv.domain.live.*
import me.lsong.mytv.domain.playback.PlaybackResource
import me.lsong.mytv.domain.provider.*
import org.junit.Assert.*
import org.junit.Test

class LiveCatalogRepositoryTest {
    private fun config(id: String) = ProviderConfig(id, id, "https://$id.example/list.m3u", ProviderKind.M3U)
    private fun catalog(provider: ProviderConfig): ProviderLiveCatalog {
        val ref = ProviderRecordRef(provider.id, "one")
        return ProviderLiveCatalog(provider, listOf(ProviderChannelRecord(ref, "频道 ${provider.id}", "频道 ${provider.id}", "分组",
            PlaybackResource("https://${provider.id}.example/live", ref, provider.name))))
    }
    @Test fun completedSourcesAreVisibleWithoutLosingLaterResults() = runBlocking {
        val fast = CompletableDeferred<Unit>(); val slow = CompletableDeferred<Unit>()
        val repo = LiveCatalogRepository(provider = object : LiveCatalogProvider {
            override suspend fun fetch(provider: ProviderConfig): ProviderLiveCatalog {
                (if (provider.id == "a") fast else slow).await()
                return catalog(provider)
            }
        })
        repo.start(flowOf(listOf(config("a"), config("b"))))
        fast.complete(Unit)
        withTimeout(5000) { while (repo.channels.isEmpty()) delay(5) }
        assertEquals(1, repo.channels.size)
        assertTrue(repo.catalog.isLoading)
        slow.complete(Unit)
        withTimeout(5000) { repo.state.first { it.refreshedAt > 0 && !it.catalog.isLoading } }
        assertEquals(2, repo.channels.size)
        assertFalse(repo.catalog.isLoading)
        repo.close()
    }
    @Test fun identityResolutionRunsOffTheCallerThread() = runBlocking {
        val caller = Thread.currentThread()
        val identities = object : CatalogIdentityStore {
            private val delegate = InMemoryCatalogIdentityStore()
            override fun resolveAll(namespace: String, requests: List<CatalogIdentityRequest>): List<String> {
                assertNotSame(caller, Thread.currentThread())
                return delegate.resolveAll(namespace, requests)
            }
        }
        val repo = LiveCatalogRepository(provider = object : LiveCatalogProvider {
            override suspend fun fetch(provider: ProviderConfig) = catalog(provider)
        }, identities = identities)
        repo.start(flowOf(listOf(config("a"))))
        withTimeout(5000) { repo.state.first { it.refreshedAt > 0 } }
        assertEquals(1, repo.channels.size)
        repo.close()
    }

    @Test fun preloadsCacheAndFreshMenuOpensReuseThePreparedChannelList() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val requests = java.util.concurrent.atomic.AtomicInteger()
        val reads = java.util.concurrent.atomic.AtomicInteger()
        val repo = LiveCatalogRepository(provider = object : LiveCatalogProvider {
            override suspend fun cached(provider: ProviderConfig): ProviderLiveCatalog { reads.incrementAndGet(); return catalog(provider) }
            override suspend fun fetch(provider: ProviderConfig): ProviderLiveCatalog { requests.incrementAndGet(); gate.await(); return catalog(provider) }
        })
        try {
            repo.start(flowOf(listOf(config("a"))))
            withTimeout(5000) { repo.state.first { it.catalog.channels.isNotEmpty() } }
            val channels = repo.channels
            assertTrue(repo.catalog.isLoading)
            gate.complete(Unit)
            withTimeout(5000) { repo.state.first { it.refreshedAt > 0 } }
            assertSame(channels,repo.channels)
            repeat(30) { repo.menuOpened(); assertSame(channels,repo.channels) }
            delay(100)
            assertEquals(1,requests.get()); assertEquals(1,reads.get())
        } finally { repo.close() }
    }

    @Test fun failedRefreshKeepsOldChannelsAndChangedEndpointCannotReuseThem() = runBlocking {
        val config = config("a")
        val sources = MutableStateFlow(listOf(config))
        val attempts = java.util.concurrent.atomic.AtomicInteger()
        val repo = LiveCatalogRepository(provider = object : LiveCatalogProvider {
            override suspend fun fetch(provider: ProviderConfig): ProviderLiveCatalog {
                if (attempts.incrementAndGet() > 1) error("offline")
                return catalog(provider)
            }
        })
        try {
            repo.start(sources)
            withTimeout(5000) { repo.state.first { it.refreshedAt > 0 } }
            val channels = repo.channels
            repo.refresh()
            withTimeout(5000) { repo.state.first { it.providers.any { p -> p.error != null } } }
            assertSame(channels,repo.channels)
            sources.value = listOf(config.copy(url = "https://changed.example/list"))
            withTimeout(5000) { repo.state.first { it.providers.firstOrNull()?.provider?.url != config.url && it.catalog.channels.isEmpty() } }
            sources.value = emptyList()
            withTimeout(5000) { repo.state.first { it.providers.isEmpty() } }
            assertTrue(repo.channels.isEmpty())
        } finally { repo.close() }
    }
}
