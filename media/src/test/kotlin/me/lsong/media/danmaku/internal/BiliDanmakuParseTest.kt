package me.lsong.media.danmaku.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import me.lsong.media.danmaku.DanmakuComment
import org.junit.Test

/** B站旧版 XML 弹幕：<d p="time,mode,fontsize,color,ts,pool,senderHash,dmid,...">文本</d>。 */
class BiliDanmakuParseTest {

    // 真实样本（cid 1176840 节选）
    private val sampleXml = """
        <?xml version="1.0" encoding="UTF-8"?><i><chatserver>chat.bilibili.com</chatserver><maxlimit>3000</maxlimit>
        <d p="9.02100,1,25,16777215,1516810212,0,f5b70f3d,4210753616,10">你指尖跃动的电光，是我此生不变的信仰。</d>
        <d p="28.97300,4,25,13369344,1502719844,0,a77032eb,3697030042,10">底部弹幕</d>
        </i>
    """.trimIndent()

    @Test
    fun parsesTimeModeColorContent() {
        val list = BiliDanmaku.parseXml(sampleXml)
        assertEquals(2, list.size)
        val first = list[0]
        assertEquals(9021L, first.timeMs)              // 9.02100s -> 9021ms
        assertEquals(DanmakuComment.MODE_SCROLL, first.mode)
        assertEquals(16777215L, first.color)
        assertEquals("你指尖跃动的电光，是我此生不变的信仰。", first.content)
        assertEquals(DanmakuComment.MODE_BOTTOM, list[1].mode)
        assertEquals(13369344L, list[1].color)
    }

    @Test
    fun unescapesXmlEntities() {
        val xml = """<i><d p="1.000,1,25,16777215,0,0,0,0,0">a&amp;b&lt;c&gt;d&quot;e</d></i>"""
        val list = BiliDanmaku.parseXml(xml)
        assertEquals(1, list.size)
        assertEquals("a&b<c>d\"e", list[0].content)
        assertEquals(1000L, list[0].timeMs)
    }

    @Test
    fun returnsEmptyOnGarbage() {
        assertTrue(BiliDanmaku.parseXml("").isEmpty())
        assertTrue(BiliDanmaku.parseXml("not xml at all").isEmpty())
        // 缺字段的 <d> 应被跳过而非崩溃
        assertTrue(BiliDanmaku.parseXml("""<i><d p="1.0">x</d></i>""").isEmpty())
    }
}
