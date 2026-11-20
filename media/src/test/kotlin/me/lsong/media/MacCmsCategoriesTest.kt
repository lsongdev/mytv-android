package me.lsong.media

import kotlinx.coroutines.runBlocking
import me.lsong.media.http.MediaEndpoint
import me.lsong.media.http.MediaHttpClient
import me.lsong.media.vod.maccms.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class MacCmsCategoriesTest {
    @Test fun categoriesRetainHierarchyAndDoNotDependOnLoadedVideos() {
        val rows = MacCmsParser.categories("""{"code":1,"list":[],"class":[
          {"type_id":1,"type_name":"电影","type_pid":0},
          {"type_id":"6","type_name":"动作片","type_pid":"1"},
          {"type_id":"7","type_name":"源自定义分类"},
          {"type_id":8,"type_name":""},{"type_name":"无ID"},
          {"type_id":6,"type_name":"重复ID"}]}""")
        assertEquals(listOf("电影","动作片","源自定义分类"),rows.map { it.title })
        assertNull(rows[0].parentId)
        assertEquals("1",rows[1].parentId)
        assertTrue(runCatching { MacCmsParser.categories("""{"code":0,"class":[]}""") }.isFailure)
        assertTrue(runCatching { MacCmsParser.categories("""{"code":1,"list":[]}""") }.isFailure)
    }

    @Test fun categoryQueriesUseProviderIdsAndClearFiltersForSearchAndDetails() = runBlocking {
        val requests = mutableListOf<Request>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body("""{"code":1,"page":2,"pagecount":3,"class":[{"type_id":90,"type_name":"电影"}],"list":[{"vod_id":7,"vod_name":"测试"}]}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        try {
            val client = MacCmsClient(MediaHttpClient(http))
            val endpoint = MediaEndpoint("https://example.com/api?token=keep&t=old&wd=old&ids=old&pg=9")
            assertEquals("90",client.categories(endpoint).single().id)
            client.page(endpoint,MacCmsQuery(page = 2,categoryId = "90"))
            client.page(endpoint,MacCmsQuery(keyword = "测试"))
            client.details(endpoint,"7")
            val urls = requests.map { it.url }
            assertEquals("list",urls[0].queryParameter("ac"))
            listOf("t","wd","pg","ids").forEach { assertNull(urls[0].queryParameter(it)) }
            assertEquals("90",urls[1].queryParameter("t")); assertEquals("2",urls[1].queryParameter("pg"))
            assertNull(urls[1].queryParameter("wd")); assertNull(urls[1].queryParameter("ids"))
            assertNull(urls[2].queryParameter("t")); assertEquals("测试",urls[2].queryParameter("wd"))
            listOf("t","wd","pg").forEach { assertNull(urls[3].queryParameter(it)) }
            assertTrue(urls.all { it.queryParameter("token") == "keep" })
        } finally { http.dispatcher.executorService.shutdown() }
    }
}
