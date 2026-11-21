package me.lsong.mytv.playback

import org.junit.Assert.*
import org.junit.Test

class TimeWindowRetryBudgetTest {
    @Test fun immediateRepeatedLiveWindowFailuresExhaustBudget() {
        var now = 0L
        val budget = TimeWindowRetryBudget(1, 60_000L) { now }
        assertTrue(budget.tryAcquire())
        assertFalse(budget.tryAcquire())
        now = 59_999L
        assertFalse(budget.tryAcquire())
        now = 60_000L
        assertTrue(budget.tryAcquire())
    }

    @Test fun explicitSelectionResetsBudget() {
        val budget = TimeWindowRetryBudget(1, 60_000L) { 123L }
        assertTrue(budget.tryAcquire())
        assertFalse(budget.tryAcquire())
        budget.reset()
        assertTrue(budget.tryAcquire())
    }

    @Test fun clockRollbackStartsFreshWindow() {
        var now = 10_000L
        val budget = TimeWindowRetryBudget(1, 60_000L) { now }
        assertTrue(budget.tryAcquire())
        now = 1L
        assertTrue(budget.tryAcquire())
    }
}
