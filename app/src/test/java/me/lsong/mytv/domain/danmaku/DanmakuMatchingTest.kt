package me.lsong.mytv.domain.danmaku

import me.lsong.media.danmaku.*
import me.lsong.mytv.domain.video.*
import me.lsong.mytv.domain.provider.ProviderRecordRef
import org.junit.Assert.*
import org.junit.Test

class DanmakuMatchingTest {
    private val work = VideoWork("work",listOf(ProviderVideoRecord(ProviderRecordRef("a","1"),"A","测试剧", "2024",VideoKind.SERIES)))
    @Test fun conflictingYearsAreNotAutomaticMatches() {
        assertEquals(0,danmakuMatchScore(work,DanmakuMedia("a","1","测试剧",year=2025)))
        assertEquals(100,danmakuMatchScore(work,DanmakuMedia("a","1","测试剧",year=2024)))
    }
    @Test fun missingEpisodeNeverFallsBackToTheFirstEpisode() {
        val episodes = listOf(DanmakuEpisode("a","one","第1集",1),DanmakuEpisode("a","three","第3集",3))
        assertNull(matchDanmakuEpisode(work,VideoPart("s1e2","第2集",2,emptyList()),episodes))
        assertEquals("three",matchDanmakuEpisode(work,VideoPart("s1e3","第三集",3,emptyList()),episodes)?.episodeId)
    }
    @Test fun datesAndMovieFeaturesHaveSeparateMatchingRules() {
        val episodes = listOf(DanmakuEpisode("a","day","2026-11-20",1))
        assertEquals("day",matchDanmakuEpisode(work,VideoPart("date:20261120","这一期",1,emptyList()),episodes)?.episodeId)
        assertEquals("day",matchDanmakuEpisode(work.copy(variants=work.variants.map { it.copy(kind=VideoKind.MOVIE) }),VideoPart("feature","正片",0,emptyList()),episodes)?.episodeId)
    }
}
