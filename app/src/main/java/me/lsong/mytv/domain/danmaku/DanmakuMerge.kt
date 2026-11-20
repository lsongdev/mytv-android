package me.lsong.mytv.domain.danmaku

import me.lsong.media.danmaku.DanmakuComment
import java.text.Normalizer
import java.util.Locale

data class DanmakuSourceBatch(val providerId: String, val offsetMs: Long, val comments: List<DanmakuComment>)

/** Offsets enter the video timeline exactly once, before cross-provider deduplication. */
fun mergeDanmakuSources(sources: List<DanmakuSourceBatch>, positionMs: Long): List<DanmakuComment> {
    data class Entry(val provider: String, val comment: DanmakuComment)
    val entries = sources.flatMap { source -> source.comments.asSequence()
        .map { it.copy(timeMs = it.timeMs + source.offsetMs) }
        .filter { it.timeMs >= 0 && it.timeMs >= positionMs - 15_000 && it.timeMs <= positionMs + 600_000 }
        .sortedBy { it.timeMs }.take(12_000).map { Entry(source.providerId,it) }.toList()
    }.sortedBy { it.comment.timeMs }
    val recent = mutableMapOf<Pair<Int,String>,Pair<Long,String>>()
    return buildList {
        for ((provider,comment) in entries) {
            val text = Normalizer.normalize(comment.content,Normalizer.Form.NFKC).trim().lowercase(Locale.ROOT).replace(whitespace," ")
            if (text.isEmpty()) continue
            val key = comment.mode to text
            val previous = recent[key]
            if (previous != null && previous.second != provider && comment.timeMs - previous.first <= 1_000) continue
            recent[key] = comment.timeMs to provider
            add(comment)
            if (size == 24_000) break
        }
    }
}
private val whitespace = Regex("\\s+")
