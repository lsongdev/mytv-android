package me.lsong.mytv.domain.video

import java.text.Normalizer
import me.lsong.mytv.domain.provider.ProviderRecordRef

data class VideoCategoryGroup(
    val kind: VideoKind,
    val records: List<ProviderVideoCategory>,
    val filters: List<VideoCategory>,
) {
    val id get() = "category:${kind.name}"
    val title get() = when (kind) { VideoKind.SERIES -> "剧集"; VideoKind.OTHER -> "其他分类"; else -> kind.title }
}

/** Application taxonomy only: no provider names, URLs or remote category IDs. */
fun buildVideoCategoryGroups(input: List<ProviderVideoCategory>): List<VideoCategoryGroup> {
    val records = input.filter { it.title.isNotBlank() }.distinctBy { it.ref }
    val byRef = records.associateBy { it.ref }
    val names = records.associate { it.ref to categoryName(it.title) }
    fun ancestors(record: ProviderVideoCategory): List<ProviderVideoCategory> {
        val seen = mutableSetOf(record.ref)
        val result = mutableListOf<ProviderVideoCategory>()
        var parent = record.parentId?.let { byRef[ProviderRecordRef(record.ref.providerId,it)] }
        while (parent != null && seen.add(parent.ref)) {
            result += parent
            parent = parent.parentId?.let { byRef[ProviderRecordRef(record.ref.providerId,it)] }
        }
        return result
    }
    val paths = records.associate { it.ref to ancestors(it) }
    val descendants = mutableMapOf<ProviderRecordRef,MutableList<ProviderVideoCategory>>()
    records.forEach { child -> paths.getValue(child.ref).forEach { parent -> descendants.getOrPut(parent.ref) { mutableListOf() } += child } }
    val kinds = records.associate { record ->
        val name = names.getValue(record.ref)
        // Explicit documentary/anime/short-form labels remain distinct even
        // when a source places them beneath its general movie/series parent.
        val kind = specialKind(name)
            ?: paths.getValue(record.ref).firstNotNullOfOrNull { explicitKind(names.getValue(it.ref)) }
            ?: explicitKind(name) ?: genreKind(name) ?: VideoKind.OTHER
        record.ref to kind
    }
    return VideoKind.entries.mapNotNull { kind ->
        val members = records.filter { kinds[it.ref] == kind }
        if (members.isEmpty()) return@mapNotNull null
        val filterRecords = linkedMapOf<String,MutableList<ProviderVideoCategory>>()
        for (record in members) {
            val label = filterTitle(kind,record.title,names.getValue(record.ref)) ?: continue
            val matches = listOf(record) + descendants[record.ref].orEmpty().filter { kinds[it.ref] == kind }
            filterRecords.getOrPut(label) { mutableListOf() }.addAll(matches)
        }
        VideoCategoryGroup(kind,members,filterRecords.map { (title, matches) ->
            VideoCategory("filter:${kind.name}:$title",title,matches.distinctBy { it.ref })
        })
    }
}

private val categorySeparators = Regex("[\\p{P}\\p{Z}\\s]+")
private fun categoryName(value: String) = Normalizer.normalize(value,Normalizer.Form.NFKC)
    .lowercase().replace(categorySeparators,"")

private fun specialKind(name: String): VideoKind? = when {
    name.contains("短剧") || name.contains("微剧") || name.contains("女频") || name.contains("男频") -> VideoKind.SHORT
    name.contains("纪录") || name.contains("记录片") || name == "documentary" -> VideoKind.DOCUMENTARY
    name.contains("动漫") || name.contains("动画") || name.contains("番剧") || name in setOf("anime","国漫","日漫") -> VideoKind.ANIME
    name.contains("综艺") || name.contains("真人秀") || name.contains("脱口秀") -> VideoKind.VARIETY
    listOf("体育","赛事","足球","篮球","网球").any(name::contains) -> VideoKind.SPORTS
    else -> null
}

private fun explicitKind(name: String): VideoKind? = specialKind(name) ?: when {
    name.contains("电影") || name in setOf("movie","movies","影片") -> VideoKind.MOVIE
    name.contains("剧") || name in setOf("tv","tvseries","series") -> VideoKind.SERIES
    else -> null
}

private val genres = listOf("动作","喜剧","爱情","科幻","恐怖","剧情","战争","犯罪","悬疑","惊悚","冒险","奇幻","灾难","家庭","动画")
private fun genreKind(name: String): VideoKind? = if (genres.any { name == it || name == "${it}片" }) VideoKind.MOVIE else null

private fun filterTitle(kind: VideoKind, raw: String, name: String): String? {
    val broad = when (kind) {
        VideoKind.MOVIE -> setOf("电影","电影片","影片","movie","movies")
        VideoKind.SERIES -> setOf("电视剧","连续剧","剧集","tv","tvseries","series")
        VideoKind.ANIME -> setOf("动漫","动画","动画片","番剧","anime")
        VideoKind.VARIETY -> setOf("综艺","综艺节目")
        VideoKind.DOCUMENTARY -> setOf("纪录","纪录片","记录片","documentary")
        VideoKind.SHORT -> setOf("短剧","微剧")
        VideoKind.SPORTS -> setOf("体育","体育赛事","赛事")
        VideoKind.OTHER -> setOf("其他","其它","其他分类")
    }
    if (name in broad) return null
    if (kind == VideoKind.MOVIE) genres.firstOrNull { name in setOf(it,"${it}片","${it}电影","${it}电影片") }?.let { return it }
    if (kind == VideoKind.SERIES) {
        val aliases = linkedMapOf(
            "国产" to setOf("国产剧","大陆剧","内地剧","中国剧"),
            "港剧" to setOf("港剧","香港剧"), "台剧" to setOf("台剧","台湾剧"),
            "港台" to setOf("港台剧"), "日剧" to setOf("日剧","日本剧"),
            "韩剧" to setOf("韩剧","韩国剧"), "日韩" to setOf("日韩剧"),
            "欧美" to setOf("欧美剧","美国剧","美剧"), "英剧" to setOf("英剧","英国剧"),
            "泰剧" to setOf("泰剧","泰国剧"), "海外" to setOf("海外剧"),
        )
        aliases.entries.firstOrNull { name in it.value }?.let { return it.key }
    }
    if (kind == VideoKind.ANIME && name in setOf("动画电影","动漫电影","剧场版","动漫剧场版")) return "剧场版"
    if (kind == VideoKind.ANIME || kind == VideoKind.VARIETY) {
        val suffix = if (kind == VideoKind.ANIME) "动漫" else "综艺"
        val aliases = linkedMapOf(
            "国产" to setOf("国产$suffix","大陆$suffix","内地$suffix","国漫"),
            "日本" to setOf("日本$suffix","日漫"), "韩国" to setOf("韩国$suffix"),
            "日韩" to setOf("日韩$suffix"), "欧美" to setOf("欧美$suffix"), "港台" to setOf("港台$suffix"),
        )
        aliases.entries.firstOrNull { name in it.value }?.let { return it.key }
    }
    return raw.trim()
}
