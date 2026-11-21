package me.lsong.mytv.domain.video

import me.lsong.mytv.domain.provider.ProviderRecordRef

import me.lsong.mytv.domain.video.ProviderPlaybackLine

import me.lsong.mytv.domain.video.ProviderVideoPartRecord
import me.lsong.mytv.domain.video.ProviderVideoRecord
import me.lsong.mytv.domain.video.VideoAggregator
import me.lsong.mytv.domain.video.VideoKind

import kotlinx.serialization.json.*
import me.lsong.mytv.domain.provider.ProviderConfig
import me.lsong.mytv.domain.provider.ProviderKind
import org.junit.Assert.*
import org.junit.Test
import me.lsong.mytv.domain.playback.PlaybackResource
import me.lsong.mytv.domain.identity.InMemoryCatalogIdentityStore
import me.lsong.media.vod.maccms.MacCmsParser
import me.lsong.mytv.data.providers.maccms.toRecord

class VideoAggregatorTest {
    private fun item(source: String, title: String = "测试剧", year: String = "2024", id: String = "1", douban: String = "") =
        ProviderVideoRecord(ProviderRecordRef(source, id), source, title, year, VideoKind.SERIES, "国产剧", "中国大陆", doubanId = douban)
    private fun withEpisodes(item: ProviderVideoRecord, vararg labels: String) = item.copy(lines = listOf(ProviderPlaybackLine("${item.ref.providerId}:line", "线路 1", labels.mapIndexed { i, label -> ProviderVideoPartRecord(label, PlaybackResource("https://example.com/${item.ref.providerId}/$i.m3u8", item.ref, item.providerName)) })))

    @Test fun largeUnrelatedCatalogueDoesNotRequirePairwiseFullScan() {
        val records = (0 until 3_000).map { index -> item("source", "不同节目$index", id = index.toString()) }
        val works = VideoAggregator.merge(records)
        assertEquals(records.size, works.size)
    }
    @Test fun manyConflictingIdsWithIdenticalTitleDoNotMerge() {
        val records = (0 until 2_000).map { index ->
            item("source", "同名影片", id = index.toString(), douban = (index + 1).toString())
        }
        val works = VideoAggregator.merge(records)
        assertEquals(records.size, works.size)
        val withUnknown = VideoAggregator.merge(records + item("other", "同名影片", id = "no-douban"))
        assertEquals(records.size, withUnknown.size)
        assertEquals(2, withUnknown.first().providerCount)
    }
    @Test fun sameTitleYearAndKindCombineWithoutLosingSources() {
        val merged = VideoAggregator.merge(listOf(item("a", "测 试 剧"), item("b")))
        assertEquals(1, merged.size)
        assertEquals(2, merged.single().providerCount)
        assertEquals(setOf("a", "b"), merged.single().variants.map { it.ref.providerId }.toSet())
    }

    @Test fun remakesSeasonsAndCutsStaySeparate() {
        assertEquals(2, VideoAggregator.merge(listOf(item("a"), item("b", year = "2025"))).size)
        assertEquals(2, VideoAggregator.merge(listOf(item("a", "测试剧第一季"), item("b", "测试剧第二季"))).size)
        assertEquals(2, VideoAggregator.merge(listOf(item("a", douban = "123"), item("b", douban = "123").copy(version = "导演剪辑"))).size)
        assertEquals(2, VideoAggregator.merge(listOf(item("a"), item("b").copy(kind = VideoKind.MOVIE))).size)
    }

    @Test fun missingYearDoesNotCauseNameOnlyMerge() {
        assertEquals(2, VideoAggregator.merge(listOf(item("a", year = ""), item("b", year = ""))).size)
        assertEquals(1, VideoAggregator.merge(listOf(item("a", year = "", douban = "123"), item("b", year = "", douban = "123"))).size)
    }

    @Test fun unidentifiedEntryCannotBridgeConflictingExternalIds() {
        val works = VideoAggregator.merge(listOf(item("a", douban = "111"), item("b"), item("c", douban = "222")))
        assertEquals(2, works.size)
        assertFalse(works.any { work -> work.variants.map { it.doubanId }.containsAll(listOf("111", "222")) })
    }

    @Test fun providerLocalIdsAreNotGlobalIdentities() {
        assertEquals(2, VideoAggregator.merge(listOf(item("a", "甲"), item("b", "乙"))).size)
    }

    @Test fun canonicalIdentitySurvivesNewProviderMetadata() {
        val identities = InMemoryCatalogIdentityStore()
        val first = VideoAggregator.merge(listOf(item("b")), identities)
        val next = VideoAggregator.merge(listOf(item("a", douban = "123"), item("b")), identities)
        assertEquals(first.single().id, next.single().id)
    }

    @Test fun richerMetadataSplitsAnEarlierMatchIntoDistinctWorks() {
        val identities = InMemoryCatalogIdentityStore()
        VideoAggregator.merge(listOf(item("a"), item("b")), identities)
        val split = VideoAggregator.merge(listOf(item("a", douban = "111"), item("b", douban = "222")), identities)
        assertEquals(2, split.map { it.id }.toSet().size)
    }

    @Test fun episodeMatchingUsesNumbersAndRetainsMissingCoverage() {
        val a = withEpisodes(item("a"), "第01集", "第03集")
        val b = withEpisodes(item("b"), "EP1", "02", "第3集")
        val work = VideoAggregator.merge(listOf(a, b)).single()
        val detail = VideoAggregator.details(work, listOf(a, b))
        assertEquals(listOf("s1e1", "s1e2", "s1e3"), detail.parts.map { it.id })
        assertEquals(listOf(2, 1, 2), detail.parts.map { it.resources.size })
        assertEquals("b", detail.parts[1].resources.single().origin.providerId)
    }

    @Test fun unknownEpisodeLabelsNeverMatchByTheirListPosition() {
        val a = withEpisodes(item("a"), "特别篇")
        val b = withEpisodes(item("b"), "花絮")
        val work = VideoAggregator.merge(listOf(a, b)).single()
        assertEquals(2, VideoAggregator.details(work, listOf(a, b)).parts.size)
    }

    @Test fun movieQualityLabelsRepresentTheSameFeature() {
        val a = withEpisodes(item("a").copy(kind = VideoKind.MOVIE), "正片")
        val b = withEpisodes(item("b").copy(kind = VideoKind.MOVIE), "HD中字")
        val work = VideoAggregator.merge(listOf(a, b)).single()
        assertEquals(2, VideoAggregator.details(work, listOf(a, b)).parts.single().resources.size)
    }

    @Test fun parserKeepsLineBoundariesAndUrlsAndExcludesWebPlayers() {
        val d = '$'
        val source = ProviderConfig("a", "来源 A", "https://example.com/api.php/provide/vod", ProviderKind.MAC_CMS)
        val json = buildJsonObject {
            put("code", 1); put("page", 1); put("pagecount", 3)
            putJsonArray("list") { addJsonObject {
                put("vod_id", 7); put("vod_name", "测试剧"); put("vod_year", "2024"); put("type_name", "国产剧")
                put("vod_play_from", "web${d}${d}${d}m3u8")
                put("vod_play_url", "第01集${d}https://example.com/share/x${d}${d}${d}第01集${d}https://cdn.example.com/1.m3u8?token=x${d}y#第02集${d}https://cdn.example.com/2.m3u8")
            } }
        }.toString()
        val parsed = MacCmsParser.page(source.url, json, true)
        assertEquals(3, parsed.pageCount)
        val protocolItem = parsed.items.single()
        val item = protocolItem.toRecord(source)
        assertEquals(2, item.lines.size)
        assertNull(item.lines[0].parts.single().resource)
        assertEquals("https://cdn.example.com/1.m3u8?token=x${d}y", item.lines[1].parts[0].resource!!.uri)
        val detail = VideoAggregator.details(VideoAggregator.merge(listOf(item)).single(), listOf(item))
        assertEquals(2, detail.parts.size)
        assertEquals(1, detail.parts.first().resources.size)
        assertEquals("", item.poster)
    }
}
