package me.lsong.mytv.playback

import android.view.SurfaceView
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import me.lsong.mytv.data.preferences.AppPreferences
import me.lsong.mytv.domain.playback.PlaybackRequest
import org.junit.Assert.*
import org.junit.Test

class PlayerWatchdogTest {
    private class Fake(scope: CoroutineScope) : PlayerEngine(scope) {
        override val positionMs = 0L
        override val durationMs = 0L
        override fun prepare(request: PlaybackRequest) { resetMonitoring(); triggerPrepared() }
        override fun play() { triggerResumed() }
        override fun pause() { triggerPaused() }
        override fun seekTo(positionMs: Long) {}
        override fun setVideoSurfaceView(surfaceView: SurfaceView) {}
        fun position(value: Long, playing: Boolean) { triggerCurrentPosition(value,playing) }
    }

    @Test fun resumedPlaybackWithUnchangedPositionStillTriggersStallWatchdog() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AppPreferences.init(context)
        val oldTimeout = AppPreferences.videoPlayerLoadTimeout
        AppPreferences.videoPlayerLoadTimeout = 1000L
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        try {
            withContext(Dispatchers.Main) {
                val player = Fake(scope)
                var stalls = 0
                player.initialize()
                player.onCutoff { stalls++ }
                player.position(1234L,true)
                player.pause()
                player.play()
                player.position(1234L,true)
                delay(1250L)
                assertEquals(1,stalls)
                player.release()
            }
        } finally {
            scope.cancel()
            AppPreferences.videoPlayerLoadTimeout = oldTimeout
        }
    }

    @Test fun aPausedInitialLoadDoesNotTimeOutUntilResumed() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AppPreferences.init(context)
        val oldTimeout = AppPreferences.videoPlayerLoadTimeout
        AppPreferences.videoPlayerLoadTimeout = 1000L
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        try {
            withContext(Dispatchers.Main) {
                val player = Fake(scope)
                var failures = 0
                player.initialize()
                player.onError { failures++ }
                player.prepare(PlaybackRequest("https://example.com/paused",playWhenReady = false))
                player.pause()
                delay(1150L)
                assertEquals(0,failures)
                player.play()
                delay(1250L)
                assertEquals(1,failures)
                player.release()
            }
        } finally {
            scope.cancel()
            AppPreferences.videoPlayerLoadTimeout = oldTimeout
        }
    }
}
