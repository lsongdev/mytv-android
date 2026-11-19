package me.lsong.mytv.data.providers

import me.lsong.media.http.MediaEndpoint
import me.lsong.mytv.app.AppContainer
import me.lsong.media.m3u.M3uClient
import me.lsong.media.vod.maccms.MacCmsClient
import me.lsong.mytv.domain.provider.ProviderCheck
import me.lsong.mytv.domain.provider.ProviderConfig
import me.lsong.mytv.domain.provider.ProviderKind

object ProviderProbe {
    suspend fun check(provider: ProviderConfig): ProviderCheck.Result = AppContainer.providerStorage.measure(provider) {
        val endpoint = MediaEndpoint(provider.url)
        when (provider.kind) {
            ProviderKind.M3U -> {
                val document = M3uClient().fetch(endpoint)
                ProviderCheck.Result("连接成功 · ${document.entries.size} 个频道", document.entries.isNotEmpty())
            }
            ProviderKind.MAC_CMS -> {
                val page = MacCmsClient().page(endpoint)
                ProviderCheck.Result("连接成功 · 本页 ${page.items.size} 部影片", true)
            }
        }
    }
}
