package me.lsong.media.danmaku.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import me.lsong.media.danmaku.DanmakuComment
import org.junit.Test

/**
 * DmSegMobileReply 最小 protobuf 解码：
 *   DmSegMobileReply { repeated DanmakuElem elems = 1 }
 *   DanmakuElem { int32 progress=2(ms); int32 mode=3; uint32 color=5; string content=7 }
 * 测试向量手工编码（与真实 seg.so 字段布局一致，已用真网样本核对）。
 */
class BiliDmProtoTest {

    /** 一个 elem：progress=1000, mode=5, color=16777215, content="hi"。 */
    private val elem = byteArrayOf(
        0x10, 0xE8.toByte(), 0x07,                       // field2 varint 1000
        0x18, 0x05,                                      // field3 varint 5
        0x28, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x07, // field5 varint 16777215
        0x3A, 0x02, 0x68, 0x69,                          // field7 len=2 "hi"
    )

    private fun reply(vararg elems: ByteArray): ByteArray {
        val out = ArrayList<Byte>()
        for (e in elems) {
            out.add(0x0A)                // field1, wire2
            out.add(e.size.toByte())     // length（测试中均 <128）
            e.forEach { out.add(it) }
        }
        return out.toByteArray()
    }

    @Test
    fun parsesSingleElem() {
        val list = BiliDmProto.parseSegment(reply(elem))
        assertEquals(1, list.size)
        assertEquals(DanmakuComment(1000L, 5, 16777215L, "hi"), list[0])
    }

    @Test
    fun parsesMultipleElems() {
        val list = BiliDmProto.parseSegment(reply(elem, elem, elem))
        assertEquals(3, list.size)
        assertEquals(DanmakuComment.MODE_TOP, list[1].mode)
    }

    @Test
    fun emptyOrGarbageReturnsEmpty() {
        assertTrue(BiliDmProto.parseSegment(ByteArray(0)).isEmpty())
        // 截断字节不应崩溃
        BiliDmProto.parseSegment(byteArrayOf(0x0A, 0x7F, 0x10))
    }
}
