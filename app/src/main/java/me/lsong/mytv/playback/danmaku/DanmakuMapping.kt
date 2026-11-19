package me.lsong.mytv.playback.danmaku

import me.lsong.media.danmaku.DanmakuComment
import me.lsong.mytv.domain.danmaku.DanmakuRenderSettings
import me.lsong.mytv.playback.danmaku.DanmakuEngineConfig
import me.lsong.mytv.playback.danmaku.DanmakuItem
import me.lsong.mytv.playback.danmaku.DanmakuMode

/** DanmakuComment → 引擎 DanmakuItem。color 取低 24 位；id = timeMs 与内容散列，与 PlayerScreen 去重口径一致。 */
fun DanmakuComment.toEngineItem(): DanmakuItem = DanmakuItem(
    id = timeMs * 31 + content.hashCode(),
    timeMs = timeMs,
    text = content,
    mode = when (mode) {
        DanmakuComment.MODE_TOP -> DanmakuMode.TOP
        DanmakuComment.MODE_BOTTOM -> DanmakuMode.BOTTOM
        else -> DanmakuMode.SCROLL
    },
    colorRgb = (color and 0xFFFFFFL).toInt(),
)

/**
 * 渲染设置 + 屏幕密度 + 显示区域比例 → 引擎配置。densityPct 在数据层抽样，不进 config；
 * areaFraction 让弹幕限制在顶部区域带内（由引擎布局实现，view 始终全屏，切区域不闪动）。
 */
fun buildEngineConfig(
    settings: DanmakuRenderSettings,
    density: Float,
    visible: Boolean,
    areaFraction: Float,
): DanmakuEngineConfig =
    DanmakuEngineConfig(
        rollingDurationMs = (DanmakuRenderSettings.BASE_DURATION_MS / settings.speed).toLong(),
        // 基准 18f：原 24f 偏大，下调使「标准」(fontScale=1.0) 即为舒适默认大小。
        textSizePx = 18f * density * settings.fontScale,
        opacity = settings.opacity,
        antiOverlap = settings.antiOverlap,
        visible = visible,
        areaFraction = areaFraction,
    )
