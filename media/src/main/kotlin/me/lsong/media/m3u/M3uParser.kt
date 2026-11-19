package me.lsong.media.m3u

object M3uParser {
    fun parse(data: String): M3uDocument {
        val lines = data.removePrefix("\uFEFF").lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
        val headers = mutableMapOf<String, String>()
        val entries = mutableListOf<M3uEntry>()
        var attributes = emptyMap<String, String>()
        var title = ""
        for (line in lines) {
            when {
                line.startsWith("#EXTM3U") -> headers.putAll(attributes(line.substring(7)))
                line.startsWith("#EXTINF:") -> {
                    val info = line.substring(8)
                    var quoted = false
                    val comma = info.indexOfFirst { char ->
                        if (char == '"') quoted = !quoted
                        char == ',' && !quoted
                    }
                    attributes = attributes(if (comma < 0) info else info.substring(0, comma))
                    title = if (comma < 0) "" else info.substring(comma + 1).trim()
                }
                !line.startsWith("#") -> {
                    entries += M3uEntry(attributes, title, line)
                    attributes = emptyMap()
                    title = ""
                }
            }
        }
        return M3uDocument(headers, entries)
    }

    private fun attributes(input: String): Map<String, String> =
        Regex("""([\w-]+)\s*=\s*(?:"([^"]*)"|([^\s]+))""").findAll(input)
            .associate { it.groupValues[1] to (it.groups[2]?.value ?: it.groupValues[3]) }
}
