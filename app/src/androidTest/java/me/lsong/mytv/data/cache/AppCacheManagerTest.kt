package me.lsong.mytv.data.cache

import android.content.Context
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import me.lsong.mytv.data.danmaku.DanmakuRepository
import me.lsong.mytv.data.providers.cache.ProviderStorage
import me.lsong.mytv.domain.provider.ProviderConfig
import me.lsong.mytv.domain.provider.ProviderKind
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

@OptIn(coil.annotation.ExperimentalCoilApi::class)
class AppCacheManagerTest {
    @Test fun clearsOnlySelectedCachesAndPreservesPreferences() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "cache-test-${UUID.randomUUID()}"
        val directory = File(context.cacheDir, name).apply { mkdirs() }
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val disk = DiskCache.Builder().directory(File(directory, "images")).build()
        val images = ImageLoader.Builder(context).diskCache(disk).build()
        try {
            prefs.edit().putString("saved-setting", "keep").commit()
            val storage = ProviderStorage(File(directory, "providers"), prefs)
            val provider = ProviderConfig("test", "Test", "https://example.com", ProviderKind.M3U)
            storage.get(provider, "page", String.serializer()) { "catalog" }
            val danmakuDirectory = File(directory, "danmaku").apply { mkdirs() }
            File(danmakuDirectory, "window.json").writeText("comments")
            val manager = AppCacheManager(storage, DanmakuRepository(danmakuDirectory), images)
            val editor = checkNotNull(disk.openEditor("poster"))
            disk.fileSystem.write(editor.data) { writeUtf8("image bytes") }
            editor.commit()
            val memoryKey = MemoryCache.Key("poster")
            images.memoryCache!![memoryKey] = MemoryCache.Value(Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888))
            assertTrue(manager.size().dataBytes > 0)
            assertTrue(manager.size().imageBytes > 0)

            val afterData = manager.clear(CacheKind.DATA)
            assertEquals(0L, afterData.dataBytes)
            assertTrue(afterData.imageBytes > 0)
            assertNull(storage.cached(provider, "page", String.serializer()))
            assertEquals(0L, storage.state(provider)!!.cachedAt)
            assertEquals("keep", prefs.getString("saved-setting", null))
            assertNotNull(images.memoryCache!![memoryKey])

            storage.get(provider, "page", String.serializer()) { "new catalog" }
            val afterImages = manager.clear(CacheKind.IMAGES)
            assertTrue(afterImages.dataBytes > 0)
            assertEquals(0L, afterImages.imageBytes)
            assertNull(images.memoryCache!![memoryKey])
            assertNull(disk.openSnapshot("poster"))
            manager.clear(CacheKind.DATA)
            assertEquals(0L, manager.clear(CacheKind.DATA).dataBytes)
        } finally {
            images.shutdown()
            disk.clear()
            directory.deleteRecursively()
            prefs.edit().clear().commit()
        }
    }
}
