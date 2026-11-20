package me.lsong.mytv.data.video

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import me.lsong.mytv.domain.identity.*
import me.lsong.mytv.domain.provider.*
import me.lsong.mytv.domain.video.*
import org.junit.Assert.*
import org.junit.Test

class VideoCatalogRepositoryTest {
    private val movie = VideoBrowseKey("category:MOVIE")
    private val series = VideoBrowseKey("category:SERIES")
    private class Configuration : ProviderConfigRepository {
        override val providers = MutableStateFlow(listOf("a","b","disabled").map {
            ProviderConfig(it,it,"https://$it.example/api",ProviderKind.MAC_CMS,it != "disabled")
        })
        override fun save(source: ProviderConfig) { providers.value = providers.value.map { if (it.id == source.id) source else it } }
        override fun delete(id: String) { providers.value = providers.value.filterNot { it.id == id } }
        override fun replace(providers: List<ProviderConfig>) { this.providers.value = providers }
    }
    private class Identities : CatalogIdentityStore {
        val calls = AtomicInteger()
        val threads = CopyOnWriteArrayList<String>()
        private val delegate = InMemoryCatalogIdentityStore()
        override fun resolveAll(namespace: String,requests: List<CatalogIdentityRequest>): List<String> {
            calls.incrementAndGet(); threads += Thread.currentThread().name
            return delegate.resolveAll(namespace,requests)
        }
    }
    private class Client(val gate: CompletableDeferred<Unit>? = null,val cachedMovieGate: CompletableDeferred<Unit>? = null) : VideoCatalogProvider {
        val reads = AtomicInteger()
        val delayedReads = AtomicInteger()
        val requests = CopyOnWriteArrayList<Pair<String,VideoCatalogQuery>>()
        @Volatile var version = 0
        override suspend fun cachedCategories(source: ProviderConfig) = listOf(
            ProviderVideoCategory(ProviderRecordRef(source.id,if (source.id == "a") "10" else "90"),"电影"),
            ProviderVideoCategory(ProviderRecordRef(source.id,if (source.id == "a") "20" else "100"),"连续剧"),
        )
        override suspend fun categories(source: ProviderConfig,force: Boolean) = cachedCategories(source)
        private fun result(source: ProviderConfig,query: VideoCatalogQuery,edition: Int): VideoPage {
            val title = when (query.categoryId) { "10","90" -> "电影"; "20","100" -> "剧集"; else -> "最近" }
            return VideoPage(listOf(ProviderVideoRecord(ProviderRecordRef(source.id,"${query.categoryId}:${query.page}"),
                source.name,"$title-v$edition-${query.page}","2024",VideoKind.MOVIE)),query.page,2)
        }
        override suspend fun cachedPage(source: ProviderConfig,query: VideoCatalogQuery): VideoPage {
            reads.incrementAndGet()
            if (query.categoryId in setOf("10","90") && cachedMovieGate != null) { delayedReads.incrementAndGet(); cachedMovieGate.await() }
            return result(source,query,0)
        }
        override suspend fun page(source: ProviderConfig,query: VideoCatalogQuery,force: Boolean): VideoPage {
            requests += source.id to query
            gate?.await(); delay(10)
            return result(source,query,version)
        }
        override suspend fun details(source: ProviderConfig,remoteId: String): ProviderVideoRecord = error("unused")
    }
    private suspend fun ready(repo: VideoCatalogRepository) = withTimeout(5000) {
        while (listOf(VideoBrowseKey(),movie,series).any { key -> repo.snapshot(key)?.let { it.refreshedAt > 0 && !it.loading } != true }) delay(10)
    }

    @Test fun restoresAllPrimaryCataloguesBeforeOpeningAnyMenu() = runBlocking {
        val gate = CompletableDeferred<Unit>(); val ids = Identities(); val client = Client(gate)
        val repo = VideoCatalogRepository(Configuration(),client,ids)
        try {
            repo.start()
            withTimeout(5000) { while (listOf(VideoBrowseKey(),movie,series).any { repo.snapshot(it)?.works.isNullOrEmpty() }) delay(10) }
            assertEquals(VideoBrowseKey(),repo.state.value.key)
            assertTrue(ids.threads.all { it.contains("DefaultDispatcher") })
            assertTrue(client.requests.none { it.first == "disabled" })
        } finally { gate.complete(Unit); repo.close() }
    }

    @Test fun repeatedSwitchesReuseExactPreparedResultsWithoutReadsRequestsOrMerges() = runBlocking {
        val client = Client(); val ids = Identities(); val repo = VideoCatalogRepository(Configuration(),client,ids)
        try {
            repo.start(); ready(repo)
            val movies = repo.snapshot(movie)!!.works; val shows = repo.snapshot(series)!!.works
            val merges = ids.calls.get(); val reads = client.reads.get(); val requests = client.requests.size
            repeat(30) {
                repo.selectCategory(movie.categoryId); assertSame(movies,repo.state.value.works)
                repo.selectCategory(series.categoryId); assertSame(shows,repo.state.value.works)
            }
            delay(200)
            assertEquals(merges,ids.calls.get()); assertEquals(reads,client.reads.get()); assertEquals(requests,client.requests.size)
        } finally { repo.close() }
    }

    @Test fun switchingAwayPreservesLoadedPagesAndInFlightWork() = runBlocking {
        val client = Client(); val repo = VideoCatalogRepository(Configuration(),client,Identities())
        try {
            repo.start(); ready(repo); repo.selectCategory(movie.categoryId); repo.loadMore()
            repo.selectCategory(series.categoryId)
            withTimeout(5000) { while (repo.snapshot(movie)?.let { !it.loading && it.works.size == 2 } != true) delay(10) }
            val expanded = repo.snapshot(movie)!!.works
            val requests = client.requests.size
            repo.selectCategory(movie.categoryId)
            assertSame(expanded,repo.state.value.works)
            assertTrue(repo.state.value.providers.all { it.page == 2 })
            delay(150); assertEquals(requests,client.requests.size)
        } finally { repo.close() }
    }

    @Test fun unchangedRefreshReusesAggregateAndChangedPagesMergeAsOneBatch() = runBlocking {
        val client = Client(); val ids = Identities(); val repo = VideoCatalogRepository(Configuration(),client,ids)
        try {
            repo.start(); ready(repo); repo.selectCategory(movie.categoryId)
            val works = repo.state.value.works; val merges = ids.calls.get()
            val before = client.requests.size
            repo.refresh()
            withTimeout(5000) { while (client.requests.size < before+2 || repo.snapshot(movie)!!.loading) delay(10) }
            assertSame(works,repo.state.value.works); assertEquals(merges,ids.calls.get())
            client.version = 1
            repo.refresh()
            assertSame(works,repo.state.value.works)
            withTimeout(5000) { repo.state.first { !it.loading && it.works.all { work -> "v1" in work.title } } }
            assertEquals(merges+1,ids.calls.get())
        } finally { repo.close() }
    }

    @Test fun disablingProviderInvalidatesItsPreparedResults() = runBlocking {
        val config = Configuration(); val repo = VideoCatalogRepository(config,Client(),Identities())
        try {
            repo.start(); ready(repo); repo.selectCategory(movie.categoryId)
            config.delete("a")
            withTimeout(5000) { repo.state.first { it.key == movie && !it.loading && it.works.isNotEmpty() && it.works.all { work -> work.variants.all { variant -> variant.ref.providerId == "b" } } } }
            assertTrue(repo.snapshot(movie)!!.providers.all { it.source.id == "b" })
        } finally { repo.close() }
    }
    @Test fun disabledSourceInvalidatesItsPreparedResults() = runBlocking {
        val config = Configuration(); val repo = VideoCatalogRepository(config,Client(),Identities())
        try {
            repo.start(); ready(repo); repo.selectCategory(movie.categoryId)
            assertEquals(2,repo.state.value.works.single().providerCount)
            config.delete("a")
            withTimeout(5000) { repo.state.first { snapshot -> snapshot.key == movie && !snapshot.loading &&
                snapshot.works.isNotEmpty() && snapshot.works.all { work -> work.variants.all { it.ref.providerId == "b" } } } }
            repo.selectCategory(series.categoryId); repo.selectCategory(movie.categoryId)
            assertTrue(repo.state.value.works.all { work -> work.variants.all { it.ref.providerId == "b" } })
        } finally { repo.close() }
    }

    @Test fun lateDiskRestorationCannotOverwriteNewerNetworkResults() = runBlocking {
        val gate = CompletableDeferred<Unit>(); val client = Client(cachedMovieGate = gate).apply { version = 1 }
        val repo = VideoCatalogRepository(Configuration(),client,Identities())
        try {
            repo.start()
            withTimeout(5000) { while (client.delayedReads.get() < 2) delay(10) }
            repo.selectCategory(movie.categoryId)
            val fresh = withTimeout(5000) { repo.state.first { it.key == movie && !it.loading && it.works.isNotEmpty() && it.works.all { work -> "v1" in work.title } } }.works
            gate.complete(Unit)
            delay(200)
            assertSame(fresh,repo.state.value.works)
            assertTrue(repo.state.value.works.all { "v1" in it.title })
        } finally { gate.complete(Unit); repo.close() }
    }

}
