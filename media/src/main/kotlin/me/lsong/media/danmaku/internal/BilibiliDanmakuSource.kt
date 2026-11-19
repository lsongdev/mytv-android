package me.lsong.media.danmaku.internal

import org.json.JSONObject
import me.lsong.media.danmaku.DanmakuComment
import me.lsong.media.danmaku.DanmakuMedia
import me.lsong.media.danmaku.DanmakuEpisode

/**
 * B站弹幕源（原生 Kotlin）。search /x/web-interface/search/all/v2（先建 buvid3 会话）；
 * episodes PGC /pgc/view/web/ep/list 或 UGC /x/player/pagelist；comments /x/v2/dm/web/seg.so（protobuf，逐段累加）。
 */
internal class BilibiliDanmakuSource : DanmakuSource {
    override val providerName = "bilibili"
    private val net = DanmakuNet()

    @Volatile private var buvid: String? = null

    private fun ensureBuvid(): String {
        buvid?.let { return it }
        val c = runCatching {
            net.get("https://www.bilibili.com")
            val spi = JSONObject(net.get("https://api.bilibili.com/x/frontend/finger/spi"))
            val d = spi.optJSONObject("data") ?: JSONObject()
            "buvid3=" + d.optString("b_3") + "; buvid4=" + d.optString("b_4")
        }.getOrDefault("")
        buvid = c
        return c
    }

    private fun headers(): Map<String, String> {
        val c = ensureBuvid()
        return if (c.isNotBlank()) mapOf("Cookie" to c) else emptyMap()
    }

    private fun clean(t: String?): String =
        (t ?: "").replace(Regex("<[^>]+>"), "").replace("&amp;", "&")

    override fun search(keyword: String): List<DanmakuMedia> {
        val pgc = ArrayList<DanmakuMedia>()
        val ugc = ArrayList<DanmakuMedia>()
        runCatching {
            val url = "https://api.bilibili.com/x/web-interface/search/all/v2?keyword=" +
                DanmakuNet.enc(keyword) + "&platform=pc&duration=0&order=totalrank"
            val root = JSONObject(net.get(url, headers()))
            val groups = root.optJSONObject("data")?.optJSONArray("result") ?: return@runCatching
            for (i in 0 until groups.length()) {
                val g = groups.optJSONObject(i) ?: continue
                val type = g.optString("result_type")
                val items = g.optJSONArray("data") ?: continue
                when (type) {
                    "media_bangumi", "media_ft" -> for (j in 0 until items.length()) {
                        val o = items.optJSONObject(j) ?: continue
                        val seasonId = o.optLong("season_id")
                        if (seasonId != 0L) {
                            pgc.add(
                                DanmakuMedia(
                                    provider = "bilibili", mediaId = "ss$seasonId", title = clean(o.optString("title")),
                                    type = if (type == "media_ft") "movie" else "tvseries",
                                    episodeCount = o.optInt("ep_size").takeIf { it > 0 },
                                ),
                            )
                        }
                    }
                    "video" -> for (j in 0 until items.length()) {
                        val o = items.optJSONObject(j) ?: continue
                        val bvid = o.optString("bvid")
                        if (bvid.isNotEmpty()) {
                            ugc.add(DanmakuMedia(provider = "bilibili", mediaId = "bv:$bvid", title = clean(o.optString("title")), type = "video"))
                        }
                    }
                }
            }
        }
        return pgc + ugc   // PGC 优先，UGC 兜底
    }

    override fun getEpisodes(mediaId: String): List<DanmakuEpisode> {
        val out = ArrayList<DanmakuEpisode>()
        runCatching {
            if (mediaId.startsWith("ss")) {
                val seasonId = mediaId.substring(2)
                val root = JSONObject(net.get("https://api.bilibili.com/pgc/view/web/ep/list?season_id=$seasonId", headers()))
                val eps = root.optJSONObject("result")?.optJSONArray("episodes") ?: return@runCatching
                for (i in 0 until eps.length()) {
                    val o = eps.optJSONObject(i) ?: continue
                    val cid = o.optLong("cid")
                    if (cid != 0L) {
                        val title = o.optString("long_title").ifEmpty { o.optString("title").ifEmpty { "第${i + 1}集" } }
                        out.add(DanmakuEpisode("bilibili", cid.toString(), title, i + 1))
                    }
                }
            } else if (mediaId.startsWith("bv:")) {
                val bvid = mediaId.substring(3)
                val root = JSONObject(net.get("https://api.bilibili.com/x/player/pagelist?bvid=$bvid", headers()))
                val pages = root.optJSONArray("data") ?: return@runCatching
                for (i in 0 until pages.length()) {
                    val o = pages.optJSONObject(i) ?: continue
                    val cid = o.optLong("cid")
                    if (cid != 0L) {
                        out.add(DanmakuEpisode("bilibili", cid.toString(), o.optString("part").ifEmpty { "P${i + 1}" }, i + 1))
                    }
                }
            }
        }
        return out
    }

    override fun getComments(episodeId: String): List<DanmakuComment> = getComments(episodeId, 0, 0)

    // 每段 360s（segment_index 1-based）。窗参省略/endSec<=0 = 全集（seg.so 空时退回旧版 XML，仅全集模式）。
    override fun getComments(episodeId: String, startSec: Int, endSec: Int): List<DanmakuComment> {
        val all = ArrayList<DanmakuComment>()
        val segFrom = if (startSec > 0) startSec / 360 + 1 else 1
        val segTo = if (endSec > 0) Math.ceil(endSec / 360.0).toInt() else 100
        val windowed = startSec > 0 || endSec > 0
        var seg = segFrom
        while (seg <= segTo) {
            val part = net.biliSegment("https://api.bilibili.com/x/v2/dm/web/seg.so?type=1&oid=$episodeId&segment_index=$seg", headers())
            if (part.isEmpty()) break
            all.addAll(part); seg++
        }
        if (all.isEmpty() && !windowed) {
            all.addAll(net.biliXml("https://api.bilibili.com/x/v1/dm/list.so?oid=$episodeId", headers()))
        }
        return all
    }
}
