package me.lsong.mytv.data.entities

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

data class TVSource(
    val tvgId: String?,
    val tvgLogo: String?,
    val tvgName: String?,
    val groupTitle: String?,
    val title: String,
    val url: String,
) {
    val name: String
        get() = (tvgName ?: tvgId ?: title).toString()

    companion object {
        val EXAMPLE = TVSource(
            tvgId = "cctv1",
            tvgName = "cctv1",
            tvgLogo = "https://live.fanmingming.com/tv/CCTV1.png",
            title = "CCTV-1",
            groupTitle = "央视",
            url = "https://pi.0472.org/chc/ym.m3u8"
        )
    }
}

/**
 * 直播源
 */
@Immutable
data class TVChannel(
    val no: String = "",
    val icon: ImageVector? = null,
    /**
     * 直播源名称
     */
    val name: String = "",
    /**
     * 频道名称
     */
    val title: String = "",
    val sources: List<TVSource> = emptyList(),
) {

    // val name: String
    //     get() = sources.first().name

    // val title: String
    //     get() = sources.first().title

    val logo: String?
        get() = sources.firstNotNullOfOrNull { it.tvgLogo }

    val groupTitle: String?
        get() = sources.firstNotNullOfOrNull { it.groupTitle }
    /**
     * 播放地址列表
     */
    val urls: List<String>
        get() = sources.map { it.url }

    companion object {
        val EXAMPLE = TVChannel(
            title = "测试频道",
            sources = listOf(
                TVSource.EXAMPLE
            )
        )
    }
}

/**
 * 直播源列表
 */
@Immutable
data class TVChannelList(
    val value: List<TVChannel> = emptyList(),
) : List<TVChannel> by value {
    companion object {
        val EXAMPLE = TVChannelList(List(10) { i -> TVChannel.EXAMPLE.copy() })
    }
}
