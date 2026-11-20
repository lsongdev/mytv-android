package me.lsong.mytv.data.danmaku

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.lsong.media.danmaku.*
import me.lsong.mytv.domain.video.VideoWork
import me.lsong.mytv.domain.danmaku.danmakuMatchScore

@Serializable
data class DanmakuCandidate(val providerId: String, val providerName: String, val media: DanmakuMedia, val score: Int) {
    val id get() = "$providerId:${media.mediaId}"
}

class DanmakuRepository(private val directory: File, private val factory: () -> List<DanmakuProvider> = ::builtInDanmakuProviders) {
    private val providers by lazy(factory)
    private val permits = Semaphore(3)
    private val json = Json { ignoreUnknownKeys = true }
    private val episodesCache = mutableMapOf<String, List<DanmakuEpisode>>()
    @Serializable private data class Window(val savedAt: Long, val comments: List<DanmakuComment>)

    val providerNames: Map<String,String> get() = providers.associate { it.id to it.name }
    suspend fun search(work: VideoWork, providerId: String): List<DanmakuCandidate> = permits.withPermit { withTimeout(15_000) {
        val provider = providers.first { it.id == providerId }
        provider.search(work.title).map { DanmakuCandidate(provider.id,provider.name,it,danmakuMatchScore(work,it)) }
            .filter { it.score > 0 }.distinctBy { it.id }.sortedByDescending { it.score }.take(10)
    } }
    suspend fun episodes(candidate: DanmakuCandidate): List<DanmakuEpisode> {
        synchronized(episodesCache) { episodesCache[candidate.id] }?.let { return it }
        val result = permits.withPermit { withTimeout(15_000) { providers.first { it.id == candidate.providerId }.episodes(candidate.media.mediaId) } }
        if (result.isNotEmpty()) synchronized(episodesCache) {
            episodesCache[candidate.id] = result
            while (episodesCache.size > 32) episodesCache.remove(episodesCache.keys.first())
        }
        return result
    }
    suspend fun window(providerId: String, episodeId: String, index: Int): List<DanmakuComment> = withContext(Dispatchers.IO) {
        val key = "$providerId|$episodeId|$index"
        val name = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        val file = File(directory, "$name.json")
        if (file.exists() && file.length() <= 4_000_000) {
            val saved = runCatching { json.decodeFromString<Window>(file.readText()) }.getOrNull()
            if (saved != null && System.currentTimeMillis() - saved.savedAt < 7 * 86400_000L) return@withContext saved.comments
        }
        val start = index * 300
        val result = permits.withPermit { withTimeout(15_000) { providers.first { it.id == providerId }.comments(episodeId,start,start + 300) } }
            .filter { it.timeMs in start * 1000L until (start + 300) * 1000L && it.content.isNotBlank() }
            .distinctBy { Triple(it.timeMs,it.mode,it.content) }.take(12_000)
        if (result.isNotEmpty()) {
            directory.mkdirs()
            val text = json.encodeToString(Window(System.currentTimeMillis(),result))
            val bytes = text.toByteArray(Charsets.UTF_8)
            if (bytes.size <= 4_000_000) {
                val temp = File(directory,"$name.tmp")
                temp.writeBytes(bytes); temp.renameTo(file)
                var totalBytes = 0L
                directory.listFiles()?.filter { it.extension == "json" }?.sortedByDescending(File::lastModified)?.forEachIndexed { i, entry ->
                    totalBytes += entry.length()
                    if (i >= 96 || totalBytes > 32_000_000) entry.delete()
                }
            }
        }
        result
    }
}
