package me.lsong.media.http

/** Request context; no application configuration, provider IDs or playback state. */
data class MediaEndpoint(val uri: String, val headers: Map<String, String> = emptyMap())
