package me.lsong.mytv.playback.ui

import org.junit.Assert.*
import org.junit.Test

class PlaybackTimingTest {
    @Test fun doubleSpeedHalvesRemainingWallTime() {
        assertEquals(61000L, estimatedPlaybackEndMs(1000, 60000, 120000, 1f))
        assertEquals(31000L, estimatedPlaybackEndMs(1000, 60000, 120000, 2f))
    }
    @Test fun pausedVideoEstimateAssumesResumingNow() {
        assertEquals(70000L, estimatedPlaybackEndMs(10000, 60000, 120000, 1f))
        assertEquals(80000L, estimatedPlaybackEndMs(20000, 60000, 120000, 1f))
    }
    @Test fun unknownDurationsDoNotDisplayAnEndClock() {
        assertNull(estimatedPlaybackEndMs(1000, 0, 0, 1f))
        assertNull(estimatedPlaybackEndMs(1000, 0, Long.MIN_VALUE, 1f))
    }
    @Test fun completedAndInvalidRateInputsStaySensible() {
        assertEquals(1000L, estimatedPlaybackEndMs(1000, 130000, 120000, 1f))
        assertEquals(61000L, estimatedPlaybackEndMs(1000, 60000, 120000, 0f))
    }
}
