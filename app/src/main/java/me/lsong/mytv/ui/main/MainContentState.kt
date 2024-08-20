package me.lsong.mytv.ui.main

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
import me.lsong.mytv.data.entities.TVChannel
import me.lsong.mytv.data.entities.TVGroupList
import me.lsong.mytv.data.entities.TVGroupList.Companion.channels
import me.lsong.mytv.data.entities.TVGroupList.Companion.findChannelIndex
import me.lsong.mytv.utils.Constants
import me.lsong.mytv.ui.player.LeanbackVideoPlayerState
import me.lsong.mytv.ui.player.rememberLeanbackVideoPlayerState
import me.lsong.mytv.utils.SP
import me.lsong.mytv.utils.Loggable
import kotlin.math.max

@Stable
class MainContentState(
    coroutineScope: CoroutineScope,
    private val videoPlayerState: LeanbackVideoPlayerState,
    private val tvGroupList: TVGroupList,
) : Loggable() {
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

    val currentChannelIndex
        get() = tvGroupList.findChannelIndex(_currentChannel)

    init {
        changeCurrentChannel(tvGroupList.channels.getOrElse(SP.iptvLastIptvIdx) {
            tvGroupList.firstOrNull()?.channels?.firstOrNull() ?: TVChannel()
        })

        videoPlayerState.onReady {
            coroutineScope.launch {
                val name = _currentChannel.name
                val urlIdx = _currentIptvUrlIdx
                delay(Constants.UI_TEMP_PANEL_SCREEN_SHOW_DURATION)
                if (name == _currentChannel.name && urlIdx == _currentIptvUrlIdx) {
                    // _isTempPanelVisible = false
                }
            }

            // 记忆可播放的域名
            SP.iptvPlayableHostList += getUrlHost(_currentChannel.urls[_currentIptvUrlIdx])
        }

        videoPlayerState.onError {
            if (_currentIptvUrlIdx < _currentChannel.urls.size - 1) {
                changeCurrentChannel(_currentChannel, _currentIptvUrlIdx + 1)
            }

            // 从记忆中删除不可播放的域名
            SP.iptvPlayableHostList -= getUrlHost(_currentChannel.urls[_currentIptvUrlIdx])
        }

        videoPlayerState.onCutoff {
            changeCurrentChannel(_currentChannel, _currentIptvUrlIdx)
        }
    }

    private fun getPrevChannel(): TVChannel {
        val currentIndex = tvGroupList.findChannelIndex(_currentChannel)
        return tvGroupList.channels.getOrElse(currentIndex - 1) {
            tvGroupList.lastOrNull()?.channels?.lastOrNull() ?: TVChannel()
        }
    }

    private fun getNextChannel(): TVChannel {
        val currentIndex = tvGroupList.findChannelIndex(_currentChannel)
        return tvGroupList.channels.getOrElse(currentIndex + 1) {
            tvGroupList.firstOrNull()?.channels?.firstOrNull() ?: TVChannel()
        }
    }

    fun changeCurrentChannel(channel: TVChannel, urlIdx: Int? = null) {
        // isChannelInfoVisible = false
        if (channel == _currentChannel && urlIdx == null) return
        if (channel == _currentChannel && urlIdx != _currentIptvUrlIdx) {
            SP.iptvPlayableHostList -= getUrlHost(_currentChannel.urls[_currentIptvUrlIdx])
        }
        // _isTempPanelVisible = true

        _currentChannel = channel
        SP.iptvLastIptvIdx = currentChannelIndex

        _currentIptvUrlIdx = if (urlIdx == null) {
            // 优先从记忆中选择可播放的域名
            max(0, _currentChannel.urls.indexOfFirst {
                SP.iptvPlayableHostList.contains(getUrlHost(it))
            })
        } else {
            (urlIdx + _currentChannel.urls.size) % _currentChannel.urls.size
        }
        val url = channel.urls[_currentIptvUrlIdx]
        log.d("播放${channel.name}（${_currentIptvUrlIdx + 1}/${_currentChannel.urls.size}）: $url")
        videoPlayerState.prepare(url)
    }

    fun changeCurrentChannelToPrev() {
        changeCurrentChannel(getPrevChannel())
    }

    fun changeCurrentChannelToNext() {
        changeCurrentChannel(getNextChannel())
    }
}

@Composable
fun rememberMainContentState(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    videoPlayerState: LeanbackVideoPlayerState = rememberLeanbackVideoPlayerState(),
    tvGroupList: TVGroupList = TVGroupList(),
) = remember {
    MainContentState(
        coroutineScope = coroutineScope,
        videoPlayerState = videoPlayerState,
        tvGroupList = tvGroupList,
    )
}

private fun getUrlHost(url: String): String {
    return url.split("://").getOrElse(1) { "" }.split("/").firstOrNull() ?: url
}