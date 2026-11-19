package me.lsong.mytv.domain.live

import me.lsong.mytv.domain.provider.ProviderConfig

interface LiveCatalogProvider {
    suspend fun cached(provider: ProviderConfig): ProviderLiveCatalog? = null
    suspend fun fetch(provider: ProviderConfig): ProviderLiveCatalog
}
