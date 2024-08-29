package me.lsong.mytv.utils

/**
 * 常量
 */
object Constants {
    /**
     * 应用 标题
     */
    const val APP_NAME = "DuckTV"

    /**
     * IPTV源地址
     *
     */
    const val IPTV_SOURCE_URL = "http://lsong.one:8888/IPTV.m3u"
    // http://lsong.one:8888/IPTV.m3u
    // https://raw.githubusercontent.com/YueChan/Live/main/IPTV.m3u
    // https://raw.githubusercontent.com/fanmingming/live/main/tv/m3u/ipv6.m3u
    // https://live.fanmingming.com/tv/m3u/index.m3u

    /**
     * 节目单XML地址
     */
    const val EPG_XML_URL = "http://epg.51zmt.top:8000/e.xml.gz"
    // const val EPG_XML_URL = "https://live.fanmingming.com/e.xml"

    /**
     * HTTP请求重试次数
     */
    const val HTTP_RETRY_COUNT = 10

    /**
     * HTTP请求重试间隔时间（毫秒）
     */
    const val HTTP_RETRY_INTERVAL = 3000L

    /**
     * 播放器加载超时
     */
    const val VIDEO_PLAYER_LOAD_TIMEOUT = 1000L * 15 // 15秒

    /**
     * 播放器 userAgent
     */
    // const val VIDEO_PLAYER_USER_AGENT = "ExoPlayer"
    // /**
    //  * 界面 临时面板界面显示时间
    //  */
    // const val UI_TEMP_PANEL_SCREEN_SHOW_DURATION = 1500L // 1.5秒
}