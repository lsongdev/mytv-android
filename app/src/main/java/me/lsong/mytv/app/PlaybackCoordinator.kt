package me.lsong.mytv.app

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import me.lsong.mytv.data.live.LiveCatalogRepository
import me.lsong.mytv.domain.live.LiveChannel
import me.lsong.mytv.domain.playback.PlaybackRequest
import me.lsong.mytv.domain.video.VideoPlaybackSelection
import me.lsong.mytv.playback.PlaybackState
import me.lsong.mytv.playback.rememberPlaybackState
import me.lsong.mytv.data.preferences.AppPreferences
import kotlin.math.max

@Stable
class PlaybackCoordinator(
    private val videoPlayerState: PlaybackState,
    private val liveCatalog: LiveCatalogRepository,
)  {
    private val channels: List<LiveChannel> get() = liveCatalog.channels
    private var _currentChannel by mutableStateOf(LiveChannel())
    val currentChannel get() = _currentChannel
    var videoSelection by mutableStateOf<VideoPlaybackSelection?>(null)
        private set
    var videoPaused by mutableStateOf(false)
        private set
    var videoSpeed by mutableStateOf(1f)
        private set
    fun cycleVideoSpeed() {
        val speeds = listOf(1f, 1.25f, 1.5f, 2f)
        videoSpeed = speeds[(speeds.indexOf(videoSpeed) + 1) % speeds.size]
        if (videoSelection != null) videoPlayerState.setSpeed(videoSpeed)
    }
    private val attemptedVideoResources = mutableSetOf<String>()
    val playingTitle: String? get() = videoSelection?.title ?: _currentChannel.title.takeIf { _currentChannel.resources.isNotEmpty() }

    private var _resourceIndex by mutableIntStateOf(0)
    val currentResourceIndex get() = _resourceIndex

    val currentChannelIndex
        get() = channels.indexOfFirst { it.id == _currentChannel.id }

    init {
        changeCurrentChannel(channels.getOrElse(AppPreferences.iptvLastIptvIdx) {
            channels.firstOrNull() ?: LiveChannel()
        })

        videoPlayerState.onReady {
            if (videoSelection != null) return@onReady
            // 记忆可播放的域名
            AppPreferences.iptvPlayableHostList += getUrlHost(_currentChannel.resources.map { it.uri }.getOrNull(_resourceIndex) ?: return@onReady)
        }

        videoPlayerState.onError {
            val vod = videoSelection
            if (vod != null) {
                val next = vod.part.resources.firstOrNull { it.id !in attemptedVideoResources }
                if (next != null) playVideo(vod.copy(resourceId = next.id), resetAttempts = false)
                return@onError
            }
            _currentChannel.resources.map { it.uri }.getOrNull(_resourceIndex)?.let {
                AppPreferences.iptvPlayableHostList -= getUrlHost(it)
            }
            if (_resourceIndex < _currentChannel.resources.size - 1) {
                changeCurrentChannel(_currentChannel, _resourceIndex + 1)
            }
        }

        videoPlayerState.onCutoff {
            if (videoSelection != null) return@onCutoff
            changeCurrentChannel(_currentChannel, _resourceIndex)
        }
    }

    private fun getPrevChannel(): LiveChannel {
        val currentIndex = channels.indexOfFirst { it.id == _currentChannel.id }
        return channels.getOrElse(currentIndex - 1) {
            channels.lastOrNull() ?: LiveChannel()
        }
    }

    private fun getNextChannel(): LiveChannel {
        val currentIndex = channels.indexOfFirst { it.id == _currentChannel.id }
        return channels.getOrElse(currentIndex + 1) {
            channels.firstOrNull() ?: LiveChannel()
        }
    }

    fun changeCurrentChannel(channel: LiveChannel, urlIdx: Int? = null) {
        if (channel.resources.isEmpty()) return
        if (channel == _currentChannel && urlIdx == null && videoSelection == null) return
        videoSelection = null
        videoPaused = false
        videoPlayerState.setSpeed(1f)
        if (channel == _currentChannel && urlIdx != null && urlIdx != _resourceIndex) {
            AppPreferences.iptvPlayableHostList -= getUrlHost(_currentChannel.resources.map { it.uri }[_resourceIndex])
        }
        _currentChannel = channel
        AppPreferences.iptvLastIptvIdx = currentChannelIndex

        _resourceIndex = if (urlIdx == null) {
            // 优先从记忆中选择可播放的域名
            max(0, _currentChannel.resources.map { it.uri }.indexOfFirst {
                AppPreferences.iptvPlayableHostList.contains(getUrlHost(it))
            })
        } else {
            (urlIdx + _currentChannel.resources.size) % _currentChannel.resources.size
        }
        val url = channel.resources.map { it.uri }[_resourceIndex]
        Log.d("Player", "播放${channel.name}（${_resourceIndex + 1}/${_currentChannel.resources.size}）: $url")
        videoPlayerState.prepare(PlaybackRequest(url, headers = channel.resources[_resourceIndex].headers, mimeType = channel.resources[_resourceIndex].mimeType, live = true))
    }

    fun playVideo(selection: VideoPlaybackSelection, resetAttempts: Boolean = true) {
        val previous = videoSelection
        val sameEpisode = previous != null && previous.details.work.id == selection.details.work.id && previous.partId == selection.partId
        val position = if (sameEpisode) videoPlayerState.positionMs else 0
        if (resetAttempts) attemptedVideoResources.clear()
        attemptedVideoResources += selection.resourceId
        videoSelection = selection
        videoPaused = false
        videoPlayerState.setSpeed(videoSpeed)
        videoPlayerState.prepare(PlaybackRequest(selection.resource.uri, headers = selection.resource.headers, mimeType = selection.resource.mimeType, startPositionMs = position))
    }

    fun toggleVideoPause() {
        if (videoSelection == null) return
        videoPaused = !videoPaused
        if (videoPaused) videoPlayerState.pause() else videoPlayerState.play()
    }

    fun seekVideoBy(offsetMs: Long) {
        if (videoSelection == null) return
        videoPlayerState.seekTo((videoPlayerState.positionMs + offsetMs).coerceAtLeast(0))
    }

    fun changeCurrentChannelToPrev() {
        changeCurrentChannel(getPrevChannel())
    }

    fun changeCurrentChannelToNext() {
        changeCurrentChannel(getNextChannel())
    }

    fun previousResource(){
        if (currentChannel.resources.size > 1) {
            changeCurrentChannel(
                channel =currentChannel,
                urlIdx = currentResourceIndex - 1,
            )
        }
    }
    fun nextResource(){
        if (currentChannel.resources.size > 1) {
            changeCurrentChannel(
                channel = currentChannel,
                urlIdx = currentResourceIndex + 1,
            )
        }
    }

}

@Composable
fun rememberPlaybackCoordinator(
    liveCatalog: LiveCatalogRepository,
    videoPlayerState: PlaybackState = rememberPlaybackState(),
) = remember {
    PlaybackCoordinator(
        liveCatalog = liveCatalog,
        videoPlayerState = videoPlayerState,
    )
}

private fun getUrlHost(url: String): String {
    return url.split("://").getOrElse(1) { "" }.split("/").firstOrNull() ?: url
}
