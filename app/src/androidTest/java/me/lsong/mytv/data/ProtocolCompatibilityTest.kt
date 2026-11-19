package me.lsong.mytv.data

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import me.lsong.media.epg.XmltvParser
import me.lsong.mytv.data.preferences.PreferencesCatalogIdentityStore
import me.lsong.mytv.domain.identity.CatalogIdentityRequest
import me.lsong.mytv.domain.provider.ProviderRecordRef
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class ProtocolCompatibilityTest {
    @Test fun xmltvParserRunsOnAndroidSax() {
        val document = XmltvParser.parse("""<tv><channel id="one"><display-name>第一台</display-name></channel><programme channel="one" start="20260101080000 +0800" stop="20260101090000 +0800"><title>早间新闻</title></programme></tv>""")
        assertEquals("早间新闻", document.channels.single().programmes.single().title)
    }
    @Test fun canonicalIdsPersistWithoutDependingOnTheTitle() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("identity-test-${UUID.randomUUID()}", Context.MODE_PRIVATE)
        try {
            val request = CatalogIdentityRequest(listOf(ProviderRecordRef("a", "1")), "first title")
            val id = PreferencesCatalogIdentityStore(prefs).resolveAll("live", listOf(request)).single()
            UUID.fromString(id)
            val restored = PreferencesCatalogIdentityStore(prefs)
            assertEquals(id, restored.resolveAll("live", listOf(request.copy(matchKey = "new title"))).single())
        } finally { prefs.edit().clear().commit() }
    }
}
