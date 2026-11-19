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
}
