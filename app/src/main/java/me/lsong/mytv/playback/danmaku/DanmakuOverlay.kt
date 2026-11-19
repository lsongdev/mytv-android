package me.lsong.mytv.playback.danmaku

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import me.lsong.media.danmaku.DanmakuComment
import me.lsong.mytv.domain.danmaku.DanmakuRenderSettings
import me.lsong.mytv.playback.danmaku.DanmakuTextureView
import me.lsong.mytv.playback.danmaku.DanmakuItem

/**
 * 弹幕叠加层（自研轻量引擎，替代 akdanmaku）。渲染在专用线程的硬件 Canvas 上完成，绝不上主线程、
 * 绝不 saveLayer、无位图池。叠在 VideoSurface 之上、控制条之下。
 *
 * [settings] 控制外观/行为（opacity/speed/fontScale/antiOverlap，经 [buildEngineConfig] 实时下发）；
 * [densityPct] 在数据层抽样；显示区域由 [modifier] 的 fillMaxHeight 决定（沿用原逻辑）。
 * [datasetKey] 变更整块重建 view（旧渲染线程干净回收），不变则同一 view 贯穿增量加载空窗。
 */
@Composable
fun DanmakuOverlay(
    comments: List<DanmakuComment>,
    positionMs: Long,
    isPlaying: Boolean,
    buffering: Boolean = false,
    speed: Float,
    visible: Boolean,
    settings: DanmakuRenderSettings,
    previewMode: Boolean = false,
    datasetKey: Any = Unit,
    areaFraction: Float = 1f,
    clearSignal: Int = 0,
    modifier: Modifier = Modifier,
) {
    androidx.compose.runtime.key(datasetKey) {
        val context = LocalContext.current
        val density = context.resources.displayMetrics.density
        var view by remember { mutableStateOf<DanmakuTextureView?>(null) }

        AndroidView(
            factory = { ctx -> DanmakuTextureView(ctx).also { view = it } },
            modifier = modifier,
        )

        // 显示密度：数据层按稳定散列抽样（沿用原口径），再映射成引擎 items。
        // 单趟 mapNotNull：免「filter 中间列表 + 二次遍历」，分片加载每到一块只过一遍累积窗口。
        val items by androidx.compose.runtime.produceState(emptyList<DanmakuItem>(), comments, settings.densityPct) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                comments.mapNotNull { c ->
                    val keep = settings.densityPct >= 100 || (((c.timeMs * 31 + c.content.hashCode()).toInt() and 0x7fffffff) % 100) < settings.densityPct
                    if (keep) c.toEngineItem() else null
                }
            }
        }

        LaunchedEffect(view, items) { view?.submitItems(items) }
        LaunchedEffect(view, settings, density, visible, areaFraction) {
            view?.submitConfig(buildEngineConfig(settings, density, visible, areaFraction))
        }
        LaunchedEffect(view, positionMs, isPlaying, buffering, speed, previewMode) {
            view?.submitPlayback(positionMs, isPlaying, speed, previewMode, buffering)
        }
        // 切匹配信号自增 → 硬清屏（首帧 clearSignal=0 不触发）。
        LaunchedEffect(view, clearSignal) {
            if (clearSignal > 0) view?.submitClear()
        }
    }
}
