package me.lsong.mytv.domain.live

import me.lsong.mytv.domain.identity.CatalogIdentityRequest

import me.lsong.mytv.domain.epg.EpgBinding
import me.lsong.mytv.domain.identity.CatalogIdentityStore
import me.lsong.mytv.domain.identity.InMemoryCatalogIdentityStore
import java.text.Normalizer

fun mergeLiveCatalogs(catalogs: List<ProviderLiveCatalog>, identities: CatalogIdentityStore = InMemoryCatalogIdentityStore()): LiveCatalog {
    val buckets = linkedMapOf<String, MutableList<ProviderChannelRecord>>()
    catalogs.flatMap { it.records }.forEach { record ->
        val name = normalizeChannelName(record.name)
        val year = liveChannelEditionYear(record.title)
        val content = if (year != null) "${name.replace(editionYear, "")}|edition:$year" else name
        val needsGroup = name in setOf("新闻", "综合", "电影", "体育", "news") || (name.length <= 2 && year == null)
        val identity = if (needsGroup) "${record.groupTitle}|$content" else content
        buckets.getOrPut(identity) { mutableListOf() } += record
    }
    val groups = linkedMapOf<String, MutableList<LiveChannel>>()
    val canonicalIds = identities.resolveAll("live", buckets.map { (key, records) -> CatalogIdentityRequest(records.map { it.ref }, key) })
    buckets.entries.forEachIndexed { index, (_, records) ->
        val first = records.first()
        val channel = LiveChannel(
            id = canonicalIds[index], name = first.name, title = first.title,
            resources = records.map { it.resource }.distinctBy { it.uri to it.headers },
            logo = records.firstNotNullOfOrNull { it.logo }, records = records,
            epgBindings = records.mapNotNull { r -> r.epgChannelId?.let { EpgBinding(r.ref.providerId, it) } }.distinct(),
        )
        records.map { it.groupTitle }.distinct().forEach { groups.getOrPut(it) { mutableListOf() } += channel }
    }
    val allFailed = catalogs.any { it.error != null } && buckets.isEmpty()
    return LiveCatalog(groups.map { (title, channels) -> ChannelGroup("group:$title", title, channels) },
        if (allFailed) "直播源加载失败，请在设置中检查地址" else null, catalogs.any { it.isLoading })
}

private fun normalizeChannelName(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
    .lowercase().replace(Regex("""[\p{P}\p{Z}\s]+"""), "")
    .replace(Regex("(?:高清|超清|标清|hd|fhd)$"), "")

/** An EPG name can describe a series; the visible year distinguishes its recordings. */
internal fun liveChannelEditionYear(title: String): String? = editionYear.findAll(Normalizer.normalize(title,Normalizer.Form.NFKC))
    .map { it.value }.distinct().toList().singleOrNull()
private val editionYear = Regex("""(?<![0-9])(?:19|20)[0-9]{2}(?![0-9])""")
