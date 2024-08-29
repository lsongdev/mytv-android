package me.lsong.mytv.providers

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

class M3uParser {
    fun parse(data: String): M3uData {
        var xTvgUrl: String? = null
        val channels = mutableListOf<TVSource>()
        data
            .trim()
            .split("\r\n", "\n")
            .filter { it.isNotBlank() }
            .windowed(2) { (line1, line2) ->
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
            allSources.addAll(m3u.sources)
            m3u.epgUrl?.let { epgUrls.add(it) }
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
        val parser = M3uParser()
        val content = request(sourceUrl)
        return parser.parse(content).also {
            Log.i("getM3uChannels", "解析直播源完成：${it.sources.size}个资源, $sourceUrl")
        }
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