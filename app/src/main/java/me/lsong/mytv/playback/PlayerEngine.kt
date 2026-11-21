package me.lsong.mytv.playback

import me.lsong.mytv.domain.playback.PlaybackRequest

import android.view.SurfaceView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.lsong.mytv.data.preferences.AppPreferences

abstract class PlayerEngine(
    private val coroutineScope: CoroutineScope,
)  {
    private var loadTimeoutJob: Job? = null
    private var cutoffTimeoutJob: Job? = null
    private var bufferingTimeoutJob: Job? = null
    private var buffering = false
    private var currentPosition = -1L
    private var awaitingReady = false
    private var monitoringPaused = false

    protected var metadata = Metadata()

    open fun initialize() {
        clearAllListeners()
    }

    open fun release() {
        awaitingReady = false
        stopMonitoring()
        clearAllListeners()
    }

    abstract fun prepare(request: PlaybackRequest)

    abstract fun seekTo(positionMs: Long)
    abstract val positionMs: Long
    abstract val durationMs: Long

    open var speed: Float = 1f
    open fun setDisplayAspectRatio(ratio: Float?) {}

    abstract fun play()

    abstract fun pause()

    abstract fun setVideoSurfaceView(surfaceView: SurfaceView)

    private val onResolutionListeners = mutableListOf<(width: Int, height: Int) -> Unit>()
    private val onErrorListeners = mutableListOf<(error: PlaybackException?) -> Unit>()
    private val onReadyListeners = mutableListOf<() -> Unit>()
    private val onBufferingListeners = mutableListOf<(buffering: Boolean) -> Unit>()
    private val onPreparedListeners = mutableListOf<() -> Unit>()
    private val onMetadataListeners = mutableListOf<(metadata: Metadata) -> Unit>()
    private val onCutoffListeners = mutableListOf<() -> Unit>()

    private fun clearAllListeners() {
        onResolutionListeners.clear()
        onErrorListeners.clear()
        onReadyListeners.clear()
        onBufferingListeners.clear()
        onPreparedListeners.clear()
        onMetadataListeners.clear()
        onCutoffListeners.clear()
    }

    protected fun triggerResolution(width: Int, height: Int) {
        onResolutionListeners.forEach { it(width, height) }
    }

    protected fun triggerError(error: PlaybackException?) {
        if (error != null) {
            awaitingReady = false
            stopMonitoring()
        }
        // Notify after canceling the old timers: listeners may immediately prepare a new resource.
        onErrorListeners.toList().forEach { it(error) }
    }

    protected fun triggerReady() {
        awaitingReady = false
        loadTimeoutJob?.cancel()
        if (buffering) startBufferingTimeout()
        onReadyListeners.toList().forEach { it() }
    }

    protected fun triggerBuffering(buffering: Boolean) {
        if (this.buffering != buffering) {
            this.buffering = buffering
            if (buffering) startBufferingTimeout() else bufferingTimeoutJob?.cancel()
        }
        onBufferingListeners.forEach { it(buffering) }
    }

    private fun startBufferingTimeout() {
        bufferingTimeoutJob?.cancel()
        if (awaitingReady || monitoringPaused || !buffering) return
        bufferingTimeoutJob = coroutineScope.launch {
            delay(AppPreferences.videoPlayerLoadTimeout.coerceAtLeast(1_000L))
            if (buffering && !monitoringPaused && !awaitingReady) triggerCutoff()
        }
    }

    protected fun triggerCutoff() {
        stopMonitoring()
        onCutoffListeners.toList().forEach { it() }
    }

    protected fun triggerPrepared() {
        awaitingReady = true
        buffering = false
        bufferingTimeoutJob?.cancel()
        currentPosition = -1L
        onPreparedListeners.forEach { it() }
        startLoadTimeout()
        cutoffTimeoutJob?.cancel()
        cutoffTimeoutJob = null
    }

    private fun startLoadTimeout() {
        loadTimeoutJob?.cancel()
        if (monitoringPaused || !awaitingReady) return
        loadTimeoutJob = coroutineScope.launch {
            delay(AppPreferences.videoPlayerLoadTimeout.coerceAtLeast(1_000L))
            if (!monitoringPaused && awaitingReady) triggerError(PlaybackException.LOAD_TIMEOUT)
        }
    }

    protected fun triggerPaused() {
        monitoringPaused = true
        stopMonitoring()
    }

    protected fun triggerResumed() {
        monitoringPaused = false
        if (awaitingReady) startLoadTimeout()
        else if (buffering) startBufferingTimeout()
    }

    protected fun triggerMetadata(metadata: Metadata) {
        onMetadataListeners.forEach { it(metadata) }
    }

    protected fun stopMonitoring() {
        loadTimeoutJob?.cancel()
        cutoffTimeoutJob?.cancel()
        bufferingTimeoutJob?.cancel()
        cutoffTimeoutJob = null
        bufferingTimeoutJob = null
    }

    protected fun triggerCurrentPosition(newPosition: Long, playing: Boolean) {
        if (!playing) { cutoffTimeoutJob?.cancel(); currentPosition = newPosition; return }
        if (currentPosition != newPosition) {
            cutoffTimeoutJob?.cancel()
            cutoffTimeoutJob = coroutineScope.launch {
                delay(AppPreferences.videoPlayerLoadTimeout.coerceAtLeast(1_000L))
                if (!monitoringPaused) triggerCutoff()
            }
        }
        currentPosition = newPosition
    }

    fun onResolution(listener: (width: Int, height: Int) -> Unit) {
        onResolutionListeners.add(listener)
    }

    fun onError(listener: (error: PlaybackException?) -> Unit) {
        onErrorListeners.add(listener)
    }

    fun onReady(listener: () -> Unit) {
        onReadyListeners.add(listener)
    }

    fun onBuffering(listener: (buffering: Boolean) -> Unit) {
        onBufferingListeners.add(listener)
    }

    fun onPrepared(listener: () -> Unit) {
        onPreparedListeners.add(listener)
    }

    fun onMetadata(listener: (metadata: Metadata) -> Unit) {
        onMetadataListeners.add(listener)
    }

    fun onCutoff(listener: () -> Unit) {
        onCutoffListeners.add(listener)
    }

    data class PlaybackException(
        val errorCodeName: String,
        val errorCode: Int,
    ) : Exception(errorCodeName) {
        companion object {
            val UNSUPPORTED_TYPE =
                PlaybackException("UNSUPPORTED_TYPE", 10002)
            val LOAD_TIMEOUT =
                PlaybackException("LOAD_TIMEOUT", 10003)
        }
    }

    /** 元数据 */
    data class Metadata(
        /** 视频编码 */
        val videoMimeType: String = "",
        /** 视频宽度 */
        val videoWidth: Int = 0,
        /** 视频高度 */
        val videoHeight: Int = 0,
        /** 视频颜色 */
        val videoColor: String = "",
        /** 视频帧率 */
        val videoFrameRate: Float = 0f,
        /** 视频比特率 */
        val videoBitrate: Int = 0,
        /** 视频解码器 */
        val videoDecoder: String = "",

        /** 音频编码 */
        val audioMimeType: String = "",
        /** 音频通道 */
        val audioChannels: Int = 0,
        /** 音频采样率 */
        val audioSampleRate: Int = 0,
        /** 音频解码器 */
        val audioDecoder: String = "",
    )
}
