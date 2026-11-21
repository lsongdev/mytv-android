package me.lsong.mytv.data.playback

import android.content.Context
import android.view.SurfaceView
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import me.lsong.mytv.app.PlaybackCoordinator
import me.lsong.mytv.data.live.LiveCatalogRepository
import me.lsong.mytv.data.preferences.AppPreferences
import me.lsong.mytv.domain.playback.*
import me.lsong.mytv.domain.provider.*
import me.lsong.mytv.domain.video.*
import me.lsong.mytv.playback.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PlaybackSessionTest {
    private val ref = ProviderRecordRef("a","movie")
    private val a = PlaybackResource("https://example.com/a.m3u8",ref,"A",headers = mapOf("Referer" to "https://example.com/"))
    private val b = a.copy(uri = "https://example.com/b.m3u8",origin = ProviderRecordRef("b","movie"),providerName = "B")
    private val work = VideoWork("work",listOf(ProviderVideoRecord(ref,"A","影片","2024",VideoKind.SERIES,summary = "简介")))
    private val selection = VideoPlaybackSelection(VideoDetails(work,listOf(VideoPart("s1e3","第3集",3,listOf(a,b)),VideoPart("s1e4","第4集",4,listOf(a)))),"s1e3",a.id)
    private fun provider(id: String, enabled: Boolean = true) = ProviderConfig(id,"源", "https://example.com/api",ProviderKind.MAC_CMS,enabled)
    private class Player(scope: CoroutineScope, val readyImmediately: Boolean = true) : PlayerEngine(scope) {
        val requests = mutableListOf<PlaybackRequest>()
        var plays = 0; var pauses = 0
        override var positionMs = 0L
        override val durationMs = 120_000L
        override fun prepare(request: PlaybackRequest) { requests += request; positionMs = if (readyImmediately) request.startPositionMs else 0; if (readyImmediately) triggerReady() }
        override fun seekTo(positionMs: Long) { this.positionMs = positionMs }
        override fun play() { plays++ }
        override fun pause() { pauses++ }
        override fun setVideoSurfaceView(surfaceView: SurfaceView) {}
        fun fail() = triggerError(PlaybackException.UNSUPPORTED_TYPE)
        fun stall() = triggerCutoff()
    }
    private fun fixture(block: suspend CoroutineScope.(PlaybackSessionStore,android.content.SharedPreferences) -> Unit) = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("session-test-${UUID.randomUUID()}",Context.MODE_PRIVATE)
        val hosts = AppPreferences.iptvPlayableHostList; val index = AppPreferences.iptvLastIptvIdx
        try { block(PlaybackSessionStore(prefs),prefs) }
        finally { prefs.edit().clear().commit(); AppPreferences.iptvPlayableHostList = hosts; AppPreferences.iptvLastIptvIdx = index }
    }
    @Test fun compactSessionSurvivesAStoreRecreationWithHeadersProgressSpeedAndPause() = fixture { store,prefs ->
        store.save(PlaybackSession(video = selection,positionMs = 65_000,durationMs = 120_000,paused = true,speed = 1.5f)).join()
        val saved = PlaybackSessionStore(prefs).load()!!
        assertEquals(1,saved.video!!.details.parts.size)
        assertEquals("s1e3",saved.video.partId); assertEquals(a.id,saved.video.resource.id)
        assertEquals(a.headers,saved.video.resource.headers)
        withContext(Dispatchers.Main) {
            val player = Player(this); val state = PlaybackState(player); state.initialize()
            val coordinator = PlaybackCoordinator(state,LiveCatalogRepository())
            assertTrue(player.requests.isEmpty())
            assertTrue(coordinator.restore(saved))
            assertEquals(65_000L,player.requests.single().startPositionMs)
            assertFalse(player.requests.single().playWhenReady)
            assertEquals(0,player.plays); assertTrue(state.isPaused); assertEquals(1.5f,player.speed)
            assertEquals(65_000L,coordinator.snapshot()!!.positionMs)
            state.release()
            assertEquals(65_000L,coordinator.snapshot()!!.positionMs)
        }
    }
    @Test fun disabledResourcesAreExcludedAndTheAvailableLineKeepsProgress() = fixture { _,_ ->
        val saved = PlaybackSession(video = selection,positionMs = 42_000,paused = true)
        val available = saved.available(listOf(provider("a",false),provider("b")))!!
        assertEquals(b.id,available.video!!.resource.id)
        assertNull(saved.available(listOf(provider("a",false),provider("b",false))))
        withContext(Dispatchers.Main) {
            val player = Player(this); val state = PlaybackState(player); state.initialize()
            assertTrue(PlaybackCoordinator(state,LiveCatalogRepository()).restore(available))
            assertEquals(b.uri,player.requests.single().uri); assertEquals(42_000L,player.requests.single().startPositionMs)
            state.release()
        }
    }
    @Test fun bufferingAndFailedRestoredUrlsNeverOverwriteTheSavedPositionWithZero() = fixture { _,_ ->
        withContext(Dispatchers.Main) {
            val player = Player(this,false); val state = PlaybackState(player); state.initialize()
            val coordinator = PlaybackCoordinator(state,LiveCatalogRepository())
            coordinator.restore(PlaybackSession(video = selection,positionMs = 78_000,durationMs = 120_000,paused = true))
            assertEquals(0L,player.positionMs)
            assertEquals(78_000L,coordinator.snapshot()!!.positionMs)
            player.fail()
            assertEquals(b.uri,player.requests.last().uri)
            assertEquals(78_000L,player.requests.last().startPositionMs)
            assertFalse(player.requests.last().playWhenReady)
            state.release()
        }
    }
    @Test fun liveRecoveryWrapsAllLinesAndStopsAfterExhaustion() = fixture { _,_ ->
        withContext(Dispatchers.Main) {
            val c = a.copy(uri = "https://example.com/c.m3u8", origin = ProviderRecordRef("c","movie"))
            val player = Player(this,false); val state = PlaybackState(player); state.initialize()
            val coordinator = PlaybackCoordinator(state,LiveCatalogRepository())
            assertTrue(coordinator.restore(PlaybackSession(
                live = SavedLiveChannel("channel","CCTV","综合",listOf(a,b,c)),resourceId = b.id)))
            player.fail()
            assertEquals(c.uri,player.requests.last().uri)
            player.fail()
            assertEquals(a.uri,player.requests.last().uri)
            player.fail()
            assertEquals(3,player.requests.size)
            assertNotNull(state.error)
            state.release()
        }
    }
    @Test fun stalledVideoRetriesThenFallsBackWithoutLosingPosition() = fixture { _,_ ->
        withContext(Dispatchers.Main) {
            val player = Player(this); val state = PlaybackState(player); state.initialize()
            val coordinator = PlaybackCoordinator(state,LiveCatalogRepository())
            assertTrue(coordinator.restore(PlaybackSession(video = selection,positionMs = 45_000)))
            player.stall()
            assertEquals(a.uri,player.requests.last().uri)
            assertEquals(45_000L,player.requests.last().startPositionMs)
            player.stall()
            assertEquals(b.uri,player.requests.last().uri)
            player.stall()
            assertEquals(b.uri,player.requests.last().uri)
            player.stall()
            assertEquals("VIDEO_STALLED",state.error)
            assertTrue(state.isPaused)
            assertEquals(4,player.requests.size)
            state.release()
        }
    }
    @Test fun liveRestoresChannelAndLineAtTheLiveEdge() = fixture { _,_ ->
        withContext(Dispatchers.Main) {
            val player = Player(this); val state = PlaybackState(player); state.initialize()
            val coordinator = PlaybackCoordinator(state,LiveCatalogRepository())
            assertTrue(coordinator.restore(PlaybackSession(live = SavedLiveChannel("channel","CCTV1","综合",listOf(a,b)),resourceId = b.id,positionMs = 900_000,paused = true)))
            assertEquals("channel",coordinator.currentChannel.id); assertEquals(1,coordinator.currentResourceIndex)
            assertTrue(player.requests.single().live); assertEquals(0L,player.requests.single().startPositionMs)
            assertEquals(b.id,coordinator.snapshot()!!.resourceId)
            state.release()
        }
    }
    @Test fun latestPauseWinsRapidWritesAndMalformedDataIsIgnored() = fixture { store,prefs ->
        val jobs = (1..20).map { store.save(PlaybackSession(video = selection,positionMs = it*1000L,paused = it == 20)) }
        jobs.joinAll()
        val loaded = PlaybackSessionStore(prefs).load()!!
        assertEquals(20_000L,loaded.positionMs); assertTrue(loaded.paused)
        prefs.edit().putString("PLAYBACK_SESSION_V1","invalid old data").commit()
        assertNull(PlaybackSessionStore(prefs).load())
        val missingPart = PlaybackSession(video = selection.copy(partId = "removed"))
        assertFalse(missingPart.isValid())
        assertNull(missingPart.available(listOf(provider("a"))))
    }
}
