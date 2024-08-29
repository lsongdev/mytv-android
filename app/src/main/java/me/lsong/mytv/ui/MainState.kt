package me.lsong.mytv.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.lsong.mytv.iptv.TVChannel
import me.lsong.mytv.iptv.TVGroupList
import me.lsong.mytv.iptv.TVGroupList.Companion.channels
import me.lsong.mytv.iptv.TVGroupList.Companion.findChannelIndex
import me.lsong.mytv.utils.Constants
import me.lsong.mytv.ui.player.LeanbackVideoPlayerState
import me.lsong.mytv.ui.player.rememberLeanbackVideoPlayerState
import me.lsong.mytv.utils.Settings
import kotlin.math.max

@Stable
class MainContentState(
    coroutineScope: CoroutineScope,
    private val videoPlayerState: LeanbackVideoPlayerState,
    private val groups: TVGroupList,
)  {
    private var _currentChannel by mutableStateOf(TVChannel())
    val currentChannel get() = _currentChannel

    private var _currentIptvUrlIdx by mutableIntStateOf(0)
    val currentSourceIndex get() = _currentIptvUrlIdx

    private var _isChannelInfoVisible by mutableStateOf(false)
    var isChannelInfoVisible
        get() = _isChannelInfoVisible
        set(value) {
            _isChannelInfoVisible = value
        }

    private var _isMenuVisible by mutableStateOf(false)
    var isMenuVisible
        get() = _isMenuVisible
        set(value) {
            _isMenuVisible = value
        }

    private var _isSettingsVisale by mutableStateOf(false)
    var isSettingsVisale
        get() = _isSettingsVisale
        set(value) {
            _isSettingsVisale = value
        }

    val currentChannelIndex
        get() = groups.findChannelIndex(_currentChannel)

    init {
        changeCurrentChannel(groups.channels.getOrElse(Settings.iptvLastIptvIdx) {
            groups.firstOrNull()?.channels?.firstOrNull() ?: TVChannel()
        })

        videoPlayerState.onReady {
            coroutineScope.launch {
                // val name = _currentChannel.name
                // val urlIdx = _currentIptvUrlIdx
            }

            // 记忆可播放的域名
            Settings.iptvPlayableHostList += getUrlHost(_currentChannel.urls[_currentIptvUrlIdx])
        }

        videoPlayerState.onError {
            if (_currentIptvUrlIdx < _currentChannel.urls.size - 1) {
                changeCurrentChannel(_currentChannel, _currentIptvUrlIdx + 1)
            }

            // 从记忆中删除不可播放的域名
            Settings.iptvPlayableHostList -= getUrlHost(_currentChannel.urls[_currentIptvUrlIdx])
        }

        videoPlayerState.onCutoff {
            changeCurrentChannel(_currentChannel, _currentIptvUrlIdx)
        }
    }

    private fun getPrevChannel(): TVChannel {
        val currentIndex = groups.findChannelIndex(_currentChannel)
        return groups.channels.getOrElse(currentIndex - 1) {
            groups.lastOrNull()?.channels?.lastOrNull() ?: TVChannel()
        }
    }

    private fun getNextChannel(): TVChannel {
        val currentIndex = groups.findChannelIndex(_currentChannel)
        return groups.channels.getOrElse(currentIndex + 1) {
            groups.firstOrNull()?.channels?.firstOrNull() ?: TVChannel()
        }
    }

    fun changeCurrentChannel(channel: TVChannel, urlIdx: Int? = null) {
        // isChannelInfoVisible = false
        if (channel == _currentChannel && urlIdx == null) return
        if (channel == _currentChannel && urlIdx != _currentIptvUrlIdx) {
            Settings.iptvPlayableHostList -= getUrlHost(_currentChannel.urls[_currentIptvUrlIdx])
        }
        // _isTempPanelVisible = true

        _currentChannel = channel
        Settings.iptvLastIptvIdx = currentChannelIndex

        _currentIptvUrlIdx = if (urlIdx == null) {
            // 优先从记忆中选择可播放的域名
            max(0, _currentChannel.urls.indexOfFirst {
                Settings.iptvPlayableHostList.contains(getUrlHost(it))
            })
        } else {
            (urlIdx + _currentChannel.urls.size) % _currentChannel.urls.size
        }
        val url = channel.urls[_currentIptvUrlIdx]
        Log.d("Player", "播放${channel.name}（${_currentIptvUrlIdx + 1}/${_currentChannel.urls.size}）: $url")
        videoPlayerState.prepare(url)
    }

    fun changeCurrentChannelToPrev() {
        changeCurrentChannel(getPrevChannel())
    }

    fun changeCurrentChannelToNext() {
        changeCurrentChannel(getNextChannel())
    }


    fun changeToPrevSource(){
        if (currentChannel.urls.size > 1) {
            changeCurrentChannel(
                channel =currentChannel,
                urlIdx = currentSourceIndex - 1,
            )
        }
    }
    fun changeToNextSource(){
        if (currentChannel.urls.size > 1) {
            changeCurrentChannel(
                channel = currentChannel,
                urlIdx = currentSourceIndex + 1,
            )
        }
    }

    fun showChannelInfo() {
        isMenuVisible = false
        isChannelInfoVisible = true
    }

    fun showMenu() {
        isMenuVisible = true
        isChannelInfoVisible = false
    }

    fun showSettings() {
        isMenuVisible = false
        isSettingsVisale = true
        isChannelInfoVisible = false
    }
}

@Composable
fun rememberMainContentState(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    videoPlayerState: LeanbackVideoPlayerState = rememberLeanbackVideoPlayerState(),
    groups: TVGroupList = TVGroupList(),
) = remember {
    MainContentState(
        coroutineScope = coroutineScope,
        videoPlayerState = videoPlayerState,
        groups = groups,
    )
}

private fun getUrlHost(url: String): String {
    return url.split("://").getOrElse(1) { "" }.split("/").firstOrNull() ?: url
}