package me.lsong.mytv.ui.input

import android.view.KeyEvent
import android.view.ViewConfiguration
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class BackKeyHandlerTest {
    private fun main(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    @Test fun shortBackFiresOnlyOnceOnRelease() {
        var shorts = 0; var longs = 0
        lateinit var handler: BackKeyHandler
        main {
            handler = BackKeyHandler({ shorts++ }, { longs++ })
            handler.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            assertEquals(0, shorts)
            handler.dispatch(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
            handler.dispatch(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
            assertEquals(1, shorts)
            assertEquals(0, longs)
        }
    }
    @Test fun heldBackExitsWithoutAlsoDispatchingShortBack() {
        var shorts = 0; var longs = 0
        val fired = CountDownLatch(1)
        lateinit var handler: BackKeyHandler
        main {
            handler = BackKeyHandler({ shorts++ }, { longs++; fired.countDown() })
            handler.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        }
        assertTrue(fired.await(ViewConfiguration.getLongPressTimeout() + 2000L, TimeUnit.MILLISECONDS))
        main {
            handler.dispatch(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
            assertEquals(0, shorts)
            assertEquals(1, longs)
        }
    }
    @Test fun losingTheWindowCancelsAHeldBack() {
        var actions = 0
        lateinit var handler: BackKeyHandler
        main {
            handler = BackKeyHandler({ actions++ }, { actions++ })
            handler.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            handler.cancel()
            handler.dispatch(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        Thread.sleep(ViewConfiguration.getLongPressTimeout() + 100L)
        main { assertEquals(0, actions) }
    }
}
