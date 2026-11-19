package me.lsong.media.epg

import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

object XmltvParser {
    fun parse(text: String): EpgDocument {
        require(!Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(text)) { "XMLTV declarations with entities are not supported" }
        val titles = linkedMapOf<String,String>()
        val names = linkedMapOf<String,MutableList<String>>()
        val icons = linkedMapOf<String,String>()
        val programmes = linkedMapOf<String,MutableList<EpgProgramme>>()
        var channel = ""; var programmeChannel = ""; var starts = 0L; var ends = 0L
        var programmeTitle = ""; var collecting = ""; val buffer = StringBuilder()
        val handler = object : DefaultHandler() {
            override fun startElement(uri: String?, local: String?, name: String, attrs: Attributes) {
                when (name) {
                    "channel" -> { channel = attrs.getValue("id").orEmpty(); titles.putIfAbsent(channel,channel) }
                    "icon" -> if (channel.isNotBlank()) attrs.getValue("src")?.let { icons[channel] = it }
                    "programme" -> { programmeChannel = attrs.getValue("channel").orEmpty(); starts = timestamp(attrs.getValue("start")); ends = timestamp(attrs.getValue("stop")); programmeTitle = "" }
                    "display-name", "title" -> { collecting = name; buffer.clear() }
                }
            }
            override fun characters(chars: CharArray, start: Int, length: Int) { if (collecting.isNotEmpty()) buffer.append(chars,start,length) }
            override fun endElement(uri: String?, local: String?, name: String) {
                when (name) {
                    "display-name" -> { if (channel.isNotBlank()) { val title = buffer.toString().trim(); if (titles[channel] == channel) titles[channel] = title; if (title.isNotBlank()) names.getOrPut(channel) { mutableListOf() }.add(title) } }
                    "title" -> if (programmeTitle.isBlank()) programmeTitle = buffer.toString().trim()
                    "channel" -> channel = ""
                    "programme" -> if (programmeChannel.isNotBlank() && starts > 0 && ends > starts) programmes.getOrPut(programmeChannel) { mutableListOf() }
                        .add(EpgProgramme(programmeChannel,programmeTitle,starts,ends))
                }
                if (name == collecting) { collecting = ""; buffer.clear() }
            }
        }
        val factory = SAXParserFactory.newInstance()
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        factory.newSAXParser().parse(InputSource(StringReader(text)),handler)
        return EpgDocument((titles.keys + programmes.keys).map { id -> EpgChannel(id,titles[id] ?: id,programmes[id].orEmpty().sortedBy { it.startsAt },names[id].orEmpty().distinct(),icons[id]) })
    }

    private fun timestamp(value: String?): Long = runCatching {
        val raw = value?.trim().orEmpty()
        val pattern = if (raw.length > 14) "yyyyMMddHHmmss Z" else "yyyyMMddHHmmss"
        SimpleDateFormat(pattern, Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = false }.parse(raw)?.time ?: 0
    }.getOrDefault(0)
}
