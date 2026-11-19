package me.lsong.media.epg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.lsong.media.http.MediaEndpoint
import me.lsong.media.http.MediaHttpClient

class EpgClient(private val http: MediaHttpClient = MediaHttpClient(maxResponseBytes = 64_000_000)) {
    suspend fun fetch(endpoint: MediaEndpoint): EpgDocument {
        val text = http.getText(endpoint)
        return withContext(Dispatchers.Default) { XmltvParser.parse(text) }
    }
}
