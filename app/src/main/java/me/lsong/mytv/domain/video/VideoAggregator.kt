package me.lsong.mytv.domain.video

import java.text.Normalizer
import me.lsong.mytv.domain.identity.*
import me.lsong.mytv.domain.playback.PlaybackResource

private val videoSeparators = Regex("[\\p{P}\\p{Z}\\s]+")
private val chineseSeason = Regex("第([零一二两三四五六七八九十0-9]+)季")
private val englishSeason = Regex("(?:season\\s*|\\bs)([0-9]{1,2})\\b", RegexOption.IGNORE_CASE)
private val videoYear = Regex("(?:19|20)[0-9]{2}")
private val featurePart = Regex("^(正片|正片.*|hd.*|bd.*|蓝光.*|超清.*|高清.*|[0-9]{3,4}p.*)$", RegexOption.IGNORE_CASE)
private val explicitPart = Regex("s([0-9]+)\\s*e([0-9]+)", RegexOption.IGNORE_CASE)
private val datePart = Regex("^(20[0-9]{2})[-/.]?([01][0-9])[-/.]?([0-3][0-9])")
private val chineseEpisode = Regex("^第?\\s*([0-9零一二两三四五六七八九十]+)\\s*[集话期](?:.*)?$")
private val englishEpisode = Regex("^(?:ep?\\s*)?([0-9]+)$", RegexOption.IGNORE_CASE)

fun normalizeVideoText(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
    .lowercase().replace(videoSeparators, "")

internal fun parseVideoNumber(value: String): Int? {
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
    chineseSeason.find(title)?.groupValues?.get(1)?.let { return parseVideoNumber(it) }
    return englishSeason.find(title)?.groupValues?.get(1)?.toIntOrNull()
}
private fun edition(item: ProviderVideoRecord): String = listOf("导演剪辑", "加长", "未删减", "修复", "重制", "剧场版", "粤语", "国语", "普通话", "英语", "原声")
    .filter { it in item.title || it in item.version }.joinToString("|")
private fun area(item: ProviderVideoRecord) = normalizeVideoText(item.area).replace("中国大陆", "大陆").replace("内地", "大陆")
private fun year(item: ProviderVideoRecord) = videoYear.find(item.year)?.value.orEmpty()
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
        // Split known external IDs from unidentified aliases. Many works
        // sharing the same title/year but having different IDs are O(n).
        val groupsById = HashMap<String, MutableList<Int>>()
        val groupsByAlias = HashMap<String, MutableList<Int>>()
        val unknownGroupsByAlias = HashMap<String, MutableList<Int>>()
        val groupIds = mutableListOf<String?>()
        val groupAliases = mutableListOf<MutableSet<String>>()
        fun discriminator(id: Identity) = "${id.kind}|${id.season}|${id.edition}"
        fun idKey(id: Identity) = id.externalId?.let { "${discriminator(id)}|$it" }
        fun aliasKey(id: Identity) = id.alias.takeIf { id.year.isNotEmpty() }?.let { "${discriminator(id)}|$it" }
        fun index(map: MutableMap<String, MutableList<Int>>, key: String, group: Int) {
            map.getOrPut(key) { mutableListOf() }.add(group)
        }
        unique.forEach { item ->
            val id = identities.getValue(item.ref)
            val externalKey = idKey(id)
            val alias = aliasKey(id)
            val possible = if (externalKey != null) {
                groupsById[externalKey].orEmpty() + (alias?.let { unknownGroupsByAlias[it] }.orEmpty())
            } else alias?.let { groupsByAlias[it] }.orEmpty()
            val found = possible.distinct().sorted().firstOrNull { group ->
                groups[group].all { same(identities.getValue(it.ref), id) }
            }
            val group = found ?: groups.size.also {
                groups.add(mutableListOf())
                groupIds.add(null)
                groupAliases.add(mutableSetOf())
            }
            groups[group].add(item)
            if (externalKey != null && groupIds[group] == null) {
                // A known ID must not bridge an earlier group with another ID.
                groupIds[group] = externalKey
                groupAliases[group].forEach { key -> unknownGroupsByAlias[key]?.remove(group) }
                index(groupsById, externalKey, group)
            }
            if (alias != null && groupAliases[group].add(alias)) {
                index(groupsByAlias, alias, group)
                if (groupIds[group] == null) index(unknownGroupsByAlias, alias, group)
            }
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
        if (featurePart.matches(compact)) return "feature" to 0
        if (compact in setOf("全集", "全剧", "完整版", "合集")) return "complete" to 0
        val explicit = explicitPart.find(text)
        if (explicit != null) {
            val s = explicit.groupValues[1].toLong(); val e = explicit.groupValues[2].toLong()
            return "s${s}e$e" to s * 1_000_000L + e
        }
        val date = datePart.find(text)
        if (date != null) { val d = date.groupValues.drop(1).joinToString(""); return "date:$d" to d.toLong() }
        val number = chineseEpisode.find(text)?.groupValues?.get(1)?.let(::parseVideoNumber)
            ?: englishEpisode.find(text)?.groupValues?.get(1)?.toIntOrNull()
        if (number != null) { val s = season(item.title) ?: 1; return "s${s}e$number" to s * 1_000_000L + number }
        // Unrecognized labels stay provider-specific; list position is never an episode identity.
        return "${item.ref.providerId}:${item.ref.remoteId}:$compact" to Long.MAX_VALUE
    }
}
