package me.lsong.mytv.data.playback

import java.io.InterruptedIOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.lsong.media.http.MediaEndpoint
import me.lsong.media.probe.StreamProbe
import me.lsong.mytv.domain.playback.PlaybackResource
import me.lsong.mytv.domain.playback.ResourceProbe

class ResourceProbeRepository(
    private val measure: suspend (PlaybackResource) -> ResourceProbe = defaultMeasure(),
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private data class Cached(val value: ResourceProbe, val expiresAt: Long)
    private val cache = linkedMapOf<String, Cached>()
    private val inFlight = mutableMapOf<String, CompletableDeferred<ResourceProbe>>()
    private val permits = Semaphore(2)

    suspend fun probe(resource: PlaybackResource, onStart: () -> Unit = {}): ResourceProbe {
        val id = resource.id
        cached(id)?.let { return it }
        // A visible channel may be queried by several UI surfaces at once.
        // Share the same network probe without globally serializing other URLs.
        val (flight, leader) = synchronized(inFlight) {
            inFlight[id]?.let { it to false } ?: (CompletableDeferred<ResourceProbe>().also {
                inFlight[id] = it
            } to true)
        }
        if (!leader) return flight.await()
        try {
            val result = permits.withPermit {
                onStart()
                try { measure(resource) }
                catch (error: TimeoutCancellationException) { ResourceProbe.Failed(timedOut = true) }
                catch (error: CancellationException) { throw error }
                catch (error: InterruptedIOException) { ResourceProbe.Failed(timedOut = true) }
                catch (error: Exception) { ResourceProbe.Failed() }
            }
            synchronized(cache) {
                cache[id] = Cached(result, now() + if (result is ResourceProbe.Measured) 180_000 else 30_000)
                while (cache.size > 128) cache.remove(cache.keys.first())
            }
            flight.complete(result)
            return result
        } catch (error: Throwable) {
            // A cancelled leader must not poison the cache or strand waiters.
            flight.completeExceptionally(error)
            throw error
        } finally {
            synchronized(inFlight) { if (inFlight[id] === flight) inFlight.remove(id) }
        }
    }

    private fun cached(id: String): ResourceProbe? = synchronized(cache) {
        cache[id]?.takeIf { it.expiresAt > now() }?.value
    }
}


private fun defaultMeasure(): suspend (PlaybackResource) -> ResourceProbe {
    val probe = StreamProbe()
    return { resource ->
        val result = probe.measure(MediaEndpoint(resource.uri, resource.headers))
        ResourceProbe.Measured(result.latencyMs, result.bytesPerSecond)
    }
}
