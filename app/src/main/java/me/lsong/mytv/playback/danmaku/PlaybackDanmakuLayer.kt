package me.lsong.mytv.playback.danmaku

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.delay
import me.lsong.mytv.playback.PlaybackState
import me.lsong.mytv.domain.video.VideoPlaybackSelection
import me.lsong.mytv.ui.danmaku.DanmakuViewModel

@Composable
fun PlaybackDanmakuLayer(player: PlaybackState, selection: VideoPlaybackSelection?, speed: Float,
    model: DanmakuViewModel, visible: Boolean, preview: Boolean) {
    val state by model.state.collectAsState()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(selection?.details?.work?.id, selection?.partId) { model.setVideo(selection) }
    val active = selection != null && state.preferences.enabled && lifecycle.isAtLeast(Lifecycle.State.STARTED)
    LaunchedEffect(active) {
        if (active) while (true) {
            position = player.positionMs
            model.advance(position)
            delay(250)
        }
    }
    if (active && visible) DanmakuOverlay(
        comments = state.comments, positionMs = (position - state.preferences.offsetMs).coerceAtLeast(0),
        isPlaying = !player.isPaused, buffering = player.isBuffering && !player.isPaused, speed = speed,
        visible = true, settings = state.preferences.rendering,
        previewMode = preview, areaFraction = state.preferences.area,
        datasetKey = "${model.datasetKey}|${state.preferences.offsetMs}", modifier = Modifier.fillMaxSize(),
    )
}
