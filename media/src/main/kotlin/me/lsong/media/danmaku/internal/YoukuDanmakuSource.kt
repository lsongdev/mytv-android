package me.lsong.media.danmaku.internal

import org.json.JSONArray
import org.json.JSONObject
import me.lsong.media.danmaku.DanmakuComment
import me.lsong.media.danmaku.DanmakuMedia
import me.lsong.media.danmaku.DanmakuEpisode

/**
 * 优酷弹幕源（原生 Kotlin）。mtop 签名链：先 GET weakget（CookieJar 自动存 _m_h5_tk），
 * token = _m_h5_tk 首段；sign = md5(token&t&appKey&dataJSON)；POST danmu.list（jar 自动回传 cookie）。
 * 注：dataJSON 须 {"vid":..,"mat":..} 顺序（Android JSONObject 用 LinkedHashMap 保序，与 JS JSON.stringify 一致）。
 */
internal class YoukuDanmakuSource : DanmakuSource {
    override val providerName = "youku"
    private val net = DanmakuNet()

    private val appKey = "24679788"
    private val ykH = mapOf("User-Agent" to "Mozilla/5.0", "Referer" to "https://v.youku.com")
    private val postH = mapOf(
        "User-Agent" to "Mozilla/5.0", "Referer" to "https://v.youku.com",
        "Content-Type" to "application/x-www-form-urlencoded",
    )

    override fun search(keyword: String): List<DanmakuMedia> {
        val out = ArrayList<DanmakuMedia>()
        runCatching {
            val j = JSONObject(net.get("https://search.youku.com/api/search?keyword=" + DanmakuNet.enc(keyword), ykH))
            val comps = j.optJSONArray("pageComponentList") ?: return@runCatching
            for (i in 0 until comps.length()) {
                val cd = comps.optJSONObject(i)?.optJSONObject("commonData") ?: continue
                if (cd.optInt("isYouku") != 1) continue
                val feat = cd.optString("feature")
                val year = Regex("(\\d{4})").find(feat)?.groupValues?.get(1)?.toIntOrNull()
                val showId = cd.optString("realShowId").ifEmpty { cd.optString("showId") }
                out.add(
                    DanmakuMedia(
                        provider = "youku", mediaId = showId,
                        title = cd.optJSONObject("titleDTO")?.optString("displayName") ?: "",
                        type = if (feat.contains("电影")) "movie" else "tv", year = year,
                    ),
                )
            }
        }
        return out
    }

    override fun getEpisodes(mediaId: String): List<DanmakuEpisode> {
        val out = ArrayList<DanmakuEpisode>()
        runCatching {
            val j = JSONObject(net.get("https://openapi.youku.com/v2/shows/videos.json?client_id=53e6cc67237fc59a&package=com.huawei.hwvplayer.youku&ext=show&show_id=$mediaId", ykH))
            val vids = j.optJSONArray("videos") ?: return@runCatching
            for (i in 0 until vids.length()) {
                val v = vids.optJSONObject(i) ?: continue
                val durSec = Math.ceil(v.optString("duration").toDoubleOrNull() ?: 0.0).toInt()
                val idx = (v.optString("seq").ifEmpty { v.optString("stage") }).toIntOrNull() ?: 0
                out.add(DanmakuEpisode("youku", v.optString("id") + "|" + durSec, v.optString("title").ifEmpty { "第${v.optString("stage")}集" }, idx))
            }
        }
        return out
    }

    private fun token(): String {
        net.get("https://acs.youku.com/h5/mtop.com.youku.aplatform.weakget/1.0/?jsv=2.5.1&appKey=$appKey", ykH)
        val tk = net.cookie("https://acs.youku.com", "_m_h5_tk")
        return if (tk.isNotEmpty()) tk.split("_")[0] else ""
    }

    private fun fetchSeg(vid: String, mat: Int, token: String, retry: Boolean): JSONArray {
        val t = System.currentTimeMillis().toString()
        val dataStr = JSONObject().put("vid", vid).put("mat", mat).toString()
        val sign = DanmakuNet.md5("$token&$t&$appKey&$dataStr")
        val url = "https://acs.youku.com/h5/mopen.youku.danmu.list/1.0/?jsv=2.7.0&appKey=$appKey&t=$t&sign=$sign" +
            "&api=mopen.youku.danmu.list&v=1.0&type=originaljson&dataType=jsonp&timeout=20000"
        val resp = net.post(url, "data=" + DanmakuNet.enc(dataStr), postH)
        val o = runCatching { JSONObject(resp) }.getOrNull() ?: return JSONArray()
        val ret = o.optJSONArray("ret")?.optString(0) ?: ""
        if (!ret.contains("SUCCESS")) {
            if (!retry && ret.contains("TOKEN")) return fetchSeg(vid, mat, token(), true)
            return JSONArray()
        }
        return runCatching {
            JSONObject(o.optJSONObject("data")?.optString("result") ?: "{}")
                .optJSONObject("data")?.optJSONArray("result")
        }.getOrNull() ?: JSONArray()
    }

    override fun getComments(episodeId: String): List<DanmakuComment> = getComments(episodeId, 0, 0)

    // 每段 mat = 60s（1-based）。窗参省略/endSec<=0 = 全集。
    override fun getComments(episodeId: String, startSec: Int, endSec: Int): List<DanmakuComment> {
        val parts = episodeId.split("|")
        val vid = parts[0]
        val durSec = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val matCount = maxOf(1, Math.ceil(durSec / 60.0).toInt())
        val matFrom = if (startSec > 0) startSec / 60 + 1 else 1
        val matTo = if (endSec > 0) minOf(matCount, Math.ceil(endSec / 60.0).toInt()) else matCount
        val out = ArrayList<DanmakuComment>()
        val tk = token()
        for (mat in matFrom..matTo) {
            val list = fetchSeg(vid, mat, tk, false)
            for (i in 0 until list.length()) {
                val d = list.optJSONObject(i) ?: continue
                var color = 16777215L
                var pos = 3
                runCatching {
                    val p = JSONObject(d.optString("propertis").ifEmpty { "{}" })
                    if (p.has("color")) color = p.optLong("color")
                    if (p.has("pos")) pos = p.optInt("pos")
                }
                val mode = if (pos == 1) 5 else if (pos == 4) 4 else 1
                out.add(DanmakuComment(timeMs = d.optLong("playat"), mode = mode, color = color, content = d.optString("content")))
            }
        }
        return out
    }
}
