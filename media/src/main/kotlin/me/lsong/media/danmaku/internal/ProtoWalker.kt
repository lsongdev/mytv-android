package me.lsong.media.danmaku.internal

import org.json.JSONArray
import org.json.JSONObject

/**
 * 通用 protobuf 解码（无 .proto，按 wire 格式走）→ JSONObject：
 *   key = 字段号（字符串）；varint→Long；fixed64→Long；fixed32→Int；
 *   length-delimited 先尝试当嵌套消息（能干净解完则用对象），否则当 UTF-8 字符串；
 *   同字段多次出现 → JSONArray。
 *
 * 启发式解码，供脚本探索未知 protobuf；字段语义由脚本按编号自行映射。
 */
internal object ProtoWalker {

    fun decode(data: ByteArray): JSONObject = decodeMessage(data, 0, data.size, 0) ?: JSONObject()

    private fun decodeMessage(b: ByteArray, start: Int, end: Int, depth: Int): JSONObject? {
        val obj = JSONObject()
        var i = start
        var sawField = false
        while (i < end) {
            val t = readVarint(b, i, end) ?: return if (sawField) obj else null
            var tag = t.first; i = t.second
            val field = (tag ushr 3).toString()
            val wire = (tag and 0x7L).toInt()
            val value: Any? = when (wire) {
                0 -> { val v = readVarint(b, i, end) ?: return null; i = v.second; v.first }
                1 -> { if (i + 8 > end) return null; val v = le64(b, i); i += 8; v }
                5 -> { if (i + 4 > end) return null; val v = le32(b, i); i += 4; v }
                2 -> {
                    val l = readVarint(b, i, end) ?: return null; i = l.second
                    val len = l.first.toInt()
                    if (len < 0 || i + len > end) return null
                    val sub = b.copyOfRange(i, i + len); i += len
                    (if (depth < 6) decodeStrict(sub, depth + 1) else null) ?: String(sub, Charsets.UTF_8)
                }
                else -> return if (sawField) obj else null
            }
            sawField = true
            accumulate(obj, field, value)
        }
        return obj
    }

    /** 严格解析：必须整段消费完且至少一个字段，否则视为非嵌套消息（返回 null）。 */
    private fun decodeStrict(b: ByteArray, depth: Int): JSONObject? {
        if (b.isEmpty()) return null
        return runCatching { decodeMessage(b, 0, b.size, depth) }.getOrNull()?.takeIf { it.length() > 0 }
    }

    private fun accumulate(obj: JSONObject, field: String, value: Any?) {
        val existing = obj.opt(field)
        when (existing) {
            null -> obj.put(field, value)
            is JSONArray -> existing.put(value)
            else -> obj.put(field, JSONArray().put(existing).put(value))
        }
    }

    private fun readVarint(b: ByteArray, start: Int, end: Int): Pair<Long, Int>? {
        var shift = 0; var result = 0L; var i = start
        while (i < end) {
            val x = b[i].toInt() and 0xFF; i++
            result = result or ((x and 0x7f).toLong() shl shift)
            if (x and 0x80 == 0) return result to i
            shift += 7
            if (shift > 63) return null
        }
        return null
    }

    private fun le64(b: ByteArray, i: Int): Long {
        var r = 0L; for (k in 0 until 8) r = r or ((b[i + k].toLong() and 0xFF) shl (8 * k)); return r
    }

    private fun le32(b: ByteArray, i: Int): Int {
        var r = 0; for (k in 0 until 4) r = r or ((b[i + k].toInt() and 0xFF) shl (8 * k)); return r
    }
}
