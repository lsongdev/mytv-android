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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody

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
    @org.junit.Test fun failedPrimaryUsesFallbackAndHealthyPrimaryDoesNotRequestIt() = runBlocking<Unit> {
        for (broken in listOf(true,false)) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val id = "epg-fallback-${UUID.randomUUID()}"
            val prefs = context.getSharedPreferences(id,Context.MODE_PRIVATE)
            val directory = File(context.cacheDir,id)
            val scope = CoroutineScope(SupervisorJob()+Dispatchers.Default)
            val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
            val format = java.text.SimpleDateFormat("yyyyMMddHHmmss Z",java.util.Locale.ROOT)
            val now = System.currentTimeMillis()
            val xml = """<tv><channel id="one"><display-name>频道</display-name></channel><programme channel="one" start="${format.format(java.util.Date(now-60000))}" stop="${format.format(java.util.Date(now+60000))}"><title>当前节目</title></programme></tv>"""
            val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
                val url = chain.request().url.toString(); requests += url
                val failed = broken && chain.request().url.host == "primary.example"
                okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(if (failed) 403 else 200).message("fixture")
                    .body((if (failed) "forbidden" else xml).toResponseBody("application/xml".toMediaType())).build()
            }.build()
            try {
                val preferences = LivePreferences(prefs)
                preferences.update(LiveOptions(epgUrls = setOf("https://primary.example/epg"),useDefaultEpg = true))
                val repository = EpgRepository(preferences,ProviderStorage(directory,prefs),EpgClient(me.lsong.media.http.MediaHttpClient(client)))
                scope.launch { repository.observe(flowOf(emptyList())) }
                val state = withTimeout(5000) { repository.state.first { !it.loading && it.programmes.any { it.programmes.isNotEmpty() } } }
                assertEquals("当前节目",state.programmes.single().programmes.single().title)
                assertEquals(if (broken) 2 else 1,requests.size)
                if (broken) assertEquals(me.lsong.mytv.app.AppConstants.EPG_XML_URL,requests.last())
            } finally {
                scope.cancel(); client.dispatcher.executorService.shutdown(); directory.deleteRecursively(); prefs.edit().clear().commit()
            }
        }
    }

}
