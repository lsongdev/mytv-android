package me.lsong.media.vod.maccms

import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException

object MacCmsParser {
    private val htmlTags = Regex("<[^>]+>")
    private val directExtensions = Regex("\\.(m3u8|mp4|mkv|mov|m4v|ts|webm)$")
    fun page(baseUri: String, text: String, includeLines: Boolean = false): MacCmsPage {
        val root = Json.parseToJsonElement(text).jsonObject
        val code = root.text("code")
        require(code.isEmpty() || code == "1") { "接口返回失败" }
        val rows = root["list"] as? JsonArray ?: throw IOException("接口未返回影片列表")
        return MacCmsPage(rows.mapNotNull { element ->
            runCatching { item(baseUri, element.jsonObject, includeLines) }.getOrNull()
        }, root.text("page").toIntOrNull() ?: 1, root.text("pagecount").toIntOrNull() ?: 1)
    }

    fun categories(text: String): List<MacCmsCategory> {
        val root = Json.parseToJsonElement(text).jsonObject
        val code = root.text("code")
        require(code.isEmpty() || code == "1") { "接口返回失败" }
        val rows = root["class"] as? JsonArray ?: throw IOException("接口未返回分类列表")
        return rows.mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            val id = row.text("type_id").trim()
            val title = clean(row.text("type_name"))
            if (id.isBlank() || title.isBlank()) null else MacCmsCategory(id, title,
                row.text("type_pid").trim().takeIf { it.isNotEmpty() && it != "0" && it != id })
        }.distinctBy { it.id }
    }

    private fun item(baseUri: String, row: JsonObject, includeLines: Boolean): MacCmsVideo {
        val id = row.text("vod_id"); val title = clean(row.text("vod_name"))
        require(id.isNotBlank() && title.isNotBlank())
        val category = clean(row.text("type_name"))
        val base = baseUri.toHttpUrl()
        val codes = row.text("vod_play_from").split("$$$")
        val lines = if (includeLines) row.text("vod_play_url").split("$$$").mapIndexedNotNull { index, value ->
            val code = codes.getOrElse(index) { "" }
            val parts = value.split('#').mapNotNull { entry ->
                val split = entry.indexOf('$')
                if (split <= 0) null else {
                    val label = clean(entry.substring(0, split))
                    val rawUrl = entry.substring(split + 1).trim()
                    val url = base.resolve(rawUrl)?.toString().orEmpty()
                    if (label.isBlank() || url.isBlank()) null else MacCmsPart(label, url, direct(url, code))
                }
            }
            if (parts.isEmpty()) null else MacCmsLine(code.ifBlank { index.toString() }, "线路 ${index + 1}", parts)
        } else emptyList()
        return MacCmsVideo(
            id, title, row.text("vod_year"), category,
            row.text("vod_area"), row.text("vod_pic").takeIf { it.isNotBlank() }?.let { base.resolve(it)?.toString() }.orEmpty(),
            clean(row.text("vod_content").ifBlank { row.text("vod_blurb") }), clean(row.text("vod_remarks")),
            row.text("vod_douban_id"), row.text("vod_version"), lines,
        )
    }

    private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun clean(value: String) = value.replace(htmlTags, " ").replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").trim()
    private fun direct(url: String, code: String): Boolean {
        val path = url.toHttpUrl().encodedPath.lowercase()
        if (path.contains("/share/") || path.endsWith(".html") || path.endsWith(".htm")) return false
        return directExtensions.containsMatchIn(path) || code.contains("m3u8", true)
    }
}
