package me.lsong.mytv.app

/**
 * Finite resource recovery for one explicit playback selection.
 *
 * A stalled stream can be retried once before moving on. Once every resource
 * has been attempted, recovery stops instead of looping forever. An explicit
 * channel/episode/resource selection resets the budget.
 */
internal class PlaybackRecoveryPolicy(private val stallRetriesPerResource: Int = 1) {
    init { require(stallRetriesPerResource >= 0) }

    private val attempted = mutableSetOf<String>()
    private val stallRetries = mutableMapOf<String, Int>()

    fun reset(resourceId: String) {
        attempted.clear()
        stallRetries.clear()
        markAttempt(resourceId)
    }

    fun markAttempt(resourceId: String) {
        attempted += resourceId
    }

    fun nextAfterFailure(resourceIds: List<String>, currentIndex: Int): Int? {
        if (currentIndex !in resourceIds.indices) return null
        attempted += resourceIds[currentIndex]
        for (step in 1 until resourceIds.size) {
            val candidate = (currentIndex + step) % resourceIds.size
            if (attempted.add(resourceIds[candidate])) return candidate
        }
        return null
    }

    fun nextAfterStall(resourceIds: List<String>, currentIndex: Int): Int? {
        if (currentIndex !in resourceIds.indices) return null
        val current = resourceIds[currentIndex]
        val retries = stallRetries[current] ?: 0
        if (retries < stallRetriesPerResource) {
            stallRetries[current] = retries + 1
            return currentIndex
        }
        return nextAfterFailure(resourceIds, currentIndex)
    }
}
