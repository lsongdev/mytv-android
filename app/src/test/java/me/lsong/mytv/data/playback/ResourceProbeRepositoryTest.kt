package me.lsong.mytv.data.playback

import kotlinx.coroutines.*
import me.lsong.mytv.domain.playback.*
import me.lsong.mytv.domain.provider.ProviderRecordRef
import org.junit.Assert.*
import org.junit.Test

class ResourceProbeRepositoryTest {
    private val resource = PlaybackResource("https://example.com/live", ProviderRecordRef("a", "1"), "A")
    @Test fun resultsAreCachedForThreeMinutesAndHeadersArePartOfIdentity() = runBlocking {
        var time = 0L; var requests = 0
        val repo = ResourceProbeRepository(measure = { requests++; ResourceProbe.Measured(100, 1024) }, now = { time })
        repo.probe(resource); repo.probe(resource)
        assertEquals(1, requests)
        repo.probe(resource.copy(headers = mapOf("Referer" to "https://other.example")))
        assertEquals(2, requests)
        time = 180001
        repo.probe(resource)
        assertEquals(3, requests)
    }
    @Test fun failuresAreRetriedAfterThirtySeconds() = runBlocking {
        var time = 0L; var requests = 0
        val repo = ResourceProbeRepository(measure = { requests++; error("offline") }, now = { time })
        assertTrue(repo.probe(resource) is ResourceProbe.Failed)
        repo.probe(resource)
        assertEquals(1, requests)
        time = 30001
        repo.probe(resource)
        assertEquals(2, requests)
    }
    @Test fun cancelledRequestsAreNotCachedAsFailures() = runBlocking {
        var requests = 0
        val repo = ResourceProbeRepository(measure = { requests++; throw CancellationException() })
        repeat(2) {
            assertTrue(runCatching { repo.probe(resource) }.exceptionOrNull() is CancellationException)
        }
        assertEquals(2, requests)
    }
    @Test fun concurrentRequestsForTheSameResourceShareOneProbe() = runBlocking {
        var requests = 0
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val repository = ResourceProbeRepository(measure = {
            requests++
            started.complete(Unit)
            release.await()
            ResourceProbe.Measured(25, 2048)
        })
        val first = async { repository.probe(resource) }
        started.await()
        val second = async { repository.probe(resource) }
        yield()
        release.complete(Unit)
        assertEquals(first.await(),second.await())
        assertEquals(1,requests)
        assertEquals(first.await(),repository.probe(resource))
        assertEquals(1,requests)
    }
    @Test fun atMostTwoRequestsRunTogether() = runBlocking {
        var running = 0; var maximum = 0
        val release = CompletableDeferred<Unit>()
        val repo = ResourceProbeRepository(measure = {
            running++; maximum = maxOf(maximum, running)
            try { release.await(); ResourceProbe.Measured(10, 1024) } finally { running-- }
        })
        val jobs = (1..5).map { async { repo.probe(resource.copy(uri = "https://example.com/$it")) } }
        yield()
        assertEquals(2, maximum)
        release.complete(Unit)
        jobs.awaitAll()
        assertEquals(2, maximum)
    }
}
