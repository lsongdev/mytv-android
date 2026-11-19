package me.lsong.media.danmaku

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.lsong.media.danmaku.internal.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Platform-scoped identities. Clients do not know about an application's VideoWork or VideoPart. */
interface DanmakuProvider {
    val id: String
    val name: String
    suspend fun search(keyword: String): List<DanmakuMedia>
    suspend fun episodes(mediaId: String): List<DanmakuEpisode>
    suspend fun comments(episodeId: String, startSeconds: Int, endSeconds: Int): List<DanmakuComment>
}

fun builtInDanmakuProviders(): List<DanmakuProvider> = listOf(
    NativeProvider("bilibili", "哔哩哔哩", BilibiliDanmakuSource()),
    NativeProvider("tencent", "腾讯视频", TencentDanmakuSource()),
    NativeProvider("iqiyi", "爱奇艺", IqiyiDanmakuSource()),
    NativeProvider("youku", "优酷", YoukuDanmakuSource()),
    NativeProvider("mgtv", "芒果 TV", MgtvDanmakuSource()),
    NativeProvider("letv", "乐视视频", LetvDanmakuSource()),
)

private class NativeProvider(override val id: String, override val name: String, private val source: DanmakuSource) : DanmakuProvider {
    override suspend fun search(keyword: String) = query { source.search(keyword) }
    override suspend fun episodes(mediaId: String) = query { source.getEpisodes(mediaId) }
    override suspend fun comments(episodeId: String, startSeconds: Int, endSeconds: Int) =
        query { source.getComments(episodeId, startSeconds, endSeconds).filter { it.content.isNotBlank() && it.content.length <= 300 }.take(12_000) }
}

/** Cancels active calls even while an original blocking platform adapter is executing on IO. */
private suspend fun <T> query(block: () -> T): T = withContext(Dispatchers.IO) {
    suspendCancellableCoroutine { continuation ->
        val requests = RequestScope()
        continuation.invokeOnCancellation { requests.cancel() }
        DanmakuQueryContext.current.set(requests)
        try {
            val value = block()
            if (continuation.isActive) continuation.resume(value)
        } catch (error: Exception) {
            if (continuation.isActive) continuation.resumeWithException(error)
        } finally { DanmakuQueryContext.current.remove(); requests.cancel() }
    }
}
