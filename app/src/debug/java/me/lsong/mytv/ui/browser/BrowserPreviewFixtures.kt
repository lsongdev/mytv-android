package me.lsong.mytv.ui.browser

import me.lsong.mytv.domain.playback.PlaybackResource
import me.lsong.mytv.domain.provider.ProviderConfig
import me.lsong.mytv.domain.provider.ProviderKind
import me.lsong.mytv.domain.provider.ProviderRecordRef
import me.lsong.mytv.ui.browser.ContentDrawer
import me.lsong.mytv.ui.theme.TvTheme

import me.lsong.mytv.ui.browser.ContentDrawerState

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import me.lsong.mytv.domain.live.*
import me.lsong.mytv.data.providers.m3u.*

/** Offline fixtures shared by the IDE preview and debug-only TV preview activity. */
fun menuPreviewPlaylists(): List<ProviderLiveCatalog> = listOf("家庭直播", "备用直播").mapIndexed { index, name ->
    val config = ProviderConfig("preview-$index", name, "https://example.com/$index.m3u", ProviderKind.M3U)
    val groups = listOf(
        "央视频道" to listOf("CCTV-1 综合", "CCTV-2 财经", "CCTV-3 综艺", "CCTV-4 中文国际", "CCTV-5 体育", "CCTV-6 电影", "CCTV-7 国防军事", "CCTV-8 电视剧", "CCTV-9 纪录", "CCTV-10 科教"),
        "卫视频道" to listOf("湖南卫视", "浙江卫视", "江苏卫视", "东方卫视", "北京卫视"),
        "地方频道" to listOf("北京新闻", "上海新闻", "深圳都市"),
    )
    ProviderLiveCatalog(config, groups.flatMap { (group, names) -> names.map { title ->
        val ref = ProviderRecordRef(config.id, "$group/$title")
        ProviderChannelRecord(ref, title, title, group, PlaybackResource("https://example.com/$index/live.m3u8", ref, name))
    } })
}

@Preview(name = "TV · 三栏直播菜单", widthDp = 960, heightDp = 540)
@Composable
private fun SourceDrawerPreview() {
    val playlists = remember { menuPreviewPlaylists() }
    val playing = mergeLiveCatalogs(playlists).channels.first()
    TvTheme {
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF243747), Color(0xFF060B13))))) {
            ContentDrawer(mergeLiveCatalogs(playlists), playing, remember { ContentDrawerState(playing) }, {}, {}, {})
        }
    }
}
