package me.lsong.mytv.playback.danmaku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/** 固定宽度的假测量：宽度 = 字数 × 字号 × 0.5，与设备无关、可断言。 */
class FakeMeasurer : TextMeasurer {
    override fun measure(text: String, textSizePx: Float): Float = text.length * textSizePx * 0.5f
}

private fun scroll(id: Long, t: Long, text: String = "abc") =
    DanmakuItem(id, t, text, DanmakuMode.SCROLL, 0xFFFFFF)

class DanmakuEngineTest {

    private fun engine(): DanmakuEngine = DanmakuEngine(FakeMeasurer()).apply {
        setViewport(1920, 1080)
        setConfig(DanmakuEngineConfig(rollingDurationMs = 8000L, textSizePx = 40f))
    }

    @Test fun spawnsOnlyDueItems() {
        val e = engine()
        e.setItems(listOf(scroll(1, 1000), scroll(2, 2000), scroll(3, 3000)))
        e.advance(1500)
        assertEquals(listOf(1L), e.active.map { it.id })
        e.advance(2500)
        assertEquals(setOf(1L, 2L), e.active.map { it.id }.toSet())
    }

    @Test fun retiresAfterRollingDuration() {
        val e = engine()
        e.setItems(listOf(scroll(1, 0)))
        e.advance(0)
        assertTrue(e.active.any { it.id == 1L })
        e.advance(8001)
        assertFalse(e.active.any { it.id == 1L })
    }

    @Test fun seekIsSoftPreservesActiveAndRepositionsCursor() {
        val e = engine()
        e.setItems((1..10).map { scroll(it.toLong(), it * 1000L) })
        e.advance(2500)
        val before = e.active.map { it.id }.toSet()
        assertTrue(before.isNotEmpty())
        e.seekTo(5000)
        // 软跳转：已发射弹幕保留（不清空）
        assertEquals(before, e.active.map { it.id }.toSet())
        // 游标重定位：从 5000 起，下一次 advance 命中 id=5；5000 之前未播的 id=4 不回补
        e.advance(5000)
        assertTrue(e.active.any { it.id == 5L })
        assertFalse(e.active.any { it.id == 4L })
    }

    @Test fun softSeekKeepsOnScreenPosition() {
        val e = engine()
        e.setItems(listOf(scroll(1, 1000)))
        e.advance(2000)            // id=1 入场（spawnTime=2000，x=viewW）
        e.advance(4000)            // 飞了一会
        val xBefore = e.active.first { it.id == 1L }.x
        e.seekTo(50000)            // 大跳：重定基，保持在屏位置
        e.advance(50000)
        val d = e.active.firstOrNull { it.id == 1L }
        assertTrue(d != null)
        assertEquals(xBefore, d!!.x, 0.5f)
    }

    @Test fun clearEmptiesActive() {
        val e = engine()
        e.setItems((1..10).map { scroll(it.toLong(), it * 1000L) })
        e.advance(2500)
        assertTrue(e.active.isNotEmpty())
        e.clear()                  // 硬清屏（切匹配）
        assertTrue(e.active.isEmpty())
    }

    @Test fun previewIsolatesRealStateDuringThenAdoptsOnEnd() {
        val e = engine()
        e.setItems((1..20).map { scroll(it.toLong(), it * 1000L) })
        e.advance(5000)
        val realActive = e.active.map { it.id }.toSet()
        val realClock = e.clock
        e.beginPreview()
        assertTrue(e.isPreviewing)
        e.tickPreview(1000)        // 预览推进
        assertEquals(realClock, e.clock)                          // 预览期不动正常时钟
        assertEquals(realActive, e.active.map { it.id }.toSet())  // 预览期不动正常活跃集
        e.endPreview()
        assertFalse(e.isPreviewing)
        // 接管：退出预览后正常活跃集采用预览画面（非空、不丢弃），无缝继续而非跳回快照
        assertTrue(e.active.isNotEmpty())
    }

    @Test fun previewLoopsSeamlesslyNeverEmptyAndBounded() {
        val e = DanmakuEngine(FakeMeasurer()).apply {
            setViewport(1920, 1080)
            setConfig(DanmakuEngineConfig(rollingDurationMs = 8000L, textSizePx = 40f))
        }
        // 稠密弹幕：当前时刻一窗 [3000,11000) 有 8 条，飞行时长 8000 → 任意时刻都有若干在屏。
        e.setItems((1..50).map { scroll(it.toLong(), it * 1000L) })
        e.advance(3000)
        e.beginPreview()
        var everEmpty = false
        var maxSize = 0
        repeat(2000) {                 // 2000 帧 × 16ms ≈ 32s，跨多圈循环
            e.tickPreview(16)
            if (e.renderActive.isEmpty()) everEmpty = true
            if (e.renderActive.size > maxSize) maxSize = e.renderActive.size
        }
        assertFalse(everEmpty)          // 无缝：循环过程中从不清空
        assertTrue(maxSize <= 64)       // 有界：不无限累积（远小于全量 50+）
        assertTrue(e.isPreviewing)
        // 退出预览不动正常状态
        e.endPreview()
        assertFalse(e.isPreviewing)
    }

    @Test fun scrollSpreadsAcrossLanesWhenOverlapAllowed() {
        val e = DanmakuEngine(FakeMeasurer()).apply {
            setViewport(1920, 1080)
            setConfig(DanmakuEngineConfig(rollingDurationMs = 8000L, textSizePx = 40f, antiOverlap = false))
        }
        e.setItems(listOf(scroll(1, 0), scroll(2, 0), scroll(3, 0)))
        e.advance(0)
        assertEquals(3, e.active.map { it.lane }.toSet().size)
    }

    @Test fun antiOverlapDropsWhenAllLanesBusy() {
        val e = DanmakuEngine(FakeMeasurer()).apply {
            setViewport(1920, 56)
            setConfig(DanmakuEngineConfig(rollingDurationMs = 8000L, textSizePx = 40f, antiOverlap = true))
        }
        e.setItems(listOf(scroll(1, 0), scroll(2, 0)))
        e.advance(0)
        assertEquals(listOf(1L), e.active.map { it.id })
    }

    @Test fun antiOverlapAdmitsAfterPreviousFullyEntered() {
        val e = DanmakuEngine(FakeMeasurer()).apply {
            setViewport(1920, 56)
            setConfig(DanmakuEngineConfig(rollingDurationMs = 8000L, textSizePx = 40f, antiOverlap = true))
        }
        e.setItems(listOf(scroll(1, 0), scroll(2, 7999)))
        e.advance(0)
        e.advance(7999)
        assertTrue(e.active.any { it.id == 2L })
    }

    private fun top(id: Long, t: Long) = DanmakuItem(id, t, "x", DanmakuMode.TOP, 0xFFFFFF)
    private fun bottom(id: Long, t: Long) = DanmakuItem(id, t, "x", DanmakuMode.BOTTOM, 0xFFFFFF)

    @Test fun topStacksFromTop() {
        val e = engine()
        e.setItems(listOf(top(1, 0), top(2, 0)))
        e.advance(0)
        val lanes = e.active.filter { it.mode == DanmakuMode.TOP }.map { it.lane }.toSet()
        assertEquals(setOf(0, 1), lanes)
        val y0 = e.active.first { it.id == 1L }.y
        val y1 = e.active.first { it.id == 2L }.y
        assertTrue(y0 < y1)
    }

    @Test fun bottomGrowsUpwardFromBottom() {
        val e = engine()
        e.setItems(listOf(bottom(1, 0), bottom(2, 0)))
        e.advance(0)
        val y0 = e.active.first { it.id == 1L }.y
        val y1 = e.active.first { it.id == 2L }.y
        assertTrue(y0 > y1)
    }

    @Test fun staticExpiresAfterDuration() {
        val e = DanmakuEngine(FakeMeasurer()).apply {
            setViewport(1920, 1080)
            setConfig(DanmakuEngineConfig(topBottomDurationMs = 4500L, textSizePx = 40f))
        }
        e.setItems(listOf(top(1, 0)))
        e.advance(0); assertTrue(e.active.any { it.id == 1L })
        e.advance(4501); assertFalse(e.active.any { it.id == 1L })
    }

    @Test fun lateChunkForPastTimeIsNotBackfilled() {
        val e = engine()
        e.setItems(listOf(scroll(1, 1000), scroll(3, 4000)))
        e.advance(3000)
        e.setItems(listOf(scroll(1, 1000), scroll(2, 2000), scroll(3, 4000)))
        e.advance(3000)
        assertFalse(e.active.any { it.id == 2L })
        e.advance(4000)
        assertTrue(e.active.any { it.id == 3L })
    }

    @Test fun areaFractionLimitsLanes() {
        // laneH = 40*1.4 = 56；viewH 1120 → 全屏 20 轨；TOP 轨满即丢弃，故活跃数 = 轨数。
        val full = DanmakuEngine(FakeMeasurer()).apply {
            setViewport(1920, 1120)
            setConfig(DanmakuEngineConfig(textSizePx = 40f))
        }
        full.setItems((1..40).map { top(it.toLong(), 0) })
        full.advance(0)
        assertEquals(20, full.active.count { it.mode == DanmakuMode.TOP })

        // areaFraction 0.5 → 可用高 560 → 10 轨。
        val half = DanmakuEngine(FakeMeasurer()).apply {
            setViewport(1920, 1120)
            setConfig(DanmakuEngineConfig(textSizePx = 40f, areaFraction = 0.5f))
        }
        half.setItems((1..40).map { top(it.toLong(), 0) })
        half.advance(0)
        assertEquals(10, half.active.count { it.mode == DanmakuMode.TOP })
    }
}
