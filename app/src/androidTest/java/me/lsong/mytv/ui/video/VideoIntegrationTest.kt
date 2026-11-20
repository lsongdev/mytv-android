package me.lsong.mytv.ui.video

import me.lsong.mytv.ui.navigation.Section

import me.lsong.mytv.domain.live.LiveCatalog
import me.lsong.mytv.ui.theme.TvTheme

import me.lsong.mytv.domain.video.VideoCatalogProvider

import me.lsong.mytv.domain.video.ProviderPlaybackLine

import me.lsong.mytv.domain.video.ProviderVideoPartRecord
import me.lsong.mytv.domain.video.ProviderVideoRecord
import me.lsong.mytv.domain.video.VideoDetails
import me.lsong.mytv.domain.video.VideoKind
import me.lsong.mytv.domain.video.VideoPage
import me.lsong.mytv.domain.video.VideoPart
import me.lsong.mytv.domain.video.VideoPlaybackSelection
import me.lsong.mytv.domain.video.VideoWork

import android.view.SurfaceView
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import me.lsong.mytv.domain.provider.*
import me.lsong.mytv.data.preferences.*
import me.lsong.mytv.data.live.LiveCatalogRepository
import me.lsong.mytv.app.PlaybackCoordinator
import me.lsong.mytv.ui.browser.*
import me.lsong.mytv.playback.*
import me.lsong.mytv.domain.playback.*

import me.lsong.mytv.ui.video.VideoLibraryViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import me.lsong.mytv.data.playback.ResourceProbeRepository
import me.lsong.mytv.domain.identity.InMemoryCatalogIdentityStore

@OptIn(ExperimentalTestApi::class)
class VideoIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private class Repository : ProviderConfigRepository {
        override val providers = MutableStateFlow(listOf(
            ProviderConfig("a", "来源 A", "https://a.example/vod", ProviderKind.MAC_CMS),
            ProviderConfig("b", "来源 B", "https://b.example/vod", ProviderKind.MAC_CMS),
            ProviderConfig("failed", "失效源", "https://failed.example/vod", ProviderKind.MAC_CMS),
            ProviderConfig("disabled", "停用源", "https://disabled.example/vod", ProviderKind.MAC_CMS, false),
        ))
        override fun save(source: ProviderConfig) { providers.value = providers.value.filterNot { it.id == source.id } + source }
        override fun delete(id: String) { providers.value = providers.value.filterNot { it.id == id } }
        override fun replace(providers: List<ProviderConfig>) { this.providers.value = providers }
    }
    private class Client(private val hideBFromLatest: Boolean = false, private val ignoreBQuery: Boolean = false) : VideoCatalogProvider {
        val requested = mutableListOf<String>()
        override suspend fun page(source: ProviderConfig, query: String, page: Int, force: Boolean): VideoPage {
            requested += source.id
            delay(if (query == "old") 100 else 5)
            if (source.id == "failed") error("offline")
            return VideoPage(listOf(item(source).copy(title = (if (ignoreBQuery && source.id == "b") "无关影片" else query).ifBlank { if (hideBFromLatest && source.id == "b") "另一部剧" else "聚合测试剧" })), page, 1)
        }
        override suspend fun details(source: ProviderConfig, remoteId: String): ProviderVideoRecord {
            delay(5)
            return item(source).copy(lines = listOf(ProviderPlaybackLine("${source.id}:line", "线路 1",
                (if (source.id == "a") listOf("第01集") else listOf("EP1", "EP2")).mapIndexed { i, label ->
                    ProviderVideoPartRecord(label, PlaybackResource("https://${source.id}.example/$i.m3u8", ProviderRecordRef(source.id, "1"), source.name))
                })))
        }
        private fun item(source: ProviderConfig) = ProviderVideoRecord(ProviderRecordRef(source.id, "1"), source.name, "聚合测试剧", "2024", VideoKind.SERIES, "国产剧", "中国大陆")
    }
    private fun createModel(client: Client = Client()): VideoLibraryViewModel {
        lateinit var model: VideoLibraryViewModel
        compose.runOnIdle { model = VideoLibraryViewModel(Repository(), client, InMemoryCatalogIdentityStore(), ResourceProbeRepository(measure = { ResourceProbe.Measured(240, 2 * 1024 * 1024) })) }
        return model
    }
    private fun click(tag: String) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) { it() }; compose.waitForIdle() }

    @Test fun failedAndDisabledProvidersDoNotBlockMergedResults() {
        val client = Client(); val model = createModel(client)
        compose.runOnIdle { model.activate() }
        compose.waitUntil(10_000) { model.state.value.providers.size == 3 && !model.state.value.loading }
        assertEquals(1, model.state.value.works.size)
        assertEquals(2, model.state.value.works.single().providerCount)
        assertEquals(1, model.state.value.providers.count { it.error != null })
        assertFalse(client.requested.contains("disabled"))
        compose.runOnIdle { model.refresh("old"); model.refresh("new") }
        compose.waitUntil(10_000) { !model.state.value.loading && model.state.value.works.firstOrNull()?.title == "new" }
        assertTrue(model.state.value.works.all { it.title == "new" })
    }

    @Test fun searchExcludesUnrelatedResultsFromAProviderThatIgnoresTheQuery() {
        val model = createModel(Client(ignoreBQuery = true))
        compose.runOnIdle { model.activate() }
        compose.waitUntil(10_000) { model.state.value.providers.size == 3 && !model.state.value.loading }
        compose.runOnIdle { model.refresh("目标影片") }
        compose.waitUntil(10_000) { !model.state.value.loading }
        assertEquals(listOf("目标影片"), model.state.value.works.map { it.title })
        assertEquals(1, model.state.value.works.single().providerCount)
    }

    @Test fun openingAWorkFindsMatchingResourcesOutsideTheOtherProvidersLatestPage() {
        val model = createModel(Client(hideBFromLatest = true))
        compose.runOnIdle { model.activate() }
        compose.waitUntil(10_000) { model.state.value.providers.size == 3 && !model.state.value.loading }
        val work = model.state.value.works.first { it.title == "聚合测试剧" }
        assertEquals(1, work.providerCount)
        compose.runOnIdle { model.open(work) }
        compose.waitUntil(10_000) { !model.state.value.detailsLoading }
        val details = model.state.value.details!!
        assertEquals(2, details.work.providerCount)
        assertEquals(2, details.parts.first().resources.size)
        assertTrue(details.parts.first().resources.any { it.origin.providerId == "b" })
    }

    @Test fun menuOpensMergedWorkAndPlaysTheChosenSourceForTheChosenEpisode() {
        val client = Client(); val model = createModel(client)
        val drawer = ContentDrawerState(null)
        val played = mutableListOf<VideoPlaybackSelection>()
        var closed = false
        compose.setContent { TvTheme { ContentDrawer(LiveCatalog(), null, drawer, {}, { closed = true }, {}, videoLibrary = model, onPlayVideo = played::add) } }
        compose.waitUntil(10_000) { model.configured.value.size == 4 }
        click("drawer-entry:videos")
        compose.waitUntil(10_000) { !model.state.value.loading && model.state.value.works.isNotEmpty() }
        val work = model.state.value.works.single()
        click("vod-work:${work.id}")
        compose.waitUntil(10_000) { !model.state.value.detailsLoading && model.state.value.details?.parts?.size == 2 }
        assertTrue(played.isEmpty())
        val details = model.state.value.details!!
        assertEquals(2, details.parts.first().resources.size)
        val candidate = details.parts.first().resources.first { it.origin.providerId == "b" }
        click("vod-candidate:${candidate.id}")
        assertEquals("b", played.single().resource.origin.providerId)
        assertEquals("s1e1", played.single().partId)
        Espresso.pressBack()
        compose.runOnIdle { assertTrue(closed) }
    }

    @Test fun remoteNavigationRestoresPosterFocusAfterLeavingDetails() {
        val model = createModel()
        val drawer = ContentDrawerState(null).apply { section = Section.VIDEOS; column = 2 }
        val played = mutableListOf<VideoPlaybackSelection>()
        var closed = false
        compose.setContent { TvTheme { ContentDrawer(LiveCatalog(), null, drawer, {}, { closed = true }, {}, videoLibrary = model, onPlayVideo = played::add) } }
        compose.waitUntil(10_000) { !model.state.value.loading && model.state.value.works.isNotEmpty() }
        val work = model.state.value.works.single()
        compose.onNodeWithTag("vod-work:${work.id}").assertIsFocused()
        fun press(key: Key) { compose.onRoot().performKeyInput { pressKey(key) }; compose.waitForIdle() }
        press(Key.DirectionLeft)
        compose.onNodeWithTag("drawer-entry:latest").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag("vod-work:${work.id}").assertIsFocused()
        press(Key.DirectionCenter)
        compose.waitUntil(10_000) { !model.state.value.detailsLoading }
        compose.onNodeWithTag("vod-play").assertIsFocused()
        compose.waitUntil(10_000) { model.resourceProbes.value.values.any { it is ResourceProbe.Measured } }
        compose.onAllNodesWithText("240 ms").assertCountEquals(2)
        press(Key.DirectionDown)
        compose.onNodeWithTag("vod-episode:s1e1").assertIsFocused()
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        val secondLine = model.state.value.details!!.parts.first().resources[1]
        compose.onNodeWithTag("vod-candidate:${secondLine.id}").assertIsFocused()
        compose.onRoot().performKeyInput {
            keyDown(Key.DirectionLeft); advanceEventTime(900); keyUp(Key.DirectionLeft)
        }
        compose.waitForIdle()
        assertEquals(2, drawer.column)
        assertNotNull(model.state.value.details)
        press(Key.DirectionRight)
        compose.onNodeWithTag("vod-candidate:${secondLine.id}").assertIsFocused()
        press(Key.DirectionUp)
        press(Key.DirectionDown)
        compose.onNodeWithTag("vod-candidate:${secondLine.id}").assertIsFocused()
        press(Key.DirectionUp)
        press(Key.DirectionRight)
        compose.onNodeWithTag("vod-episode:s1e2").assertIsFocused()
        assertEquals("s1e2", model.state.value.selectedPartId)
        press(Key.DirectionDown)
        val candidate = model.state.value.details!!.parts.last().resources.single()
        compose.onNodeWithTag("vod-candidate:${candidate.id}").assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithTag("vod-episode:s1e2").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("vod-candidate:${candidate.id}").assertIsFocused()
        repeat(3) { press(Key.DirectionDown) }
        compose.onNodeWithTag("drawer-settings").assertIsNotFocused()
        assertEquals(Section.VIDEOS, drawer.section)
        assertTrue(played.isEmpty())
        press(Key.DirectionCenter)
        assertEquals("s1e2", played.single().partId)
        press(Key.DirectionLeft)
        press(Key.DirectionCenter)
        compose.onNodeWithTag("vod-work:${work.id}").assertIsFocused()
        Espresso.pressBack()
        compose.runOnIdle { assertTrue(closed) }
    }

    @Test fun failedResourceSwitchesOnlyWithinCurrentEpisodeAndPreservesPosition() {
        val player = Player()
        val state = PlaybackState(player).apply { initialize() }
        val main = PlaybackCoordinator(state, LiveCatalogRepository())
        val resources = listOf("a", "b", "c").map { PlaybackResource("https://example.com/$it.m3u8", ProviderRecordRef(it, "1"), it) }
        val work = VideoWork("work", listOf(ProviderVideoRecord(ProviderRecordRef("a", "1"), "A", "测试剧")))
        val details = VideoDetails(work, listOf(VideoPart("e1", "第1集", 1, resources), VideoPart("e2", "第2集", 2, listOf(resources[0].copy(uri = "https://example.com/a2.m3u8")))))
        main.playVideo(VideoPlaybackSelection(details, "e1", resources[0].id))
        player.position = 42_000
        player.fail()
        assertEquals(resources[1].id, main.videoSelection!!.resourceId)
        assertEquals(42_000L, player.requests.last().startPositionMs)
        // A replacement may fail before it has loaded the requested position.
        player.position = 0
        state.pause()
        player.fail()
        assertEquals(resources[2].id, main.videoSelection!!.resourceId)
        assertEquals(42_000L, player.requests.last().startPositionMs)
        assertFalse(player.requests.last().playWhenReady)
        player.fail()
        assertEquals(3, player.requests.size)
        assertEquals("e1", main.videoSelection!!.partId)
        main.playVideo(VideoPlaybackSelection(details, "e2", details.parts[1].resources[0].id))
        assertEquals(0L, player.requests.last().startPositionMs)
        state.release()
    }

    @Test fun aUserPauseIsPreservedWhenTheActivityResumes() {
        val player = Player(); val state = PlaybackState(player)
        state.prepare(PlaybackRequest("https://example.com/a.m3u8"))
        state.pause(); val plays = player.plays
        state.onHostStopped(); state.onHostResumed()
        assertEquals(plays, player.plays)
        state.play(); state.onHostStopped(); state.onHostResumed()
        assertEquals(plays + 2, player.plays)
        state.release()
    }

    private class Player : PlayerEngine(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) {
        val requests = mutableListOf<PlaybackRequest>(); var position = 0L; var plays = 0
        override val positionMs get() = position
        override val durationMs get() = 100_000L
        override fun prepare(request: PlaybackRequest) { requests += request; position = request.startPositionMs }
        override fun play() { plays++ }
        override fun pause() {}
        override fun seekTo(positionMs: Long) { position = positionMs }
        override fun setVideoSurfaceView(surfaceView: SurfaceView) {}
        fun fail() = triggerError(PlaybackException("OFFLINE", 1))
    }
}
