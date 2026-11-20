package me.lsong.mytv.app

import androidx.compose.runtime.*
import me.lsong.mytv.data.live.LiveCatalogRepository
import me.lsong.mytv.data.playback.*
import me.lsong.mytv.data.preferences.AppPreferences
import me.lsong.mytv.domain.live.LiveChannel
import me.lsong.mytv.domain.playback.PlaybackRequest
import me.lsong.mytv.domain.video.*
import me.lsong.mytv.playback.PlaybackState
import me.lsong.mytv.playback.rememberPlaybackState

@Stable
class PlaybackCoordinator(private val videoPlayerState: PlaybackState, private val liveCatalog: LiveCatalogRepository) {
    private val channels get() = liveCatalog.channels
    private var channel by mutableStateOf(LiveChannel())
    val currentChannel get() = channel
    var videoSelection by mutableStateOf<VideoPlaybackSelection?>(null); private set
    val videoPaused get() = videoPlayerState.isPaused
    var videoSpeed by mutableStateOf(1f); private set
    private var resourceIndex by mutableIntStateOf(0)
    val currentResourceIndex get() = resourceIndex
    val currentChannelIndex get() = channels.indexOfFirst { it.id == channel.id }
    val playingTitle get() = videoSelection?.title ?: channel.title.takeIf { channel.resources.isNotEmpty() }
    val hasMedia get() = videoSelection != null || channel.resources.isNotEmpty()
    private val attemptedVideoResources = mutableSetOf<String>()
    private var rememberedPosition = 0L
    private var rememberedDuration = 0L

    init {
        // Initial playback is chosen after loading the saved session, never before it.
        videoPlayerState.onReady {
            if (videoSelection == null) channel.resources.getOrNull(resourceIndex)?.let { AppPreferences.iptvPlayableHostList += host(it.uri) }
        }
        videoPlayerState.onError {
            videoSelection?.let { selection ->
                val next = selection.part.resources.firstOrNull { it.id !in attemptedVideoResources }
                if (next != null) startVideo(selection.copy(resourceId = next.id),positionForSave(),videoPlayerState.isPaused,false)
                return@onError
            }
            channel.resources.getOrNull(resourceIndex)?.let { AppPreferences.iptvPlayableHostList -= host(it.uri) }
            if (resourceIndex < channel.resources.lastIndex) startChannel(channel,resourceIndex+1,videoPlayerState.isPaused)
        }
        videoPlayerState.onCutoff {
            if (videoSelection == null && channel.resources.isNotEmpty()) startChannel(channel,resourceIndex,videoPlayerState.isPaused)
        }
    }
    private fun positionForSave(): Long {
        if (!videoPlayerState.isReleased) {
            val position = videoPlayerState.positionMs.coerceAtLeast(0)
            // An error callback can still expose a valid final position. Keep a
            // saved bookmark when a failed/buffering engine instead reports zero.
            val hasReliableZero = !videoPlayerState.isBuffering && videoPlayerState.error == null
            if (position > 0 || hasReliableZero) rememberedPosition = position
        }
        return rememberedPosition
    }
    fun cycleVideoSpeed() {
        val speeds = listOf(1f,1.25f,1.5f,2f)
        videoSpeed = speeds[(speeds.indexOf(videoSpeed)+1)%speeds.size]
        if (videoSelection != null) videoPlayerState.setSpeed(videoSpeed)
    }
    fun changeCurrentChannel(channel: LiveChannel, urlIdx: Int? = null) {
        if (channel.resources.isEmpty() || channel == this.channel && urlIdx == null && videoSelection == null) return
        val index = urlIdx ?: channel.resources.indexOfFirst { host(it.uri) in AppPreferences.iptvPlayableHostList }.coerceAtLeast(0)
        startChannel(channel,index,false)
    }
    private fun startChannel(value: LiveChannel, index: Int, paused: Boolean) {
        if (value.resources.isEmpty()) return
        videoSelection = null; channel = value
        resourceIndex = ((index % value.resources.size)+value.resources.size)%value.resources.size
        rememberedPosition = 0; rememberedDuration = 0
        AppPreferences.iptvLastIptvIdx = currentChannelIndex.coerceAtLeast(0)
        videoPlayerState.setSpeed(1f)
        val resource = value.resources[resourceIndex]
        videoPlayerState.prepare(PlaybackRequest(resource.uri,resource.headers,resource.mimeType,live = true,playWhenReady = !paused))
    }
    fun playVideo(selection: VideoPlaybackSelection) {
        val previous = videoSelection
        val sameEpisode = previous?.details?.work?.id == selection.details.work.id && previous?.partId == selection.partId
        if (sameEpisode && previous?.resourceId == selection.resourceId && previous.resource.uri == selection.resource.uri &&
            previous.resource.headers == selection.resource.headers && previous.resource.mimeType == selection.resource.mimeType && videoPlayerState.error == null) {
            videoSelection = selection
            videoPlayerState.play()
            return
        }
        val paused = sameEpisode && previous?.resourceId != selection.resourceId && videoPlayerState.isPaused
        if (!sameEpisode) rememberedDuration = 0
        startVideo(selection,if (sameEpisode) positionForSave() else 0,paused,true)
    }
    private fun startVideo(selection: VideoPlaybackSelection, position: Long, paused: Boolean, resetAttempts: Boolean) {
        if (resetAttempts) attemptedVideoResources.clear()
        attemptedVideoResources += selection.resourceId
        videoSelection = selection
        rememberedPosition = position.coerceAtLeast(0)
        videoPlayerState.setSpeed(videoSpeed)
        videoPlayerState.prepare(PlaybackRequest(selection.resource.uri,selection.resource.headers,selection.resource.mimeType,
            startPositionMs = rememberedPosition,playWhenReady = !paused))
    }
    fun restore(session: PlaybackSession): Boolean {
        if (!session.isValid()) return false
        return runCatching {
        session.video?.let { selection ->
            videoSpeed = session.speed.takeIf { it.isFinite() && it in .25f..4f } ?: 1f
            rememberedDuration = session.durationMs.coerceAtLeast(0)
            val position = session.positionMs.coerceAtLeast(0).let { if (rememberedDuration > 0) it.coerceAtMost(rememberedDuration) else it }
            startVideo(selection,position,session.paused,true)
            return true
        }
        session.live?.let { saved ->
            val oldResource = saved.resources.firstOrNull { it.id == session.resourceId }
            val channel = channels.firstOrNull { it.id == saved.id }
                ?: channels.filter { candidate -> candidate.resources.any { it.uri == oldResource?.uri && it.headers == oldResource?.headers } }.singleOrNull()
                ?: saved.toChannel()
            val index = channel.resources.indexOfFirst { it.id == session.resourceId || it.uri == oldResource?.uri && it.headers == oldResource?.headers }.coerceAtLeast(0)
            if (channel.resources.isEmpty()) return false
            startChannel(channel,index,session.paused)
            return true
        }
        false
    }.getOrDefault(false) }
    fun snapshot(pausedOverride: Boolean? = null): PlaybackSession? {
        val selection = videoSelection
        if (selection != null) {
            if (!videoPlayerState.isReleased && !videoPlayerState.isBuffering && videoPlayerState.durationMs > 0) rememberedDuration = videoPlayerState.durationMs
            return PlaybackSession(video = selection,positionMs = positionForSave(),durationMs = rememberedDuration,
                paused = pausedOverride ?: videoPlayerState.isPaused,speed = videoSpeed)
        }
        if (channel.resources.isEmpty()) return null
        return PlaybackSession(live = SavedLiveChannel(channel.id,channel.name,channel.title,channel.resources),
            resourceId = channel.resources.getOrNull(resourceIndex)?.id,paused = pausedOverride ?: videoPlayerState.isPaused)
    }
    fun reconcileLiveCatalog() {
        if (videoSelection != null) return
        val old = channel.resources.getOrNull(resourceIndex)
        val latest = channels.firstOrNull { it.id == channel.id }
            ?: channels.filter { candidate -> candidate.resources.any { it.uri == old?.uri && it.headers == old?.headers } }.singleOrNull()
            ?: return
        val index = latest.resources.indexOfFirst { it.id == old?.id }.takeIf { it >= 0 }
            ?: latest.resources.indexOfFirst { it.origin.providerId == old?.origin?.providerId }.coerceAtLeast(0)
        if (old?.uri != latest.resources.getOrNull(index)?.uri) startChannel(latest,index,videoPlayerState.isPaused)
        else { channel = latest; resourceIndex = index }
    }
    fun refreshRestoredVideo(details: VideoDetails) {
        val current = videoSelection ?: return
        if (details.work.id != current.details.work.id) return
        val part = details.parts.firstOrNull { it.id == current.partId && it.resources.isNotEmpty() } ?: return
        val same = part.resources.firstOrNull { it.id == current.resourceId }
        if (same != null) videoSelection = current.copy(details = details)
        else if (videoPlayerState.error != null) {
            val resource = part.resources.firstOrNull { it.origin.providerId == current.resource.origin.providerId && it.line?.id == current.resource.line?.id } ?: part.resources.first()
            startVideo(VideoPlaybackSelection(details,part.id,resource.id),rememberedPosition,videoPlayerState.isPaused,true)
        } else {
            val merged = part.copy(resources = listOf(current.resource) + part.resources)
            videoSelection = current.copy(details = details.copy(parts = details.parts.map { if (it.id == part.id) merged else it }))
        }
    }
    fun toggleVideoPause() { if (videoSelection != null) { if (videoPlayerState.isPaused) videoPlayerState.play() else videoPlayerState.pause() } }
    fun seekVideoBy(offsetMs: Long) { if (videoSelection != null) videoPlayerState.seekTo((positionForSave()+offsetMs).coerceAtLeast(0)) }
    fun changeCurrentChannelToPrev() { changeCurrentChannel(channels.getOrElse(currentChannelIndex-1) { channels.lastOrNull() ?: LiveChannel() }) }
    fun changeCurrentChannelToNext() { changeCurrentChannel(channels.getOrElse(currentChannelIndex+1) { channels.firstOrNull() ?: LiveChannel() }) }
    fun previousResource() { if (channel.resources.size > 1) changeCurrentChannel(channel,resourceIndex-1) }
    fun nextResource() { if (channel.resources.size > 1) changeCurrentChannel(channel,resourceIndex+1) }
}
@Composable
fun rememberPlaybackCoordinator(liveCatalog: LiveCatalogRepository, videoPlayerState: PlaybackState = rememberPlaybackState()) =
    remember(liveCatalog,videoPlayerState) { PlaybackCoordinator(videoPlayerState,liveCatalog) }
private fun host(url: String) = url.substringAfter("://",url).substringBefore('/')
