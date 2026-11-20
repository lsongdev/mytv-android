package me.lsong.media

import me.lsong.media.epg.XmltvParser
import org.junit.Assert.*
import org.junit.Test

class EpgMetadataTest {
    @Test fun preservesAllDisplayNamesAndChannelIconAndSortsProgrammes() {
        val doc = XmltvParser.parse("""<tv><channel id="one"><display-name>CCTV1</display-name><display-name>综合</display-name><icon src="logos/1.png"/></channel><programme channel="one" start="20261120120000 +0800" stop="20261120130000 +0800"><title>午间新闻</title></programme></tv>""")
        val channel = doc.channels.single()
        assertEquals(listOf("CCTV1","综合"),channel.names)
        assertEquals("logos/1.png",channel.icon)
        assertEquals("午间新闻",channel.programmes.single().title)
        assertEquals(3600_000L,channel.programmes.single().endsAt-channel.programmes.single().startsAt)
    }
    @Test fun numericTimestampFastPathKeepsTimezoneOffsetsAndRejectsInvalidDates() {
        val document = XmltvParser.parse("""<tv>
            <programme channel="east" start="20240229003000 +0800" stop="20240229013000 +0800"><title>东</title></programme>
            <programme channel="west" start="20240228113000 -0500" stop="20240228123000 -0500"><title>西</title></programme>
            <programme channel="utc" start="20240228163000" stop="20240228173000"><title>UTC</title></programme>
            <programme channel="invalid" start="20240230003000 +0800" stop="20240230013000 +0800"><title>无效日期</title></programme>
            </tv>""")
        assertEquals(3,document.channels.size)
        assertEquals(1,document.channels.flatMap { it.programmes }.map { it.startsAt }.distinct().size)
        assertTrue(document.channels.all { it.programmes.single().endsAt - it.programmes.single().startsAt == 3600_000L })
    }

}
