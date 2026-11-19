package me.lsong.mytv.playback.ui

import me.lsong.mytv.playback.PlaybackState
import me.lsong.mytv.playback.rememberPlaybackState

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
import androidx.compose.ui.viewinterop.AndroidView
import me.lsong.mytv.rememberScreenPadding

import me.lsong.mytv.data.preferences.AppPreferences

@Composable
fun PlayerSurface(
    modifier: Modifier = Modifier,
    state: PlaybackState = rememberPlaybackState(),
    aspectRatioProvider: () -> AppPreferences.VideoPlayerAspectRatio,
    showMetadataProvider: () -> Boolean = { false },
) {
    val context = LocalContext.current
    val childPadding = rememberScreenPadding()

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = when (aspectRatioProvider()) {
                AppPreferences.VideoPlayerAspectRatio.ORIGINAL -> Modifier
                AppPreferences.VideoPlayerAspectRatio.ASPECT_16_9 -> Modifier.aspectRatio(16f / 9f)
                AppPreferences.VideoPlayerAspectRatio.ASPECT_4_3 -> Modifier.aspectRatio( 4f / 3f)
                AppPreferences.VideoPlayerAspectRatio.FULL_SCREEN -> {
                    val configuration = LocalConfiguration.current
                    Modifier.aspectRatio(configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat())
                }

            }.fillMaxSize().align(Alignment.Center),
            factory = { SurfaceView(context) },
            update = { surfaceView -> state.setVideoSurfaceView(surfaceView) },
        )

        PlayerErrorOverlay(
            errorProvider = { state.error },
        )

        // Text(text = "$aspectRatio")

        if (showMetadataProvider()) {
            PlayerMetadataOverlay(
                modifier = Modifier.padding(start = childPadding.start, top = childPadding.top),
                metadata = state.metadata,
            )
        }
    }
}
