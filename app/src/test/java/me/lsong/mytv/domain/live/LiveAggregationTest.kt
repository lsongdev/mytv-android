package me.lsong.mytv.domain.live

import me.lsong.media.m3u.M3uEntry
import me.lsong.mytv.data.providers.m3u.toChannelRecord
import me.lsong.mytv.domain.provider.*
import me.lsong.mytv.domain.identity.InMemoryCatalogIdentityStore
import org.junit.Assert.*
import org.junit.Test

class LiveAggregationTest {
    private fun provider(id: String) = ProviderConfig(id, "来源 $id", "https://$id.example/list", ProviderKind.M3U)
    private fun playlist(id: String, title: String = "CCTV-1", group: String = "央视", url: String = "https://$id.example/live", headers: Map<String,String> = emptyMap()) =
        ProviderLiveCatalog(provider(id), listOf(M3uEntry(mapOf("group-title" to group) + headers, title, url).toChannelRecord(provider(id))))

    @Test fun matchingChannelsKeepResourcesAndOriginsAcrossGroups() {
        val merged = mergeLiveCatalogs(listOf(playlist("a"), playlist("b", "CCTV1高清", "央视频道")))
        assertEquals(1, merged.channels.size)
        assertEquals(2, merged.groups.size)
        assertEquals(listOf("a", "b"), merged.channels.single().records.map { it.ref.providerId })
        assertEquals(listOf("来源 a", "来源 b"), merged.channels.single().resources.map { it.providerName })
    }
    @Test fun removingFirstProviderKeepsIdentity() {
        val a = playlist("a"); val b = playlist("b", "CCTV1高清")
        assertEquals(mergeLiveCatalogs(listOf(a,b)).channels.single().id, mergeLiveCatalogs(listOf(b)).channels.single().id)
    }
    @Test fun regionalNamesAndDifferentChannelsStaySeparate() {
        assertEquals(2, mergeLiveCatalogs(listOf(playlist("a", "新闻", "北京"), playlist("b", "新闻", "上海"))).channels.size)
        assertEquals(2, mergeLiveCatalogs(listOf(playlist("a", "CCTV4"), playlist("b", "CCTV4K"))).channels.size)
    }
    @Test fun duplicateUrlsDoNotRepeatFallbackAttempts() {
        assertEquals(1, mergeLiveCatalogs(listOf(playlist("a"), playlist("b", url = "https://a.example/live"))).channels.single().resources.size)
    }
    @Test fun failedAndLoadingProvidersDoNotHideSuccessfulChannels() {
        val merged = mergeLiveCatalogs(listOf(playlist("a"), ProviderLiveCatalog(provider("bad"),error="offline"), ProviderLiveCatalog(provider("slow"),isLoading=true)))
        assertEquals(1, merged.channels.size)
        assertTrue(merged.isLoading)
        assertNull(merged.error)
    }
    @Test fun blankMetadataFallsBackToTitle() {
        val record = M3uEntry(mapOf("tvg-name" to "", "tvg-id" to ""), "CCTV1", "https://example.com/1").toChannelRecord(provider("a"))
        assertEquals("CCTV1", record.name)
    }
    @Test fun epgIdKeepsCanonicalIdentityWhenTitleAndGroupChange() {
        val identities = InMemoryCatalogIdentityStore()
        fun catalog(title: String, group: String) = ProviderLiveCatalog(provider("a"), listOf(
            M3uEntry(mapOf("tvg-id" to "channel-one", "group-title" to group), title, "https://example.com/live").toChannelRecord(provider("a"))
        ))
        val before = mergeLiveCatalogs(listOf(catalog("第一台", "旧分组")), identities)
        val after = mergeLiveCatalogs(listOf(catalog("第一频道", "新分组")), identities)
        assertEquals(before.channels.single().id, after.channels.single().id)
    }

    @Test fun headerVariantsRemainAvailable() {
        val headers = mapOf("http-user-agent" to "AptvPlayer-UA", "http-header" to "Referer=https://example.com/?a=1")
        val merged = mergeLiveCatalogs(listOf(playlist("a"), playlist("b", url="https://a.example/live", headers=headers)))
        assertEquals(2, merged.channels.single().resources.size)
        assertEquals("AptvPlayer-UA", merged.channels.single().resources[1].headers["User-Agent"])
        assertEquals("https://example.com/?a=1", merged.channels.single().resources[1].headers["Referer"])
    }
}
