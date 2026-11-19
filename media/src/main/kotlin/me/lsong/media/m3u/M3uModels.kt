package me.lsong.media.m3u

import kotlinx.serialization.Serializable

@Serializable
data class M3uDocument(val headers: Map<String, String>, val entries: List<M3uEntry>)
@Serializable
data class M3uEntry(val attributes: Map<String, String>, val title: String, val uri: String)
