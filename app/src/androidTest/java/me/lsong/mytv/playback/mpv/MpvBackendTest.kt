package me.lsong.mytv.playback.mpv

import me.lsong.mytv.playback.mpv.MpvPlayerEngine

import android.os.Build
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import me.lsong.mytv.playback.*
import me.lsong.mytv.domain.playback.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

@androidx.test.filters.SdkSuppress(minSdkVersion = 26)
class MpvBackendTest {
    @get:Rule val compose = createComposeRule()
    @Test fun nativePlayerRendersAndAdvancesOnATvSurface() {
        assumeTrue(Build.VERSION.SDK_INT >= MPV_MIN_SDK && Build.SUPPORTED_ABIS.any { it in listOf("arm64-v8a", "armeabi-v7a") })
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = java.io.File(instrumentation.targetContext.cacheDir, "player-smoke.mp4")
        instrumentation.context.assets.open("player-smoke.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        lateinit var player: MpvPlayerEngine
        var ready = false
        compose.runOnIdle { player = MpvPlayerEngine(instrumentation.targetContext, scope); player.initialize(); player.onReady { ready = true } }
        try {
            compose.setContent { AndroidView(factory = { SurfaceView(it).also(player::setVideoSurfaceView) }, modifier = Modifier.fillMaxSize()) }
            compose.runOnIdle { player.prepare(PlaybackRequest(file.toURI().toString())); player.play() }
            compose.waitUntil(20_000) { ready && compose.runOnIdle { player.positionMs > 1_000 } }
            compose.runOnIdle { player.setDisplayAspectRatio(4f/3f); player.pause(); player.seekTo(2_000); player.speed = 1.25f; player.play() }
            compose.waitUntil(10_000) { compose.runOnIdle { player.positionMs > 2_500 } }
        } finally { compose.runOnIdle { player.release() }; scope.cancel(); file.delete() }
    }
}
