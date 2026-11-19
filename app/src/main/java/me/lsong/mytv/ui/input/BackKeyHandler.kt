package me.lsong.mytv.ui.input

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.ViewConfiguration

/** Consume the entire gesture so releasing a long Back never also toggles the menu. */
class BackKeyHandler(
    private val onShortPress: () -> Unit,
    private val onLongPress: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var pressed = false
    private var longPressed = false
    private val hold = Runnable {
        if (pressed && !longPressed) {
            longPressed = true
            onLongPress()
        }
    }

    fun dispatch(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0 && !pressed) {
                pressed = true
                longPressed = false
                handler.postDelayed(hold, ViewConfiguration.getLongPressTimeout().toLong())
            }
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(hold)
                val short = pressed && !longPressed && !event.isCanceled
                pressed = false
                longPressed = false
                if (short) onShortPress()
            }
        }
        return true
    }

    fun cancel() {
        handler.removeCallbacks(hold)
        pressed = false
        longPressed = false
    }
}
