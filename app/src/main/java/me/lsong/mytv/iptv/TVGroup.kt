package me.lsong.mytv.iptv

import androidx.compose.runtime.Immutable

/**
 * 直播源分组
 */
data class TVGroup(
    /**
     * 分组名称
     */
    val title: String = "",
    /**
     * 直播源列表
     */
    val channels: TVChannelList = TVChannelList(),
) {
    // val title: String
    //     get() = name ?: channels.first().groupTitle ?: "其他"

    companion object {
        val EXAMPLE = TVGroup(
            title = "测试分组",
            channels = TVChannelList(
                List(10) { idx ->
                    TVChannel.EXAMPLE
                },
            )
        )
    }
}


/**
 * 直播源分组列表
 */
@Immutable
data class TVGroupList(
    val value: List<TVGroup> = emptyList(),
) : List<TVGroup> by value {
    companion object {
        val EXAMPLE = TVGroupList(List(5) { groupIdx ->
            TVGroup.EXAMPLE
        })

        fun TVGroupList.findGroupIndex(iptv: TVChannel) =
            this.indexOfFirst { group -> group.channels.any { it == iptv } }

        fun TVGroupList.findChannelIndex(iptv: TVChannel) =
            this.flatMap { it.channels }.indexOfFirst { it == iptv }

        val TVGroupList.channels: List<TVChannel>
            get() = this.flatMap { it.channels }
    }
}