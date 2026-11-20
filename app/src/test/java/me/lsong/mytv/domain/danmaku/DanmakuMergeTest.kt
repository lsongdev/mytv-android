package me.lsong.mytv.domain.danmaku

import me.lsong.media.danmaku.DanmakuComment
import org.junit.Assert.*
import org.junit.Test

class DanmakuMergeTest {
    private fun comment(time: Long, text: String, mode: Int = 1) = DanmakuComment(time,mode,0xFFFFFF,text)
    @Test fun mergesIndependentOffsetsIntoOneSortedVideoTimeline() {
        val merged = mergeDanmakuSources(listOf(
            DanmakuSourceBatch("a",2000,listOf(comment(1000,"A"))),
            DanmakuSourceBatch("b",-500,listOf(comment(2500,"B"))),
        ),0)
        assertEquals(listOf(2000L,3000L),merged.map { it.timeMs })
        assertEquals(listOf("B","A"),merged.map { it.content })
    }
    @Test fun crossProviderDuplicatesCollapseAfterOffsetsAndNormalization() {
        val merged = mergeDanmakuSources(listOf(
            DanmakuSourceBatch("a",1000,listOf(comment(1000," Hello  World "))),
            DanmakuSourceBatch("b",0,listOf(comment(2300,"hello world"),comment(5000,"hello world"))),
        ),0)
        assertEquals(2,merged.size)
        assertEquals(listOf(2000L,5000L),merged.map { it.timeMs })
    }
    @Test fun preservesSameSourceRepeatsAndDifferentModes() {
        val merged = mergeDanmakuSources(listOf(
            DanmakuSourceBatch("a",0,listOf(comment(1000,"same"),comment(1200,"same"))),
            DanmakuSourceBatch("b",0,listOf(comment(1100,"same",5))),
        ),0)
        assertEquals(3,merged.size)
    }
    @Test fun rejectsNegativeTimesAndBoundsHistoryAndFuture() {
        val merged = mergeDanmakuSources(listOf(DanmakuSourceBatch("a",-2000,
            listOf(comment(1000,"negative"),comment(10_000,"old"),comment(100_000,"near"),comment(900_000,"far")))),100_000)
        assertEquals(listOf("near"),merged.map { it.content })
    }
}
