package me.lsong.mytv.playback.danmaku

import android.graphics.Paint
import android.os.Handler
import android.os.HandlerThread
import android.view.Choreographer
import android.view.Surface
import kotlin.math.abs

/**
 * 专用渲染线程：自带 Looper + Choreographer 的 vsync 循环，从 Surface.lockHardwareCanvas()
 * 拿 GPU 加速 Canvas 绘制。所有引擎状态变更经本线程的 Handler 串行执行 → 无锁。
 *
 * 空闲门控：仅在动画态（播放 或 预览）持续投递帧；静止态绘制一帧冻结画面后停循环，
 * 由 setPlayback/requestRedraw 重新 kick。
 */
class DanmakuRenderThread(
    private val surface: Surface,
    private val engine: DanmakuEngine,
) : HandlerThread("DanmakuRender") {

    @Volatile private var handler: Handler? = null
    private var choreographer: Choreographer? = null
    // 不加 setShadowLayer：模糊阴影是逐条弹幕每帧一次离屏模糊，弱 GPU 上拖慢帧→滚动卡顿。
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 以下仅本线程触碰
    private var hostPositionMs = 0L
    private var isPlaying = false
    private var previewMode = false
    private var buffering = false
    private var playSpeed = 1f
    private var engineClockMs = 0L
    private var lastFrameNs = 0L
    private var lastSyncNs = 0L
    private var looping = false
    private var wasPreview = false

    private val frameCallback = Choreographer.FrameCallback { onVsync(it) }

    override fun onLooperPrepared() {
        val h = Handler(looper)
        handler = h
        h.post {
            choreographer = Choreographer.getInstance()
            kick()
        }
    }

    fun post(r: () -> Unit) { handler?.post(r) }

    fun setPlayback(positionMs: Long, playing: Boolean, speed: Float, preview: Boolean, buffering: Boolean) = post {
        if (!preview && (!looping || abs(positionMs - engineClockMs) > 1200)) {  // 用户拖拽/切集归零即时纠正（软跳转，不清屏）
            engineClockMs = positionMs
            engine.seekTo(positionMs)
        }
        hostPositionMs = positionMs
        isPlaying = playing
        previewMode = preview
        this.buffering = buffering
        playSpeed = speed
        kick()
    }

    /** 起线程前一次性播种初始播放态与时钟（仅在 start() 之前调用，此时无渲染线程，直接赋值安全）。 */
    fun setInitialPlayback(positionMs: Long, playing: Boolean, speed: Float, preview: Boolean, buffering: Boolean) {
        hostPositionMs = positionMs
        isPlaying = playing
        playSpeed = speed
        previewMode = preview
        this.buffering = buffering
        engineClockMs = positionMs
    }

    fun requestRedraw() = post { kick() }

    private fun kick() {
        if (looping) return
        looping = true
        lastFrameNs = 0L
        choreographer?.postFrameCallback(frameCallback)
    }

    private fun onVsync(frameNs: Long) {
        // 预览态切换：进入→快照当前帧做循环底；退出→丢弃预览缓冲，正常状态原样恢复（绝不清空已发射弹幕）。
        if (previewMode != wasPreview) {
            if (previewMode) {
                engine.beginPreview()
            } else {
                engine.endPreview()                             // 接管预览画面为正常活跃集（不跳回快照）
                engine.seekTo(hostPositionMs)                   // 软对齐真实位置：保位置 + 恢复正常装填
                engineClockMs = hostPositionMs
            }
            wasPreview = previewMode
            lastFrameNs = 0L                                     // 切态后重置 dt 基准
        }

        // dt 上限 64ms：偶发停顿(GC/调度)后不一次性大跳，避免“顿一下”。
        val dtMs = if (lastFrameNs != 0L) ((frameNs - lastFrameNs) / 1_000_000f).coerceAtMost(64f) else 0f
        lastFrameNs = frameNs

        // 推进时机 = 播放 或 缓冲（加载/切集中）。缓冲也推进，让已发射弹幕继续飞出，不在加载时冻结。
        // 仅「用户主动暂停」（非播放、非缓冲、非预览）才冻结。
        val animating = isPlaying || buffering
        if (animating) {
            engineClockMs += (dtMs * playSpeed).toLong()
            if (isPlaying) {                                     // 漂移校正仅在真正播放时（缓冲期宿主位置不可靠）
                if (lastSyncNs == 0L) lastSyncNs = frameNs
                else if (frameNs - lastSyncNs > 2_000_000_000L) {
                    lastSyncNs = frameNs
                    if (abs(engineClockMs - hostPositionMs) > 1200) {
                        engineClockMs = hostPositionMs
                        engine.seekTo(engineClockMs)
                    }
                }
            }
            engine.advance(engineClockMs)
        } else if (previewMode) {
            engine.tickPreview(dtMs.toLong())                    // 预览循环：反复滚动当前时间点的弹幕
        }
        // 否则：用户暂停且无预览 → 冻结，仅按需重绘当前帧。

        drawFrame()

        if ((animating || previewMode) && engine.hasContent && engine.config.visible) choreographer?.postFrameCallback(frameCallback)
        else looping = false                                    // 暂停：冻结画面已绘，停循环省 CPU
    }

    private fun drawFrame() {
        if (!surface.isValid) return
        val canvas = try { surface.lockHardwareCanvas() } catch (e: Exception) { null } ?: return
        try {
            DanmakuRenderer.draw(canvas, paint, engine)
        } finally {
            try { surface.unlockCanvasAndPost(canvas) } catch (e: Exception) { /* surface 已失效 */ }
        }
    }

    fun quitAndJoin() {
        post {
            choreographer?.removeFrameCallback(frameCallback)
            looping = false
        }
        quitSafely()
        try { join(500) } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
    }
}
