package me.lsong.mytv.domain.video

import me.lsong.mytv.domain.provider.ProviderConfig

data class VideoBrowseKey(val categoryId: String? = null, val filterId: String? = null, val query: String = "")
data class VideoProviderProgress(
    val source: ProviderConfig, val page: Int = 0, val pages: Int = 1,
    val loading: Boolean = false, val error: String? = null, val categoryId: String? = null,
) { val key get() = source.id to categoryId }
data class VideoCategoriesState(val groups: List<VideoCategoryGroup> = emptyList(), val loading: Boolean = false, val failedProviders: Set<String> = emptySet())
data class VideoCatalogSnapshot(
    val key: VideoBrowseKey = VideoBrowseKey(), val works: List<VideoWork> = emptyList(),
    val providers: List<VideoProviderProgress> = emptyList(),
    val categoryTitle: String? = null, val filterTitle: String? = null,
    val preparing: Boolean = true, val refreshedAt: Long = 0,
) {
    val loading get() = preparing || providers.any { it.loading }
}
