package me.lsong.mytv.domain.playback

sealed interface ResourceProbe {
    data object Pending : ResourceProbe
    data object Testing : ResourceProbe
    data class Measured(val latencyMs: Long, val bytesPerSecond: Long) : ResourceProbe
    data class Failed(val timedOut: Boolean = false) : ResourceProbe
}
