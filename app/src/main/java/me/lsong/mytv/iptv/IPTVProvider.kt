package me.lsong.mytv.iptv

import android.util.Log
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.lsong.mytv.epg.EpgChannel
import me.lsong.mytv.epg.EpgList
import me.lsong.mytv.epg.EpgRepository
import me.lsong.mytv.utils.Constants
import me.lsong.mytv.utils.Settings
import okhttp3.OkHttpClient
import okhttp3.Request

// 接口定义
interface TVProvider {
    suspend fun load()
    fun groups(): TVGroupList
    suspend fun epg(): EpgList
}

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

data class M3uData(
    var epgUrl: String?,
    val sources: List<TVSource>
)

// IPTV解析器
interface IptvParser {
    fun isSupport(url: String, data: String): Boolean
    suspend fun parse(data: String): M3uData

    companion object {
        val instances = listOf(M3uIptvParser())
    }
}

class M3uIptvParser : IptvParser {
    override fun isSupport(url: String, data: String) = data.startsWith("#EXTM3U")

    override suspend fun parse(data: String): M3uData {
        val lines = data.split("\r\n", "\n").filter { it.isNotBlank() }
        val channels = mutableListOf<TVSource>()
        var xTvgUrl: String? = null

        lines.windowed(2) { (line1, line2) ->
            when {
                line1.startsWith("#EXTM3U") -> {
                    xTvgUrl = Regex("x-tvg-url=\"(.+?)\"").find(line1)?.groupValues?.get(1)?.trim()
                }
                line1.startsWith("#EXTINF") && !line2.startsWith("#") -> {
                    val title = line1.split(",").lastOrNull()?.trim() ?: return@windowed
                    val attributes = parseTvgAttributes(line1)
                    channels.add(
                        TVSource(
                            tvgId = attributes["tvg-id"],
                            tvgName = attributes["tvg-name"],
                            tvgLogo = attributes["tvg-logo"],
                            groupTitle = attributes["group-title"],
                            title = title,
                            url = line2.trim()
                        )
                    )
                }
            }
        }

        return M3uData(epgUrl = xTvgUrl, channels)
    }

    private fun parseTvgAttributes(line: String): Map<String, String> =
        Regex("""(\S+?)="(.+?)"""").findAll(line)
            .associate { it.groupValues[1] to it.groupValues[2].trim() }
}

// 合并后的 IPTV 提供者和仓库
class IPTVProvider(private val epgRepository: EpgRepository) : TVProvider {
    private var groupList: TVGroupList = TVGroupList()
    private var epgList: EpgList = EpgList()

    override suspend fun load() {
        val (sources, epgUrls) = fetchIPTVSources()
        groupList = processChannelSources(sources)
        epgList = fetchEPGData(epgUrls)
    }

    override fun groups(): TVGroupList = groupList

    override suspend fun epg(): EpgList = epgList

    private suspend fun fetchIPTVSources(): Pair<List<TVSource>, List<String>> {
        val allSources = mutableListOf<TVSource>()
        val epgUrls = mutableListOf<String>()

        val iptvUrls = Settings.iptvSourceUrls.ifEmpty { listOf(Constants.IPTV_SOURCE_URL) }

        iptvUrls.forEach { url ->
            val m3u = fetchDataWithRetry { getChannelSourceList(sourceUrl = url) }
            allSources.addAll(m3u.sources)
            m3u.epgUrl?.let { epgUrls.add(it) }
        }

        if (epgUrls.isEmpty()) epgUrls.add(Constants.EPG_XML_URL)

        return Pair(allSources, epgUrls.distinct())
    }

    private suspend fun fetchEPGData(epgUrls: List<String>): EpgList {
        val epgChannels = mutableListOf<EpgChannel>()
        epgUrls.forEach { url ->
            val epg = fetchDataWithRetry { epgRepository.getEpgList(url) }
            epgChannels.addAll(epg.value)
        }
        return EpgList(epgChannels.distinctBy { it.id })
    }

    private fun processChannelSources(sources: List<TVSource>): TVGroupList {
        val channelList = sources.groupBy { it.name }
            .map { (name, channelSources) ->
                TVChannel(
                    name = name,
                    title = channelSources.first().title,
                    sources = channelSources
                )
            }

        return TVGroupList(
            channelList.groupBy { it.groupTitle ?: "其他" }
                .map { (title, channels) -> TVGroup(title = title, channels = TVChannelList(channels)) }
        )
    }

    private suspend fun <T> fetchDataWithRetry(fetch: suspend () -> T): T {
        repeat(Constants.HTTP_RETRY_COUNT) {
            try {
                return fetch()
            } catch (e: Exception) {
                if (it == Constants.HTTP_RETRY_COUNT - 1) throw e
                delay(Constants.HTTP_RETRY_INTERVAL)
            }
        }
        throw IllegalStateException("Failed to fetch data after ${Constants.HTTP_RETRY_COUNT} attempts")
    }

    private suspend fun fetchSource(sourceUrl: String) = withContext(Dispatchers.IO) {
        Log.d("iptv", sourceUrl)
        val client = OkHttpClient()
        val request = Request.Builder().url(sourceUrl).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("fetchSource failed: ${response.code}")
                response.body?.string()?.trim() ?: throw Exception("Empty response body")
            }
        } catch (ex: Exception) {
            Log.d("iptv", "获取远程直播源失败: $sourceUrl")
            throw Exception("获取远程直播源失败，请检查网络连接", ex)
        }
    }

    private suspend fun getChannelSourceList(sourceUrl: String): M3uData {
        val sourceData = fetchSource(sourceUrl)
        val parser = IptvParser.instances.first { it.isSupport(sourceUrl, sourceData) }
        return parser.parse(sourceData).also {
            Log.i("iptv", "解析直播源完成：${it.sources.size}个资源, $sourceUrl")
        }
    }
}