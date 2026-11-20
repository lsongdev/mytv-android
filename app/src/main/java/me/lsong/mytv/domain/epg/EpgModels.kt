package me.lsong.mytv.domain.epg

import kotlinx.serialization.Serializable
import me.lsong.mytv.domain.live.LiveChannel
import me.lsong.mytv.domain.epg.EpgChannel.Companion.currentProgrammes
import me.lsong.mytv.domain.epg.EpgProgramme.Companion.isLive

/**
 * 频道节目单
 */
@Serializable
data class EpgChannel(

    val id: String = "",
    val providerId: String? = null,
    /**
     * 频道名称
     */
    val title:  String = "",
    /**
     * 节目列表
     */
    val programmes: List<EpgProgramme> = emptyList(),
    val names: List<String> = emptyList(),
    val icon: String? = null,
) {
    companion object {
        /**
         * 获取本频道的当前节目和下一个节目
         */
        fun EpgChannel.currentProgrammes(): EpgProgrammeCurrent? {
            val currentProgramme = programmes.firstOrNull { it.isLive() } ?: return null
            return EpgProgrammeCurrent(
                now = currentProgramme,
                next = programmes.indexOf(currentProgramme).let { index ->
                    if (index + 1 < programmes.size) programmes[index + 1]
                    else null
                },
            )
        }
    }
}

data class EpgList(
    val value: List<EpgChannel> = emptyList(),
) : List<EpgChannel> by value {
    private val scopedIds = value.filter { it.providerId != null }.groupBy { it.providerId to it.id }
    private val globalIds = value.filter { it.providerId == null }.groupBy { it.id }
    private val nameIndex = buildMap<String,MutableList<EpgChannel>> {
        value.forEach { channel -> (listOf(channel.id,channel.title) + channel.names).map(::normalizeEpgName).distinct().forEach {
            getOrPut(it) { mutableListOf() }.add(channel)
        } }
    }
    companion object {
        fun EpgList.getEpgChannel(channel: LiveChannel): EpgChannel? {
            val candidates = buildList {
                channel.epgBindings.forEach { binding -> addAll(scopedIds[binding.providerId to binding.remoteChannelId].orEmpty()) }
                channel.epgBindings.forEach { binding -> addAll(globalIds[binding.remoteChannelId].orEmpty()) }
                val owners = channel.records.map { it.ref.providerId }.toSet()
                val names = (listOf(channel.name,channel.title) + channel.records.flatMap { listOf(it.name,it.title) })
                    .map(::normalizeEpgName).filter(String::isNotBlank).distinct()
                names.forEach { name -> addAll(nameIndex[name].orEmpty().filter { it.providerId == null || it.providerId in owners }) }
            }
            val now = System.currentTimeMillis()
            return candidates.firstOrNull { epg -> epg.programmes.any { now >= it.startAt && now < it.endAt } }
                ?: candidates.firstOrNull { epg -> epg.programmes.any { it.endAt > now } }
                ?: candidates.firstOrNull()
        }
        /**
         * 获取指定频道的当前节目和下一个节目
         */
        fun EpgList.currentProgrammes(channel: LiveChannel): EpgProgrammeCurrent? {
            return getEpgChannel(channel)?.currentProgrammes()
        }
    }
}

/**
 * 频道节目
 */
@Serializable
data class EpgProgramme(
    val channelId: String = "",
    /**
     * 开始时间（时间戳）
     */
    val startAt: Long = 0,

    /**
     * 结束时间（时间戳）
     */
    val endAt: Long = 0,

    /**
     * 节目名称
     */
    val title: String = "",
) {
    companion object {
        /**
         * 是否正在直播
         */
        fun EpgProgramme.isLive() = System.currentTimeMillis() in startAt..<endAt

        /**
         * 节目进度
         */
        fun EpgProgramme.progress() =
            (System.currentTimeMillis() - startAt).toFloat() / (endAt - startAt)
    }
}

/**
 * 当前节目/下一个节目
 */
data class EpgProgrammeCurrent(
    /**
     * 当前正在播放
     */
    val now: EpgProgramme? = null,

    /**
     * 稍后播放
     */
    val next: EpgProgramme? = null,
) {
    companion object {
        val EXAMPLE = EpgProgrammeCurrent(
            now = EpgProgramme(
                startAt = 0,
                endAt = 0,
                title = "实况录像-2023/2024赛季中国男子篮球职业联赛季后赛12进8第五场",
            ),
            next = null,
        )
    }
}


internal fun normalizeEpgName(value: String): String = java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFKC)
    .lowercase(java.util.Locale.ROOT).replace(epgSeparators, "")
    .replace(epgQualitySuffix, "")

private val epgSeparators = Regex("[\\p{P}\\p{Z}\\s]+")
private val epgQualitySuffix = Regex("(?:高清|超清|标清|fhd|hd)$")
