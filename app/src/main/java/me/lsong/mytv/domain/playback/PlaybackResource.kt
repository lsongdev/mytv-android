package me.lsong.mytv.domain.playback

import me.lsong.mytv.domain.provider.ProviderRecordRef
import java.util.UUID

/** One provider's playlist, spanning multiple parts of a video work. */
data class PlaybackLineRef(val work: ProviderRecordRef, val id: String, val title: String)

/** Resolved playable media shared by live channels and video parts. */
data class PlaybackResource(
    val uri: String,
    val origin: ProviderRecordRef,
    val providerName: String,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
    val line: PlaybackLineRef? = null,
) {
    val id: String = UUID.nameUUIDFromBytes(listOf(origin.key, line?.id.orEmpty(), uri,
        headers.toSortedMap(String.CASE_INSENSITIVE_ORDER).entries.joinToString { "${it.key.lowercase()}=${it.value}" }).joinToString("\u0000").toByteArray()).toString()
}
