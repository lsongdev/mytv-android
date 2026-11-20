package me.lsong.mytv.data.providers

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import me.lsong.media.http.MediaHttpClient
import me.lsong.media.vod.maccms.MacCmsClient
import me.lsong.mytv.data.providers.cache.ProviderStorage
import me.lsong.mytv.data.providers.maccms.MacCmsVideoProvider
import me.lsong.mytv.domain.provider.*
import me.lsong.mytv.domain.video.VideoCatalogQuery
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class MacCmsCategoriesTest {
    @Test fun categoryFailuresDoNotBlockPagesAndCachesSeparateCategoryQueries() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "category-cache-${UUID.randomUUID()}"
        val prefs = context.getSharedPreferences(id,Context.MODE_PRIVATE)
        val directory = File(context.cacheDir,id)
        val storage = ProviderStorage(directory,prefs)
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val categoryRequest = request.url.queryParameter("ac") == "list"
            val category = request.url.queryParameter("t").orEmpty()
            val body = if (categoryRequest) "forbidden" else """{"code":1,"list":[{"vod_id":"$category","vod_name":"分类$category"}]}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(if (categoryRequest) 403 else 200)
                .message("fixture").body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        try {
            val provider = MacCmsVideoProvider(MacCmsClient(MediaHttpClient(http)),storage)
            val source = ProviderConfig("source","源","https://example.com/api",ProviderKind.MAC_CMS)
            assertTrue(runCatching { provider.categories(source) }.isFailure)
            assertNull(storage.state(source))
            val first = VideoCatalogQuery(categoryId = "1")
            val second = VideoCatalogQuery(categoryId = "90")
            assertEquals("1",provider.page(source,first).items.single().ref.remoteId)
            assertEquals("90",provider.page(source,second).items.single().ref.remoteId)
            assertEquals("1",provider.cachedPage(source,first)!!.items.single().ref.remoteId)
            assertEquals("90",provider.cachedPage(source,second)!!.items.single().ref.remoteId)
            assertEquals(0,storage.state(source)!!.failures)
        } finally { http.dispatcher.executorService.shutdown(); directory.deleteRecursively(); prefs.edit().clear().commit() }
    }

    @Test fun categoriesPersistIndependentlyOfVideoPagesAndSurviveRefreshFailures() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "category-persist-${UUID.randomUUID()}"
        val prefs = context.getSharedPreferences(id,Context.MODE_PRIVATE)
        val directory = File(context.cacheDir,id)
        var requests = 0
        var offline = false
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            if (offline) throw java.io.IOException("offline")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body("""{"code":1,"list":[],"class":[{"type_id":90,"type_name":"电影"},{"type_id":96,"type_name":"动作片","type_pid":90}]}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        try {
            val client = MacCmsClient(MediaHttpClient(http))
            val source = ProviderConfig("source","源","https://example.com/api",ProviderKind.MAC_CMS)
            val provider = MacCmsVideoProvider(client,ProviderStorage(directory,prefs))
            val categories = provider.categories(source)
            assertEquals(1,requests)
            offline = true
            val restored = MacCmsVideoProvider(client,ProviderStorage(directory,prefs))
            assertEquals(categories,restored.cachedCategories(source))
            assertEquals(categories,restored.categories(source))
            assertEquals(1,requests)
            assertEquals(categories,restored.categories(source,force = true))
            assertEquals("90",categories.last().parentId)
            assertTrue(requests > 1)
        } finally { http.dispatcher.executorService.shutdown(); directory.deleteRecursively(); prefs.edit().clear().commit() }
    }
}
