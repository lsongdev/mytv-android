package me.lsong.mytv.playback.danmaku

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView

/**
 * 承载弹幕的 TextureView：onAvailable 建 Surface 起渲染线程，onDestroyed 干净收尾（无反射、无泄漏）。
 * submit* 把变更交给渲染线程；onAvailable 之前的最新状态缓存下来，避免早到的提交丢失。
 */
class DanmakuTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {

    private val engine = DanmakuEngine(PaintTextMeasurer())
    private var renderThread: DanmakuRenderThread? = null
    private var surface: Surface? = null

    private var pendingItems: List<DanmakuItem> = emptyList()
    private var pendingConfig: DanmakuEngineConfig? = null
    private var lastPlayback: PlaybackState? = null

    private data class PlaybackState(val pos: Long, val playing: Boolean, val speed: Float, val preview: Boolean, val buffering: Boolean)

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    fun submitItems(list: List<DanmakuItem>) {
        pendingItems = list
        renderThread?.let { t -> t.post { engine.setItems(list) }; t.requestRedraw() }
    }

    fun submitConfig(config: DanmakuEngineConfig) {
        pendingConfig = config
        renderThread?.let { t -> t.post { engine.setConfig(config) }; t.requestRedraw() }
    }

    fun submitPlayback(pos: Long, playing: Boolean, speed: Float, preview: Boolean, buffering: Boolean) {
        lastPlayback = PlaybackState(pos, playing, speed, preview, buffering)
        renderThread?.setPlayback(pos, playing, speed, preview, buffering)
    }

    /** 硬清屏（弹窗切匹配专用）：回收全部在屏弹幕。 */
    fun submitClear() {
        renderThread?.let { t -> t.post { engine.clear() }; t.requestRedraw() }
    }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
        val s = Surface(st)
        surface = s
        engine.setViewport(w, h)
        pendingConfig?.let { engine.setConfig(it) }
        engine.setItems(pendingItems)
        val t = DanmakuRenderThread(s, engine)
        // 起线程前播种初始播放态/时钟（渲染线程尚未启动，直接调用安全）；首帧由 onLooperPrepared 的 kick() 绘制，
        // 不再依赖 start() 后 post 的时序竞争。后续变化经 LaunchedEffect→submitPlayback 持续下发。
        lastPlayback?.let {
            engine.seekTo(it.pos)
            t.setInitialPlayback(it.pos, it.playing, it.speed, it.preview, it.buffering)
        }
        renderThread = t
        t.start()
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
        renderThread?.let { t -> t.post { engine.setViewport(w, h) }; t.requestRedraw() }
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        renderThread?.quitAndJoin()
        renderThread = null
        surface?.release()
        surface = null
        return true
    }
}
