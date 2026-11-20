package me.lsong.mytv.domain.danmaku

import me.lsong.media.danmaku.*
import me.lsong.mytv.domain.video.*

fun danmakuMatchScore(work: VideoWork, media: DanmakuMedia): Int {
    val year = Regex("(?:19|20)[0-9]{2}").find(work.item.year)?.value?.toIntOrNull()
    if (year != null && media.year != null && year != media.year) return 0
    val a = normalizeVideoText(work.title)
    val b = normalizeVideoText(media.title)
    if (a.isBlank() || b.isBlank()) return 0
    return when { a == b -> 100; a.length >= 3 && (a.contains(b) || b.contains(a)) -> 70; else -> 0 }
}

fun matchDanmakuEpisode(work: VideoWork, part: VideoPart, episodes: List<DanmakuEpisode>): DanmakuEpisode? {
    val usable = episodes.filterNot { extras.containsMatchIn(it.title) }
    val exact = usable.filter { normalizeVideoText(it.title) == normalizeVideoText(part.title) }
    if (exact.isNotEmpty()) return exact.singleOrNull()
    val number = canonicalEpisode.matchEntire(part.id)?.groupValues?.get(1)?.toIntOrNull()
    if (number != null) {
        fun explicit(title: String): Int? {
            val normalized = java.text.Normalizer.normalize(title,java.text.Normalizer.Form.NFKC).trim()
            return chineseEpisode.find(normalized)?.groupValues?.get(1)?.let(::parseVideoNumber)
                ?: numberedEpisode.find(normalized)?.groupValues?.get(1)?.toIntOrNull()
        }
        val numbered = usable.filter { explicit(it.title) == number }
        if (numbered.isNotEmpty()) return numbered.singleOrNull()
        return usable.filter { explicit(it.title) == null && it.episodeIndex == number }.singleOrNull()
    }
    if (part.id.startsWith("date:")) {
        val date = part.id.removePrefix("date:")
        return usable.filter { it.title.filter(Char::isDigit).contains(date) }.singleOrNull()
    }
    if (work.item.kind == VideoKind.MOVIE || part.id in setOf("feature", "complete")) {
        return usable.filter { it.title.contains("正片") || it.title.contains("完整版") || it.title.contains("全集") }.singleOrNull()
            ?: usable.singleOrNull()
    }
    return null
}
private val extras = Regex("预告|花絮|片花|幕后|特辑|彩蛋")
private val canonicalEpisode = Regex("^s[0-9]+e([0-9]+)$")
private val chineseEpisode = Regex("""^第\s*([0-9零一二两三四五六七八九十]+)\s*[集话期]""")
private val numberedEpisode = Regex("""^(?:ep?\s*)?([0-9]+)\s*(?:集|话|期|$)""",RegexOption.IGNORE_CASE)
