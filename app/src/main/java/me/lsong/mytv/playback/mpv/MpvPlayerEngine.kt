package me.lsong.mytv.playback.mpv

import me.lsong.mytv.playback.PlayerEngine

import me.lsong.mytv.domain.playback.PlaybackRequest

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.jdtech.mpv.MPVLib
import me.lsong.mytv.data.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope

/**
 * 基于 libmpv 的 VideoPlayer 实现。仅在 API>=26 由 PlayerFactory 构造。
 * 状态通过 MPVLib 属性观察回调（工作线程）切回主线程更新。
 *
 * 注：libmpv 1.0.0（dev.jdtech.mpv）为实例式 API——`MPVLib.create(ctx)` 返回实例，
 * 其余 init/destroy/attachSurface/command/setOptionString/observeProperty/addObserver 等均为实例方法；
 * 格式/事件常量在嵌套对象 `MPVLib.MpvFormat` / `MPVLib.MpvEvent` 中。
 */
@androidx.annotation.RequiresApi(26)
class MpvPlayerEngine(
    context: Context,
    scope: CoroutineScope,
) : PlayerEngine(scope), MPVLib.EventObserver {

    private val mpv: MPVLib = MPVLib.create(context)!!

    private val main = Handler(Looper.getMainLooper())
    private var pendingUrl: String? = null
    private var pendingStartMs: Long = 0
    private var pendingPlayWhenReady: Boolean = true
    private var surfaceAttached = false
    @Volatile private var released = false

    private var fileActive = false
    private var pendingStopEndFiles = 0

    override var positionMs by mutableLongStateOf(0L); private set
    override var durationMs by mutableLongStateOf(0L); private set
    private var bufferedMs by mutableLongStateOf(0L); private set
    private var isPlaying by mutableStateOf(false); private set
    private var isBuffering by mutableStateOf(false); private set
    private var error by mutableStateOf<String?>(null); private set
    private var hasStarted by mutableStateOf(false); private set
    private var onEnded: () -> Unit = {}
    override var speed: Float = 1f
        set(value) {
            field = value
            if (!released) mpv.setPropertyString("speed", value.toString())
        }

    init {
        mpv.setOptionString("vo", "gpu")
        mpv.setOptionString("gpu-context", "android")
        mpv.setOptionString("tls-verify", "no")
        mpv.setOptionString("ytdl", "no")
        when (AppPreferences.softwareDecode) {
            false -> {
                mpv.setOptionString("hwdec", "mediacodec,mediacodec-copy")
                mpv.setOptionString("hwdec-codecs", "h264,hevc,vp9,av1")
            }
            true -> mpv.setOptionString("hwdec", "no")
        }

        mpv.setOptionString("cache", "yes")
        mpv.setOptionString("demuxer-readahead-secs", "30")
        // Bound buffering on memory-constrained Android TV boxes.
        mpv.setOptionString("demuxer-max-bytes", "64MiB")
        mpv.setOptionString("demuxer-max-back-bytes", "16MiB")
        mpv.setOptionString(
            "stream-lavf-o",
            "reconnect=1,reconnect_streamed=1,reconnect_on_network_error=1,reconnect_delay_max=2",
        )

        mpv.init()
        mpv.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("demuxer-cache-time", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        mpv.observeProperty("paused-for-cache", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        mpv.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        mpv.addObserver(this)
    }

    override fun prepare(request: PlaybackRequest) {
        stopMonitoring()
        triggerPrepared()
        val url = if (request.uri.startsWith("file:")) android.net.Uri.parse(request.uri).path ?: request.uri else request.uri
        val startMs = request.startPositionMs
        val playWhenReady = request.playWhenReady
        mpv.setPropertyString("http-header-fields", request.headers.entries.joinToString(",") { "${it.key}: ${it.value}" })
        error = null
        hasStarted = false
        positionMs = 0; durationMs = 0; bufferedMs = 0
        isBuffering = true
        if (!surfaceAttached) {
            pendingUrl = url; pendingStartMs = startMs
            pendingPlayWhenReady = playWhenReady
            return
        }
        doLoad(url, startMs, playWhenReady)
    }

    private fun doLoad(url: String, startMs: Long, playWhenReady: Boolean) {
        if (fileActive) pendingStopEndFiles++
        fileActive = true
        val startSec = (startMs / 1000.0).coerceAtLeast(0.0)
        if (startSec > 0) {
            mpv.command(arrayOf("loadfile", url, "replace", "0", "start=$startSec"))
        } else {
            mpv.command(arrayOf("loadfile", url, "replace"))
        }
        mpv.setPropertyString("pause", if (playWhenReady) "no" else "yes")
    }

    override fun seekTo(ms: Long) {
        mpv.command(arrayOf("seek", (ms / 1000.0).coerceAtLeast(0.0).toString(), "absolute"))
    }

    fun togglePlay() {
        mpv.setPropertyString("pause", if (isPlaying) "yes" else "no")
    }

    override fun setDisplayAspectRatio(ratio: Float?) {
        if (!released) mpv.setPropertyString("video-aspect-override",ratio?.toString() ?: "no")
    }
    override fun play() { triggerResumed(); if (!released) mpv.setPropertyString("pause", "no") }
    override fun pause() { triggerPaused(); if (!released) mpv.setPropertyString("pause", "yes") }

    fun seekToLiveEdge() {}

    fun syncProgress() { /* MPV 走属性回调推送，无需主动拉取 */ }

    override fun release() {
        if (released) return
        released = true
        attachedView?.holder?.removeCallback(surfaceCallback)
        if (surfaceAttached) { mpv.setOptionString("force-window", "no"); mpv.setPropertyString("vo", "null"); mpv.detachSurface(); surfaceAttached = false }
        mpv.removeObserver(this)
        super.release()
        val player = mpv
        Thread({
            try { player.command(arrayOf("stop")); player.destroy() } catch (_: Throwable) {}
        }, "mpv-release").start()
    }

    override fun eventProperty(property: String) {}
    override fun eventProperty(property: String, value: Long) {}
    override fun eventProperty(property: String, value: Boolean) {
        main.post {
            if (released) return@post
            when (property) {
                "pause" -> isPlaying = !value
                "paused-for-cache" -> { isBuffering = value; triggerBuffering(value) }
                "eof-reached" -> if (value) onEnded()
            }
        }
    }
    override fun eventProperty(property: String, value: Double) {
        main.post {
            if (released) return@post
            when (property) {
                "time-pos" -> {
                    positionMs = (value * 1000).toLong().coerceAtLeast(0)
                    if (!hasStarted && value > 0) { hasStarted = true; isBuffering = false; triggerReady() }
                    triggerCurrentPosition(positionMs, isPlaying)
                }
                "duration" -> durationMs = (value * 1000).toLong().coerceAtLeast(0)
                "demuxer-cache-time" -> bufferedMs = (value * 1000).toLong().coerceAtLeast(0)
            }
        }
    }
    override fun eventProperty(property: String, value: String) {}
    override fun event(eventId: Int) {
        if (eventId == MPVLib.MpvEvent.MPV_EVENT_END_FILE) main.post {
            if (released) return@post
            if (pendingStopEndFiles > 0) { pendingStopEndFiles--; return@post }
            fileActive = false
            if (!hasStarted) triggerError(PlaybackException("MPV_LOAD_FAILED", 10004))
        }
    }

    private var attachedView: SurfaceView? = null
    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            if (released || surfaceAttached) return
            mpv.attachSurface(holder.surface)
            mpv.setOptionString("force-window", "yes")
            mpv.setPropertyString("vo", "gpu")
            surfaceAttached = true
            pendingUrl?.let { doLoad(it, pendingStartMs, pendingPlayWhenReady); pendingUrl = null }
        }
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            if (!released) mpv.setPropertyString("android-surface-size", "${width}x$height")
        }
        override fun surfaceDestroyed(holder: SurfaceHolder) {
            if (released) return
            surfaceAttached = false
            mpv.setPropertyString("vo", "null")
            mpv.setOptionString("force-window", "no")
            mpv.detachSurface()
        }
    }
    override fun setVideoSurfaceView(surfaceView: SurfaceView) {
        if (attachedView === surfaceView) return
        attachedView?.holder?.removeCallback(surfaceCallback)
        if (surfaceAttached) surfaceCallback.surfaceDestroyed(attachedView!!.holder)
        attachedView = surfaceView
        surfaceView.holder.addCallback(surfaceCallback)
        if (surfaceView.holder.surface.isValid) surfaceCallback.surfaceCreated(surfaceView.holder)
    }
}
