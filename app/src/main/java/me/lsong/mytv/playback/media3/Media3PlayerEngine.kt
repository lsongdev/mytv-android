package me.lsong.mytv.playback.media3

import me.lsong.mytv.playback.PlayerEngine
import me.lsong.mytv.playback.TimeWindowRetryBudget

import me.lsong.mytv.domain.playback.PlaybackRequest

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.util.EventLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.lsong.mytv.data.preferences.AppPreferences
import androidx.media3.common.PlaybackException as Media3PlaybackException

@OptIn(UnstableApi::class)
class Media3PlayerEngine(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
) : PlayerEngine(coroutineScope) {
    private val videoPlayer = ExoPlayer.Builder(
        context,
        DefaultRenderersFactory(context).setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
            .apply { if (AppPreferences.softwareDecode) setMediaCodecSelector(SoftwareFirstSelector) }
    ).build().apply {
        playWhenReady = true
    }

    private val contentTypeAttempts = mutableMapOf<Int, Boolean>()
    private val liveWindowRetries = TimeWindowRetryBudget(maxRetries = 1, windowMs = 60_000L)
    private var updatePositionJob: Job? = null
    private var request = PlaybackRequest("")

    @OptIn(UnstableApi::class)
    private fun prepare(uri: Uri, contentType: Int? = null) {
        val dataSourceFactory =
            DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory().apply {
                setDefaultRequestProperties(request.headers)
                setConnectTimeoutMs(AppPreferences.videoPlayerLoadTimeout.toInt())
                setReadTimeoutMs(AppPreferences.videoPlayerLoadTimeout.toInt())
                setKeepPostFor302Redirects(true)
                setAllowCrossProtocolRedirects(true)
            })

        val mediaItem = MediaItem.Builder().setUri(uri).setMimeType(request.mimeType).build()
        val mediaSource = when (val type = contentType ?: Util.inferContentTypeForUriAndMimeType(uri, request.mimeType)) {
            C.CONTENT_TYPE_HLS -> {
                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            }

            C.CONTENT_TYPE_RTSP -> {
                RtspMediaSource.Factory().createMediaSource(mediaItem)
            }

            C.CONTENT_TYPE_OTHER -> {
                ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            }

            else -> {
                triggerError(
                    PlaybackException.UNSUPPORTED_TYPE.copy(
                        errorCodeName = "${PlaybackException.UNSUPPORTED_TYPE.message}_$type"
                    )
                )
                null
            }
        }

        if (mediaSource != null) {
            contentTypeAttempts[contentType ?: Util.inferContentTypeForUriAndMimeType(uri, request.mimeType)] = true
            videoPlayer.playWhenReady = request.playWhenReady
            videoPlayer.setMediaSource(mediaSource)
            if (request.live) videoPlayer.seekToDefaultPosition() else videoPlayer.seekTo(request.startPositionMs)
            videoPlayer.prepare()
            triggerPrepared()
        }
        updatePositionJob?.cancel()
        updatePositionJob = null
    }

    private val playerListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            triggerResolution(videoSize.width, videoSize.height)
        }

        override fun onPlayerError(ex: Media3PlaybackException) {
            // 如果是直播加载位置错误，尝试重新播放
            if (ex.errorCode == Media3PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW &&
                request.live && liveWindowRetries.tryAcquire()) {
                // A single bounded refresh handles stale HLS windows; repeated
                // failures must advance to the next resource instead of looping.
                triggerPrepared()
                videoPlayer.seekToDefaultPosition()
                videoPlayer.prepare()
            } else if (ex.errorCode == Media3PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                triggerError(PlaybackException(ex.errorCodeName, ex.errorCode))
            }
            // 当解析容器不支持时，尝试使用其他解析容器
            else if (ex.errorCode == Media3PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED) {
                val uri = videoPlayer.currentMediaItem?.localConfiguration?.uri
                if (uri != null) {
                    if (contentTypeAttempts[C.CONTENT_TYPE_HLS] != true) {
                        prepare(uri, C.CONTENT_TYPE_HLS)
                    } else if (contentTypeAttempts[C.CONTENT_TYPE_OTHER] != true) {
                        prepare(uri, C.CONTENT_TYPE_OTHER)
                    } else {
                        triggerError(PlaybackException.UNSUPPORTED_TYPE)
                    }
                }
            } else {
                triggerError(
                    PlaybackException(ex.errorCodeName, ex.errorCode)
                )
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_BUFFERING) {
                triggerBuffering(true)
            } else if (playbackState == Player.STATE_READY) {
                triggerReady()

                updatePositionJob?.cancel()
                updatePositionJob = coroutineScope.launch {
                    triggerCurrentPosition(-1, videoPlayer.isPlaying)
                    while (true) {
                        triggerCurrentPosition(videoPlayer.currentPosition, videoPlayer.isPlaying)
                        delay(1000)
                    }
                }
            }

            if (playbackState != Player.STATE_BUFFERING) {
                triggerBuffering(false)
            }
        }
    }

    private val metadataListener = @UnstableApi object : AnalyticsListener {
        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            metadata = metadata.copy(
                videoMimeType = format.sampleMimeType ?: "",
                videoWidth = format.width,
                videoHeight = format.height,
                videoColor = format.colorInfo?.toLogString() ?: "",
                // TODO 帧率、比特率目前是从tag中获取，有的返回空，后续需要实时计算
                videoFrameRate = format.frameRate,
                videoBitrate = format.bitrate,
            )
            triggerMetadata(metadata)
        }

        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            metadata = metadata.copy(videoDecoder = decoderName)
            triggerMetadata(metadata)
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            metadata = metadata.copy(
                audioMimeType = format.sampleMimeType ?: "",
                audioChannels = format.channelCount,
                audioSampleRate = format.sampleRate,
            )
            triggerMetadata(metadata)
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            metadata = metadata.copy(audioDecoder = decoderName)
            triggerMetadata(metadata)
        }
    }

    private val eventLogger = EventLogger()

    override fun initialize() {
        super.initialize()
        videoPlayer.addListener(playerListener)
        videoPlayer.addAnalyticsListener(metadataListener)
        if (AppPreferences.debugShowVideoPlayerMetadata) videoPlayer.addAnalyticsListener(eventLogger)
    }

    override fun release() {
        videoPlayer.removeListener(playerListener)
        videoPlayer.removeAnalyticsListener(metadataListener)
        videoPlayer.removeAnalyticsListener(eventLogger)
        updatePositionJob?.cancel()
        videoPlayer.release()
        super.release()
    }

    @UnstableApi
    override fun prepare(request: PlaybackRequest) {
        resetMonitoring()
        liveWindowRetries.reset()
        this.request = request
        contentTypeAttempts.clear()
        prepare(Uri.parse(request.uri))
    }

    override var speed: Float
        get() = videoPlayer.playbackParameters.speed
        set(value) { videoPlayer.setPlaybackSpeed(value) }

    override val positionMs: Long get() = videoPlayer.currentPosition
    override val durationMs: Long get() = videoPlayer.duration.coerceAtLeast(0)
    override fun seekTo(positionMs: Long) { videoPlayer.seekTo(positionMs.coerceAtLeast(0)) }

    override fun play() {
        triggerResumed()
        videoPlayer.play()
    }

    override fun pause() {
        triggerPaused()
        videoPlayer.pause()
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView) {
        videoPlayer.setVideoSurfaceView(surfaceView)
    }
}
