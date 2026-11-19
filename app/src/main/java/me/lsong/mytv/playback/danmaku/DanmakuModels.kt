package me.lsong.mytv.playback.danmaku

/** 弹幕模式（对齐 DanmakuComment.mode：1 滚动 / 5 顶 / 4 底）。 */
enum class DanmakuMode { SCROLL, TOP, BOTTOM }

/** 引擎输入：一条弹幕在视频时间轴上的出现点与外观。colorRgb 取低 24 位。 */
data class DanmakuItem(
    val id: Long,
    val timeMs: Long,
    val text: String,
    val mode: DanmakuMode,
    val colorRgb: Int,
)

/** 文本宽度测量抽象：设备上用 Paint，单测用假实现，使引擎核心零 android.graphics 依赖。 */
interface TextMeasurer {
    fun measure(text: String, textSizePx: Float): Float
}

/**
 * 引擎配置（外观/行为）。opacity/visible 不影响布局，仅供 DanmakuRenderer 绘制时使用。
 */
data class DanmakuEngineConfig(
    val rollingDurationMs: Long = 8000L,
    val topBottomDurationMs: Long = 4500L,
    val textSizePx: Float = 42f,
    val lineHeightFactor: Float = 1.4f,
    val antiOverlap: Boolean = false,
    val opacity: Float = 0.85f,
    val visible: Boolean = true,
    /** 显示区域：弹幕限制在视图顶部这一比例的高度带内（1f=全屏）。改它只动布局、不改 view 尺寸（免闪动）。 */
    val areaFraction: Float = 1f,
)

/** 活跃弹幕（对象池复用，避免每帧分配）。x/y 每帧由 advance 重算。 */
class ActiveDanmaku {
    var inUse = false
    var id = 0L
    var text = ""
    var colorRgb = 0
    var mode = DanmakuMode.SCROLL
    var spawnTimeMs = 0L
    var width = 0f
    var lane = 0
    var x = 0f
    var y = 0f
}

/** 低 24 位 RGB 折入不透明度 → ARGB（绝不走 saveLayer：单 drawText 自带 alpha）。 */
fun argbColor(colorRgb: Int, opacity: Float): Int {
    val a = (opacity.coerceIn(0f, 1f) * 255f).toInt()
    return (a shl 24) or (colorRgb and 0xFFFFFF)
}
