package me.lsong.media.danmaku.internal

import org.json.JSONObject
import me.lsong.media.danmaku.DanmakuComment
import me.lsong.media.danmaku.DanmakuMedia
import me.lsong.media.danmaku.DanmakuEpisode

/**
 * 乐视视频弹幕源（原生 Kotlin）。search HTML(UTF-8) 内嵌 vidEpisode → getEpisodes 零网络；
 * comments danmu/list?vid&start&end(秒, 单次≤1000 需拆窗)。每段 SEG=300s。
 * 注：so.le.com 已迁 UTF-8，search 用普通 get（GBK 会乱码致标题对不上、永不命中）。
 */
internal class LetvDanmakuSource : DanmakuSource {
    override val providerName = "letv"
    private val net = DanmakuNet()

    private val searchH = mapOf("User-Agent" to "Mozilla/5.0")
    private val dmH = mapOf("User-Agent" to "Mozilla/5.0", "Referer" to "https://www.le.com/")

    private fun f(s: String, key: String): String? = Regex("$key:'([^']*)'").find(s)?.groupValues?.get(1)
    private fun leType(t: String?): String = when (t) {
        "tv" -> "tv"; "movie", "film" -> "movie"; "cartoon", "comic" -> "anime"
        "zongyi", "variety" -> "show"; else -> if (t.isNullOrEmpty()) "tv" else t
    }
    private fun leMode(p: String?): Int = when (p?.toIntOrNull()) { 1 -> 5; 3 -> 4; else -> 1 } // 2,4=滚动
    private fun leColor(c: String?): Long =
        (c?.ifEmpty { "FFFFFF" } ?: "FFFFFF").removePrefix("#").toLongOrNull(16) ?: 16777215L

    override fun search(keyword: String): List<DanmakuMedia> {
        val out = ArrayList<DanmakuMedia>()
        runCatching {
            val html = net.get("https://so.le.com/s?wd=" + DanmakuNet.enc(keyword), searchH)
            for (m in Regex("data-info=\"\\{([^\"]*?)\\}\"").findAll(html)) {
                val info = m.groupValues[1]
                val pid = f(info, "pid"); val type = f(info, "type"); val ve = f(info, "vidEpisode")
                if (pid.isNullOrEmpty() || ve.isNullOrEmpty()) continue
                val end = m.range.last + 1
                val after = html.substring(end, minOf(end + 500, html.length))
                val tm = Regex("title=\"([^\"]+)\"").find(after)?.groupValues?.get(1)
                val kw = f(info, "keyWord") ?: ""
                val year = Regex("((19|20)\\d{2})").find(kw)?.groupValues?.get(1)?.toIntOrNull()
                out.add(DanmakuMedia(provider = "letv", mediaId = "$pid|$ve", title = tm ?: kw, type = leType(type), year = year))
            }
        }
        return out
    }

    override fun getEpisodes(mediaId: String): List<DanmakuEpisode> {
        val ve = mediaId.substring(mediaId.indexOf("|") + 1)
        val out = ArrayList<DanmakuEpisode>()
        for (pair in ve.split(",")) {
            val p = pair.split("-")
            if (p.size < 2) continue
            val idx = p[0].toIntOrNull() ?: continue
            out.add(DanmakuEpisode("letv", p[1], "第${idx}集", idx))
        }
        out.sortBy { it.episodeIndex }
        return out
    }

    /** danmu/list 一窗；JSON 解析失败 → null（终止信号）；无 list → 空。 */
    private fun leWindow(vid: String, a: Int, b: Int): List<JSONObject>? {
        val raw = net.get("https://hd-my.le.com/danmu/list?vid=$vid&start=$a&end=$b", dmH)
        val j = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val list = j.optJSONObject("data")?.optJSONArray("list") ?: return emptyList()
        return (0 until list.length()).mapNotNull { list.optJSONObject(it) }
    }

    private fun leSplit(out: MutableList<DanmakuComment>, vid: String, a: Int, b: Int) {
        if (b - a <= 10) { (leWindow(vid, a, b) ?: emptyList()).forEach { out.add(leMap(it)) }; return }
        val mid = (a + b) / 2
        val left = leWindow(vid, a, mid) ?: emptyList()
        if (left.size >= 1000) leSplit(out, vid, a, mid) else left.forEach { out.add(leMap(it)) }
        val right = leWindow(vid, mid, b) ?: emptyList()
        if (right.size >= 1000) leSplit(out, vid, mid, b) else right.forEach { out.add(leMap(it)) }
    }

    private fun leMap(c: JSONObject): DanmakuComment = DanmakuComment(
        timeMs = Math.round((c.optString("start").toDoubleOrNull() ?: 0.0) * 1000),
        mode = leMode(c.optString("position")),
        color = leColor(c.optString("color")),
        content = c.optString("txt"),
    )

    override fun getComments(episodeId: String): List<DanmakuComment> = getComments(episodeId, 0, 0)

    // 每段 SEG=300s；单窗 ≥1000 条则二分细拉。窗参省略/endSec<=0 = 全集。
    override fun getComments(episodeId: String, startSec: Int, endSec: Int): List<DanmakuComment> {
        val vid = episodeId
        val out = ArrayList<DanmakuComment>()
        val seg = 300
        var empty = 0
        val sStart = if (startSec > 0) startSec / seg * seg else 0
        val sStop = if (endSec > 0) endSec else seg * 60
        var s = sStart
        while (s < sStop) {
            val raw = leWindow(vid, s, s + seg) ?: break
            if (raw.isEmpty()) { empty++; if (empty >= 2) break; s += seg; continue }
            empty = 0
            if (raw.size >= 1000) leSplit(out, vid, s, s + seg) else raw.forEach { out.add(leMap(it)) }
            s += seg
        }
        return out
    }
}
