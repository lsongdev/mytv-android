package me.lsong.mytv.domain.epg

import me.lsong.mytv.domain.epg.EpgList.Companion.getEpgChannel
import me.lsong.mytv.domain.live.LiveChannel
import org.junit.Assert.*
import org.junit.Test

class EpgMatchingTest {
    @Test fun providerScopedIdCannotMatchAnotherProvidersFeed() {
        val wrong = EpgChannel(id = "1",providerId = "other",title = "Other")
        val own = EpgChannel(id = "1",providerId = "own",title = "News")
        val channel = LiveChannel(name = "News",epgBindings = listOf(EpgBinding("own","1")))
        assertEquals(own,EpgList(listOf(wrong,own)).getEpgChannel(channel))
        assertNull(EpgList(listOf(wrong)).getEpgChannel(channel))
    }
    @Test fun customFeedMatchesTvgIdBeforeNames() {
        val exact = EpgChannel(id = "cctv1",title = "综合",names = listOf("CCTV 1"))
        val byName = EpgChannel(id = "different",title = "CCTV-1 HD")
        val channel = LiveChannel(name = "CCTV-1 HD",epgBindings = listOf(EpgBinding("own","cctv1")))
        assertEquals(exact,EpgList(listOf(byName,exact)).getEpgChannel(channel))
    }
    @Test fun displayNameAliasesNormalizeWidthWhitespaceAndHdSuffix() {
        val feed = EpgChannel(id = "remote",title = "综合",names = listOf("CCTV 1"))
        assertEquals(feed,EpgList(listOf(feed)).getEpgChannel(LiveChannel(name = "ＣＣＴＶ－１ 高清")))
        assertNull(EpgList(listOf(feed)).getEpgChannel(LiveChannel(name = "CCTV 10")))
    }
    @Test fun staleScopedOrDuplicateFeedsDoNotHideCurrentProgrammes() {
        val now = System.currentTimeMillis()
        val stale = EpgChannel(id = "one",providerId = "own",title = "频道",programmes = listOf(EpgProgramme("one",now-20000,now-10000,"旧节目")))
        val current = stale.copy(programmes = listOf(EpgProgramme("one",now-1000,now+10000,"当前节目")))
        val channel = LiveChannel(name = "频道",epgBindings = listOf(EpgBinding("own","one")))
        assertEquals(current,EpgList(listOf(current,stale)).getEpgChannel(channel))
        assertEquals(current.copy(providerId = null),EpgList(listOf(stale,current.copy(providerId = null))).getEpgChannel(channel))
    }

}
