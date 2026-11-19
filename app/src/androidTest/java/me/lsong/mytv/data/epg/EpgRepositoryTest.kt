package me.lsong.mytv.data.epg

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.lsong.media.epg.*
import me.lsong.mytv.data.preferences.*
import me.lsong.mytv.data.providers.cache.ProviderStorage
import org.junit.Assert.*
import org.junit.Test

class EpgRepositoryTest {
    @Test fun publishesCachedScheduleWithoutNetworkAndTracksExternalSettingsChanges() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "epg-test-${UUID.randomUUID()}"
        val prefs = context.getSharedPreferences(id,Context.MODE_PRIVATE)
        val directory = File(context.cacheDir,id)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val preferences = LivePreferences(prefs)
            val url = "https://cached.invalid/epg.xml"
            preferences.update(LiveOptions(epgUrls = setOf(url)))
            val storage = ProviderStorage(directory,prefs)
            val feed = EpgFeed(url)
            val document = EpgDocument(listOf(EpgChannel("one","新闻",listOf(EpgProgramme("one","现在",1000,2000)),icon = "logos/one.png")))
            storage.get(feed.provider,"epg",EpgDocument.serializer()) { document }
            val repository = EpgRepository(preferences,storage)
            scope.launch { repository.observe(flowOf(emptyList())) }
            val state = withTimeout(5000) { repository.state.first { !it.loading && it.programmes.isNotEmpty() } }
            assertEquals("现在",state.programmes.single().programmes.single().title)
            assertEquals("https://cached.invalid/logos/one.png",state.programmes.single().icon)
            assertEquals(0,storage.state(feed.provider)?.failures ?: -1)
            preferences.update(preferences.options.value.copy(epgEnabled = false))
            withTimeout(5000) { repository.state.first { it.programmes.isEmpty() && it.feeds.isEmpty() } }
            withContext(Dispatchers.Main) { prefs.edit().putStringSet("EPG_URL_LIST",setOf("https://external/epg")).commit() }
            withTimeout(5000) { preferences.options.first { it.epgUrls == setOf("https://external/epg") } }
        } finally { scope.cancel(); directory.deleteRecursively(); prefs.edit().clear().commit() }
    }
}
