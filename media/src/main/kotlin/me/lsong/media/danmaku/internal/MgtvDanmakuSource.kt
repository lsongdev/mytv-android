package me.lsong.media.danmaku.internal

import org.json.JSONArray
import org.json.JSONObject
import me.lsong.media.danmaku.DanmakuComment
import me.lsong.media.danmaku.DanmakuMedia
import me.lsong.media.danmaku.DanmakuEpisode

/**
 * 芒果TV弹幕源（原生 Kotlin）。search msite/search/v2；episodes variety/showlist(综艺) 或 episode/list(剧集)；
 * comments getctlbarrage→CDN {n}.json(分钟序号,404停) 兜底 rdbarrage(time 步进)。mode 统一 1，color 统一白。
 * 注：mobileso 校验 UA，须用完整 Chrome UA（短 "Mozilla/5.0" 会 401）。
 */
internal class MgtvDanmakuSource : DanmakuSource {
    override val providerName = "mgtv"
    private val net = DanmakuNet()

    private val h = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36",
        "Referer" to "https://www.mgtv.com/",
    )

    private fun stripB(s: String?): String = (s ?: "").replace(Regex("</?B>"), "")
    private fun durSec(t: String?): Int {
        if (t.isNullOrEmpty()) return 0
        val p = t.split(":").map { it.toIntOrNull() ?: 0 }
        return if (p.size == 3) p[0] * 3600 + p[1] * 60 + p[2] else if (p.size == 2) p[0] * 60 + p[1] else 0
    }

    override fun search(keyword: String): List<DanmakuMedia> {
        val out = ArrayList<DanmakuMedia>()
        val seen = HashSet<String>()
        runCatching {
            val resp = JSONObject(net.get("https://mobileso.bz.mgtv.com/msite/search/v2?q=" + DanmakuNet.enc(keyword), h))
            val contents = resp.optJSONObject("data")?.optJSONArray("contents") ?: return@runCatching
            for (b in 0 until contents.length()) {
                val c = contents.optJSONObject(b) ?: continue
                if (c.optString("type") != "media") continue
                val arr = c.optJSONArray("data") ?: continue
                for (i in 0 until arr.length()) {
                    val it = arr.optJSONObject(i) ?: continue
                    val m = Regex("^/b/(\\d+)/(\\d+)\\.html$").find(it.optString("url")) ?: continue
                    val cid = m.groupValues[1]
                    if (!seen.add(cid)) continue
                    var type = ""; var year: Int? = null
                    val d0 = it.optJSONArray("desc")?.optString(0) ?: ""
                    val dm = d0.replace(Regex("^类型:\\s*"), "").split("/")
                    if (dm.isNotEmpty()) {
                        type = dm[0].replace(Regex("\\s"), "")
                        year = Regex("(\\d{4})").find(dm.last())?.groupValues?.get(1)?.toIntOrNull()
                    }
                    out.add(DanmakuMedia(provider = "mgtv", mediaId = cid, title = stripB(it.optString("title")), type = type, year = year))
                }
            }
        }
        return out
    }

    override fun getEpisodes(mediaId: String): List<DanmakuEpisode> {
        val cid = mediaId
        val out = ArrayList<DanmakuEpisode>()
        var idx = 1
        // 综艺：variety/showlist 按月聚合
        runCatching {
            val probe = JSONObject(net.get("https://pcweb.api.mgtv.com/variety/showlist?collection_id=$cid&page=1&_support=10000000", h))
            val months = probe.optJSONObject("data")?.optJSONArray("tab_m")
            if (months != null && months.length() > 0) {
                for (mi in 0 until months.length()) {
                    val mm = months.optJSONObject(mi)?.optString("m") ?: continue
                    val r = JSONObject(net.get("https://pcweb.api.mgtv.com/variety/showlist?collection_id=$cid&month=$mm&page=1&_support=10000000", h))
                    val lst = r.optJSONObject("data")?.optJSONArray("list") ?: JSONArray()
                    for (j in 0 until lst.length()) {
                        val e = lst.optJSONObject(j) ?: continue
                        val title = e.optString("t3").ifEmpty { e.optString("t1").ifEmpty { "第${idx}期" } }
                        out.add(DanmakuEpisode("mgtv", e.optString("video_id") + "|" + cid, title, idx++))
                    }
                }
                return out
            }
        }
        // 剧集/动漫：episode/list（page 从 0）
        runCatching {
            var page = 0; var totalPage = 1
            do {
                val rr = JSONObject(net.get("https://pcweb.api.mgtv.com/episode/list?video_id=$cid&cid=$cid&page=$page&size=30&allowedRC=1", h))
                val data = rr.optJSONObject("data")
                val list = data?.optJSONArray("list") ?: JSONArray()
                totalPage = data?.optInt("total_page", 1) ?: 1
                for (k in 0 until list.length()) {
                    val ee = list.optJSONObject(k) ?: continue
                    val n = ee.optString("t1").toIntOrNull()
                    val epIdx = n ?: idx
                    out.add(DanmakuEpisode("mgtv", ee.optString("video_id") + "|" + cid, ee.optString("t4").ifEmpty { "第${epIdx}集" }, epIdx))
                    idx++
                }
                page++
            } while (page < totalPage)
        }
        return out
    }

    private fun push(items: JSONArray?, out: MutableList<DanmakuComment>) {
        if (items == null) return
        for (i in 0 until items.length()) {
            val it = items.optJSONObject(i) ?: continue
            if (it.optInt("type", -1) != 0) continue
            val c = it.optString("content").trim()
            if (c.isEmpty()) continue
            out.add(DanmakuComment(timeMs = it.optLong("time"), mode = 1, color = 16777215L, content = c))
        }
    }

    override fun getComments(episodeId: String): List<DanmakuComment> = getComments(episodeId, 0, 0)

    // 每段 60s（n.json / rdbarrage time）。窗参省略/endSec<=0 = 全集。
    override fun getComments(episodeId: String, startSec: Int, endSec: Int): List<DanmakuComment> {
        val parts = episodeId.split("|")
        val vid = parts[0]; val cid = parts.getOrNull(1) ?: ""
        val out = ArrayList<DanmakuComment>()
        val nFrom = if (startSec > 0) startSec / 60 else 0
        var cdnVer = ""; var cdnHost = ""
        runCatching {
            val ctl = JSONObject(net.get("https://galaxy.bz.mgtv.com/getctlbarrage?version=8.1.39&abroad=0&platform=0&vid=$vid&cid=$cid", h))
            ctl.optJSONObject("data")?.let { d ->
                cdnVer = d.optString("cdn_version")
                cdnHost = d.optString("cdn_list").split(",").firstOrNull()?.ifEmpty { "bullet-ali.hitv.com" } ?: "bullet-ali.hitv.com"
            }
        }
        if (cdnVer.isNotEmpty() && cdnHost.isNotEmpty()) {
            val nTo = if (endSec > 0) Math.ceil(endSec / 60.0).toInt() else 599
            for (n in nFrom..nTo) {
                val raw = net.get("https://$cdnHost/$cdnVer/$n.json", h)
                if (raw.isEmpty() || raw[0] == '<' || raw.contains("NoSuchKey")) break
                val seg = runCatching { JSONObject(raw) }.getOrNull() ?: break
                push(seg.optJSONObject("data")?.optJSONArray("items"), out)
            }
            if (out.isNotEmpty()) return out
        }
        // 兜底 rdbarrage
        var dur = 0
        runCatching {
            val info = JSONObject(net.get("https://pcweb.api.mgtv.com/video/info?cid=$cid&vid=$vid&allowedRC=1", h))
            dur = durSec(info.optJSONObject("data")?.optJSONObject("info")?.optString("time"))
        }
        val segCount = if (dur > 0) Math.ceil(dur / 60.0).toInt() else 200
        val sTo = if (endSec > 0) minOf(segCount, Math.ceil(endSec / 60.0).toInt()) else segCount
        for (s in nFrom..sTo) {
            val r = runCatching { JSONObject(net.get("https://galaxy.bz.mgtv.com/rdbarrage?vid=$vid&cid=$cid&time=${s * 60000}", h)) }.getOrNull() ?: continue
            if (r.optInt("status") == 202) break
            push(r.optJSONObject("data")?.optJSONArray("items"), out)
        }
        return out
    }
}
