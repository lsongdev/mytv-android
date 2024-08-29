package me.lsong.mytv.epg

import android.util.Log
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import me.lsong.mytv.epg.fetcher.EpgFetcher
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 节目单获取
 */
class EpgRepository {

    /**
     * 解析节目单xml
     */
    private fun parseFromXml(xmlString: String): EpgList {
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xmlString))
        val epgMap = mutableMapOf<String, EpgChannel>()
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "channel") {
                        val channelId = parser.getAttributeValue(null, "id")
                        parser.nextTag()
                        val channelDisplayName = parser.nextText()
                        val channel = EpgChannel(
                            id = channelId,
                            title = channelDisplayName,
                        )
                        // Log.d("epg", "${channel.id}, ${channel.title}")
                        epgMap[channelId] = channel
                    } else if (parser.name == "programme") {
                        val channelId = parser.getAttributeValue(null, "channel")
                        val startTime = parser.getAttributeValue(null, "start")
                        val stopTime = parser.getAttributeValue(null, "stop")
                        parser.nextTag()
                        val title = parser.nextText()
                        fun parseTime(time: String): Long {
                            if (time.length < 14) return 0
                            return SimpleDateFormat("yyyyMMddHHmmss Z", Locale.getDefault()).parse(time)?.time ?: 0
                        }
                        val programme = EpgProgramme(
                            channelId = channelId,
                            startAt = parseTime(startTime),
                            endAt = parseTime(stopTime),
                            title = title,
                        )
                        if (epgMap.containsKey(channelId)) {
                            // Log.d("epg", "${programme.channelId}, ${programme.title}")
                            epgMap[channelId] = epgMap[channelId]!!.copy(
                                programmes = epgMap[channelId]!!.programmes + listOf(programme)
                            )
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        Log.i("epg","解析节目单完成，共${epgMap.size}个频道")
        return EpgList(epgMap.values.toList())
    }

    private suspend fun fetchXml(url: String): String = withContext(Dispatchers.IO) {
        Log.d("epg", "获取远程节目单xml: $url")

        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        try {
            with(client.newCall(request).execute()) {
                if (!isSuccessful) {
                    throw Exception("获取远程节目单xml失败: $code")
                }

                val fetcher = EpgFetcher.instances.first { it.isSupport(url) }

                return@with fetcher.fetch(this)
            }
        } catch (ex: Exception) {
            throw Exception("获取远程节目单xml失败，请检查网络连接", ex)
        }
    }

    suspend fun getEpgList(xmlUrl: String): EpgList = withContext(Dispatchers.Default) {
        try {
            val xmlString = fetchXml(xmlUrl)
            return@withContext parseFromXml(xmlString)
        } catch (ex: Exception) {
            Log.e("epg", "获取节目单失败", ex)
            throw Exception(ex)
        }
    }
}
