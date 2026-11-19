package me.lsong.media.danmaku

import kotlinx.serialization.Serializable

/**
 * 统一弹幕模型——所有弹幕源（B站/腾讯/爱奇艺…）解析后都归一到此结构，
 * 与 misaka_danmu_server 的 get_comments 契约对齐，方便后续脚本引擎桥接。
 *
 * @param timeMs  弹幕出现时间（毫秒，相对视频起点）
 * @param mode    弹幕模式：1=滚动 4=底部 5=顶部（对齐 B站 <d> 与弹弹play 规范）
 * @param color   颜色，十进制值。通常 RGB（16777215=0xFFFFFF 白）；部分源（如优酷）返回带 alpha 的
 *                ARGB，最大可达 0xFFFFFFFF 超出 Int 范围，故用 Long——渲染时取低 24 位 RGB。
 *                用 Int 时，一条越界 color 会让整列弹幕反序列化失败（实测优酷 color=4294722163 致全列丢弃）。
 * @param content 弹幕文本
 */
@Serializable
data class DanmakuComment(
    val timeMs: Long,
    val mode: Int,
    val color: Long,
    val content: String,
) {
    companion object {
        const val MODE_SCROLL = 1
        const val MODE_BOTTOM = 4
        const val MODE_TOP = 5
    }
}

/** 搜索结果候选。type: tvseries / movie / ova / web（信息性，便于匹配排序）。 */
@Serializable
data class DanmakuMedia(
    val provider: String,
    val mediaId: String,
    val title: String,
    val type: String = "tvseries",
    val season: Int? = null,
    val year: Int? = null,
    val episodeCount: Int? = null,
)

/** 分集。episodeIndex 从 1 开始。 */
@Serializable
data class DanmakuEpisode(
    val provider: String,
    val episodeId: String,
    val title: String,
    val episodeIndex: Int,
)
