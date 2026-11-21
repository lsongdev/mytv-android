package me.lsong.mytv.app

import org.junit.Assert.*
import org.junit.Test

class PlaybackRecoveryPolicyTest {
    @Test fun failureWrapsAndExhaustsEveryResourceExactlyOnce() {
        val policy = PlaybackRecoveryPolicy()
        val ids = listOf("a", "b", "c")
        policy.reset("b")
        assertEquals(2, policy.nextAfterFailure(ids, 1))
        assertEquals(0, policy.nextAfterFailure(ids, 2))
        assertNull(policy.nextAfterFailure(ids, 0))
    }

    @Test fun aStallRetriesOnceThenSwitchesAndFinallyStops() {
        val policy = PlaybackRecoveryPolicy()
        val ids = listOf("a", "b")
        policy.reset("a")
        assertEquals(0, policy.nextAfterStall(ids, 0))
        assertEquals(1, policy.nextAfterStall(ids, 0))
        assertEquals(1, policy.nextAfterStall(ids, 1))
        assertNull(policy.nextAfterStall(ids, 1))
    }

    @Test fun explicitSelectionStartsAFreshRecoveryBudget() {
        val policy = PlaybackRecoveryPolicy()
        val ids = listOf("a", "b")
        policy.reset("a")
        assertEquals(1, policy.nextAfterFailure(ids, 0))
        assertNull(policy.nextAfterFailure(ids, 1))
        policy.reset("b")
        assertEquals(0, policy.nextAfterFailure(ids, 1))
    }

    @Test fun duplicateResourcesAreNeverRetriedAsSeparateLines() {
        val policy = PlaybackRecoveryPolicy()
        policy.reset("a")
        assertEquals(2, policy.nextAfterFailure(listOf("a", "a", "b"), 0))
        assertNull(policy.nextAfterFailure(listOf("a", "a", "b"), 2))
    }
}
