package me.lsong.mytv.providers

import android.util.Log
import android.util.Xml
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.lsong.mytv.epg.EpgChannel
import me.lsong.mytv.epg.EpgList
import me.lsong.mytv.epg.EpgProgramme
import me.lsong.mytv.epg.EpgRepository
import me.lsong.mytv.utils.Constants
import me.lsong.mytv.utils.Settings
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale


// 数据类定义
@Immutable
data class TVSource(
    val tvgId: String? = null,
    val tvgLogo: String? = null,
    val tvgName: String? = null,
    val groupTitle: String? = null,
    val title: String,
    val url: String
) {
    val name: String get() = tvgName ?: tvgId ?: title

    companion object {
        val EXAMPLE = TVSource(
            tvgId = "cctv1",
            tvgName = "cctv1",
            tvgLogo = "https://live.fanmingming.com/tv/CCTV1.png",
            title = "CCTV-1",
            groupTitle = "央视",
            url = "https://pi.0472.org/chc/ym.m3u8"
        )
    }
}

@Immutable
data class TVChannel(
    val name: String = "",
    val title: String = "",
    val sources: List<TVSource> = emptyList()
) {
    val logo: String? get() = sources.firstNotNullOfOrNull { it.tvgLogo }
    val groupTitle: String? get() = sources.firstNotNullOfOrNull { it.groupTitle }
    val urls: List<String> get() = sources.map { it.url }

    companion object {
        val EXAMPLE = TVChannel(
            title = "测试频道",
            sources = listOf(TVSource.EXAMPLE)
        )
    }
}

@Immutable
data class TVGroup(
    val title: String = "",
    val channels: TVChannelList = TVChannelList()
) {
    companion object {
        val EXAMPLE = TVGroup(
            title = "测试分组",
            channels = TVChannelList(List(10) { TVChannel.EXAMPLE })
        )
    }
}

@Immutable
data class TVGroupList(val value: List<TVGroup> = emptyList()) : List<TVGroup> by value {
    companion object {
        val EXAMPLE = TVGroupList(List(5) { TVGroup.EXAMPLE.copy(title = "Group $it") })

        fun TVGroupList.findGroupIndex(channel: TVChannel) =
            indexOfFirst { it.channels.contains(channel) }

        fun TVGroupList.findChannelIndex(channel: TVChannel) =
            flatMap { it.channels }.indexOf(channel)

        val TVGroupList.channels: List<TVChannel>
            get() = flatMap { it.channels }
    }
}

@Immutable
data class TVChannelList(val value: List<TVChannel> = emptyList()) : List<TVChannel> by value {
    companion object {
        val EXAMPLE = TVChannelList(List(10) { TVChannel.EXAMPLE.copy() })
    }
}

class IPTVProvider(private val epgRepository: EpgRepository) : TVProvider {
    private var groupList: TVGroupList = TVGroupList()
    private var epgList: EpgList = EpgList()

    override suspend fun load() {
        val (sources, epgUrls) = fetchIPTVSources()
        groupList = process(sources)
        epgList = fetchEPGData(epgUrls)
    }
    override fun groups(): TVGroupList {
        return groupList
    }

    override fun channels(groupTitle: String): TVChannelList {
        return groupList.find { it.title == groupTitle }?.channels ?: TVChannelList()
    }

    private suspend fun fetchIPTVSources(): Pair<List<TVSource>, List<String>> {
        val allSources = mutableListOf<TVSource>()
        val epgUrls = mutableListOf<String>()
        val iptvUrls = Settings.iptvSourceUrls.ifEmpty { listOf(Constants.IPTV_SOURCE_URL) }
        iptvUrls.forEach { url ->
            val m3u = retry { getM3uChannels(sourceUrl = url) }
            allSources.addAll(m3u.channels.map {
                TVSource(
                    tvgId = it.attributes["tvg-id"],
                    tvgLogo = it.attributes["tvg-logo"],
                    tvgName = it.attributes["tvg-name"],
                    groupTitle = it.attributes["group-title"],
                    title = it.title,
                    url = it.url,
                )
            })
            epgUrls += m3u.headers["x-tvg-url"]?.split(",").orEmpty()
        }
        if (epgUrls.isEmpty()) epgUrls.add(Constants.EPG_XML_URL)
        return Pair(allSources, epgUrls.distinct())
    }

    private suspend fun fetchEPGData(epgUrls: List<String>): EpgList {
        val epgChannels = mutableListOf<EpgChannel>()
        epgUrls.forEach { url ->
            val epg = retry { epgRepository.getEpgList(url) }
            epgChannels.addAll(epg.value)
        }
        return EpgList(epgChannels.distinctBy { it.id })
    }

    private fun process(sources: List<TVSource>): TVGroupList {
        val channels = sources.groupBy { it.name }
            .map { (name, sources) ->
                TVChannel(
                    name = name,
                    title = sources.first().title,
                    sources = sources,
                )
            }

        return TVGroupList(
            channels.groupBy { it.groupTitle ?: "其他" }
                .map { (title, channels) -> TVGroup(title = title, channels = TVChannelList(channels)) }
        )
    }

    private suspend fun <T> retry(fn: suspend () -> T): T {
        repeat(Constants.HTTP_RETRY_COUNT) {
            try {
                return fn()
            } catch (e: Exception) {
                if (it == Constants.HTTP_RETRY_COUNT) throw e
                delay(Constants.HTTP_RETRY_INTERVAL)
            }
        }
        throw IllegalStateException("Failed to fetch data after ${Constants.HTTP_RETRY_COUNT} attempts")
    }

    private suspend fun request(url: String) = withContext(Dispatchers.IO) {
        Log.d("request", "request start: $url")
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("failed: ${response.code}")
                response.body?.string()?.trim() ?: throw Exception("Empty response body")
            }
        } catch (ex: Exception) {
            val e = Exception("request failed $url", ex)
            Log.d("request", "${e.message}")
            throw  e;
        }
    }

    private suspend fun getM3uChannels(sourceUrl: String): M3uData {
        return parseM3u(request(sourceUrl))
    }
}


// Interface definition
interface TVProvider {
    suspend fun load()
    fun groups(): TVGroupList
    fun channels(groupTitle: String): TVChannelList
}

class MyTvProviderManager : TVProvider {
    private val providers: List<TVProvider> = listOf(
        IPTVProvider(EpgRepository())
    )
    override suspend fun load() {
        providers.forEach { it.load() }
    }
    override fun groups(): TVGroupList = TVGroupList(providers.flatMap { it.groups() })
    override fun channels(groupTitle: String): TVChannelList = TVChannelList(providers.flatMap { it.channels(groupTitle) })
}

data class M3uData(
    val headers: Map<String, String>,
    val channels: List<M3uSource>
)

data class M3uSource(
    val attributes: Map<String, String>,
    val title: String,
    val url: String
)

fun parseM3u(data: String): M3uData {
    val lines = data.trim().split("\r\n", "\n").filter { it.isNotBlank() }
    val headers = mutableMapOf<String, String>()
    val channels = mutableListOf<M3uSource>()
    var currentAttributes = mutableMapOf<String, String>()
    var currentTitle = ""
    for (processedLine in lines) {
        when {
            processedLine.startsWith("#EXTM3U") -> {
                headers.putAll(parseAttributes(processedLine.substring(7).trim()))
            }
            processedLine.startsWith("#EXTINF:") -> {
                val (duration, rest) = processedLine.substring(8).split(',', limit = 2)
                currentAttributes = parseAttributes(duration).toMutableMap()
                currentTitle = rest.trim()
            }
            !processedLine.startsWith("#") -> {
                channels.add(M3uSource(currentAttributes, currentTitle, processedLine.trim()))
                currentAttributes = mutableMapOf()
                currentTitle = ""
            }
        }
    }

    return M3uData(headers, channels)
}

fun parseAttributes(input: String): Map<String, String> {
    val attributes = mutableMapOf<String, String>()
    var remaining = input.trim().replace("\",\"", ",")
    while (remaining.isNotEmpty()) {
        val equalIndex = remaining.indexOf('=')
        if (equalIndex == -1) break

        val key = remaining.substring(0, equalIndex).trim()
        remaining = remaining.substring(equalIndex + 1).trim()
        val value: String
        if (remaining.startsWith("\"")) {
            val endQuoteIndex = remaining.indexOf("\"", 1)
            if (endQuoteIndex == -1) break
            value = remaining.substring(1, endQuoteIndex)
            remaining = remaining.substring(endQuoteIndex + 1).trim()
        } else {
            val spaceIndex = remaining.indexOf(' ')
            if (spaceIndex == -1) {
                value = remaining
                remaining = ""
            } else {
                value = remaining.substring(0, spaceIndex)
                remaining = remaining.substring(spaceIndex + 1).trim()
            }
        }
        attributes[key] = value
    }
    return attributes
}


fun parseEpgXML(xmlString: String): List<EpgChannel> {
    val epgMap = mutableMapOf<String, EpgChannel>()
    val parser: XmlPullParser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    parser.setInput(StringReader(xmlString))
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
                    Log.d("epg", "${channel.id}, ${channel.title}")
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
    return epgMap.values.toList()
}
