package me.lsong.mytv.providers

import android.util.Log
import android.util.Xml
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.lsong.mytv.epg.EpgChannel
import me.lsong.mytv.epg.EpgList
import me.lsong.mytv.epg.EpgProgramme
import me.lsong.mytv.utils.Constants
import me.lsong.mytv.utils.Settings
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.GZIPInputStream


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

// Interface definition
interface TVProvider {
    suspend fun load()
    fun groups(): TVGroupList
    fun channels(groupTitle: String): TVChannelList
}

class MyTvProviderManager : TVProvider {
    private val providers: List<TVProvider> = listOf(
        IPTVProvider()
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


/**
 * 解析节目单xml
 */
private fun parseEpgXml(xmlString: String): EpgList {
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

private suspend fun request(url: String) = withContext(Dispatchers.IO) {
    Log.d("request", "request start: $url")
    val client = OkHttpClient()
    val request = Request.Builder().url(url).build()
    try {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("failed: ${response.code}")
            val contentType = response.header("content-type")
            if (contentType?.startsWith("text/") == true || url.endsWith(".m3u")) {
                response.body?.string() ?: throw Exception("Empty response body")
            } else {
                val gzData = response.body?.bytes() ?: throw Exception("Empty response body")
                BufferedReader(InputStreamReader(GZIPInputStream(ByteArrayInputStream(gzData)))).use { reader ->
                    reader.lineSequence().joinToString("\n")
                }
            }
        }
    } catch (ex: Exception) {
        val e = Exception("request failed $url", ex)
        Log.d("request", "${e.message}")
        throw  e;
    }
}

class IPTVProvider : TVProvider {
    private var groupList: TVGroupList = TVGroupList()
    private var epgList: EpgList = EpgList()

    override suspend fun load() {
        val (sources, epgUrls) = fetchIPTVSources()
        groupList = processSources(sources)
        epgList = fetchEPGData(epgUrls)
    }

    override fun groups(): TVGroupList = groupList

    override fun channels(groupTitle: String): TVChannelList =
        groupList.find { it.title == groupTitle }?.channels ?: TVChannelList()

    private suspend fun fetchIPTVSources(): Pair<List<TVSource>, List<String>> = coroutineScope {
        val iptvUrls = Settings.iptvSourceUrls.ifEmpty { listOf(Constants.IPTV_SOURCE_URL) }
        val deferredResults = iptvUrls.map { url ->
            async { fetchM3uData(url) }
        }
        val results = deferredResults.awaitAll()
        val allSources = results.flatMap { it.first }
        val allEpgUrls = results.flatMap { it.second }.distinct()

        Pair(allSources, if (allEpgUrls.isEmpty()) listOf(Constants.EPG_XML_URL) else allEpgUrls)
    }

    private suspend fun fetchM3uData(url: String): Pair<List<TVSource>, List<String>> {
        val m3u = retry { parseM3u(request(url)) }
        val sources = m3u.channels.map { it.toTVSource() }
        val epgUrls = m3u.headers["x-tvg-url"]?.split(",").orEmpty()
        return Pair(sources, epgUrls)
    }

    private suspend fun fetchEPGData(epgUrls: List<String>): EpgList = coroutineScope {
        val deferredEpgChannels = epgUrls.map { url ->
            async { retry { getEpgList(url) } }
        }
        val epgChannels = deferredEpgChannels.awaitAll().flatten()
        EpgList(epgChannels.distinctBy { it.id })
    }

    private suspend fun getEpgList(url: String): List<EpgChannel> = withContext(Dispatchers.Default) {
        try {
            parseEpgXml(request(url)).value
        } catch (ex: Exception) {
            Log.e("epg", "Failed to fetch EPG data", ex)
            emptyList()
        }
    }

    private fun processSources(sources: List<TVSource>): TVGroupList {
        val channels = sources.groupBy { it.name }
            .map { (name, sources) -> TVChannel(name, sources.first().title, sources) }

        return TVGroupList(
            channels.groupBy { it.groupTitle ?: "Others" }
                .map { (title, channels) -> TVGroup(title, TVChannelList(channels)) }
        )
    }

    private suspend fun request(url: String): String = withContext(Dispatchers.IO) {
        Log.d("request", "Request start: $url")
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("Request failed: ${response.code}")

                val contentType = response.header("content-type")
                val body = response.body ?: throw Exception("Empty response body")

                when {
                    contentType?.startsWith("text/") == true || url.endsWith(".m3u") -> body.string()
                    else -> decodeGzipContent(body.bytes())
                }
            }
        } catch (ex: Exception) {
            Log.d("request", "Request failed: $url", ex)
            throw Exception("Request failed: $url", ex)
        }
    }

    private fun decodeGzipContent(gzData: ByteArray): String {
        return BufferedReader(InputStreamReader(GZIPInputStream(ByteArrayInputStream(gzData)))).use { reader ->
            reader.lineSequence().joinToString("\n")
        }
    }

    private suspend fun <T> retry(attempts: Int = Constants.HTTP_RETRY_COUNT, block: suspend () -> T): T {
        repeat(attempts - 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                Log.w("retry", "Attempt ${attempt + 1} failed", e)
                delay(Constants.HTTP_RETRY_INTERVAL)
            }
        }
        return block() // Last attempt
    }
}

// Extension function to convert M3uChannel to TVSource
private fun M3uSource.toTVSource() = TVSource(
    tvgId = attributes["tvg-id"],
    tvgLogo = attributes["tvg-logo"],
    tvgName = attributes["tvg-name"],
    groupTitle = attributes["group-title"],
    title = title,
    url = url
)