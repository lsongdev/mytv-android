package me.lsong.mytv.data.live

import kotlinx.coroutines.*
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
        val load = launch { repo.load(listOf(config("a"), config("b"))) }
        fast.complete(Unit)
        withTimeout(5000) { while (repo.channels.isEmpty()) delay(5) }
        assertEquals(1, repo.channels.size)
        assertTrue(repo.catalog.isLoading)
        slow.complete(Unit)
        load.join()
        assertEquals(2, repo.channels.size)
        assertFalse(repo.catalog.isLoading)
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
        repo.load(listOf(config("a")))
        assertEquals(1, repo.channels.size)
    }
}
