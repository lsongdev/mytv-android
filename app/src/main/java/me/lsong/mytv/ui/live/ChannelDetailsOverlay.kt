package me.lsong.mytv.ui.live

import me.lsong.mytv.ui.components.ClockLabel
import me.lsong.mytv.ui.epg.EpgSchedule

import me.lsong.mytv.ui.theme.TvTheme

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
import me.lsong.mytv.rememberScreenPadding
import me.lsong.mytv.domain.epg.EpgList
import me.lsong.mytv.domain.epg.EpgList.Companion.getEpgChannel
import me.lsong.mytv.domain.live.LiveChannel
import me.lsong.mytv.playback.PlayerEngine

@Composable
fun ChannelDetailsOverlay(
    modifier: Modifier = Modifier,
    epgListProvider: () -> EpgList = { EpgList() },
    channelProvider: () -> LiveChannel = { LiveChannel() },
    channelIndexProvider: () -> Int = { 0 },
    sourceIndexProvider: () -> Int = { 0 },
    videoPlayerMetadataProvider: () -> PlayerEngine.Metadata = { PlayerEngine.Metadata() },
    onClose: () -> Unit = {},
) {
    val childPadding = rememberScreenPadding()

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
            ChannelInfo(
                modifier = modifier
                    .padding(
                        start = childPadding.start,
                        end = childPadding.end
                    ),
                channelProvider = channelProvider,
                channelIndexProvider = channelIndexProvider,
                channelSourceIndexProvider = sourceIndexProvider,
            )

            // val epg = epgListProvider().getEpgChannel(channelProvider())
            // if (epg != null) {
            //     EpgSchedule(
            //         modifier = modifier,
            //         epgProvider = { epg },
            //     )
            // }
            PlayerInfo(
                modifier = modifier.padding(start = childPadding.start, bottom = childPadding.bottom),
                metadataProvider = videoPlayerMetadataProvider
            )
        }
        Column (
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = childPadding.top, end = childPadding.end)
        ) {
            ClockLabel()
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun MyTvNowPlayingPreview() {
    TvTheme {
        ChannelDetailsOverlay(
            channelProvider = { LiveChannel(title = "测试频道") },
            sourceIndexProvider = { 0 },
        )
    }
}
