package me.lsong.media.danmaku.internal

import org.json.JSONArray
import org.json.JSONObject
import me.lsong.media.danmaku.DanmakuComment
import me.lsong.media.danmaku.DanmakuMedia
import me.lsong.media.danmaku.DanmakuEpisode

/**
 * 腾讯视频弹幕源（原生 Kotlin）。search MbSearch；episodes GetPageData(分页 tabs)；comments barrage base→segment(JSON)。
 * search 只收腾讯自有站点(enName "qq")的正片——异网独播(如爱奇艺独播)在 GetPageData 取不到分集且零弹幕，会误占默认源。
 */
internal class TencentDanmakuSource : DanmakuSource {
    override val providerName = "tencent"
    private val net = DanmakuNet()

    private val qq = mapOf(
        "Content-Type" to "application/json",
        "Origin" to "https://v.qq.com",
        "Referer" to "https://v.qq.com/",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36",
    )
    private val dm = mapOf("Referer" to "https://v.qq.com/", "User-Agent" to "Mozilla/5.0")

    private fun stripEm(s: String?): String = (s ?: "").replace(Regex("</?em>", RegexOption.IGNORE_CASE), "")

    private fun hexToDec(hexIn: String?): Long {
        if (hexIn.isNullOrEmpty()) return 16777215L
        var hex = hexIn.removePrefix("#")
        if (hex.length == 3) hex = "${hex[0]}${hex[0]}${hex[1]}${hex[1]}${hex[2]}${hex[2]}"
        return hex.toLongOrNull(16) ?: 16777215L
    }

    override fun search(keyword: String): List<DanmakuMedia> {
        val out = ArrayList<DanmakuMedia>()
        runCatching {
            val body = JSONObject()
                .put("query", keyword).put("version", "").put("retry", 0).put("grp", 1)
                .put("preQid", "").put("pagenum", 0).put("pagesize", 30).put("queryFrom", 0)
                .put("isneedQc", true).put("adClientInfo", "")
                .put("extraInfo", JSONObject().put("isNewMarkLabel", "1").put("multi_terminal_pc", "1").put("themeType", "1"))
                .toString()
            val url = "https://pbaccess.video.qq.com/trpc.videosearch.mobile_search.MultiTerminalSearch/MbSearch?vplatform=2"
            val json = JSONObject(net.post(url, body, qq))
            if (json.optInt("ret", -1) != 0) return@runCatching
            val data = json.optJSONObject("data") ?: return@runCatching
            val items = ArrayList<JSONObject>()
            data.optJSONObject("normalList")?.optJSONArray("itemList")?.let { il -> for (n in 0 until il.length()) il.optJSONObject(n)?.let(items::add) }
            data.optJSONArray("areaBoxList")?.let { boxes ->
                for (b in 0 until boxes.length()) {
                    boxes.optJSONObject(b)?.optJSONArray("itemList")?.let { il -> for (bj in 0 until il.length()) il.optJSONObject(bj)?.let(items::add) }
                }
            }
            for (it in items) {
                val doc = it.optJSONObject("doc") ?: JSONObject()
                val vi = it.optJSONObject("videoInfo") ?: continue
                val docId = doc.optString("id"); if (docId.isEmpty()) continue
                if (doc.has("dataType") && doc.optInt("dataType") != 2) continue
                val sites = vi.optJSONArray("playSites") ?: vi.optJSONArray("episodeSites")
                if (sites == null || sites.length() == 0) continue
                var onQQ = false
                for (si in 0 until sites.length()) { if (sites.optJSONObject(si)?.optString("enName") == "qq") { onQQ = true; break } }
                if (!onQQ) continue
                out.add(
                    DanmakuMedia(
                        provider = "tencent", mediaId = docId, title = stripEm(vi.optString("title")),
                        type = vi.optString("typeName"), year = vi.optInt("year").takeIf { it > 0 } ?: 0,
                    ),
                )
            }
        }
        return out
    }

    private fun getPageData(cid: String, ctx: String): JSONObject? = runCatching {
        val body = JSONObject().put(
            "page_params",
            JSONObject().put("req_from", "web_vsite").put("page_id", "vsite_episode_list")
                .put("page_type", "detail_operation").put("id_type", "1").put("page_size", "")
                .put("cid", cid).put("vid", "").put("lid", "").put("page_num", "")
                .put("page_context", ctx).put("detail_page_type", "1"),
        ).put("has_cache", 1).toString()
        val url = "https://pbaccess.video.qq.com/trpc.universal_backend_service.page_server_rpc.PageServer/GetPageData?video_appid=3000010&vversion_name=8.2.96&vversion_platform=2"
        JSONObject(net.post(url, body, qq))
    }.getOrNull()

    private fun extractModule(json: JSONObject?): JSONObject? {
        if (json == null || json.optInt("ret", -1) != 0) return null
        val mld = json.optJSONObject("data")?.optJSONArray("module_list_datas") ?: return null
        val mds = mld.optJSONObject(0)?.optJSONArray("module_datas") ?: return null
        return mds.optJSONObject(0)
    }

    private fun collectEpisodes(mod: JSONObject, acc: MutableList<DanmakuEpisode>, startIdx: Int): Int {
        var idx = startIdx
        val lists = mod.optJSONObject("item_data_lists")?.optJSONArray("item_datas") ?: return idx
        for (i in 0 until lists.length()) {
            val p = lists.optJSONObject(i)?.optJSONObject("item_params") ?: continue
            val vid = p.optString("vid"); val playTitle = p.optString("play_title")
            if (vid.isEmpty() || playTitle.isEmpty()) continue
            idx++
            acc.add(DanmakuEpisode("tencent", vid, playTitle.ifEmpty { "第${idx}集" }, idx))
        }
        return idx
    }

    override fun getEpisodes(mediaId: String): List<DanmakuEpisode> {
        val out = ArrayList<DanmakuEpisode>()
        runCatching {
            val mod = extractModule(getPageData(mediaId, "")) ?: return@runCatching
            val tabs: JSONArray = runCatching {
                mod.optJSONObject("module_params")?.optString("tabs")?.takeIf { it.isNotEmpty() }?.let { JSONArray(it) }
            }.getOrNull() ?: JSONArray()
            var idx = 0
            if (tabs.length() > 0) {
                for (t in 0 until tabs.length()) {
                    val tab = tabs.optJSONObject(t) ?: continue
                    val modT = if (tab.optBoolean("selected")) mod else extractModule(getPageData(mediaId, tab.optString("page_context"))) ?: continue
                    idx = collectEpisodes(modT, out, idx)
                }
            } else {
                collectEpisodes(mod, out, idx)
            }
        }
        return out
    }

    private fun mapMode(p: Int): Int = when (p) { 2 -> 5; 3 -> 4; else -> 1 }

    override fun getComments(episodeId: String): List<DanmakuComment> = getComments(episodeId, 0, 0)

    // segment_index 的 key 是该段起始毫秒；按 [startMs-30s, endMs] 过滤命中段。窗参省略/endSec<=0 = 全集。
    override fun getComments(episodeId: String, startSec: Int, endSec: Int): List<DanmakuComment> {
        val out = ArrayList<DanmakuComment>()
        val startMs = if (startSec > 0) startSec.toLong() * 1000 - 30000 else -1L
        val endMs = if (endSec > 0) endSec.toLong() * 1000 else -1L
        runCatching {
            val base = JSONObject(net.get("https://dm.video.qq.com/barrage/base/$episodeId", dm))
            val index = base.optJSONObject("segment_index") ?: return@runCatching
            val keys = index.keys().asSequence().toMutableList()
            keys.sortBy { it.toLongOrNull() ?: 0L }
            for (key in keys) {
                val keyMs = key.toLongOrNull() ?: continue
                if (startMs >= 0 && keyMs < startMs) continue
                if (endMs >= 0 && keyMs > endMs) continue
                val segName = index.optJSONObject(key)?.optString("segment_name")
                if (segName.isNullOrEmpty()) continue
                val seg = runCatching { JSONObject(net.get("https://dm.video.qq.com/barrage/segment/$episodeId/$segName", dm)) }.getOrNull() ?: continue
                val list = seg.optJSONArray("barrage_list") ?: continue
                for (i in 0 until list.length()) {
                    val bb = list.optJSONObject(i) ?: continue
                    val timeMs = bb.optString("time_offset").toLongOrNull() ?: 0L
                    var mode = 1
                    var color = 16777215L
                    val csStr = bb.optString("content_style")
                    if (csStr.isNotEmpty()) runCatching {
                        val cs = JSONObject(csStr)
                        mode = mapMode(cs.optInt("position"))
                        val grad = cs.optJSONArray("gradient_colors")
                        color = if (grad != null && grad.length() > 0) hexToDec(grad.optString(0))
                        else if (cs.has("color")) hexToDec(cs.optString("color")) else color
                    }
                    out.add(DanmakuComment(timeMs = timeMs, mode = mode, color = color, content = bb.optString("content")))
                }
            }
        }
        return out
    }
}
