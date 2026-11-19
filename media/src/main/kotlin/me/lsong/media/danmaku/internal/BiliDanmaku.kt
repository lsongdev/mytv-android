package me.lsong.media.danmaku.internal

import me.lsong.media.danmaku.DanmakuComment
import me.lsong.media.danmaku.DanmakuMedia
import me.lsong.media.danmaku.DanmakuEpisode

/**
 * B站旧版 XML 弹幕解析。纯函数，无网络——便于单测。
 * 格式：<d p="time,mode,fontsize,color,timestamp,pool,senderHash,dmid,...">文本</d>
 *  - p[0] time（秒，float）→ timeMs
 *  - p[1] mode（1滚/4底/5顶）
 *  - p[3] color（十进制 RGB）
 */
internal object BiliDanmaku {
    private val D_REGEX = Regex("""<d\s+p="([^"]*)"[^>]*>([^<]*)</d>""")

    fun parseXml(xml: String): List<DanmakuComment> {
        if (xml.isBlank()) return emptyList()
        val out = ArrayList<DanmakuComment>()
        for (m in D_REGEX.findAll(xml)) {
            val attrs = m.groupValues[1].split(",")
            if (attrs.size < 4) continue
            val timeSec = attrs[0].toDoubleOrNull() ?: continue
            val mode = attrs[1].toIntOrNull() ?: DanmakuComment.MODE_SCROLL
            val color = attrs[3].toLongOrNull() ?: 0xFFFFFFL
            val content = unescape(m.groupValues[2])
            if (content.isEmpty()) continue
            out += DanmakuComment((timeSec * 1000).toLong(), mode, color, content)
        }
        return out
    }

    private fun unescape(s: String): String =
        s.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
}
