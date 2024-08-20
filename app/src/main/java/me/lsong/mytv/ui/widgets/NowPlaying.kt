package me.lsong.mytv.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ButtonDefaults
import me.lsong.mytv.rememberLeanbackChildPadding
import me.lsong.mytv.data.entities.EpgList
import me.lsong.mytv.data.entities.EpgList.Companion.getEpgChannel
import me.lsong.mytv.data.entities.TVChannel
import me.lsong.mytv.ui.components.MyTvChannelInfo
import me.lsong.mytv.ui.components.MyTvEpgView
import me.lsong.mytv.ui.components.MyTvPlayerInfo
import me.lsong.mytv.ui.player.LeanbackVideoPlayer
import me.lsong.mytv.ui.theme.LeanbackTheme
import me.lsong.mytv.utils.handleLeanbackKeyEvents

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
                .padding(bottom =  childPadding.bottom),
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