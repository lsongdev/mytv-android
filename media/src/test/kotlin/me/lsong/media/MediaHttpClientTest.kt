package me.lsong.media

import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.zip.GZIPOutputStream
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import me.lsong.media.http.MediaEndpoint
import me.lsong.media.http.MediaHttpClient
import me.lsong.media.m3u.M3uClient
import me.lsong.media.epg.EpgClient
import org.junit.Assert.*
import org.junit.Test

class MediaHttpClientTest {
    private fun serve(bytes: ByteArray, action: (String) -> Unit) {
        ServerSocket(0).use { server ->
            val worker = thread(isDaemon = true) {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val input = socket.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) { }
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(bytes)
                        flush()
                    }
                }
            }
            try { action("http://127.0.0.1:${server.localPort}/feed") } finally { worker.join(5000) }
        }
    }
    private fun gzip(text: String): ByteArray = ByteArrayOutputStream().apply {
        GZIPOutputStream(this).use { it.write(text.toByteArray()) }
    }.toByteArray()

    @Test fun m3uClientFetchesAndRemovesBom() {
        serve("\uFEFF#EXTM3U\n#EXTINF:-1,测试\nhttps://example.com/live".toByteArray()) { uri ->
            val result = runBlocking { M3uClient().fetch(MediaEndpoint(uri)) }
            assertEquals("测试", result.entries.single().title)
        }
    }
    @Test fun epgClientDetectsGzipWithoutExtensionOrHttpEncoding() {
        serve(gzip("<tv><channel id=\"one\"><display-name>第一台</display-name></channel></tv>")) { uri ->
            val result = runBlocking { EpgClient().fetch(MediaEndpoint(uri)) }
            assertEquals("第一台", result.channels.single().title)
        }
    }
    @Test fun decodedResponseIsBounded() {
        serve(gzip("a".repeat(1000))) { uri ->
            assertTrue(runCatching { runBlocking { MediaHttpClient(maxResponseBytes = 100).getText(MediaEndpoint(uri)) } }.isFailure)
        }
    }
}
