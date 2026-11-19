package me.lsong.media.m3u

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.lsong.media.http.MediaEndpoint
import me.lsong.media.http.MediaHttpClient

class M3uClient(private val http: MediaHttpClient = MediaHttpClient()) {
    suspend fun fetch(endpoint: MediaEndpoint): M3uDocument {
        val text = http.getText(endpoint)
        return withContext(Dispatchers.Default) {
            require(text.trimStart().startsWith("#EXTM3U")) { "Response is not an extended M3U playlist" }
            M3uParser.parse(text)
        }
    }
}
