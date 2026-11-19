package me.lsong.media.vod.maccms

import kotlinx.serialization.Serializable

/** AppleCMS wire data, independent of a client's canonical content and playback models. */
@Serializable
data class MacCmsPage(val items: List<MacCmsVideo>, val page: Int, val pageCount: Int)
@Serializable
data class MacCmsVideo(
    val id: String, val title: String, val year: String, val category: String, val area: String,
    val poster: String, val summary: String, val remarks: String, val doubanId: String,
    val version: String, val lines: List<MacCmsLine>,
)
@Serializable
data class MacCmsLine(val code: String, val title: String, val parts: List<MacCmsPart>)
@Serializable
data class MacCmsPart(val label: String, val uri: String, val directlyPlayable: Boolean)
@Serializable
data class MacCmsQuery(val keyword: String = "", val page: Int = 1)
