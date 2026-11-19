package me.lsong.mytv.playback.danmaku

import org.junit.Assert.assertEquals
import org.junit.Test

class DanmakuColorTest {
    @Test fun opaqueRed() {
        assertEquals(0xFFFF0000.toInt(), argbColor(0xFF0000, 1f))
    }

    @Test fun halfAlphaWhite() {
        val c = argbColor(0xFFFFFF, 0.5f)
        assertEquals(127, (c ushr 24) and 0xFF)
        assertEquals(0xFFFFFF, c and 0xFFFFFF)
    }

    @Test fun stripsHighBitsOfColor() {
        assertEquals(0xFFFFFFFF.toInt(), argbColor(0xFFFFFFFF.toInt(), 1f))
    }

    @Test fun opacityClamped() {
        assertEquals(255, (argbColor(0, 2f) ushr 24) and 0xFF)
        assertEquals(0, (argbColor(0, -1f) ushr 24) and 0xFF)
    }
}
