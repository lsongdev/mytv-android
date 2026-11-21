package me.lsong.mytv.playback

import me.lsong.mytv.domain.playback.PlaybackRequest

import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * 播放器状态
 */
@Stable
class PlaybackState(
    private val instance: PlayerEngine,
    private val defaultAspectRatioProvider: () -> Float? = { null },
) {
    /** 视频宽高比 */
    var aspectRatio by mutableFloatStateOf(16f / 9f)

    /** 错误 */
    var error by mutableStateOf<String?>(null)

    /** 元数据 */
    var metadata by mutableStateOf(PlayerEngine.Metadata())

    var isBuffering by mutableStateOf(false)
        private set

    var isReleased: Boolean = false
        private set

    private var playRequested by mutableStateOf(true)
    val isPaused get() = !playRequested

    fun prepare(request: PlaybackRequest) {
        error = null
        isBuffering = true
        playRequested = request.playWhenReady
        instance.prepare(request)
        if (request.playWhenReady) instance.play() else instance.pause()
    }

    /** The engine can be stalled without producing an explicit player error. */
    fun reportFailure(reason: String) {
        error = reason
        isBuffering = false
        playRequested = false
        instance.pause()
    }

    fun setSpeed(value: Float) { instance.speed = value }
    fun setDisplayAspectRatio(ratio: Float?) = instance.setDisplayAspectRatio(ratio)

    val positionMs: Long get() = instance.positionMs
    val durationMs: Long get() = instance.durationMs
    fun seekTo(positionMs: Long) = instance.seekTo(positionMs.coerceAtLeast(0))

    fun play() {
        playRequested = true
        instance.play()
    }

    fun pause() {
        playRequested = false
        instance.pause()
    }

    internal fun onHostStopped() { instance.pause() }
    internal fun onHostResumed() { if (playRequested) instance.play() }

    fun setVideoSurfaceView(surfaceView: SurfaceView) {
        instance.setVideoSurfaceView(surfaceView)
    }

    private val onReadyListeners = mutableListOf<() -> Unit>()
    private val onErrorListeners = mutableListOf<() -> Unit>()
    private val onCutoffListeners = mutableListOf<() -> Unit>()

    fun onReady(listener: () -> Unit) {
        onReadyListeners.add(listener)
    }

    fun onError(listener: () -> Unit) {
        onErrorListeners.add(listener)
    }

    fun onCutoff(listener: () -> Unit) {
        onCutoffListeners.add(listener)
    }

    fun initialize() {
        instance.initialize()
        instance.onResolution { width, height ->
            val defaultAspectRatio = defaultAspectRatioProvider()

            if (defaultAspectRatio == null) {
                if (width > 0 && height > 0) aspectRatio = width.toFloat() / height
            } else {
                aspectRatio = defaultAspectRatio
            }
        }
        instance.onError { ex ->
            error = if (ex != null) "${ex.errorCodeName}(${ex.errorCode})"
            else null
            if (error != null) isBuffering = false

            if (error != null) onErrorListeners.forEach { it.invoke() }

        }
        instance.onReady { isBuffering = false; onReadyListeners.forEach { it.invoke() } }
        instance.onBuffering { isBuffering = it; if (it) error = null }
        instance.onPrepared { }
        instance.onMetadata { metadata = it }
        instance.onCutoff { onCutoffListeners.forEach { it.invoke() } }
    }

    fun release() {
        if (isReleased) return
        isReleased = true
        onReadyListeners.clear()
        onErrorListeners.clear()
        onCutoffListeners.clear()
        instance.release()
    }
}

@Composable
fun rememberPlaybackState(
    defaultAspectRatioProvider: () -> Float? = { null },
): PlaybackState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val state = remember {
        PlaybackState(
            PlayerBackendFactory.create(context, coroutineScope),
            defaultAspectRatioProvider = defaultAspectRatioProvider,
        )
    }

    DisposableEffect(Unit) {
        state.initialize()

        onDispose {
            state.release()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.onHostResumed()
            } else if (event == Lifecycle.Event.ON_STOP) {
                state.onHostStopped()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return state
}
