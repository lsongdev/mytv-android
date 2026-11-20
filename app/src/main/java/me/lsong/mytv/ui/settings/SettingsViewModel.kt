package me.lsong.mytv.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import me.lsong.mytv.data.preferences.AppPreferences

class SettingsViewModel : ViewModel() {

    private var _debugShowFps by mutableStateOf(AppPreferences.debugShowFps)
    var debugShowFps: Boolean
        get() = _debugShowFps
        set(value) {
            _debugShowFps = value
            AppPreferences.debugShowFps = value
        }

    private var _debugShowVideoPlayerMetadata by mutableStateOf(AppPreferences.debugShowVideoPlayerMetadata)
    var debugShowVideoPlayerMetadata: Boolean
        get() = _debugShowVideoPlayerMetadata
        set(value) {
            _debugShowVideoPlayerMetadata = value
            AppPreferences.debugShowVideoPlayerMetadata = value
        }

    private var _iptvChannelChangeFlip by mutableStateOf(AppPreferences.iptvChannelChangeFlip)
    var iptvChannelChangeFlip: Boolean
        get() = _iptvChannelChangeFlip
        set(value) {
            _iptvChannelChangeFlip = value
            AppPreferences.iptvChannelChangeFlip = value
        }

    private var _iptvPlayableHostList by mutableStateOf(AppPreferences.iptvPlayableHostList)
    var iptvPlayableHostList: Set<String>
        get() = _iptvPlayableHostList
        set(value) {
            _iptvPlayableHostList = value
            AppPreferences.iptvPlayableHostList = value
        }

    // private var _iptvSourceUrlHistoryList by mutableStateOf(SP.iptvSourceUrlHistoryList)
    // var iptvSourceUrlHistoryList: Set<String>
    //     get() = _iptvSourceUrlHistoryList
    //     set(value) {
    //         _iptvSourceUrlHistoryList = value
    //         SP.iptvSourceUrlHistoryList = value
    //     }

    private var _iptvChannelFavoriteList by mutableStateOf(AppPreferences.iptvChannelFavoriteList)
    var iptvChannelFavoriteList: Set<String>
        get() = _iptvChannelFavoriteList
        set(value) {
            _iptvChannelFavoriteList = value
            AppPreferences.iptvChannelFavoriteList = value
        }

    private var _epgXmlUrlHistoryList by mutableStateOf(AppPreferences.epgUrls)
    var epgUrls: Set<String>
        get() = _epgXmlUrlHistoryList
        set(value) {
            _epgXmlUrlHistoryList = value
            AppPreferences.epgUrls = value
        }

    private var _uiDensityScaleRatio by mutableFloatStateOf(AppPreferences.uiDensityScaleRatio)
    var uiDensityScaleRatio: Float
        get() = _uiDensityScaleRatio
        set(value) {
            _uiDensityScaleRatio = value
            AppPreferences.uiDensityScaleRatio = value
        }

    private var _uiFontScaleRatio by mutableFloatStateOf(AppPreferences.uiFontScaleRatio)
    var uiFontScaleRatio: Float
        get() = _uiFontScaleRatio
        set(value) {
            _uiFontScaleRatio = value
            AppPreferences.uiFontScaleRatio = value
        }

    // private var _uiPipMode by mutableStateOf(AppPreferences.uiPipMode)
    // var uiPipMode: Boolean
    //     get() = _uiPipMode
    //     set(value) {
    //         _uiPipMode = value
    //         AppPreferences.uiPipMode = value
    //     }

    private var _videoPlayerLoadTimeout by mutableLongStateOf(AppPreferences.videoPlayerLoadTimeout)
    var videoPlayerLoadTimeout: Long
        get() = _videoPlayerLoadTimeout
        set(value) {
            _videoPlayerLoadTimeout = value
            AppPreferences.videoPlayerLoadTimeout = value
        }

    private var _videoPlayerAspectRatio by mutableStateOf(AppPreferences.videoPlayerAspectRatio)
    var videoPlayerAspectRatio: AppPreferences.VideoPlayerAspectRatio
        get() = _videoPlayerAspectRatio
        set(value) {
            _videoPlayerAspectRatio = value
            AppPreferences.videoPlayerAspectRatio = value
        }
    fun cycleVideoAspectRatio() {
        val values = AppPreferences.VideoPlayerAspectRatio.entries
        videoPlayerAspectRatio = values[(values.indexOf(videoPlayerAspectRatio) + 1) % values.size]
    }

}
