package me.lsong.media.danmaku.internal

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import me.lsong.media.danmaku.DanmakuComment
import me.lsong.media.danmaku.DanmakuMedia
import me.lsong.media.danmaku.DanmakuEpisode
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

/**
 * 弹幕源的原生网络/编解码原语（取代原 Rhino `host.*` 桥接，VM 移除后各源直接用它）。
 *
 * - 复用项目 trust-all OkHttp 配置，**每个实例自带进程内 CookieJar**（优酷 mtop 的 _m_h5_tk 需 cookie 往返；
 *   每个源持有独立 [DanmakuNet] 实例，cookie 互不串）。
 * - 头部用 Map<String,String>；缺省注入桌面版 UA（可被传入头覆盖）。
 * - 失败一律返回空串/空数组，不抛——弹幕缺席不可中断播放。
 */
internal class DanmakuNet {

    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS).readTimeout(8, java.util.concurrent.TimeUnit.SECONDS).callTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
        .cookieJar(MemoryCookieJar())
        .build()

    /** GET → UTF-8 文本。非 2xx 也返回 body（不少源用错误体/404 当终止信号）。 */
    fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        String(bytes("GET", url, null, headers), Charsets.UTF_8)

    /** GET → GBK 文本（个别 GBK 页面）。 */
    fun getGbk(url: String, headers: Map<String, String> = emptyMap()): String =
        String(bytes("GET", url, null, headers), Charset.forName("GBK"))

    /** GET → 原始字节（protobuf / zlib / gzip）。 */
    fun getBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray =
        bytes("GET", url, null, headers)

    /** POST → UTF-8 文本。 */
    fun post(url: String, body: String, headers: Map<String, String> = emptyMap()): String =
        String(bytes("POST", url, body, headers), Charsets.UTF_8)

    /** 读 CookieJar 中某 cookie 当前值（优酷取 _m_h5_tk 算签名用）。 */
    fun cookie(url: String, name: String): String = runCatching {
        val httpUrl = url.toHttpUrlOrNull() ?: return ""
        (client.cookieJar as? MemoryCookieJar)?.loadForRequest(httpUrl)
            ?.firstOrNull { it.name == name }?.value ?: ""
    }.getOrDefault("")

    // ─────────────── B 站弹幕（protobuf / XML） ───────────────

    /** B站 seg.so（protobuf）→ 统一弹幕。失败 → 空。 */
    fun biliSegment(url: String, headers: Map<String, String>): List<DanmakuComment> =
        runCatching { BiliDmProto.parseSegment(getBytes(url, headers)) }.getOrDefault(emptyList())

    /** B站 list.so（raw deflate XML）→ 统一弹幕。失败 → 空。 */
    fun biliXml(url: String, headers: Map<String, String>): List<DanmakuComment> = runCatching {
        val raw = getBytes(url, headers)
        val xml = String(DanmakuPrimitives.inflate(raw, raw = true), Charsets.UTF_8)
        val parsed = if (xml.contains("<d ")) xml else String(raw, Charsets.UTF_8)
        BiliDanmaku.parseXml(parsed)
    }.getOrDefault(emptyList())

    private fun bytes(method: String, url: String, body: String?, headers: Map<String, String>): ByteArray =
        runCatching {
            val b = Request.Builder().url(url)
            if (headers.keys.none { it.equals("User-Agent", true) }) b.header("User-Agent", UA)
            headers.forEach { (k, v) -> b.header(k, v) }
            if (method == "POST") b.post((body ?: "").toRequestBody(null))
            val call = client.newCall(b.build())
            val scope = DanmakuQueryContext.current.get()
            scope?.register(call)
            try {
                call.execute().use {
                    val source = it.body?.source() ?: return@use ByteArray(0)
                    source.request(8_000_001)
                    check(source.buffer.size <= 8_000_000) { "Danmaku response exceeds limit" }
                    source.readByteArray()
                }
            } finally { scope?.unregister(call) }
        }.getOrDefault(ByteArray(0))

    /** 极简进程内 CookieJar（按 host 存，覆盖同名）。 */
    private class MemoryCookieJar : CookieJar {
        private val store = ConcurrentHashMap<String, MutableMap<String, Cookie>>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val map = store.getOrPut(url.host) { ConcurrentHashMap() }
            cookies.forEach { map[it.name] = it }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val host = url.host
            val out = ArrayList<Cookie>()
            store.forEach { (h, map) -> if (host == h || host.endsWith(".$h") || h.endsWith(".$host")) out.addAll(map.values) }
            return out
        }
    }

    companion object {
        const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0 Safari/537.36"

        fun md5(s: String): String = DanmakuPrimitives.md5(s)

        /** 等价 JS encodeURIComponent（URLEncoder 的 '+' 还原为 %20）。 */
        fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

        // ─────────────── 爱奇艺 bullet XML 原生解析 ───────────────
        private val IQ_BULLET = Regex("<bulletInfo>([\\s\\S]*?)</bulletInfo>")
        private val IQ_SHOWTIME = Regex("<showTime>([\\s\\S]*?)</showTime>")
        private val IQ_CONTENT = Regex("<content>([\\s\\S]*?)</content>")
        private val IQ_COLOR = Regex("<color>([\\s\\S]*?)</color>")
        private val ENT_HEX = Regex("&#x([0-9a-fA-F]+);")
        private val ENT_DEC = Regex("&#(\\d+);")

        /** 爱奇艺 bullet XML（已 inflate）→ 统一弹幕。mode 统一 1，color 取 hex→十进制。 */
        fun iqiyiBullets(xml: String): List<DanmakuComment> {
            val out = ArrayList<DanmakuComment>()
            for (m in IQ_BULLET.findAll(xml)) {
                val blk = m.groupValues[1]
                val st = IQ_SHOWTIME.find(blk)?.groupValues?.get(1)?.trim()?.toLongOrNull() ?: continue
                val content = xmlUnescape(IQ_CONTENT.find(blk)?.groupValues?.get(1) ?: continue)
                if (content.isEmpty()) continue
                val colorHex = IQ_COLOR.find(blk)?.groupValues?.get(1)?.trim() ?: "ffffff"
                val color = colorHex.toLongOrNull(16) ?: 16777215L
                out.add(DanmakuComment(timeMs = st * 1000, mode = 1, color = color, content = content))
            }
            return out
        }

        /** XML 实体反转义（对齐旧 _unesc：数字实体含 surrogate；&amp; 最后处理）。 */
        private fun xmlUnescape(s: String): String {
            if (s.indexOf('&') < 0) return s
            var r = s
            r = ENT_HEX.replace(r) { String(Character.toChars(it.groupValues[1].toInt(16))) }
            r = ENT_DEC.replace(r) { String(Character.toChars(it.groupValues[1].toInt())) }
            return r.replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
        }
    }
}
