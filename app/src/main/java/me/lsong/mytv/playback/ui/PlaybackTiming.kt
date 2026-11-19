package me.lsong.mytv.playback.ui

/** An estimate assuming playback continues now at the current rate. */
internal fun estimatedPlaybackEndMs(nowMs: Long, positionMs: Long, durationMs: Long, speed: Float): Long? {
    if (durationMs <= 0) return null
    val rate = speed.takeIf { it.isFinite() && it > 0 } ?: 1f
    val remaining = (durationMs - positionMs.coerceAtLeast(0)).coerceAtLeast(0)
    return nowMs + (remaining / rate.toDouble()).toLong()
}
