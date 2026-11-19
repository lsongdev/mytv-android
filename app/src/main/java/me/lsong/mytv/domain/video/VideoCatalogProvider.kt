package me.lsong.mytv.domain.video

import me.lsong.mytv.domain.provider.ProviderConfig

interface VideoCatalogProvider {
    suspend fun cachedPage(source: ProviderConfig, query: String, page: Int): VideoPage? = null
    suspend fun page(source: ProviderConfig, query: String, page: Int, force: Boolean = false): VideoPage
    suspend fun details(source: ProviderConfig, remoteId: String): ProviderVideoRecord
}
