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
        // SAX callbacks are confined to this parse. Reuse formatters and common
        // timestamps instead of constructing two calendars for every programme.
        val utcFormat = SimpleDateFormat("yyyyMMddHHmmss",Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = false }
        val zonedFormat = SimpleDateFormat("yyyyMMddHHmmss Z",Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = false }
        val times = HashMap<String,Long>()
        val days = HashMap<String,Long>()
        val calendar = java.util.GregorianCalendar(TimeZone.getTimeZone("UTC"),Locale.ROOT).apply { isLenient = false }
        fun digits(raw: String, start: Int, count: Int): Int {
            var value = 0
            for (i in start until start+count) {
                val digit = raw[i] - '0'
                require(digit in 0..9)
                value = value*10+digit
            }
            return value
        }
        fun numericTime(raw: String): Long {
            val hour = digits(raw,8,2); val minute = digits(raw,10,2); val second = digits(raw,12,2)
            require(hour < 24 && minute < 60 && second < 60)
            val key = raw.substring(0,8)
            val midnight = days.getOrPut(key) {
                calendar.clear()
                calendar.set(digits(raw,0,4),digits(raw,4,2)-1,digits(raw,6,2),0,0,0)
                calendar.timeInMillis
            }
            val offset = if (raw.length == 14) 0 else {
                val zoneHour = digits(raw,16,2); val zoneMinute = digits(raw,18,2)
                require(zoneHour < 24 && zoneMinute < 60)
                (zoneHour*60+zoneMinute)*60_000L * (if (raw[15] == '+') 1 else -1)
            }
            if (days.size > 128) days.clear()
            return midnight+(hour*3600+minute*60+second)*1000L-offset
        }
        fun readTime(value: String?): Long {
            val raw = value?.trim().orEmpty()
            times[raw]?.let { return it }
            val numeric = raw.length == 14 || raw.length == 20 && raw[14] == ' ' && raw[15] in "+-"
            val result = runCatching {
                if (numeric) numericTime(raw) else (if (raw.length > 14) zonedFormat else utcFormat).parse(raw)?.time ?: 0
            }.getOrDefault(0)
            if (times.size >= 8192) times.clear()
            times[raw] = result
            return result
        }
        var channel = ""; var programmeChannel = ""; var starts = 0L; var ends = 0L
        var programmeTitle = ""; var collecting = ""; val buffer = StringBuilder()
        val handler = object : DefaultHandler() {
            override fun startElement(uri: String?, local: String?, name: String, attrs: Attributes) {
                when (name) {
                    "channel" -> { channel = attrs.getValue("id").orEmpty(); titles.putIfAbsent(channel,channel) }
                    "icon" -> if (channel.isNotBlank()) attrs.getValue("src")?.let { icons[channel] = it }
                    "programme" -> { programmeChannel = attrs.getValue("channel").orEmpty(); starts = readTime(attrs.getValue("start")); ends = readTime(attrs.getValue("stop")); programmeTitle = "" }
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
        // Harden the parser as well as rejecting declarations in the input.
        runCatching { factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        runCatching { factory.setXIncludeAware(false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        factory.newSAXParser().parse(InputSource(StringReader(text)),handler)
        return EpgDocument((titles.keys + programmes.keys).map { id -> EpgChannel(id,titles[id] ?: id,programmes[id].orEmpty().sortedBy { it.startsAt },names[id].orEmpty().distinct(),icons[id]) })
    }

}
