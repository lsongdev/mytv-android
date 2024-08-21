package me.lsong.mytv.ui.player

import android.view.SurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import me.lsong.mytv.rememberLeanbackChildPadding
import me.lsong.mytv.ui.components.LeanbackVideoPlayerMetadata
import me.lsong.mytv.utils.Settings

@Composable
fun MyTvVideoScreen(
    modifier: Modifier = Modifier,
    state: LeanbackVideoPlayerState = rememberLeanbackVideoPlayerState(),
    aspectRatioProvider: () -> Settings.VideoPlayerAspectRatio,
    showMetadataProvider: () -> Boolean = { false },
) {
    val context = LocalContext.current
    val childPadding = rememberLeanbackChildPadding()

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = when (aspectRatioProvider()) {
                Settings.VideoPlayerAspectRatio.ORIGINAL -> Modifier
                Settings.VideoPlayerAspectRatio.ASPECT_16_9 -> Modifier.aspectRatio(16f / 9f)
                Settings.VideoPlayerAspectRatio.ASPECT_4_3 -> Modifier.aspectRatio( 4f / 3f)
                Settings.VideoPlayerAspectRatio.FULL_SCREEN -> {
                    val configuration = LocalConfiguration.current
                    Modifier.aspectRatio(configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat())
                }

            }.fillMaxSize().align(Alignment.Center),
            factory = { SurfaceView(context) },
            update = { surfaceView -> state.setVideoSurfaceView(surfaceView) },
        )

        LeanbackVideoPlayerErrorScreen(
            errorProvider = { state.error },
        )

        // Text(text = "$aspectRatio")

        if (showMetadataProvider()) {
            LeanbackVideoPlayerMetadata(
                modifier = Modifier.padding(start = childPadding.start, top = childPadding.top),
                metadata = state.metadata,
            )
        }
    }
}