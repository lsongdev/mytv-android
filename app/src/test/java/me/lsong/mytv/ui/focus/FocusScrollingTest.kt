@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package me.lsong.mytv.ui.focus

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusScrollingTest {
    @Test fun fullyVisibleTargetsDoNotMove() {
        assertEquals(0f, EdgeBringIntoViewSpec.calculateScrollDistance(160f, 100f, 500f))
        assertEquals(0f, EdgeBringIntoViewSpec.calculateScrollDistance(300f, 100f, 500f))
    }
    @Test fun clippedTargetsMoveOnlyByTheirOverflow() {
        assertEquals(-20f, EdgeBringIntoViewSpec.calculateScrollDistance(-20f, 100f, 500f))
        assertEquals(30f, EdgeBringIntoViewSpec.calculateScrollDistance(430f, 100f, 500f))
    }
    @Test fun oversizedTargetSpanningTheViewportIsNotRecentred() {
        assertEquals(0f, EdgeBringIntoViewSpec.calculateScrollDistance(-20f, 600f, 500f))
    }
}
