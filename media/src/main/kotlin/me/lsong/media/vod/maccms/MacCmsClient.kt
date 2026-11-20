package me.lsong.media.vod.maccms

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.lsong.media.http.MediaEndpoint
import me.lsong.media.http.MediaHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException

class MacCmsClient(private val http: MediaHttpClient = MediaHttpClient()) {
    suspend fun page(endpoint: MediaEndpoint, query: MacCmsQuery = MacCmsQuery()): MacCmsPage {
        require(query.page > 0) { "Page must be positive" }
        val uri = endpoint.uri.toHttpUrl().newBuilder()
            .setQueryParameter("ac", "detail")
            .setQueryParameter("pg", query.page.toString())
            .removeAllQueryParameters("ids")
            .apply {
                if (query.categoryId.isNullOrBlank()) removeAllQueryParameters("t")
                else setQueryParameter("t", query.categoryId)
                if (query.keyword.isBlank()) removeAllQueryParameters("wd")
                else setQueryParameter("wd", query.keyword)
            }.build()
        val text = http.getText(endpoint.copy(uri = uri.toString()))
        return withContext(Dispatchers.Default) { MacCmsParser.page(endpoint.uri, text) }
    }

    suspend fun categories(endpoint: MediaEndpoint): List<MacCmsCategory> {
        val uri = endpoint.uri.toHttpUrl().newBuilder().setQueryParameter("ac", "list")
            .removeAllQueryParameters("t").removeAllQueryParameters("wd")
            .removeAllQueryParameters("ids").removeAllQueryParameters("pg").build()
        val text = http.getText(endpoint.copy(uri = uri.toString()))
        return withContext(Dispatchers.Default) { MacCmsParser.categories(text) }
    }

    suspend fun details(endpoint: MediaEndpoint, remoteId: String): MacCmsVideo {
        require(remoteId.isNotBlank()) { "Video ID must not be blank" }
        val uri = endpoint.uri.toHttpUrl().newBuilder()
            .setQueryParameter("ac", "detail")
            .setQueryParameter("ids", remoteId)
            .removeAllQueryParameters("wd")
            .removeAllQueryParameters("t")
            .removeAllQueryParameters("pg")
            .build()
        val text = http.getText(endpoint.copy(uri = uri.toString()))
        return withContext(Dispatchers.Default) {
            MacCmsParser.page(endpoint.uri, text, includeLines = true).items.firstOrNull { it.id == remoteId }
        } ?: throw IOException("Requested video was not returned")
    }
}
