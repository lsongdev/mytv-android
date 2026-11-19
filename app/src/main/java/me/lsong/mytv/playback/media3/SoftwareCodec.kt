package me.lsong.mytv.playback.media3

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/** 名称以 OMX.google. 或 c2.android. 开头者为系统软件解码器（大小写不敏感）。 */
fun isSoftwareCodec(name: String): Boolean {
    val n = name.lowercase()
    return n.startsWith("omx.google.") || n.startsWith("c2.android.")
}

/** 把系统软件解码器排到候选列表最前的选择器；其余顺序由 DEFAULT 决定（稳定排序）。 */
@OptIn(UnstableApi::class)
val SoftwareFirstSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
    MediaCodecSelector.DEFAULT
        .getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
        .sortedByDescending { isSoftwareCodec(it.name) }
}
