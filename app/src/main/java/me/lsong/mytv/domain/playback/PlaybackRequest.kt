package me.lsong.mytv.domain.playback

/** The player consumes resolved media, never a provider catalog or vendor episode syntax. */
data class PlaybackRequest(
    val uri: String,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
    val startPositionMs: Long = 0,
    val live: Boolean = false,
    val playWhenReady: Boolean = true,
)
