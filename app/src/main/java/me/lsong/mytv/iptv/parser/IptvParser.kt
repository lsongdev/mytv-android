package me.lsong.mytv.iptv.parser

import me.lsong.mytv.iptv.TVSource

data class M3uData(
    var epgUrl: String?,
    val sources: List<TVSource>,
)

/**
 * 直播源数据解析接口
 */
interface IptvParser {
    /**
     * 是否支持该直播源格式
     */
    fun isSupport(url: String, data: String): Boolean

    /**
     * 解析直播源数据
     */
    suspend fun parse(data: String): M3uData

    companion object {
        val instances = listOf(
            M3uIptvParser(),
        )
    }
}

