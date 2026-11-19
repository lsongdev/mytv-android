package me.lsong.mytv.playback.danmaku

import android.graphics.Paint

/** 渲染线程私有：用一个 Paint 测文本宽度。仅本线程调用，无需同步。 */
class PaintTextMeasurer : TextMeasurer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun measure(text: String, textSizePx: Float): Float {
        paint.textSize = textSizePx
        return paint.measureText(text)
    }
}
