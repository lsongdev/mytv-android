package me.lsong.mytv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.lsong.mytv.rememberLeanbackChildPadding
import me.lsong.mytv.epg.EpgList
import me.lsong.mytv.epg.EpgList.Companion.getEpgChannel
import me.lsong.mytv.iptv.TVChannel
import me.lsong.mytv.ui.player.LeanbackVideoPlayer
import me.lsong.mytv.ui.theme.LeanbackTheme

@Composable
fun MyTvNowPlaying(
    modifier: Modifier = Modifier,
    epgListProvider: () -> EpgList = { EpgList() },
    channelProvider: () -> TVChannel = { TVChannel() },
    channelIndexProvider: () -> Int = { 0 },
    sourceIndexProvider: () -> Int = { 0 },
    videoPlayerMetadataProvider: () -> LeanbackVideoPlayer.Metadata = { LeanbackVideoPlayer.Metadata() },
    onClose: () -> Unit = {},
) {
    val childPadding = rememberLeanbackChildPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClose() }) },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = childPadding.bottom),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            MyTvChannelInfo(
                modifier = modifier
                    .padding(
                        start = childPadding.start,
                        end = childPadding.end
                    ),
                channelProvider = channelProvider,
                channelIndexProvider = channelIndexProvider,
                channelSourceIndexProvider = sourceIndexProvider,
            )

            val epg = epgListProvider().getEpgChannel(channelProvider())
            if (epg != null) {
                MyTvEpgView(
                    modifier = modifier,
                    epgProvider = { epg },
                )
            }
            MyTvPlayerInfo(
                modifier = modifier.padding(start = childPadding.start, bottom = childPadding.bottom),
                metadataProvider = videoPlayerMetadataProvider
            )
        }
        Column (
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = childPadding.top, end = childPadding.end)
        ) {
            LeanbackPanelDateTime()
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun MyTvNowPlayingPreview() {
    LeanbackTheme {
        MyTvNowPlaying(
            channelProvider = { TVChannel.EXAMPLE },
            sourceIndexProvider = { 0 },
        )
    }
}