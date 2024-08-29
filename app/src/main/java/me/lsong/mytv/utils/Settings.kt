package me.lsong.mytv.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * 应用配置存储
 */
object Settings {
    private const val SP_NAME = Constants.APP_NAME
    private const val SP_MODE = Context.MODE_PRIVATE
    private lateinit var sp: SharedPreferences

    private fun getInstance(context: Context): SharedPreferences =
        context.getSharedPreferences(SP_NAME, SP_MODE)

    fun init(context: Context) {
        sp = getInstance(context)
    }

    enum class KEY {
        /** ==================== 应用 ==================== */
        /** 开机自启 */
        APP_BOOT_LAUNCH,

        /** 设备显示类型 */
        APP_DEVICE_DISPLAY_TYPE,

        /** ==================== 调式 ==================== */
        /** 显示fps */
        DEBUG_SHOW_FPS,

        /** 播放器详细信息 */
        DEBUG_SHOW_VIDEO_PLAYER_METADATA,

        /** ==================== 直播源 ==================== */
        /** 上一次直播源序号 */
        IPTV_LAST_IPTV_IDX,

        /** 换台反转 */
        IPTV_CHANNEL_CHANGE_FLIP,

        /** 直播源url */
        IPTV_SOURCE_URL_LIST,

        /** 直播源可播放host列表 */
        IPTV_PLAYABLE_HOST_LIST,

        /** 直播源历史列表 */
        IPTV_SOURCE_URL_HISTORY_LIST,

        /** 直播源频道收藏列表 */
        IPTV_CHANNEL_FAVORITE_LIST,

        /** ==================== 节目单 ==================== */

        /** 节目单刷新时间阈值（小时） */
        EPG_REFRESH_TIME_THRESHOLD,

        /** 节目单历史列表 */
        EPG_URL_LIST,

        /** ==================== 界面 ==================== */
        /** 界面密度缩放比例 */
        UI_DENSITY_SCALE_RATIO,

        /** 界面字体缩放比例 */
        UI_FONT_SCALE_RATIO,

        /** 时间显示模式 */
        UI_TIME_SHOW_MODE,

        /** 画中画模式 */
        UI_PIP_MODE,

        /** ==================== 播放器 ==================== */
        /** 播放器 加载超时 */
        VIDEO_PLAYER_LOAD_TIMEOUT,

        /** 播放器 画面比例 */
        VIDEO_PLAYER_ASPECT_RATIO,
    }

    /** ==================== 调式 ==================== */
    /** 显示fps */
    var debugShowFps: Boolean
        get() = sp.getBoolean(KEY.DEBUG_SHOW_FPS.name, false)
        set(value) = sp.edit().putBoolean(KEY.DEBUG_SHOW_FPS.name, value).apply()

    /** 播放器详细信息 */
    var debugShowVideoPlayerMetadata: Boolean
        get() = sp.getBoolean(KEY.DEBUG_SHOW_VIDEO_PLAYER_METADATA.name, false)
        set(value) = sp.edit().putBoolean(KEY.DEBUG_SHOW_VIDEO_PLAYER_METADATA.name, value).apply()

    /** ==================== 直播源 ==================== */
    /** 上一次直播源序号 */
    var iptvLastIptvIdx: Int
        get() = sp.getInt(KEY.IPTV_LAST_IPTV_IDX.name, 0)
        set(value) = sp.edit().putInt(KEY.IPTV_LAST_IPTV_IDX.name, value).apply()

    /** 换台反转 */
    var iptvChannelChangeFlip: Boolean
        get() = sp.getBoolean(KEY.IPTV_CHANNEL_CHANGE_FLIP.name, false)
        set(value) = sp.edit().putBoolean(KEY.IPTV_CHANNEL_CHANGE_FLIP.name, value).apply()

    /** 直播源可播放host列表 */
    var iptvPlayableHostList: Set<String>
        get() = sp.getStringSet(KEY.IPTV_PLAYABLE_HOST_LIST.name, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY.IPTV_PLAYABLE_HOST_LIST.name, value).apply()

    /** 直播源 url */
    var iptvSourceUrls: Set<String>
        get() = sp.getStringSet(KEY.IPTV_SOURCE_URL_LIST.name, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY.IPTV_SOURCE_URL_LIST.name, value).apply()

    /** 直播源历史列表 */
    var iptvSourceUrlHistoryList: Set<String>
        get() = sp.getStringSet(KEY.IPTV_SOURCE_URL_HISTORY_LIST.name, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY.IPTV_SOURCE_URL_HISTORY_LIST.name, value).apply()

    /** 直播源频道收藏列表 */
    var iptvChannelFavoriteList: Set<String>
        get() = sp.getStringSet(KEY.IPTV_CHANNEL_FAVORITE_LIST.name, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY.IPTV_CHANNEL_FAVORITE_LIST.name, value).apply()

    /** ==================== 节目单 ==================== */

    /** 节目单历史列表 */
    var epgUrls: Set<String>
        get() = sp.getStringSet(KEY.EPG_URL_LIST.name, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY.EPG_URL_LIST.name, value).apply()

    /** ==================== 界面 ==================== */
    /** 界面密度缩放比例 */
    var uiDensityScaleRatio: Float
        get() = sp.getFloat(KEY.UI_DENSITY_SCALE_RATIO.name, 1f)
        set(value) = sp.edit().putFloat(KEY.UI_DENSITY_SCALE_RATIO.name, value).apply()

    /** 界面字体缩放比例 */
    var uiFontScaleRatio: Float
        get() = sp.getFloat(KEY.UI_FONT_SCALE_RATIO.name, 1f)
        set(value) = sp.edit().putFloat(KEY.UI_FONT_SCALE_RATIO.name, value).apply()

    /** ==================== 播放器 ==================== */

    /** 播放器 加载超时 */
    var videoPlayerLoadTimeout: Long
        get() = sp.getLong(KEY.VIDEO_PLAYER_LOAD_TIMEOUT.name, Constants.VIDEO_PLAYER_LOAD_TIMEOUT)
        set(value) = sp.edit().putLong(KEY.VIDEO_PLAYER_LOAD_TIMEOUT.name, value).apply()

    /** 播放器 画面比例 */
    var videoPlayerAspectRatio: VideoPlayerAspectRatio
        get() = VideoPlayerAspectRatio.fromValue(
            sp.getInt(KEY.VIDEO_PLAYER_ASPECT_RATIO.name, VideoPlayerAspectRatio.ORIGINAL.value)
        )
        set(value) = sp.edit().putInt(KEY.VIDEO_PLAYER_ASPECT_RATIO.name, value.value).apply()

    enum class VideoPlayerAspectRatio(val value: Int) {
        /** 原始 */
        ORIGINAL(0),

        /** 16:9 */
        ASPECT_16_9(1),

        /** 4:3 */
        ASPECT_4_3(2),

        /** full screen */
        FULL_SCREEN(3);

        companion object {
            fun fromValue(value: Int): VideoPlayerAspectRatio {
                return entries.firstOrNull { it.value == value } ?: ORIGINAL
            }
        }
    }
}