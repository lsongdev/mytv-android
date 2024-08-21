package me.lsong.mytv.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import me.lsong.mytv.utils.SP

class LeanbackSettingsViewModel : ViewModel() {
    private var _appBootLaunch by mutableStateOf(SP.appBootLaunch)
    var appBootLaunch: Boolean
        get() = _appBootLaunch
        set(value) {
            _appBootLaunch = value
            SP.appBootLaunch = value
        }

    private var _appDeviceDisplayType by mutableStateOf(SP.appDeviceDisplayType)
    var appDeviceDisplayType: SP.AppDeviceDisplayType
        get() = _appDeviceDisplayType
        set(value) {
            _appDeviceDisplayType = value
            SP.appDeviceDisplayType = value
        }

    private var _debugShowFps by mutableStateOf(SP.debugShowFps)
    var debugShowFps: Boolean
        get() = _debugShowFps
        set(value) {
            _debugShowFps = value
            SP.debugShowFps = value
        }

    private var _debugShowVideoPlayerMetadata by mutableStateOf(SP.debugShowVideoPlayerMetadata)
    var debugShowVideoPlayerMetadata: Boolean
        get() = _debugShowVideoPlayerMetadata
        set(value) {
            _debugShowVideoPlayerMetadata = value
            SP.debugShowVideoPlayerMetadata = value
        }

    private var _iptvLastIptvIdx by mutableIntStateOf(SP.iptvLastIptvIdx)
    var iptvLastIptvIdx: Int
        get() = _iptvLastIptvIdx
        set(value) {
            _iptvLastIptvIdx = value
            SP.iptvLastIptvIdx = value
        }

    private var _iptvChannelChangeFlip by mutableStateOf(SP.iptvChannelChangeFlip)
    var iptvChannelChangeFlip: Boolean
        get() = _iptvChannelChangeFlip
        set(value) {
            _iptvChannelChangeFlip = value
            SP.iptvChannelChangeFlip = value
        }

    private var _iptvSourceCacheTime by mutableLongStateOf(SP.iptvSourceCacheTime)
    var iptvSourceCacheTime: Long
        get() = _iptvSourceCacheTime
        set(value) {
            _iptvSourceCacheTime = value
            SP.iptvSourceCacheTime = value
        }

    private var _iptvSourceUrls by mutableStateOf(SP.iptvSourceUrls)
    var iptvSourceUrls: Set<String>
        get() = _iptvSourceUrls
        set(value) {
            _iptvSourceUrls = value
            SP.iptvSourceUrls = value
        }

    private var _iptvPlayableHostList by mutableStateOf(SP.iptvPlayableHostList)
    var iptvPlayableHostList: Set<String>
        get() = _iptvPlayableHostList
        set(value) {
            _iptvPlayableHostList = value
            SP.iptvPlayableHostList = value
        }

    // private var _iptvSourceUrlHistoryList by mutableStateOf(SP.iptvSourceUrlHistoryList)
    // var iptvSourceUrlHistoryList: Set<String>
    //     get() = _iptvSourceUrlHistoryList
    //     set(value) {
    //         _iptvSourceUrlHistoryList = value
    //         SP.iptvSourceUrlHistoryList = value
    //     }

    private var _iptvChannelFavoriteList by mutableStateOf(SP.iptvChannelFavoriteList)
    var iptvChannelFavoriteList: Set<String>
        get() = _iptvChannelFavoriteList
        set(value) {
            _iptvChannelFavoriteList = value
            SP.iptvChannelFavoriteList = value
        }

    private var _epgRefreshTimeThreshold by mutableIntStateOf(SP.epgRefreshTimeThreshold)
    var epgRefreshTimeThreshold: Int
        get() = _epgRefreshTimeThreshold
        set(value) {
            _epgRefreshTimeThreshold = value
            SP.epgRefreshTimeThreshold = value
        }

    private var _epgXmlUrlHistoryList by mutableStateOf(SP.epgUrls)
    var epgUrls: Set<String>
        get() = _epgXmlUrlHistoryList
        set(value) {
            _epgXmlUrlHistoryList = value
            SP.epgUrls = value
        }

    private var _uiDensityScaleRatio by mutableFloatStateOf(SP.uiDensityScaleRatio)
    var uiDensityScaleRatio: Float
        get() = _uiDensityScaleRatio
        set(value) {
            _uiDensityScaleRatio = value
            SP.uiDensityScaleRatio = value
        }

    private var _uiFontScaleRatio by mutableFloatStateOf(SP.uiFontScaleRatio)
    var uiFontScaleRatio: Float
        get() = _uiFontScaleRatio
        set(value) {
            _uiFontScaleRatio = value
            SP.uiFontScaleRatio = value
        }

    private var _uiTimeShowMode by mutableStateOf(SP.uiTimeShowMode)
    var uiTimeShowMode: SP.UiTimeShowMode
        get() = _uiTimeShowMode
        set(value) {
            _uiTimeShowMode = value
            SP.uiTimeShowMode = value
        }

    private var _uiPipMode by mutableStateOf(SP.uiPipMode)
    var uiPipMode: Boolean
        get() = _uiPipMode
        set(value) {
            _uiPipMode = value
            SP.uiPipMode = value
        }

    private var _videoPlayerUserAgent by mutableStateOf(SP.videoPlayerUserAgent)
    var videoPlayerUserAgent: String
        get() = _videoPlayerUserAgent
        set(value) {
            _videoPlayerUserAgent = value
            SP.videoPlayerUserAgent = value
        }

    private var _videoPlayerLoadTimeout by mutableLongStateOf(SP.videoPlayerLoadTimeout)
    var videoPlayerLoadTimeout: Long
        get() = _videoPlayerLoadTimeout
        set(value) {
            _videoPlayerLoadTimeout = value
            SP.videoPlayerLoadTimeout = value
        }

    private var _videoPlayerAspectRatio by mutableStateOf(SP.videoPlayerAspectRatio)
    var videoPlayerAspectRatio: SP.VideoPlayerAspectRatio
        get() = _videoPlayerAspectRatio
        set(value) {
            _videoPlayerAspectRatio = value
            SP.videoPlayerAspectRatio = value
        }
}