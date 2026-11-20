package me.lsong.mytv.ui.video

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import me.lsong.mytv.domain.identity.InMemoryCatalogIdentityStore
import me.lsong.mytv.domain.live.LiveCatalog
import me.lsong.mytv.domain.provider.*
import me.lsong.mytv.domain.video.*
import me.lsong.mytv.ui.browser.ContentDrawer
import me.lsong.mytv.ui.browser.ContentDrawerState
import me.lsong.mytv.data.video.VideoCatalogRepository
import me.lsong.mytv.ui.navigation.Section
import me.lsong.mytv.ui.theme.TvTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class VideoCategoriesTest {
    @get:Rule val compose = createComposeRule()
    private class Repository : ProviderConfigRepository {
        override val providers = MutableStateFlow(listOf("a","b","failed","disabled").map {
            ProviderConfig(it,it,"https://$it.example/api",ProviderKind.MAC_CMS,it != "disabled")
        })
        override fun save(source: ProviderConfig) { providers.value = providers.value.map { if (it.id == source.id) source else it } }
        override fun delete(id: String) { providers.value = providers.value.filterNot { it.id == id } }
        override fun replace(providers: List<ProviderConfig>) { this.providers.value = providers }
    }
    private class Client(val metadataGate: CompletableDeferred<Unit>? = null, val lateMovie: Boolean = false, val large: Boolean = false, val slowB: CompletableDeferred<Unit>? = null) : VideoCatalogProvider {
        val requests = java.util.concurrent.CopyOnWriteArrayList<Pair<String,VideoCatalogQuery>>()
        val categoryRequests = java.util.concurrent.CopyOnWriteArrayList<String>()
        var lateCompleted = false
        var active = 0
        var maximumActive = 0
        override suspend fun cachedCategories(source: ProviderConfig): List<ProviderVideoCategory>? =
            if (metadataGate != null && source.id == "a") listOf(ProviderVideoCategory(ProviderRecordRef("a","cached"),"缓存分类")) else null
        override suspend fun categories(source: ProviderConfig, force: Boolean): List<ProviderVideoCategory> {
            categoryRequests += source.id
            metadataGate?.await()
            if (source.id == "b") slowB?.await()
            if (source.id == "failed") error("分类接口不可用")
            val ids = if (large) listOf(if (source.id == "a") "1" else "90") + (0..20).map { "${source.id}-$it" }
                else if (source.id == "a") listOf("1","6","8") else listOf("90","96")
            return ids.mapIndexed { index,id -> ProviderVideoCategory(ProviderRecordRef(source.id,id),
                if (index == 0) "电影" else if (large) "专题$index" else "动作片",if (index == 0) null else ids.first()) }
        }
        override suspend fun page(source: ProviderConfig, query: VideoCatalogQuery, force: Boolean): VideoPage {
            requests += source.id to query
            active++; maximumActive = maxOf(maximumActive,active)
            try {
                if (lateMovie && query.categoryId == "1") withContext(NonCancellable) { delay(500); lateCompleted = true } else delay(10)
            } finally { active-- }
            if (source.id == "failed") error("offline")
            val title = when (query.categoryId) { null -> "最近影片"; "1","90" -> "电影"; else -> "动作片" }
            val item = ProviderVideoRecord(ProviderRecordRef(source.id,"${query.categoryId}:${query.page}"),source.name,"$title${query.page}","2024",VideoKind.MOVIE)
            val pages = when (query.categoryId) { "6" -> 2; "96" -> 3; else -> 1 }
            return VideoPage(listOf(item),query.page,pages)
        }
        override suspend fun details(source: ProviderConfig, remoteId: String): ProviderVideoRecord = error("unused")
    }
    private fun model(repo: Repository, client: Client): VideoLibraryViewModel {
        lateinit var model: VideoLibraryViewModel
        compose.runOnIdle { model = VideoLibraryViewModel(VideoCatalogRepository(repo,client,InMemoryCatalogIdentityStore())) }
        return model
    }
    private fun awaitLoaded(model: VideoLibraryViewModel, category: String? = null) {
        compose.waitUntil(5000) { model.state.value.categoryId == category && !model.state.value.loading && model.state.value.works.isNotEmpty() }
    }
    private fun press(key: Key) { compose.onRoot().performKeyInput { pressKey(key) }; compose.waitForIdle() }

    @Test fun menuUsesDiscoveredCategoriesAndRestoresCategoryFocus() {
        val repo = Repository(); val client = Client(); val model = model(repo,client)
        val drawer = ContentDrawerState(null).apply { section = Section.VIDEOS; column = 1 }
        compose.setContent { TvTheme { ContentDrawer(LiveCatalog(),null,drawer,{}, {}, {},videoLibrary = model) } }
        compose.waitUntil(5000) { !model.categories.value.loading && model.categories.value.groups.size == 1 }
        compose.onNodeWithTag("drawer-entry:latest").assertIsFocused()
        compose.onNodeWithTag("drawer-entry:kind:SERIES").assertDoesNotExist()
        press(Key.DirectionDown)
        awaitLoaded(model,"category:MOVIE")
        assertEquals(setOf("a" to "1","a" to "6","a" to "8","b" to "90","b" to "96"),client.requests.filter { it.second.categoryId != null }.map { it.first to it.second.categoryId }.toSet())
        compose.onNodeWithTag("drawer-entry:filter:MOVIE:动作").assertDoesNotExist()
        press(Key.DirectionRight)
        compose.onNodeWithTag("vod-filter:all").assertIsFocused()
        press(Key.DirectionRight)
        compose.waitUntil(5000) { model.state.value.filterId == "filter:MOVIE:动作" && !model.state.value.loading }
        compose.onNodeWithTag("vod-filter:filter:MOVIE:动作").assertIsFocused()
        assertEquals(setOf("6","8","96"),model.state.value.providers.map { it.categoryId }.toSet())
        press(Key.DirectionDown)
        compose.onNodeWithTag("vod-work:${model.state.value.works.single().id}").assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithTag("vod-filter:filter:MOVIE:动作").assertIsFocused()
        press(Key.DirectionLeft)
        compose.onNodeWithTag("vod-filter:all").assertIsFocused()
        press(Key.DirectionLeft)
        compose.onNodeWithTag("drawer-entry:category:MOVIE").assertIsFocused()
        assertEquals(setOf("failed"),model.categories.value.failedProviders)
        assertFalse(client.categoryRequests.contains("disabled"))
        compose.runOnIdle { repo.delete("a"); repo.delete("b") }
        compose.waitUntil(5000) { !model.categories.value.loading && drawer.videoNavigation == "latest" }
        compose.onNodeWithTag("drawer-entry:latest").assertIsFocused()
    }

    @Test fun categoryBindingsPageIndependentlyAndDisabledSourcesDisappear() {
        val repo = Repository(); val client = Client(); val model = model(repo,client)
        compose.waitUntil(5000) { !model.categories.value.loading }
        compose.runOnIdle { model.selectCategory("category:MOVIE"); model.selectFilter("filter:MOVIE:动作") }
        awaitLoaded(model,"category:MOVIE")
        assertEquals(3,model.state.value.providers.size)
        compose.runOnIdle { model.loadMore() }
        compose.waitUntil(5000) { !model.state.value.loading && model.state.value.works.size == 2 }
        compose.runOnIdle { model.loadMore() }
        compose.waitUntil(5000) { !model.state.value.loading && model.state.value.works.size == 3 }
        assertFalse(model.state.value.hasMore)
        assertEquals(setOf("a" to "6","b" to "96"),client.requests.filter { it.second.page == 2 }.map { it.first to it.second.categoryId }.toSet())
        assertEquals(listOf("b"),client.requests.filter { it.second.page == 3 }.map { it.first })
        compose.runOnIdle { repo.delete("a") }
        compose.waitUntil(5000) { !model.categories.value.loading && model.state.value.providers.all { it.source.id == "b" } }
        compose.runOnIdle { repo.delete("b") }
        compose.waitUntil(5000) { !model.categories.value.loading && model.state.value.categoryId == null }
        assertTrue(model.categories.value.groups.isEmpty())
    }

    @Test fun cachedCategoriesAndLatestPublishWhileCategoryRefreshIsSlow() {
        val gate = CompletableDeferred<Unit>(); val client = Client(gate); val model = model(Repository(),client)
        try {
            awaitLoaded(model)
            compose.waitUntil(5000) { model.categories.value.groups.any { it.kind == VideoKind.OTHER } }
            assertTrue(model.categories.value.loading)
            compose.runOnIdle { model.selectCategory("category:OTHER") }
            awaitLoaded(model,"category:OTHER")
            assertTrue(client.requests.any { it.first == "a" && it.second.categoryId == "cached" })
        } finally { gate.complete(Unit) }
        compose.waitUntil(5000) { !model.categories.value.loading && model.state.value.categoryId == null }
    }

    @Test fun lateResponseFromPreviousCategoryDoesNotReplaceSelection() {
        val client = Client(lateMovie = true); val model = model(Repository(),client)
        compose.waitUntil(5000) { !model.categories.value.loading }
        compose.runOnIdle { model.selectCategory("category:MOVIE") }
        compose.waitUntil(5000) { client.requests.any { it.second.categoryId == "1" } }
        compose.runOnIdle { model.selectFilter("filter:MOVIE:动作") }
        awaitLoaded(model,"category:MOVIE")
        compose.waitUntil(5000) { client.lateCompleted }
        assertTrue(model.state.value.works.all { it.title.startsWith("动作片") })
        compose.runOnIdle { model.search("目标") }
        compose.waitUntil(5000) { !model.state.value.loading }
        assertNull(model.state.value.categoryId)
        assertTrue(client.requests.takeLast(3).all { it.second.categoryId == null && it.second.keyword == "目标" })
    }
    @Test fun broadGroupsUseBoundedBatchesAndRoundRobinProviders() {
        val client = Client(large = true); val model = model(Repository(),client)
        compose.waitUntil(5000) { !model.categories.value.loading }
        awaitLoaded(model)
        compose.runOnIdle { model.selectCategory("category:MOVIE") }
        awaitLoaded(model,"category:MOVIE")
        val first = client.requests.filter { it.second.categoryId != null }
        assertEquals(8,first.size)
        assertEquals(mapOf("a" to 4,"b" to 4),first.groupingBy { it.first }.eachCount())
        assertFalse(first.any { it.second.categoryId in setOf("1","90") })
        assertTrue(model.state.value.hasMore)
        compose.runOnIdle { model.loadMore() }
        compose.waitUntil(5000) { !model.state.value.loading && client.requests.count { it.second.categoryId != null } == 16 }
        assertTrue(client.maximumActive <= 4)
        assertTrue(model.state.value.providers.any { it.page == 0 })
    }

    @Test fun lateMetadataRetainsLoadedWorksAndDoesNotRestartCompletedPages() {
        val gate = CompletableDeferred<Unit>(); val client = Client(slowB = gate); val model = model(Repository(),client)
        try {
            compose.waitUntil(5000) { model.categories.value.loading && model.categories.value.groups.any { it.kind == VideoKind.MOVIE } }
            awaitLoaded(model)
            compose.runOnIdle { model.selectCategory("category:MOVIE") }
            awaitLoaded(model,"category:MOVIE")
            val oldIds = model.state.value.works.map { it.id }.toSet()
            val oldRequests = client.requests.filter { it.first == "a" }.toList()
            gate.complete(Unit)
            compose.waitUntil(5000) { !model.categories.value.loading && !model.state.value.loading && model.state.value.providers.size == 5 }
            assertEquals(oldRequests,client.requests.filter { it.first == "a" })
            assertTrue(model.state.value.works.map { it.id }.containsAll(oldIds))
            assertEquals(2,model.state.value.works.first().providerCount)
        } finally { gate.complete(Unit) }
    }

}
