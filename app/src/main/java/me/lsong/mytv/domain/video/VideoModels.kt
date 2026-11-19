package me.lsong.mytv.domain.video

import me.lsong.mytv.domain.playback.PlaybackResource
import me.lsong.mytv.domain.provider.ProviderRecordRef

/** Provider identities are retained even when several entries describe one work. */
enum class VideoKind(val title: String) {
    MOVIE("电影"), SERIES("电视剧"), ANIME("动漫"), VARIETY("综艺"), DOCUMENTARY("纪录片"), SHORT("短剧"), SPORTS("体育"), OTHER("其他")
}
data class ProviderVideoRecord(
    val ref: ProviderRecordRef, val providerName: String, val title: String, val year: String = "",
    val kind: VideoKind = VideoKind.OTHER, val category: String = "", val area: String = "",
    val poster: String = "", val summary: String = "", val remarks: String = "",
    val doubanId: String = "", val version: String = "", val lines: List<ProviderPlaybackLine> = emptyList(),
)
data class ProviderPlaybackLine(val id: String, val title: String, val parts: List<ProviderVideoPartRecord>)
data class ProviderVideoPartRecord(val label: String, val resource: PlaybackResource?)
data class VideoPage(val items: List<ProviderVideoRecord>, val page: Int, val pageCount: Int, val error: String? = null)
data class VideoWork(val id: String, val variants: List<ProviderVideoRecord>) {
    val item: ProviderVideoRecord get() = variants.first()
    val title get() = item.title
    val poster get() = variants.firstOrNull { it.poster.isNotBlank() }?.poster.orEmpty()
    val providerCount get() = variants.map { it.ref.providerId }.distinct().size
}
data class VideoPart(val id: String, val title: String, val order: Long, val resources: List<PlaybackResource>)
data class VideoDetails(val work: VideoWork, val parts: List<VideoPart>, val errors: Map<String, String> = emptyMap())
data class VideoPlaybackSelection(val details: VideoDetails, val partId: String, val resourceId: String) {
    val part get() = details.parts.first { it.id == partId }
    val resource get() = part.resources.first { it.id == resourceId }
    val title get() = "${details.work.title} · ${part.title}"
}
