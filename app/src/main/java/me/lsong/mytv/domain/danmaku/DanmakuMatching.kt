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
    val usable = episodes.filterNot { Regex("预告|花絮|片花|幕后|特辑|彩蛋").containsMatchIn(it.title) }
    usable.firstOrNull { normalizeVideoText(it.title) == normalizeVideoText(part.title) }?.let { return it }
    val number = Regex("^s[0-9]+e([0-9]+)$").matchEntire(part.id)?.groupValues?.get(1)?.toIntOrNull()
    if (number != null) {
        fun explicit(title: String): Int? = Regex("^(?:第\\s*)?([0-9]+)\\s*(?:集|话|期|$)").find(title)?.groupValues?.get(1)?.toIntOrNull()
        usable.firstOrNull { explicit(it.title) == number }?.let { return it }
        return usable.firstOrNull { explicit(it.title) == null && it.episodeIndex == number }
    }
    if (part.id.startsWith("date:")) {
        val date = part.id.removePrefix("date:")
        return usable.firstOrNull { it.title.filter(Char::isDigit).contains(date) }
    }
    if (work.item.kind == VideoKind.MOVIE || part.id in setOf("feature", "complete")) {
        return usable.firstOrNull { it.title.contains("正片") || it.title.contains("完整版") || it.title.contains("全集") }
            ?: usable.singleOrNull()
    }
    return null
}
