package me.lsong.mytv.playback.ui

import android.view.SurfaceView
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import kotlinx.coroutines.*
import me.lsong.mytv.playback.*
import me.lsong.mytv.domain.playback.*
import me.lsong.mytv.ui.theme.TvTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PlayerControlsTest {
    @get:Rule val compose = createComposeRule()
    private fun press(key: Key) { compose.onRoot().performKeyInput { pressKey(key) }; compose.waitForIdle() }

    @Test fun progressTogglesPlaybackAndUpReachesCompactSpeed() {
        val player = Player()
        val state = PlaybackState(player)
        var menu = false; var closed = false; var speeds = 0
        compose.setContent { TvTheme { PlayerControls(state,"测试影片",false,1f,
            { if (state.isPaused) state.play() else state.pause() }, { speeds++ }, { menu = true }, { closed = true }) } }
        compose.onNodeWithTag("player-progress").assertIsFocused()
        compose.onNodeWithTag("player-pause").assertDoesNotExist()
        compose.onNodeWithTag("player-menu").assertDoesNotExist()
        press(Key.DirectionRight)
        assertEquals(40_000L, player.positionMs)
        press(Key.DirectionCenter)
        assertTrue(state.isPaused)
        compose.onNodeWithTag("player-progress").assertIsFocused()
        press(Key.DirectionCenter)
        assertFalse(state.isPaused)
        compose.onNodeWithTag("player-end-time").assertIsDisplayed()
        press(Key.DirectionUp)
        compose.onNodeWithTag("player-speed").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(1, speeds)
        press(Key.DirectionDown)
        compose.onNodeWithTag("player-progress").assertIsFocused()
        press(Key.Menu)
        assertTrue(menu)
        assertFalse(closed)
        state.release()
    }

    @Test fun liveProgressDoesNotSeekAndSharesFocusWithItsIndicator() {
        val player = Player()
        val state = PlaybackState(player)
        var switches = 0
        compose.setContent { TvTheme { PlayerControls(state,"直播频道",true,1f,
            { if (state.isPaused) state.play() else state.pause() }, {}, {}, {},
            resourceLabel = "家庭源 · 1/2", onResource = { switches++ }) } }
        compose.onNodeWithTag("player-progress").assertIsFocused()
        compose.onNodeWithText("LIVE").assertIsDisplayed()
        compose.onAllNodes(isFocusable()).assertCountEquals(2)
        press(Key.DirectionRight)
        press(Key.DirectionLeft)
        assertEquals(30_000L, player.positionMs)
        press(Key.DirectionCenter)
        assertTrue(state.isPaused)
        compose.onNodeWithTag("player-end-time").assertDoesNotExist()
        press(Key.DirectionUp)
        compose.onNodeWithTag("player-resource").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(1, switches)
        press(Key.DirectionDown)
        compose.onNodeWithTag("player-progress").assertIsFocused()
        state.release()
    }

    @Test fun backHidesControlsAndSingleResourceLiveKeepsProgressFocus() {
        val state = PlaybackState(Player())
        var menu = false; var closed = false
        compose.setContent { TvTheme { PlayerControls(state,"直播频道",true,1f, {}, {}, { menu = true }, { closed = true }) } }
        press(Key.DirectionDown)
        compose.onNodeWithTag("player-progress").assertIsFocused()
        Espresso.pressBack()
        compose.runOnIdle { assertFalse(menu); assertTrue(closed) }
        state.release()
    }

    private class Player : PlayerEngine(CoroutineScope(SupervisorJob() + Dispatchers.Main)) {
        override var positionMs = 30_000L
        override val durationMs = 120_000L
        override fun prepare(request: PlaybackRequest) {}
        override fun seekTo(positionMs: Long) { this.positionMs = positionMs }
        override fun play() {}
        override fun pause() {}
        override fun setVideoSurfaceView(surfaceView: SurfaceView) {}
    }
}
