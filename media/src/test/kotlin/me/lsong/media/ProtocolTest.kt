package me.lsong.media

import me.lsong.media.m3u.M3uParser
import me.lsong.media.epg.XmltvParser
import me.lsong.media.vod.maccms.MacCmsParser
import org.junit.Assert.*
import org.junit.Test

class ProtocolTest {
    @Test fun m3uKeepsQuotedCommasAndMetadata() {
        val text = "\uFEFF#EXTM3U x-tvg-url=\"https://a/epg.xml,https://b/epg.xml\"\r\n#EXTINF:-1 tvg-id=\"one\" tvg-name=\"Name, HD\" group-title=\"央视\",CCTV1\r\nhttps://example.com/live\r\n"
        val result = M3uParser.parse(text)
        assertEquals("https://a/epg.xml,https://b/epg.xml", result.headers["x-tvg-url"])
        assertEquals("Name, HD", result.entries.single().attributes["tvg-name"])
        assertEquals("CCTV1", result.entries.single().title)
        assertEquals("https://example.com/live", result.entries.single().uri)
    }
    @Test fun missingExtinfTitleDoesNotCrashOrLeakMetadata() {
        val result = M3uParser.parse("#EXTM3U\n#EXTINF:-1 tvg-id=\"one\"\nhttps://example.com/1\nhttps://example.com/2")
        assertEquals("one", result.entries[0].attributes["tvg-id"])
        assertTrue(result.entries[1].attributes.isEmpty())
    }
    @Test fun xmltvReadsOffsetsCdataAndProgrammesBeforeChannels() {
        val result = XmltvParser.parse("""<tv><programme channel="one" start="20260101080000 +0800" stop="20260101090000 +0800"><title><![CDATA[A & B]]></title></programme><channel id="one"><display-name>第一台</display-name></channel></tv>""")
        val channel = result.channels.single()
        assertEquals("第一台", channel.title)
        assertEquals("A & B", channel.programmes.single().title)
        assertEquals(1767225600000L, channel.programmes.single().startsAt)
        assertEquals(3600000L, channel.programmes.single().endsAt - channel.programmes.single().startsAt)
    }
    @Test fun xmltvRejectsExternalEntities() {
        assertTrue(runCatching { XmltvParser.parse("""<!DOCTYPE tv [<!ENTITY leak SYSTEM "file:///etc/passwd">]><tv/>""") }.isFailure)
    }
    @Test fun macCmsRetainsLineBoundariesAndDollarInUrl() {
        val dollar = '$'
        val json = """{"code":1,"list":[{"vod_id":7,"vod_name":"测试","vod_pic":"/cover.jpg","vod_play_from":"web${dollar}${dollar}${dollar}m3u8","vod_play_url":"正片${dollar}/share/a${dollar}${dollar}${dollar}第1集${dollar}https://cdn.example.com/1.m3u8?token=a${dollar}b#第2集${dollar}/2.m3u8"}],"pagecount":3}"""
        val result = MacCmsParser.page("https://example.com/api", json, true)
        val video = result.items.single()
        assertEquals(3, result.pageCount)
        assertEquals("https://example.com/cover.jpg", video.poster)
        assertFalse(video.lines[0].parts.single().directlyPlayable)
        assertEquals("https://cdn.example.com/1.m3u8?token=a${dollar}b", video.lines[1].parts[0].uri)
        assertEquals("https://example.com/2.m3u8", video.lines[1].parts[1].uri)
    }
    @Test fun macCmsRejectsFailureAndNonCatalogPayloads() {
        assertTrue(runCatching { MacCmsParser.page("https://example.com", """{"code":0,"list":[]}""") }.isFailure)
        assertTrue(runCatching { MacCmsParser.page("https://example.com", "{}") }.isFailure)
    }
}
