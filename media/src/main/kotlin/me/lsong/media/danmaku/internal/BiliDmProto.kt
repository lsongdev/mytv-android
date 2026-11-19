package me.lsong.media.danmaku.internal

import me.lsong.media.danmaku.DanmakuComment
import me.lsong.media.danmaku.DanmakuMedia
import me.lsong.media.danmaku.DanmakuEpisode

/**
 * B站新版分段弹幕 `x/v2/dm/web/seg.so` 的 protobuf 解码（手写最小 wire-format，无 protobuf 依赖）。
 *
 *   DmSegMobileReply { repeated DanmakuElem elems = 1 }
 *   DanmakuElem { int64 id=1; int32 progress=2(ms); int32 mode=3; int32 fontsize=4;
 *                 uint32 color=5; ...; string content=7; ... }
 *
 * 只取需要的 4 个字段（progress/mode/color/content），其余跳过。纯函数，已单测。
 */
internal object BiliDmProto {

    fun parseSegment(data: ByteArray): List<DanmakuComment> {
        if (data.isEmpty()) return emptyList()
        val out = ArrayList<DanmakuComment>()
        val r = Reader(data)
        while (r.hasMore()) {
            val tag = r.readVarint() ?: break
            val field = (tag ushr 3).toInt()
            val wire = (tag and 0x7L).toInt()
            if (field == 1 && wire == 2) {
                val len = r.readVarint()?.toInt() ?: break
                val elem = r.readBytes(len)
                parseElem(elem)?.let { out += it }
            } else if (!r.skip(wire)) break
        }
        return out
    }

    private fun parseElem(b: ByteArray): DanmakuComment? {
        val r = Reader(b)
        var progress = -1L
        var mode = DanmakuComment.MODE_SCROLL
        var color = 0xFFFFFFL
        var content = ""
        while (r.hasMore()) {
            val tag = r.readVarint() ?: break
            val field = (tag ushr 3).toInt()
            val wire = (tag and 0x7L).toInt()
            when {
                field == 2 && wire == 0 -> progress = r.readVarint() ?: break
                field == 3 && wire == 0 -> mode = (r.readVarint() ?: break).toInt()
                field == 5 && wire == 0 -> color = r.readVarint() ?: break
                field == 7 && wire == 2 -> {
                    val len = r.readVarint()?.toInt() ?: break
                    content = String(r.readBytes(len), Charsets.UTF_8)
                }
                else -> if (!r.skip(wire)) break
            }
        }
        if (progress < 0 || content.isEmpty()) return null
        return DanmakuComment(progress, mode, color, content)
    }

    /** 越界安全的字节游标。读越界返回 null/截断，不抛异常。 */
    private class Reader(val b: ByteArray) {
        var i = 0
        fun hasMore() = i < b.size

        fun readVarint(): Long? {
            var shift = 0
            var result = 0L
            while (i < b.size) {
                val x = b[i].toInt() and 0xFF; i++
                result = result or ((x and 0x7f).toLong() shl shift)
                if (x and 0x80 == 0) return result
                shift += 7
                if (shift > 63) return null
            }
            return null
        }

        fun readBytes(len: Int): ByteArray {
            if (len <= 0) return ByteArray(0)
            val end = minOf(i + len, b.size)
            val o = b.copyOfRange(i, end)
            i = end
            return o
        }

        /** 跳过一个字段值；越界/未知返回 false。 */
        fun skip(wire: Int): Boolean = when (wire) {
            0 -> readVarint() != null
            2 -> { val len = readVarint()?.toInt() ?: return false; i = minOf(i + len, b.size); true }
            5 -> { i += 4; true }
            1 -> { i += 8; true }
            else -> false
        }
    }
}
