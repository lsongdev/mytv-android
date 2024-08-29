package me.lsong.mytv.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import me.lsong.mytv.utils.Settings

class MyTvSettingsViewModel : ViewModel() {

    private var _debugShowFps by mutableStateOf(Settings.debugShowFps)
    var debugShowFps: Boolean
        get() = _debugShowFps
        set(value) {
            _debugShowFps = value
            Settings.debugShowFps = value
        }

    private var _debugShowVideoPlayerMetadata by mutableStateOf(Settings.debugShowVideoPlayerMetadata)
    var debugShowVideoPlayerMetadata: Boolean
        get() = _debugShowVideoPlayerMetadata
        set(value) {
            _debugShowVideoPlayerMetadata = value
            Settings.debugShowVideoPlayerMetadata = value
        }

    private var _iptvChannelChangeFlip by mutableStateOf(Settings.iptvChannelChangeFlip)
    var iptvChannelChangeFlip: Boolean
        get() = _iptvChannelChangeFlip
        set(value) {
            _iptvChannelChangeFlip = value
            Settings.iptvChannelChangeFlip = value
        }

    private var _iptvSourceUrls by mutableStateOf(Settings.iptvSourceUrls)
    var iptvSourceUrls: Set<String>
        get() = _iptvSourceUrls
        set(value) {
            _iptvSourceUrls = value
            Settings.iptvSourceUrls = value
        }

    private var _iptvPlayableHostList by mutableStateOf(Settings.iptvPlayableHostList)
    var iptvPlayableHostList: Set<String>
        get() = _iptvPlayableHostList
        set(value) {
            _iptvPlayableHostList = value
            Settings.iptvPlayableHostList = value
        }

    // private var _iptvSourceUrlHistoryList by mutableStateOf(SP.iptvSourceUrlHistoryList)
    // var iptvSourceUrlHistoryList: Set<String>
    //     get() = _iptvSourceUrlHistoryList
    //     set(value) {
    //         _iptvSourceUrlHistoryList = value
    //         SP.iptvSourceUrlHistoryList = value
    //     }

    private var _iptvChannelFavoriteList by mutableStateOf(Settings.iptvChannelFavoriteList)
    var iptvChannelFavoriteList: Set<String>
        get() = _iptvChannelFavoriteList
        set(value) {
            _iptvChannelFavoriteList = value
            Settings.iptvChannelFavoriteList = value
        }

    private var _epgXmlUrlHistoryList by mutableStateOf(Settings.epgUrls)
    var epgUrls: Set<String>
        get() = _epgXmlUrlHistoryList
        set(value) {
            _epgXmlUrlHistoryList = value
            Settings.epgUrls = value
        }

    private var _uiDensityScaleRatio by mutableFloatStateOf(Settings.uiDensityScaleRatio)
    var uiDensityScaleRatio: Float
        get() = _uiDensityScaleRatio
        set(value) {
            _uiDensityScaleRatio = value
            Settings.uiDensityScaleRatio = value
        }

    private var _uiFontScaleRatio by mutableFloatStateOf(Settings.uiFontScaleRatio)
    var uiFontScaleRatio: Float
        get() = _uiFontScaleRatio
        set(value) {
            _uiFontScaleRatio = value
            Settings.uiFontScaleRatio = value
        }

    // private var _uiPipMode by mutableStateOf(Settings.uiPipMode)
    // var uiPipMode: Boolean
    //     get() = _uiPipMode
    //     set(value) {
    //         _uiPipMode = value
    //         Settings.uiPipMode = value
    //     }

    private var _videoPlayerLoadTimeout by mutableLongStateOf(Settings.videoPlayerLoadTimeout)
    var videoPlayerLoadTimeout: Long
        get() = _videoPlayerLoadTimeout
        set(value) {
            _videoPlayerLoadTimeout = value
            Settings.videoPlayerLoadTimeout = value
        }

    private var _videoPlayerAspectRatio by mutableStateOf(Settings.videoPlayerAspectRatio)
    var videoPlayerAspectRatio: Settings.VideoPlayerAspectRatio
        get() = _videoPlayerAspectRatio
        set(value) {
            _videoPlayerAspectRatio = value
            Settings.videoPlayerAspectRatio = value
        }
}