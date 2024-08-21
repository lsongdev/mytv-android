package me.lsong.mytv.utils

import androidx.compose.ui.text.intl.Locale

/**
 * 常量
 */
object Constants {
    /**
     * 应用 标题
     */
    const val APP_NAME = "DuckTV"

    val TIME_ZONE = java.util.Locale.CHINA

    /**
     * IPTV源地址
     *
     */
    const val IPTV_SOURCE_URL = "https://raw.githubusercontent.com/YueChan/Live/main/IPTV.m3u"
    // https://raw.githubusercontent.com/fanmingming/live/main/tv/m3u/ipv6.m3u

    /**
     * IPTV源缓存时间（毫秒）
     */
    const val IPTV_SOURCE_CACHE_TIME = 1000 * 60 * 60 * 24L // 24小时

    /**
     * 节目单XML地址
     */
    const val EPG_XML_URL = "http://epg.51zmt.top:8000/e.xml.gz"
    // const val EPG_XML_URL = "https://live.fanmingming.com/e.xml"
    //
    /**
     * 节目单刷新时间阈值（小时）
     */
    const val EPG_REFRESH_TIME_THRESHOLD = 2 // 不到2点不刷新

    /**
     * GitHub加速代理地址
     */
    const val GITHUB_PROXY = "https://mirror.ghproxy.com/"

    /**
     * HTTP请求重试次数
     */
    const val HTTP_RETRY_COUNT = 10L

    /**
     * HTTP请求重试间隔时间（毫秒）
     */
    const val HTTP_RETRY_INTERVAL = 3000L

    /**
     * 播放器 userAgent
     */
    const val VIDEO_PLAYER_USER_AGENT = "ExoPlayer"

    /**
     * 日志历史最大保留条数
     */
    const val LOG_HISTORY_MAX_SIZE = 50

    /**
     * 播放器加载超时
     */
    const val VIDEO_PLAYER_LOAD_TIMEOUT = 1000L * 15 // 15秒

    /**
     * 界面 超时未操作自动关闭界面
     */
    const val UI_SCREEN_AUTO_CLOSE_DELAY = 1000L * 15 // 15秒

    /**
     * 界面 时间显示前后范围
     */
    const val UI_TIME_SHOW_RANGE = 1000L * 30 // 前后30秒

    /**
     * 界面 临时面板界面显示时间
     */
    const val UI_TEMP_PANEL_SCREEN_SHOW_DURATION = 1500L // 1.5秒
}