package me.lsong.media

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.io.IOException
import kotlinx.coroutines.*
import me.lsong.media.http.MediaEndpoint
import me.lsong.media.probe.StreamProbe
import org.junit.Assert.*
import org.junit.Test

class StreamProbeTest {
    private fun server(action: (HttpServer, String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        try { action(server, "http://127.0.0.1:${server.address.port}") } finally { server.stop(0) }
    }
    @Test fun followsRedirectsAndHlsToSampleMediaWithOriginalHeaders() = server { server, base ->
        val paths = mutableListOf<String>()
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            paths += path
            assertEquals("TV-UA", exchange.requestHeaders.getFirst("User-Agent"))
            assertEquals("https://origin.example/", exchange.requestHeaders.getFirst("Referer"))
            assertEquals("bytes=0-1023", exchange.requestHeaders.getFirst("Range"))
            if (path == "/start") {
                exchange.responseHeaders.add("Location", "/lists/master.m3u8")
                exchange.sendResponseHeaders(302, -1); exchange.close()
            } else {
                val bytes = when (path) {
                    "/lists/master.m3u8" -> "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000\nmedia.m3u8\n".toByteArray()
                    "/lists/media.m3u8" -> "#EXTM3U\n#EXTINF:6,\n../segments/1.ts\n".toByteArray()
                    else -> ByteArray(100_000) { 0x47 }
                }
                exchange.responseHeaders.add("Content-Type", if (path.endsWith("m3u8")) "application/vnd.apple.mpegurl" else "video/mp2t")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                try { exchange.responseBody.use { it.write(bytes) } } catch (_: IOException) { }
            }
        }
        val result = runBlocking { StreamProbe(sampleBytes = 1024).measure(MediaEndpoint("$base/start", mapOf("User-Agent" to "TV-UA", "Referer" to "https://origin.example/"))) }
        assertEquals(1024, result.sampledBytes)
        assertTrue(result.bytesPerSecond > 0)
        assertTrue(result.latencyMs > 0)
        assertEquals(listOf("/start", "/lists/master.m3u8", "/lists/media.m3u8", "/segments/1.ts"), paths)
    }
    @Test fun rejectsHtmlInsteadOfReportingAPositiveSpeed() = server { server, base ->
        server.createContext("/") { exchange ->
            val bytes = "<html>Access denied</html>".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        assertTrue(runCatching { runBlocking { StreamProbe().measure(MediaEndpoint(base)) } }.exceptionOrNull() is IOException)
    }
    @Test fun boundsNestedPlaylistRequests() = server { server, base ->
        var requests = 0
        server.createContext("/") { exchange ->
            requests++
            val bytes = "#EXTM3U\nloop.m3u8\n".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        assertTrue(runCatching { runBlocking { StreamProbe().measure(MediaEndpoint(base)) } }.exceptionOrNull() is IOException)
        assertEquals(3, requests)
    }
    @Test fun cancellingAProbeCancelsItsHttpRequest() = server { server, base ->
        server.createContext("/") { exchange ->
            Thread.sleep(500)
            try {
                exchange.sendResponseHeaders(200, 1000)
                exchange.responseBody.use { it.write(ByteArray(1000)) }
            } catch (_: IOException) { }
        }
        val error = runCatching { runBlocking { withTimeout(100) { StreamProbe().measure(MediaEndpoint(base)) } } }.exceptionOrNull()
        assertTrue(error is TimeoutCancellationException)
    }
}
