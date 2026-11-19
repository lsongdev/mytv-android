package me.lsong.media.danmaku.internal

import org.json.JSONObject
import me.lsong.media.danmaku.DanmakuComment
import me.lsong.media.danmaku.DanmakuMedia
import me.lsong.media.danmaku.DanmakuEpisode

/**
 * 爱奇艺弹幕源（原生 Kotlin）。逆向：search→avlistinfo→cmts.iqiyi.com/bullet/{s1}/{s2}/{tvid}_300_{mat}.z。
 * 弹幕 .z 是 zlib 包裹的 XML（非 protobuf），inflate 后用 [DanmakuNet.iqiyiBullets] 原生解析。mode 统一 1。
 */
internal class IqiyiDanmakuSource : DanmakuSource {
    override val providerName = "iqiyi"
    private val net = DanmakuNet()
    private val h = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

    override fun search(keyword: String): List<DanmakuMedia> {
        val out = ArrayList<DanmakuMedia>()
        runCatching {
            val obj = JSONObject(net.get("https://search.video.iqiyi.com/o?if=html5&key=" + DanmakuNet.enc(keyword), h))
            val docs = obj.optJSONObject("data")?.optJSONArray("docinfos") ?: return@runCatching
            for (i in 0 until docs.length()) {
                val a = docs.optJSONObject(i)?.optJSONObject("albumDocInfo") ?: continue
                if (a.optString("siteId") != "iqiyi") continue
                val channelId = a.optString("channel").split(",").getOrNull(1) ?: ""
                out.add(
                    DanmakuMedia(
                        provider = "iqiyi", mediaId = a.optString("albumId"), title = a.optString("albumTitle"),
                        type = if (channelId == "1") "movie" else "tv",
                        year = a.optString("releaseDate").take(4).toIntOrNull(),
                    ),
                )
            }
        }
        return out
    }

    override fun getEpisodes(mediaId: String): List<DanmakuEpisode> {
        val out = ArrayList<DanmakuEpisode>()
        runCatching {
            val obj = JSONObject(net.get("https://pcw-api.iqiyi.com/albums/album/avlistinfo?aid=" + DanmakuNet.enc(mediaId) + "&page=1&size=60", h))
            val list = obj.optJSONObject("data")?.optJSONArray("epsodelist")
            if (list == null || list.length() == 0) {
                out.add(DanmakuEpisode("iqiyi", mediaId, "正片", 1)); return@runCatching
            }
            for (i in 0 until list.length()) {
                val e = list.optJSONObject(i) ?: continue
                out.add(DanmakuEpisode("iqiyi", e.optString("tvId"), e.optString("name"), e.optInt("order")))
            }
        }
        return out
    }

    override fun getComments(episodeId: String): List<DanmakuComment> = getComments(episodeId, 0, 0)

    // 每段 mat = 300s（1-based，覆盖 [(mat-1)*300, mat*300)）。窗参省略/endSec<=0 = 全集。
    override fun getComments(episodeId: String, startSec: Int, endSec: Int): List<DanmakuComment> {
        val tvid = episodeId
        val comments = ArrayList<DanmakuComment>()
        var durSec = 0
        runCatching {
            val b = JSONObject(net.get("https://pcw-api.iqiyi.com/video/video/baseinfo/$tvid", h))
            durSec = b.optJSONObject("data")?.optInt("durationSec") ?: 0
        }
        val maxMat = if (durSec > 0) Math.ceil(durSec / 300.0).toInt() else 100
        val matFrom = if (startSec > 0) startSec / 300 + 1 else 1
        val matTo = if (endSec > 0) minOf(maxMat, Math.ceil(endSec / 300.0).toInt()) else maxMat
        val s1 = tvid.dropLast(2).takeLast(2)   // slice(-4,-2)
        val s2 = tvid.takeLast(2)               // slice(-2)
        for (mat in matFrom..matTo) {
            val raw = net.getBytes("https://cmts.iqiyi.com/bullet/$s1/$s2/${tvid}_300_$mat.z", h)
            if (raw.isEmpty()) break
            val xml = runCatching { String(DanmakuPrimitives.inflate(raw, raw = false), Charsets.UTF_8) }.getOrNull() ?: continue
            if (xml.isEmpty()) continue
            comments.addAll(DanmakuNet.iqiyiBullets(xml))
        }
        return comments
    }
}
