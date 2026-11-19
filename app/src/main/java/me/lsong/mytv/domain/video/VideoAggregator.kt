package me.lsong.mytv.domain.video

import java.text.Normalizer
import me.lsong.mytv.domain.identity.*
import me.lsong.mytv.domain.playback.PlaybackResource

fun normalizeVideoText(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
    .lowercase().replace(Regex("[\\p{P}\\p{Z}\\s]+"), "")

private fun chineseNumber(value: String): Int? {
    value.toIntOrNull()?.let { return it }
    val digits = mapOf('零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
    if (value == "十") return 10
    if ('十' in value) {
        val parts = value.split('十')
        return (parts[0].firstOrNull()?.let(digits::get) ?: 1) * 10 + (parts.getOrNull(1)?.firstOrNull()?.let(digits::get) ?: 0)
    }
    return value.singleOrNull()?.let(digits::get)
}

private fun season(title: String): Int? {
    Regex("第([零一二两三四五六七八九十0-9]+)季").find(title)?.groupValues?.get(1)?.let { return chineseNumber(it) }
    return Regex("(?:season\\s*|\\bs)([0-9]{1,2})\\b", RegexOption.IGNORE_CASE).find(title)?.groupValues?.get(1)?.toIntOrNull()
}
private fun edition(item: ProviderVideoRecord): String = listOf("导演剪辑", "加长", "未删减", "修复", "重制", "剧场版", "粤语", "国语", "普通话", "英语", "原声")
    .filter { it in item.title || it in item.version }.joinToString("|")
private fun area(item: ProviderVideoRecord) = normalizeVideoText(item.area).replace("中国大陆", "大陆").replace("内地", "大陆")
private fun year(item: ProviderVideoRecord) = Regex("(?:19|20)[0-9]{2}").find(item.year)?.value.orEmpty()
private fun alias(item: ProviderVideoRecord) = listOf(normalizeVideoText(item.title), year(item), item.kind.name, area(item), season(item.title).toString(), edition(item)).joinToString("|")
private fun knownId(item: ProviderVideoRecord) = item.doubanId.takeIf { it.toLongOrNull()?.let { n -> n > 0 } == true }

object VideoAggregator {
    private data class Identity(val year: String, val alias: String, val kind: VideoKind, val season: Int?, val edition: String, val externalId: String?)
    private fun identity(item: ProviderVideoRecord) = Identity(year(item), alias(item), item.kind, season(item.title), edition(item), knownId(item))
    private fun same(a: Identity, b: Identity): Boolean {
        if (a.kind != b.kind || a.season != b.season || a.edition != b.edition) return false
        if (a.externalId != null && b.externalId != null) return a.externalId == b.externalId
        return a.year.isNotEmpty() && b.year.isNotEmpty() && a.alias == b.alias
    }
    fun sameWork(a: ProviderVideoRecord, b: ProviderVideoRecord): Boolean = a.ref == b.ref || same(identity(a), identity(b))

    fun merge(items: List<ProviderVideoRecord>, identityStore: CatalogIdentityStore = InMemoryCatalogIdentityStore()): List<VideoWork> {
        val groups = mutableListOf<MutableList<ProviderVideoRecord>>()
        val unique = items.distinctBy { it.ref }
        val identities = unique.associate { it.ref to identity(it) }
        unique.forEach { item ->
            // Every pair must agree: an entry with no external ID cannot bridge two conflicting IDs.
            val group = groups.firstOrNull { members -> members.all { same(identities.getValue(it.ref), identities.getValue(item.ref)) } }
            if (group == null) groups += mutableListOf(item) else group += item
        }
        val ids = identityStore.resolveAll("video", groups.map { variants ->
            val first = variants.first()
            val key = if (year(first).isNotBlank()) alias(first) else first.ref.key
            CatalogIdentityRequest(variants.map { it.ref }, key + "|" + variants.mapNotNull(::knownId).distinct().sorted().joinToString())
        })
        return groups.mapIndexed { index, variants -> VideoWork(ids[index], variants) }
    }

    fun details(work: VideoWork, variants: List<ProviderVideoRecord>, errors: Map<String, String> = emptyMap()): VideoDetails {
        data class EpisodeBucket(val label: String, val order: Long, val resources: MutableList<PlaybackResource>)
        val parts = linkedMapOf<String, EpisodeBucket>()
        variants.forEach { item ->
            item.lines.forEach { line ->
                line.parts.forEach resourceLoop@ { record ->
                    val resource = record.resource ?: return@resourceLoop
                    val key = partKey(item, record.label)
                    val bucket = parts.getOrPut(key.first) { EpisodeBucket(record.label, key.second, mutableListOf()) }
                    bucket.resources += resource
                }
            }
        }
        val completeWork = work.copy(variants = variants.ifEmpty { work.variants })
        return VideoDetails(completeWork, parts.map { (id, b) -> VideoPart(id, b.label, b.order, b.resources.distinctBy { it.id }) }
            .sortedWith(compareBy<VideoPart> { it.order }.thenBy { it.title }), errors)
    }

    private fun partKey(item: ProviderVideoRecord, label: String): Pair<String, Long> {
        val text = Normalizer.normalize(label, Normalizer.Form.NFKC).trim()
        val compact = normalizeVideoText(text)
        if (Regex("^(正片|正片.*|hd.*|bd.*|蓝光.*|超清.*|高清.*|[0-9]{3,4}p.*)$", RegexOption.IGNORE_CASE).matches(compact)) return "feature" to 0
        if (compact in setOf("全集", "全剧", "完整版", "合集")) return "complete" to 0
        val explicit = Regex("s([0-9]+)\\s*e([0-9]+)", RegexOption.IGNORE_CASE).find(text)
        if (explicit != null) {
            val s = explicit.groupValues[1].toLong(); val e = explicit.groupValues[2].toLong()
            return "s${s}e$e" to s * 1_000_000L + e
        }
        val date = Regex("^(20[0-9]{2})[-/.]?([01][0-9])[-/.]?([0-3][0-9])").find(text)
        if (date != null) { val d = date.groupValues.drop(1).joinToString(""); return "date:$d" to d.toLong() }
        val number = Regex("^第?\\s*([0-9零一二两三四五六七八九十]+)\\s*[集话期](?:.*)?$").find(text)?.groupValues?.get(1)?.let(::chineseNumber)
            ?: Regex("^(?:ep?\\s*)?([0-9]+)$", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toIntOrNull()
        if (number != null) { val s = season(item.title) ?: 1; return "s${s}e$number" to s * 1_000_000L + number }
        // Unrecognized labels stay provider-specific; list position is never an episode identity.
        return "${item.ref.providerId}:${item.ref.remoteId}:$compact" to Long.MAX_VALUE
    }
}
