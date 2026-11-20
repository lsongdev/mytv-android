package me.lsong.mytv.data.cache

import coil.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.lsong.mytv.data.danmaku.DanmakuRepository
import me.lsong.mytv.data.providers.cache.ProviderStorage

data class CacheSize(val dataBytes: Long, val imageBytes: Long)
enum class CacheKind { DATA, IMAGES }

/** Only regenerable metadata and Coil resources; preferences and playback stay intact. */
@OptIn(coil.annotation.ExperimentalCoilApi::class)
class AppCacheManager(
    private val providers: ProviderStorage,
    private val danmaku: DanmakuRepository,
    private val images: ImageLoader,
) {
    private val mutex = Mutex()

    suspend fun size(): CacheSize = withContext(Dispatchers.IO) { mutex.withLock { readSize() } }

    suspend fun clear(kind: CacheKind): CacheSize = withContext(Dispatchers.IO) { mutex.withLock {
        when (kind) {
            CacheKind.DATA -> {
                providers.clearCache()
                danmaku.clearCache()
            }
            CacheKind.IMAGES -> {
                images.memoryCache?.clear()
                images.diskCache?.clear()
            }
        }
        readSize()
    } }

    private suspend fun readSize() = CacheSize(
        providers.cacheSize() + danmaku.cacheSize(),
        images.diskCache?.size ?: 0,
    )
}
