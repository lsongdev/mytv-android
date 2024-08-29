package me.lsong.mytv.epg

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import me.lsong.mytv.providers.TVChannel
import me.lsong.mytv.epg.EpgChannel.Companion.currentProgrammes
import me.lsong.mytv.epg.EpgProgramme.Companion.isLive

/**
 * 频道节目单
 */
@Serializable
data class EpgChannel(

    val id: String = "",
    /**
     * 频道名称
     */
    val title:  String = "",
    /**
     * 节目列表
     */
    val programmes: List<EpgProgramme> = emptyList(),
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

@Immutable
data class EpgList(
    val value: List<EpgChannel> = emptyList(),
) : List<EpgChannel> by value {
    companion object {
        fun EpgList.getEpgChannel(channel: TVChannel): EpgChannel? {
            return (
                    firstOrNull{ it.id == channel.name } ?:
                    firstOrNull{ it.title == channel.title } ?:
                    firstOrNull{ it.title == channel.name }
                    )
        }
        /**
         * 获取指定频道的当前节目和下一个节目
         */
        fun EpgList.currentProgrammes(channel: TVChannel): EpgProgrammeCurrent? {
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

