package me.lsong.mytv.domain.danmaku

import kotlinx.serialization.Serializable

/**
 * 弹幕渲染设置（外观/行为，与「显示区域」「弹幕源」并列，统一在播放页弹窗里调）。
 * - [opacity]     不透明度 0.3~1.0 → 折入每条弹幕 paint 的 alpha 通道
 * - [speed]       滚动速度倍率 0.5~2.0（越大越快）→ rollingDurationMs = 8000 / speed
 * - [fontScale]   字号倍率 0.6~1.6 → textSizePx
 * - [densityPct]  显示密度百分比（25/50/75/100）→ 数据层抽样上屏比例（防刷屏）
 * - [antiOverlap] 防重叠 → 分轨拒绝重叠（放不下丢弃）
 */
@Serializable
data class DanmakuRenderSettings(
    val opacity: Float = 1.0f,
    val speed: Float = 1.0f,
    val fontScale: Float = 1.0f,
    val densityPct: Int = 100,
    val antiOverlap: Boolean = true,
) {
    companion object {
        const val BASE_DURATION_MS = 8000f   // speed=1.0 时滚动弹幕飞过屏幕的时长

        val OPACITY_STEPS = listOf(0.4f, 0.55f, 0.7f, 0.85f, 1.0f)
        val SPEED_STEPS = listOf(0.5f, 0.75f, 1.0f, 1.5f, 2.0f)
        val FONT_STEPS = listOf(0.7f, 0.85f, 1.0f, 1.2f, 1.4f)
        val DENSITY_STEPS = listOf(25, 50, 75, 100)
    }
}
