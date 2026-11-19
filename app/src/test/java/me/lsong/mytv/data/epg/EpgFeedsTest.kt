package me.lsong.mytv.data.epg

import me.lsong.mytv.data.preferences.LiveOptions
import me.lsong.mytv.domain.live.ProviderLiveCatalog
import me.lsong.mytv.domain.provider.*
import org.junit.Assert.*
import org.junit.Test

class EpgFeedsTest {
    private val a = ProviderConfig(id = "a",name = "A",url = "https://a/list.m3u",kind = ProviderKind.M3U)
    private val b = a.copy(id = "b")
    @Test fun sameDiscoveredUrlDownloadsOnceAndKeepsBothOwners() {
        val feeds = epgFeeds(listOf(ProviderLiveCatalog(a,epgUrls = listOf("https://a/epg.xml")),ProviderLiveCatalog(b,epgUrls = listOf("https://a/epg.xml"))),LiveOptions())
        assertEquals(1,feeds.size); assertEquals(setOf("a","b"),feeds.single().owners)
    }
    @Test fun explicitUrlHasGlobalScopeAndDiscoveryCanBeDisabled() {
        val catalogs = listOf(ProviderLiveCatalog(a,epgUrls = listOf("https://a/epg.xml")))
        assertTrue(epgFeeds(catalogs,LiveOptions(epgUrls = setOf("https://a/epg.xml"))).single().owners.isEmpty())
        assertTrue(epgFeeds(catalogs,LiveOptions(discoverEpg = false)).isEmpty())
    }
    @Test fun defaultFeedRequiresExplicitOptInAndNoOtherFeeds() {
        assertTrue(epgFeeds(emptyList(),LiveOptions()).isEmpty())
        assertEquals(1,epgFeeds(emptyList(),LiveOptions(useDefaultEpg = true)).size)
        assertEquals("https://custom/epg",epgFeeds(emptyList(),LiveOptions(useDefaultEpg = true,epgUrls = setOf("https://custom/epg"))).single().url)
    }
}
