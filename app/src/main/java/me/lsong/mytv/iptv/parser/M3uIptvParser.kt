package me.lsong.mytv.iptv.parser

import me.lsong.mytv.iptv.TVSource



class M3uIptvParser : IptvParser {
    override fun isSupport(url: String, data: String): Boolean {
        return data.startsWith("#EXTM3U")
    }
    override suspend fun parse(data: String): M3uData {
        val lines = data.split("\r\n", "\n").filter { it.isNotBlank() }
        val channels = mutableListOf<TVSource>()
        var xTvgUrl: String? = null
        for (i in lines.indices) {
            val line = lines[i]
            when {
                line.startsWith("#EXTM3U") -> {
                    xTvgUrl = Regex("x-tvg-url=\"(.+?)\"").find(line)?.groupValues?.get(1)?.trim()
                }
                line.startsWith("#EXTINF") -> {
                    if (i + 1 >= lines.size) break // Ensure there's a next line for the URL

                    val title = line.split(",").lastOrNull()?.trim() ?: continue
                    val attributes = parseTvgAttributes(line)
                    val url = lines[i + 1].trim()

                    if (url.isEmpty() || url.startsWith("#")) continue // Skip if URL is empty or another #EXTINF line

                    channels.add(
                        TVSource(
                            tvgId = attributes["tvg-id"],
                            tvgName = attributes["tvg-name"],
                            tvgLogo = attributes["tvg-logo"],
                            groupTitle = attributes["group-title"],
                            title = title,
                            url = url,
                        )
                    )
                }
            }
        }
        return M3uData(epgUrl = xTvgUrl, channels.toList())
    }

    private fun parseTvgAttributes(line: String): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        val regex = Regex("""(\S+?)="(.+?)"""")
        regex.findAll(line).forEach { matchResult ->
            val (key, value) = matchResult.destructured
            attributes[key] = value.trim()
        }
        return attributes
    }
}