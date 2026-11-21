package me.lsong.media.probe

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import me.lsong.media.http.MediaEndpoint
import okhttp3.*

/** A small HTTP sample, not a sustained bandwidth test or a decoding/playability check. */
data class StreamProbeResult(val latencyMs: Long, val bytesPerSecond: Long, val sampledBytes: Int)

class StreamProbe(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS).build(),
    private val sampleBytes: Int = 256 * 1024,
) {
    init { require(sampleBytes in 1024..1024 * 1024) }

    suspend fun measure(endpoint: MediaEndpoint): StreamProbeResult = withContext(Dispatchers.Default) { withTimeout(6_000) {
        val started = System.nanoTime()
        var target = endpoint
        repeat(3) {
            val sample = fetch(target)
            // Binary TS/MP4 chunks should not allocate a 256 KiB UTF-8 string.
            val prefix = String(sample.bytes,0,minOf(sample.bytes.size,2048),Charsets.UTF_8)
                .removePrefix("\uFEFF").trimStart()
            if (prefix.startsWith("#EXTM3U")) {
                val playlist = sample.bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
                val next = playlist.lineSequence().map(String::trim)
                    .firstOrNull { it.isNotBlank() && !it.startsWith('#') }
                    ?: throw IOException("Empty HLS playlist")
                val uri = sample.uri.resolve(next) ?: throw IOException("Invalid HLS media URI")
                target = target.copy(uri = uri.toString())
            } else {
                if (sample.bytes.isEmpty() || sample.contentType.contains("text/", true) ||
                    sample.contentType.contains("json", true) || prefix.startsWith("<html", true) || prefix.startsWith("<!doctype", true)) {
                    throw IOException("Response is not media")
                }
                val elapsedNanos = (sample.finished - sample.started).coerceAtLeast(1)
                return@withTimeout StreamProbeResult(
                    latencyMs = TimeUnit.NANOSECONDS.toMillis(sample.firstByte - started).coerceAtLeast(1),
                    bytesPerSecond = (sample.bytes.size * 1_000_000_000.0 / elapsedNanos).toLong(),
                    sampledBytes = sample.bytes.size,
                )
            }
        }
        throw IOException("Too many nested playlists")
    } }

    private data class Sample(
        val uri: HttpUrl, val contentType: String, val bytes: ByteArray,
        val started: Long, val firstByte: Long, val finished: Long,
    )

    private suspend fun fetch(endpoint: MediaEndpoint): Sample = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(endpoint.uri).apply {
            endpoint.headers.forEach { (key, value) -> header(key, value) }
        }.header("Range", "bytes=0-${sampleBytes - 1}").header("Accept-Encoding", "identity").build()
        val started = System.nanoTime()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val sample = response.use {
                        if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                        val body = it.body ?: throw IOException("Empty response")
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var firstByte = 0L
                        body.byteStream().use { input ->
                            while (output.size() < sampleBytes) {
                                val count = input.read(buffer, 0, minOf(buffer.size, sampleBytes - output.size()))
                                if (count < 0) break
                                if (firstByte == 0L) firstByte = System.nanoTime()
                                output.write(buffer, 0, count)
                            }
                            if (output.size() == sampleBytes) call.cancel()
                        }
                        Sample(it.request.url, it.header("Content-Type").orEmpty(), output.toByteArray(),
                            started, firstByte, System.nanoTime())
                    }
                    if (continuation.isActive) continuation.resume(sample)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }
}
