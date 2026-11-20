package me.lsong.mytv.domain.video

import me.lsong.mytv.domain.provider.ProviderRecordRef
import org.junit.Assert.*
import org.junit.Test

class VideoCategoriesTest {
    private fun record(source: String,id: String,title: String,parent: String? = null) = ProviderVideoCategory(ProviderRecordRef(source,id),title,parent)

    @Test fun mergesGenreAliasesWithinTheirGroupWhileRetainingRemoteIds() {
        val records = listOf(record("a","1","电影"),record("a","6","动作片","1"),
            record("b","90","电影"),record("b","96","动作电影","90"),record("b","97","动作","90"),
            record("c","1","动漫"),record("c","6","动作动漫","1"))
        val groups = buildVideoCategoryGroups(records + records.first())
        assertEquals(listOf(VideoKind.MOVIE,VideoKind.ANIME),groups.map { it.kind })
        assertEquals(setOf("6","96","97"),groups[0].filters.single().records.map { it.ref.remoteId }.toSet())
        assertEquals("动作",groups[0].filters.single().title)
        assertEquals(setOf("a","b"),groups[0].records.map { it.ref.providerId }.toSet())
        assertEquals("动作动漫",groups[1].filters.single().title)
        assertNotEquals(groups[0].filters.single().id,groups[1].filters.single().id)
        assertEquals(groups.map { it.id },buildVideoCategoryGroups(records.reversed()).map { it.id })
    }

    @Test fun hierarchyIsScopedAndUnknownChildrenInheritKnownParents() {
        val groups = buildVideoCategoryGroups(listOf(record("a","1","连续剧"),
            record("a","2","悬疑","1"),record("a","3","特别专题","2"),
            record("b","1","电影"),record("b","2","悬疑片","1")))
        val series = groups.first { it.kind == VideoKind.SERIES }
        assertEquals(setOf("1","2","3"),series.records.map { it.ref.remoteId }.toSet())
        assertEquals(setOf("2","3"),series.filters.first { it.title == "悬疑" }.records.map { it.ref.remoteId }.toSet())
        assertTrue(series.records.all { it.ref.providerId == "a" })
        assertEquals("悬疑",groups.first { it.kind == VideoKind.MOVIE }.filters.single().title)
    }

    @Test fun explicitAnimeDocumentaryAndShortDramaKeepTheirOwnGroups() {
        val groups = buildVideoCategoryGroups(listOf(record("a","1","电影"),record("a","2","动漫电影","1"),
            record("a","3","纪录片","1"),record("a","4","连续剧"),record("a","5","短剧","4"),record("a","6","国漫")))
        assertEquals(setOf(VideoKind.MOVIE,VideoKind.SERIES,VideoKind.ANIME,VideoKind.DOCUMENTARY,VideoKind.SHORT),groups.map { it.kind }.toSet())
        val anime = groups.first { it.kind == VideoKind.ANIME }
        assertEquals(setOf("2","6"),anime.records.map { it.ref.remoteId }.toSet())
        assertEquals(setOf("剧场版","国产"),anime.filters.map { it.title }.toSet())
    }

    @Test fun regionAliasesMergeAndUncertainCategoriesRemainDiscoverable() {
        val groups = buildVideoCategoryGroups(listOf(record("a","1","国产剧"),record("b","2","大陆剧"),
            record("a","7","原始专题"),record("b","8","神秘资源")))
        assertEquals(listOf(VideoKind.SERIES,VideoKind.OTHER),groups.map { it.kind })
        assertEquals("国产",groups.first().filters.single().title)
        assertEquals(2,groups.first().filters.single().records.size)
        assertEquals(setOf("原始专题","神秘资源"),groups.last().filters.map { it.title }.toSet())
        assertTrue(buildVideoCategoryGroups(emptyList()).isEmpty())
    }

    @Test fun malformedParentCyclesTerminateWithoutLosingRecords() {
        val groups = buildVideoCategoryGroups(listOf(record("a","1","电影","2"),record("a","2","动作片","1"),record("a","3","自引用","3")))
        assertEquals(3,groups.sumOf { it.records.size })
        assertEquals(2,groups.first { it.kind == VideoKind.MOVIE }.records.size)
    }
}
