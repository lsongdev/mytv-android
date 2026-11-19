package me.lsong.mytv.data.providers

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.serialization.builtins.serializer
import me.lsong.mytv.data.providers.cache.*
import me.lsong.mytv.domain.provider.*
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class ProviderStorageTest {
    private val provider = ProviderConfig("test", "测试源", "https://example.com/list", ProviderKind.M3U)
    private fun fixture(block: suspend CoroutineScope.(ProviderStorage, () -> Long, (Long) -> Unit) -> Unit) = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "provider-cache-test-${UUID.randomUUID()}"
        val prefs = context.getSharedPreferences(name,Context.MODE_PRIVATE)
        val directory = File(context.cacheDir,name)
        var time = 1_000_000L
        val store = ProviderStorage(directory,prefs,now = { time })
        try { block(store,{time},{time=it}) } finally { directory.deleteRecursively(); prefs.edit().clear().commit() }
    }
    @Test fun freshCacheSkipsNetworkButExplicitRefreshDoesNot() = fixture { store, _, _ ->
        var requests = 0
        suspend fun get(force: Boolean = false) = store.get(provider,"page",String.serializer(),force = force) { "value-${++requests}" }
        assertEquals("value-1", get())
        assertEquals("value-1", get())
        assertEquals(1,requests)
        assertEquals("value-2", get(true))
        assertEquals(2,requests)
        assertTrue(store.state(provider)!!.cachedAt > 0)
    }
    @Test fun staleCacheSurvivesFailureAndBackoffCanBeBypassed() = fixture { store, now, time ->
        store.get(provider,"page",String.serializer()) { "last good" }
        time(now()+700_000)
        var requests = 0
        suspend fun fail(force: Boolean = false) = store.get(provider,"page",String.serializer(),force=force) { requests++; error("offline") }
        assertEquals("last good", fail())
        assertEquals(1,requests)
        assertNotNull(store.state(provider)!!.error)
        assertTrue(store.state(provider)!!.retryAt > now())
        assertEquals("last good", fail())
        assertEquals(1,requests)
        fail(true)
        assertEquals(2,requests)
        assertEquals(2,store.state(provider)!!.failures)
        time(store.state(provider)!!.retryAt + 1)
        fail()
        assertEquals(3,requests)
    }
    @Test fun changingEndpointDoesNotReuseOldCacheOrBackoff() = fixture { store, _, _ ->
        store.get(provider,"page",String.serializer()) { "old endpoint" }
        val changed = provider.copy(url = "https://other.example/list")
        assertNull(store.cached(changed,"page",String.serializer()))
        assertNull(store.state(changed))
        assertEquals("new endpoint",store.get(changed,"page",String.serializer()) { "new endpoint" })
    }
    @Test fun cancellingDoesNotRecordAProviderFailure() = fixture { store, _, _ ->
        val request = launch { store.get(provider,"page",String.serializer()) { delay(10_000); "unused" } }
        delay(50); request.cancelAndJoin()
        assertNull(store.state(provider))
    }
}
