package me.lsong.mytv.domain.video

import me.lsong.mytv.domain.provider.ProviderConfig

interface VideoCatalogProvider {
    suspend fun cachedCategories(source: ProviderConfig): List<ProviderVideoCategory>? = null
    suspend fun categories(source: ProviderConfig, force: Boolean = false): List<ProviderVideoCategory>
    suspend fun cachedPage(source: ProviderConfig, query: VideoCatalogQuery): VideoPage? = null
    suspend fun page(source: ProviderConfig, query: VideoCatalogQuery, force: Boolean = false): VideoPage
    suspend fun details(source: ProviderConfig, remoteId: String): ProviderVideoRecord
}
