package me.lsong.media.http

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Bounded, cancellable HTTP transport shared by the protocol clients. */
class MediaHttpClient(
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS).build(),
    private val maxResponseBytes: Long = 8_000_000,
) {
    suspend fun getText(endpoint: MediaEndpoint): String {
        val bytes = get(endpoint)
        val input = if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte())
            GZIPInputStream(ByteArrayInputStream(bytes)) else ByteArrayInputStream(bytes)
        return withContext(Dispatchers.Default) { input.use {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                if (output.size().toLong() + count > maxResponseBytes) throw IOException("Decoded response exceeds size limit")
                output.write(buffer, 0, count)
            }
            output.toString("UTF-8").removePrefix("\uFEFF")
        } }
    }

    private suspend fun get(endpoint: MediaEndpoint): ByteArray = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(endpoint.uri).apply { endpoint.headers.forEach { (key,value) -> header(key,value) } }.build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) { continuation.resumeWithException(error) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val bytes = response.use {
                        if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                        val body = it.body ?: throw IOException("Empty response body")
                        val source = body.source()
                        source.request(maxResponseBytes + 1)
                        if (source.buffer.size > maxResponseBytes) throw IOException("Response exceeds size limit")
                        source.readByteArray()
                    }
                    continuation.resume(bytes)
                } catch (error: Exception) { continuation.resumeWithException(error) }
            }
        })
    }
}
