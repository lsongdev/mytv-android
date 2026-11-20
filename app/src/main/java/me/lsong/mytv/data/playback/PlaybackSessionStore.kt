package me.lsong.mytv.data.playback

import android.content.SharedPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.lsong.mytv.domain.live.LiveChannel
import me.lsong.mytv.domain.playback.PlaybackResource
import me.lsong.mytv.domain.provider.ProviderConfig
import me.lsong.mytv.domain.video.*

@Serializable
data class SavedLiveChannel(val id: String, val name: String, val title: String, val resources: List<PlaybackResource>) {
    fun toChannel() = LiveChannel(id = id,name = name,title = title,resources = resources)
}
@Serializable
data class PlaybackSession(
    val live: SavedLiveChannel? = null, val video: VideoPlaybackSelection? = null,
    val resourceId: String? = null, val positionMs: Long = 0, val durationMs: Long = 0,
    val paused: Boolean = false, val speed: Float = 1f,
) {
    fun isValid(): Boolean = runCatching {
        when {
            video != null && live == null -> video.details.work.variants.isNotEmpty() && video.part.resources.isNotEmpty() && video.resource.uri.isNotBlank()
            live != null && video == null -> live.id.isNotBlank() && live.resources.isNotEmpty() && live.resources.all { it.uri.isNotBlank() }
            else -> false
        }
    }.getOrDefault(false)
    /** Restore only resources belonging to still-enabled providers. Expired URLs can fail over. */
    fun available(providers: List<ProviderConfig>): PlaybackSession? {
        if (!isValid()) return null
        val enabled = providers.filter { it.isEnabled }.map { it.id }.toSet()
        video?.let { selection ->
            val resources = selection.part.resources.filter { it.origin.providerId in enabled }
            if (resources.isEmpty()) return null
            val resource = resources.firstOrNull { it.id == selection.resourceId } ?: resources.first()
            return copy(video = selection.copy(details = selection.details.copy(parts = listOf(selection.part.copy(resources = resources))),resourceId = resource.id))
        }
        live?.let { channel ->
            val resources = channel.resources.filter { it.origin.providerId in enabled }
            if (resources.isEmpty()) return null
            return copy(live = channel.copy(resources = resources),resourceId = resources.firstOrNull { it.id == resourceId }?.id ?: resources.first().id)
        }
        return null
    }
}

/** Application-scoped writes survive a composition/activity disposal; no catalog dumps in prefs. */
class PlaybackSessionStore(private val preferences: SharedPreferences) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val sequence = java.util.concurrent.atomic.AtomicLong()
    private var writtenSequence = 0L
    @Volatile private var latest: PlaybackSession? = null
    suspend fun load(): PlaybackSession? = withContext(Dispatchers.IO) {
        latest?.takeIf { it.isValid() }?.let { return@withContext it }
        runCatching { preferences.getString(KEY,null)?.takeIf { it.length <= 512_000 }?.let { json.decodeFromString<PlaybackSession>(it).takeIf(PlaybackSession::isValid) } }.getOrNull()
    }
    fun save(session: PlaybackSession): Job {
        if (!session.isValid()) return scope.launch { }
        latest = session
        val revision = sequence.incrementAndGet()
        return scope.launch { mutex.withLock {
        if (revision < writtenSequence) return@withLock
        val compact = session.copy(video = session.video?.let { selected -> selected.copy(details = VideoDetails(
            selected.details.work.copy(variants = selected.details.work.variants.take(8).map { it.copy(lines = emptyList(),summary = it.summary.take(4000)) }),
            listOf(selected.part.copy(resources = (listOf(selected.resource) + selected.part.resources).distinctBy { it.id }.take(64))),
        )) })
        if (revision == sequence.get()) latest = compact
        val text = json.encodeToString(compact)
        if (text.toByteArray(Charsets.UTF_8).size <= 512_000) {
            preferences.edit().putString(KEY,text).commit(); writtenSequence = revision
        }
    } }
    }
    companion object { private const val KEY = "PLAYBACK_SESSION_V1" }
}
