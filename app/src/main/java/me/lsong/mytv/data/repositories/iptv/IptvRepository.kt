package me.lsong.mytv.data.repositories.iptv

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import me.lsong.mytv.data.entities.TVSource
import me.lsong.mytv.data.repositories.FileCacheRepository
import me.lsong.mytv.data.repositories.iptv.parser.IptvParser
import me.lsong.mytv.data.repositories.iptv.parser.M3uData
import me.lsong.mytv.utils.Logger

/**
 * 直播源获取
 */
class IptvRepository : FileCacheRepository("iptv.txt") {
    private val log = Logger.create(javaClass.simpleName)

    /**
     * 获取远程直播源数据
     */
    private suspend fun fetchSource(sourceUrl: String) = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val request = Request.Builder().url(sourceUrl).build()
        try {
            with(client.newCall(request).execute()) {
                if (!isSuccessful) {
                    throw Exception("fetchSource failed: $code")
                }
                return@with body!!.string().trim()
            }
        } catch (ex: Exception) {
            throw Exception("获取远程直播源失败，请检查网络连接", ex)
        }
    }

    /**
     * 获取直播源列表
     */
    suspend fun getChannelSourceList(sourceUrl: String): M3uData {
        val sourceData = fetchSource(sourceUrl)
        val parser = IptvParser.instances.first { it.isSupport(sourceUrl, sourceData) }
        val m3u = parser.parse(sourceData)
        log.i("解析直播源完成：${m3u.sources.size}个资源, $sourceUrl")
        return m3u
    }
}