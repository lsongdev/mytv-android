package me.lsong.mytv.playback

/**
 * Guards short, backend-internal recovery loops (for example stale HLS live
 * windows). A transient failure much later may be retried again.
 */
internal class TimeWindowRetryBudget(
    private val maxRetries: Int,
    private val windowMs: Long,
    private val now: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    init {
        require(maxRetries > 0)
        require(windowMs > 0)
    }

    private var windowStart: Long? = null
    private var retries = 0

    fun reset() {
        windowStart = null
        retries = 0
    }

    fun tryAcquire(): Boolean {
        val timestamp = now()
        val start = windowStart
        if (start == null || timestamp < start || timestamp - start >= windowMs) {
            windowStart = timestamp
            retries = 0
        }
        if (retries >= maxRetries) return false
        retries++
        return true
    }
}
