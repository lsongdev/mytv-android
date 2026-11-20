package me.lsong.mytv.ui.browser

import android.view.SurfaceView
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import me.lsong.mytv.app.PlaybackCoordinator
import me.lsong.mytv.data.live.LiveCatalogRepository
import me.lsong.mytv.data.playback.ResourceProbeRepository
import me.lsong.mytv.data.video.VideoCatalogRepository
import me.lsong.mytv.domain.identity.InMemoryCatalogIdentityStore
import me.lsong.mytv.domain.live.*
import me.lsong.mytv.domain.playback.*
import me.lsong.mytv.domain.provider.*
import me.lsong.mytv.domain.video.*
import me.lsong.mytv.playback.PlaybackState
import me.lsong.mytv.playback.PlayerEngine
import me.lsong.mytv.ui.navigation.Section
import me.lsong.mytv.ui.theme.TvTheme
import me.lsong.mytv.ui.video.VideoLibraryViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PlaybackMenuTest {
    @get:Rule val compose = createComposeRule()

    @Test fun liveMenuReopensOnPlayingChannelInsteadOfBrowsedChannelOrSettings() {
        val catalog = mergeLiveCatalogs(menuPreviewPlaylists())
        val playing = catalog.channels.first()
        val state = ContentDrawerState(playing)
        var shown by mutableStateOf(true)
        compose.setContent { TvTheme { if (shown) ContentDrawer(catalog,playing,state,{}, { shown = false }, {}) } }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("drawer-entry:${catalog.channels[1].id}").assertIsFocused()
        compose.runOnIdle { shown = false }
        compose.runOnIdle { state.section = Section.SETTINGS; state.focusSettings = true; state.revealLive(catalog,playing); shown = true }
        compose.onNodeWithTag("drawer-entry:${playing.id}").assertIsFocused()
        assertEquals(Section.LIVE,state.section)
        assertFalse(state.focusSettings)
        assertEquals(2,state.column)
    }

    @Test fun liveMenuKeepsAValidGroupAndReplacesAGroupThatDoesNotContainTheChannel() {
        val a = LiveChannel("a",title = "频道 A")
        val b = LiveChannel("b",title = "频道 B")
        val catalog = LiveCatalog(listOf(ChannelGroup("group:first","第一组",listOf(a)),ChannelGroup("group:second","第二组",listOf(a,b))))
        val state = ContentDrawerState(null)
        compose.runOnIdle {
            state.groupIds["live"] = "group:second"
            state.revealLive(catalog,a)
            assertEquals("group:second",state.groupIds["live"])
            state.groupIds["live"] = "group:first"
            state.revealLive(catalog,b)
            assertEquals("group:second",state.groupIds["live"])
            assertEquals("b",state.channelIds["live/group:second"])
        }
    }

    private class Configuration : ProviderConfigRepository {
        override val providers = MutableStateFlow(listOf(ProviderConfig("source","测试源","https://example.com/api",ProviderKind.MAC_CMS)))
        override fun save(source: ProviderConfig) {}
        override fun delete(id: String) {}
        override fun replace(providers: List<ProviderConfig>) {}
    }
    private class Client : VideoCatalogProvider {
        var detailsCalls = 0
        override suspend fun categories(source: ProviderConfig,force: Boolean) = emptyList<ProviderVideoCategory>()
        override suspend fun page(source: ProviderConfig,query: VideoCatalogQuery,force: Boolean) = VideoPage(emptyList(),1,1)
        override suspend fun details(source: ProviderConfig,remoteId: String): ProviderVideoRecord { detailsCalls++; error("Menu reveal must use playback metadata") }
    }
    private fun selection(): VideoPlaybackSelection {
        val work = VideoWork("work",listOf(ProviderVideoRecord(ProviderRecordRef("source","film"),"测试源","测试影片")))
        val parts = (1..3).map { episode -> VideoPart("e$episode","第${episode}集",episode.toLong(),(1..2).map { line ->
            PlaybackResource("https://example.com/$episode-$line.m3u8",ProviderRecordRef("source","$episode-$line"),"线路$line")
        }) }
        return VideoPlaybackSelection(VideoDetails(work,parts),"e2",parts[1].resources[1].id)
    }

    @Test fun videoMenuUsesPlayingEpisodeAndRouteWithoutFetchingDetailsOrPlayingAnything() {
        val client = Client(); val repository = VideoCatalogRepository(Configuration(),client,InMemoryCatalogIdentityStore())
        lateinit var model: VideoLibraryViewModel
        compose.runOnIdle { model = VideoLibraryViewModel(repository,ResourceProbeRepository(measure = { ResourceProbe.Measured(1,1024) })) }
        compose.waitUntil(5000) { !model.state.value.loading && !model.categories.value.loading }
        val playing = selection(); val drawer = ContentDrawerState(null)
        compose.runOnIdle { drawer.rememberVideoPlayback(playing,model.browseKey); model.search("其他影片") }
        compose.waitUntil(5000) { model.state.value.query == "其他影片" && !model.state.value.loading }
        val requests = mutableListOf<VideoPlaybackSelection>()
        compose.runOnIdle { model.revealPlayback(playing,drawer.revealVideo(playing)) }
        compose.setContent { TvTheme { ContentDrawer(LiveCatalog(),null,drawer,{}, {}, {},videoLibrary = model,playingVideo = playing,onPlayVideo = requests::add) } }
        compose.onNodeWithTag("vod-play").assertIsFocused().assertTextContains("继续播放 第2集")
        assertEquals("e2",model.state.value.selectedPartId)
        assertEquals(playing.resourceId,model.selection()!!.resourceId)
        assertTrue(requests.isEmpty())
        assertEquals(0,client.detailsCalls)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("vod-episode:e2").assertIsFocused()
        assertEquals(playing.resourceId,model.selection()!!.resourceId)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.DirectionCenter) }
        assertEquals(playing,requests.single())
        repository.close()
    }

    @Test fun continuingTheCurrentVideoResumesWithoutPreparingTheStreamAgain() {
        val engine = Engine(); val player = PlaybackState(engine).apply { initialize() }
        val coordinator = PlaybackCoordinator(player,LiveCatalogRepository())
        val playing = selection()
        coordinator.playVideo(playing)
        engine.position = 42_000
        player.pause()
        coordinator.playVideo(playing)
        assertEquals(1,engine.requests.size)
        assertEquals(42_000L,engine.position)
        assertFalse(player.isPaused)
        player.release()
    }
    private class Engine : PlayerEngine(CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)) {
        val requests = mutableListOf<PlaybackRequest>()
        var position = 0L
        override val positionMs get() = position
        override val durationMs get() = 100_000L
        override fun prepare(request: PlaybackRequest) { requests += request; position = request.startPositionMs }
        override fun seekTo(positionMs: Long) { position = positionMs }
        override fun setVideoSurfaceView(surfaceView: SurfaceView) {}
        override fun play() {}
        override fun pause() {}
    }
}
